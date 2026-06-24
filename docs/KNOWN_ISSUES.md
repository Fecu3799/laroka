# Known Issues

## Tokens JWT activos no se invalidan al desactivar un usuario (US-11-05)

**Contexto:** Al marcar un `StaffUser` como `active = false` via `PATCH /backoffice/staff-users/{id}/status`, los JWT ya emitidos para ese usuario **no son invalidados de inmediato**.

**Por qué:** La `TokenBlacklist` actual solo soporta invalidación por token individual (string completo del JWT). No existe un índice por `userId` que permita revocar todos los tokens de un usuario específico. Agregar esa capacidad requeriría persistir los tokens activos (base de datos o Redis), lo que agrega complejidad significativa no justificada en el contexto actual de despliegue single-instance.

**Impacto:** Un usuario desactivado puede seguir operando con su JWT vigente hasta su expiración natural (máximo `JWT_EXPIRATION`, por defecto 8 horas). Los refresh tokens del usuario sí pueden revocarse via `AuthService.revokeAllRefreshTokens`, pero el endpoint `PATCH /{id}/status` no lo hace (tampoco está en el scope de US-11-05).

**Workaround manual:** Llamar a `POST /auth/logout` con el JWT del usuario afectado (si se conoce el token) para agregarlo a la blacklist. Alternativamente, esperar la expiración natural.

**Resolución planificada:** Implementar revocación por userId en una historia posterior si el negocio lo requiere, usando persistencia de tokens activos en DB o migrando la blacklist a Redis.
