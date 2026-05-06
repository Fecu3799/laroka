# Convención de colores — client

Fuente de verdad: `client/src/index.css`

---

## Variables globales

| Variable | Dark | Light | Uso |
|---|---|---|---|
| `--bg-primary` | `#000000` | `#f5f5f0` | Fondo base de todas las pantallas |
| `--bg-secondary` | `#095E2F` | `#d4e8d9` | Paneles, hero, bottom nav, search area |
| `--bg-card` | `#0F954B` | `#e8f5e8` | Cards de selección de sucursal |
| `--bg-card-product` | `#061A0F` | `#d4e8d9` | Cards de producto, panel de detalle |
| `--text-primary` | `#ffffff` | `#1a1a1a` | Texto principal |
| `--accent` | `#FECD18` | `#d4a800` | Precios, tab activo, botones add, badges, CTA label |
| `--border` | `#65A369` | `#4a9d5a` | Bordes generales, texto de tamaño en detail screen |
| `--green-cta` | `#0d7838` | *(no definida)* | Gradiente de botones primarios (junto a `--bg-secondary`) |

---

## Colores hardcodeados en uso

Valores que aparecen en los archivos CSS pero no tienen variable global aún.

| Valor | Archivos | Uso |
|---|---|---|
| `#071a0f` | `App.css` | Fondo de cart items, subfooter, counter — variante de `--bg-card-product` |
| `#00c853` | `App.css` | Botón "añadido" en extra cards (estado confirmado) |
| `rgba(255, 60-100, 60-100, X)` | `App.css` | Acciones destructivas: delete item, vaciar carrito, confirmar |
| `#f87171` | `App.css` | Texto del botón de confirmación de borrado |

---

## Variables locales de módulo

`CheckoutScreen.module.css` define sus propias variables con prefijo `--co-*`.
La mayoría son aliases de las globales; las únicas con valor propio:

| Variable | Valor | Equivalente global |
|---|---|---|
| `--co-card` | `#071a0f` | `--bg-card-product` (levemente distinto) |
| `--co-text-sec` | `rgba(255,255,255,0.45)` | Sin equivalente |
| `--co-section-lbl` | `rgba(254,205,24,0.70)` | `--accent` con 70% de opacidad |

---

## Jerarquía visual

```
--bg-primary        negro puro     → base de pantalla
--bg-secondary      verde oscuro   → superficie elevada
--bg-card           verde medio    → cards de selección
--bg-card-product   verde profundo → cards de contenido / detail
--green-cta         verde CTA      → fondo de botones de acción primaria
--border            verde claro    → bordes y texto de contexto
--accent            gold           → toda la interactividad visible
--text-primary      blanco/negro   → texto
(sin variable)      rojo           → acciones destructivas
```
