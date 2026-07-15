# Known Issues

## Tokens JWT activos no se invalidan al desactivar un usuario (US-11-05)

**Contexto:** Al marcar un `StaffUser` como `active = false` via `PATCH /backoffice/staff-users/{id}/status`, los JWT ya emitidos para ese usuario **no son invalidados de inmediato**.

**Por qué:** La `TokenBlacklist` actual solo soporta invalidación por token individual (string completo del JWT). No existe un índice por `userId` que permita revocar todos los tokens de un usuario específico. Agregar esa capacidad requeriría persistir los tokens activos (base de datos o Redis), lo que agrega complejidad significativa no justificada en el contexto actual de despliegue single-instance.

**Impacto:** Un usuario desactivado puede seguir operando con su JWT vigente hasta su expiración natural (máximo `JWT_EXPIRATION`, por defecto 8 horas). Los refresh tokens del usuario sí pueden revocarse via `AuthService.revokeAllRefreshTokens`, pero el endpoint `PATCH /{id}/status` no lo hace (tampoco está en el scope de US-11-05).

**Workaround manual:** Llamar a `POST /auth/logout` con el JWT del usuario afectado (si se conoce el token) para agregarlo a la blacklist. Alternativamente, esperar la expiración natural.

**Resolución planificada:** Implementar revocación por userId en una historia posterior si el negocio lo requiere, usando persistencia de tokens activos en DB o migrando la blacklist a Redis.

## Auto-cierre de turno con pedidos activos: cancelación automática + reembolso total

**Contexto:** El auto-cierre de turno (`ShiftAutoCloseJob` → `WorkShiftService.autoCloseShift`) se dispara cuando un turno supera `max_shift_duration_minutes` sin cerrarse manualmente. A diferencia del cierre manual —que **bloquea** si hay pedidos activos sin resolver— el auto-cierre no puede pedirle nada al operador: corre desatendido.

**Política:** Al momento del auto-cierre, todo pedido en estado activo del turno (`RECEIVED`, `IN_PREPARATION`, `ON_THE_WAY`, `READY_FOR_PICKUP`) se **cancela automáticamente antes de cerrar el turno**. Así ningún pedido queda huérfano referenciando un turno `CLOSED` ni atrasado e invisible para el staff al abrirse el turno siguiente. El turno se cierra recién después de resolver todos los pedidos activos, y su `WorkShiftSummary` se calcula incluyéndolos como cancelados.

- **Motivo de cancelación:** se registra `"No se pudo procesar a tiempo"` (constante `OrderService.SHIFT_AUTO_CLOSE_CANCELLATION_REASON`), distinguible de una cancelación normal en el historial (`order_status_history`). Se notifica al cliente por el mismo canal que cualquier otra cancelación.
- **Reembolso TOTAL sin excepción:** si el pedido tenía un `Payment` con `status = APPROVED` y `method = MERCADOPAGO`, se dispara un reembolso **total** vía `PaymentGateway.refundPayment` (sin monto → reembolso completo) y el pago queda `REFUNDED`. La responsabilidad de no procesar a tiempo es **operativa** (del local), no del cliente; por eso el reembolso es del 100%.
- Un pago en **efectivo** no dispara reembolso (no hubo cobro electrónico que revertir).
- Si el gateway falla, el reembolso se loguea para acción manual y **no** aborta el cierre del turno (mismo patrón que el reembolso por race-condition del webhook de MercadoPago).

**Diferencia con Sprint 17:** esta política difiere del **reembolso parcial (85%)** que se implementará en Sprint 17 para **cancelaciones tardías iniciadas por el cliente**. En ese caso la responsabilidad es del cliente y el local retiene una fracción; acá, al ser responsabilidad operativa, el reembolso es total.
