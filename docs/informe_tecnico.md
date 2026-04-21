# LaRoka

## Introducción

### 1. Introducción al sistema

#### 1.1 Propósito del proyecto

El presente proyecto tiene como finalidad el desarrollo de un sistema de
software para la gestión digital de pedidos y operación comercial de una pizzería
con varias sucursales, concebido como base evolutiva hacia una plataforma
multi-pizzería.

#### 1.2 Alcance conceptual

El sistema se concibe como una solución orientada inicialmente a la **gestión
de pedidos** y al **seguimiento operativo desde el local** , con posibilidad de
ampliación futura tanto hacia nuevos subsistemas vinculados a la operación
gastronómica, como hacia un esquema de plataforma compartida por múltiples
pizzerías.

#### 1.3 Objetivo general

Proveer una solución que permita digitalizar, centralizar y organizar el flujo de
pedidos entre clientes y sucursales, estableciendo una base arquitectónica y
funcional apta para el crecimiento modular del sistema.

### 2. Descripción general del sistema

#### 2.1 Naturaleza del sistema

El sistema corresponde a un **sistema de información transaccional** , con
proyección a convertirse en un **sistema operacional de gestión gastronómica**.

#### 2.2 Contexto de negocio

La organización destinataria del sistema posee actualmente tres sucursales
(Playa Unión, Rawson y Madryn) y requiere una herramienta que permita:
● centralizar la recepción de pedidos,
● mejorar la operación interna,
● reducir errores en la gestión,
● disponer de trazabilidad sobre el estado de los pedidos,


● contar con una base digital para la futura incorporación de nuevos módulos
funcionales.
Además, manifiesta una visión de evolución futura hacia un ecosistema digital que
pueda ser utilizado por otras pizzerías bajo un modelo de plataforma.

#### 2.3 Enfoque evolutivo

El sistema será desarrollado de manera incremental, comenzando por una
primera versión funcional que cubra el proceso principal de negocio y habilite
posteriores iteraciones sobre la misma base tecnológica y conceptual.

### 3. Alcance del sistema

#### 3.1 Alcance de la primera versión

La primera entrega del sistema abarcará los siguientes componentes:
3.1.1 Subsistema cliente
Interfaz orientada al cliente final para:
● consultar menú,
● seleccionar productos,
● conformar un pedido,
● pagar el pedido,
● enviarlo a una sucursal.
3.1.2 Subsistema backoffice
Interfaz orientada al personal del local para:
● visualizar y gestionar pedidos recibidos,
● identificar su estado,
● gestionar su avance operativo.

#### 3.2 Alcance evolutivo

En etapas posteriores, el sistema podrá incorporar:
● registro de clientes que permita persistencia de datos, acceso a
historial de pedidos y funcionalidades promocionales,
● gestión de cocina y comandas,
● gestión de stock y disponibilidad,
● administración de menú y promociones,
● dashboard de métricas en tiempo real,
● control operativo por sucursal,
● funcionalidades administrativas avanzadas.
● incorporación de múltiples pizzerías.


```
● administración por comercio.
● separación operativa por negocio.
● eventual modelo de comisión / uso de plataforma
```
### 4. Dominio del problema

#### 4.1 Dominio principal

Desde la perspectiva de modelado, el sistema se ubica dentro del dominio:
**_Gestión Operativa de Pedidos para Plataforma Gastronómica especializada_**

#### 4.2 Subdominios identificados

A nivel de análisis, se reconocen los siguientes subdominios principales:
**● Catálogo:** comprende la definición y organización de productos,
categorías, precios y posibles configuraciones del menú.
**● Pedidos:** comprende la creación, recepción, seguimiento y
actualización del ciclo de vida de cada pedido.
**● Sucursales:** comprende la identificación de los puntos de operación
física del negocio y su vinculación con los pedidos.
**● Pagos:** comprende el registro y control del medio de pago y del estado
asociado a la transacción.
**● Usuarios internos:** comprende a los actores del negocio que
interactúan con el sistema desde el backoffice.
**● Operación futura:** incluye cocina, comandas, stock, métricas y
administración, como extensiones funcionales del sistema base.
**● Pizzería:** comprende la identificación de cada negocio participante de
la plataforma, su configuración operativa y su relación con sucursales,
catálogos, pedidos y usuarios.

### 5. Actores del sistema

#### 5.1 Actor primario externo

**Cliente:** interactúa con el sistema para consultar productos y generar
pedidos.

#### 5.2 Actores primarios internos

**Personal del local / Caja:** interactúa con el sistema para recibir, visualizar y
actualizar pedidos y pagos.


#### 5.3 Actores secundarios futuros

**Cocinero:** podrá interactuar con módulos futuros vinculados a producción y
comandas.
**Administrador / Dueño:** podrá interactuar con módulos futuros vinculados a
métricas, configuración y control general.
**Responsable del comercio adherido.
Administrador de plataforma.**

### 6. Objetivo del sistema

#### 6.1 Objetivo funcional principal

Permitir la gestión digital del flujo de pedidos desde su creación por parte del
cliente hasta su visualización y tratamiento inicial en el local.

#### 6.2 Objetivos específicos

```
● Centralizar la información de los pedidos.
● Mejorar la trazabilidad del proceso operativo.
● Brindar soporte a múltiples sucursales.
● Sentar una base funcional y técnica para futuras expansiones.
● Organizar el dominio del negocio en módulos claramente
identificables.
● Permitir evolución futura hacia un modelo de plataforma para múltiples
pizzerías.
```
### 7. Modelo conceptual inicial

#### 7.1 Entidades conceptuales preliminares

En términos UML, se identifican como clases conceptuales iniciales:
**● Pizzería
● Sucursal
● Producto
● Categoría
● Pedido
● DetallePedido
● Pago
● UsuarioInterno
● Cliente**


#### 7.2 Relaciones conceptuales preliminares

```
● Un Cliente genera uno o varios Pedidos.
● Un Pedido se compone de uno o varios DetallesPedido.
● Cada DetallePedido referencia un Producto.
● Un Producto pertenece a una Categoría.
● Un Pedido se asocia a una Sucursal.
● Un Pedido se vincula a un único Pago.
● Un UsuarioInterno gestiona pedidos dentro de una Sucursal.
● Una Sucursal pertenece a una Pizzería.
```
#### 7.3 Flujo principal del negocio

A nivel de caso de uso, el flujo principal puede resumirse de la siguiente
manera:
● El cliente consulta el catálogo.
● El cliente selecciona productos.
● El cliente inicia el pago
● El pago se aprobó
● El sistema crea y registra el pedido.
● El sistema registra el pedido y lo asocia a una sucursal.
● El personal del local visualiza el pedido.
● El personal actualiza el estado operativo del pedido.

### 8. Visión arquitectónica inicial

#### 8.1 Estilo arquitectónico

El sistema será abordado bajo una arquitectura de tipo:
**Monolito modular**.

#### 8.2 Criterio de diseño

La solución será estructurada de manera modular, separando
responsabilidades de negocio y permitiendo la evolución incremental del sistema sin
comprometer la coherencia global.

#### 8.3 Stack tecnológico base

```
● Backend: Java + Spring Boot.
● Frontend cliente: PWA al inicio (futura implementación nativa).
● Frontend interno: aplicación web para backoffice.
● Base de datos: PostgreSQL
```

#### 8.4 Criterio de escalabilidad

La escalabilidad del sistema se plantea inicialmente en términos:
● funcionales,
● modulares,
● organizativos,
● de soporte a múltiples sucursales.

### 9. Criterios de análisis para la siguiente etapa

#### 9.1 Relevamiento de requisitos

A partir de esta definición inicial, la siguiente etapa del proyecto deberá
profundizar en:
● requerimientos funcionales,
● requerimientos no funcionales,
● reglas de negocio,
● restricciones operativas,
● identificación precisa de actores,
● casos de uso,
● historias de usuario,
● prioridades del sistema.

#### 9.2 Enfoque de modelado

Durante el análisis se deberá avanzar en:
● diagrama de casos de uso,
● modelo de dominio,
● diagramas de actividad de procesos clave,
● especificación de escenarios principales y alternativos,
● identificación de estados del pedido,
● definición de responsabilidades por subsistema.


## Análisis de Requisitos

### 1. Requisitos Funcionales

#### 1.1 Subsistema Cliente

**Catálogo:
● RF-CL-00:** El sistema debe permitir al cliente seleccionar una sucursal
antes de consultar el menú disponible.
**● RF-CL-01:** El sistema debe permitir al cliente visualizar el catálogo de
productos organizado por categorías.
**● RF-CL-02:** El sistema debe mostrar para cada producto:
○ nombre
○ descripción
○ foto
○ precio vigente
○ categoría
○ disponibilidad
**● RF-CL-03:** El sistema debe permitir al cliente elegir una sucursal como
favorita para evitar re-seleccionar en ingresos futuros.
**● RF-CL-15:** El sistema debe permitir al cliente cambiar de sucursal
cuando éste lo desee.
**Pedido:
● RF-CL-04:** El sistema debe permitir al cliente agregar productos a un
pedido.
**● RF-CL-05:** El sistema debe permitir modificar cantidades de productos
dentro del pedido.
**● RF-CL-06:** El sistema debe permitir eliminar productos del pedido.
**● RF-CL-07:** El sistema debe calcular automáticamente el total del
pedido.
**● RF-CL-13:** El sistema debe permitir al cliente enviar notas sobre el
pedido.
**● RF-CL-16:** El sistema debe permitir definir la modalidad del pedido.
**● RF-CL-19:** El sistema debe permitir la solicitud de cancelación del
pedido.
**Pago:
● RF-CL-10:** El sistema debe permitir al cliente iniciar el pago del pedido
mediante un proveedor externo (MercadoPago).
**● RF-CL-11:** El sistema debe reflejar el estado del pago en el pedido:
○ pendiente
○ aprobado
○ rechazado


**● RF-CL-17:** El sistema debe permitir definir la modalidad de pago
**Confirmación:
● RF-CL-12:** El sistema debe registrar el pedido únicamente cuando el
pago haya sido aprobado.
**● RF-CL-18:** El sistema debe mostrar una confirmación del pedido
generado.
**● RF-CL-14:** El sistema debe mostrar el estado actual del pedido.
**● RF-CL-20:** El sistema debe permitir al cliente consultar el seguimiento
del pedido actual.

#### 1.2 Subsistema Backoffice

**Recepción de pedidos:
● RF-BO-01:** El sistema debe permitir al personal visualizar pedidos
recibidos en su sucursal.
**● RF-BO-02:** El sistema debe mostrar por cada pedido:
○ identificador
○ fecha y hora de creación
○ detalle del pedido
○ total
○ estado del pago
○ estado del pedido
○ notas
**● RF-BO-03:** El sistema debe permitir ordenar o filtrar los pedidos.
**● RF-BO-04:** El sistema debe permitir al personal crear pedidos
manuales desde el backoffice para clientes que se acerquen al local,
llamen por teléfono o contacten por whatsapp.
**● RF-BO-05:** El sistema debe permitir seleccionar medio de pago al
crear un pedido manual: Efectivo o MercadoPago QR.
**● RF-BO-06:** Si el medio de pago es MercadoPago QR, el sistema debe
asociar el monto del pedido al QR fijo de la sucursal para que el cliente
pueda escanearlo y pagar.
**Gestión del pedido:
● RF-BO-07:** El sistema debe permitir visualizar y actualizar el estado
del pedido.
○ recibido
○ en preparación
○ en camino
○ entregado
**● RF-BO-08:** El sistema debe registrar la fecha y hora de cada cambio
de estado del pedido.
**● RF-BO-09:** El sistema debe permitir visualizar y gestionar notas
asociadas al pedido.


**Trazabilidad:
● RF-BO-10:** El sistema debe mantener un historial de pedidos.

### 2. Reglas de Negocio

#### 2.1 Reglas sobre pedidos

```
● RN-01: Un pedido con modalidad de pago MERCADOPAGO solo
existe si el pago fue aprobado. Un pedido con modalidad CASH pasa
directamente a estado RECEIVED al ser confirmado.
● RN-02: Un pedido pertenece a una única sucursal.
● RN-03: Un pedido debe contener al menos un producto.
● RN-04: El total del pedido debe ser mayor a cero.
● RN-05: El precio registrado en el pedido debe corresponder al valor
vigente del producto al momento de su confirmación.
● RN-06: Una vez confirmado el pedido, su detalle no debe modificarse
desde la interfaz cliente.
● RN-07: Una vez armado un carrito con el pedido, si se cambia de
sucursal, el sistema debe avisar al cliente antes de avanzar.
● RN-18: La modalidad de los pedidos son DELIVERY y TAKEAWAY
● RN-19: Si modalidad = DELIVERY → dirección de entrega obligatoria
● RN-20: Si modalidad = TAKEAWAY → no requiere dirección
● RN-28: Un pedido manual creado desde backoffice tiene el campo
“origin” con valor BACKOFFICE. Un pedido creado desde la app
cliente tiene CLIENT. Esto permite distinguir el canal de origen en
reportes y trazabilidad.
```
#### 2.2 Reglas sobre pagos

```
● RN-08: Cada pedido con modalidad MERCADOPAGO debe vincularse
a un único pago.
● RN-09: Un pedido con modalidad MERCADOPAGO no debe avanzar
si su pago no está aprobado. Un pedido con modalidad CASH no
requiere validación de pago externo.
● RN-10: El estado del pago es independiente del estado operativo.
```

```
● RN-11: La creación del pedido debe depender exclusivamente de la
aprobación del pago.
● RN-21: Las modalidades de pago son MERCADOPAGO y EFECTIVO
● RN-22: Si modalidad = MERCADOPAGO → se inicia el flujo de pago
con el proveedor externo
● RN-23: Si modalidad = EFECTIVO → el pedido se crea sin un pago
asociado. Pasa directamente a RECEIVED.
● RN-24: Cada sucursal tiene un QR fijo de MercadoPago asociado a su
caja. Este QR no cambia entre pedidos.
● RN-25: Al confirmar un pedido manual con MPQR, el sistema debe
registrar el monto pendiente en el QR correspondiente via API de MP
● RN-26: El pedido manual con MPQR queda en PENDING_PAYMENT
hasta que el webhook de MP confirme el pago.
● RN-27: Solo un pedido puede tener el QR de una sucursal activo a la
vez. Si el operador inicia un nuevo cobro QR antes de que el anterior
sea pagado o cancelado, el sistema debe cancelar el cobro anterior en
MP antes de registrar uno nuevo.
```
#### 2.3 Reglas sobre operación

```
● RN-12: El personal del local solo debe visualizar y gestionar pedidos
de su propia sucursal.
● RN-13: Todo cambio de estado de un pedido debe quedar registrado
con fecha y hora
● RN-14: Un pedido finalizado no debe poder seguir modificando su flujo
operativo normal.
```
#### 2.4 Reglas de cancelación

```
● RN-15: Un pedido solo podrá ser cancelado por el cliente mientras se
encuentre en estado Received.
● RN-16: Si el pedido se encuentra en estado In Preparation , la
cancelación deberá quedar sujeta a validación del local.
● RN-17: Un pedido no podrá cancelarse en estado On the Way.
```

### 3. Requisitos No Funcionales

#### 3.1 Usabilidad

```
● RNF-01: La interfaz cliente debe ser simple e intuitiva, permitiendo
completar un pedido sin necesidad de capacitación previa.
● RNF-02: La interfaz debe estar diseñada con enfoque responsive para
su uso prioritario desde dispositivos móviles.
● RNF-03: La interfaz del backoffice debe permitir al personal identificar
rápidamente pedido nuevos, en preparación y finalizados.
● RNF-04: La navegación principal del sistema debe minimizar la
cantidad de pasos necesarios para consultar menú, armar pedido y
completar el pago.
● RNF-33: Durante el horario no operativo del local, la interfaz cliente
solo debe mostrar el menú, sin permitir agregar productos al carrito.
```
#### 3.2 Rendimiento

```
● RNF-05: El sistema debe responder en tiempos adecuados para
operación transaccional habitual.
● RNF-06: Las operaciones de consulta de menú y visualización de
pedidos no deben degradar la experiencia del usuario bajo carga
normal.
● RNF-07: La actualización de estados de pedidos en backoffice debe
reflejarse con baja latencia operativa.
```
#### 3.3 Disponibilidad y continuidad operativa

```
● RNF-08: El sistema debe estar disponible durante el horario operativo
del negocio.
● RNF-09: El sistema debe tolerar fallos transitorios de red mostrando
mensajes claros al usuario y evitando pérdidas silenciosas de
información.
● RNF-10: El sistema debe preservar la integridad del pedido aun
cuando falle temporalmente la confirmación del pago o la
comunicación con servicios externos.
```

#### 3.4 Seguridad

```
● RNF-11: El acceso al backoffice debe requerir autenticación de
usuarios internos.
● RNF-12: El sistema debe restringir el acceso de cada usuario interno
según su rol y sucursal asignada.
● RNF-13: La información sensible intercambiada entre cliente,
backoffice y backend debe transmitirse mediante canales seguros.
● RNF-14: El sistema no debe almacenar datos de pago sensibles que
correspondan al proveedor externo, salvo identificadores o estados
necesarios para trazabilidad.
```
#### 3.5 Integridad y consistencia de datos

```
● RNF-15: El sistema debe garantizar consistencia entre pedido, detalle,
sucursal y pago asociado.
● RNF-16: Cada pedido debe contar con identificador único y
trazabilidad de eventos principales.
● RNF-17: Las operaciones críticas del sistema deben evitar duplicación
involuntaria de pedidos o registros de pago.
```
#### 3.6 Escalabilidad y mantenibilidad

```
● RNF-18: El sistema debe desarrollarse bajo una arquitectura modular
que facilite la incorporación futura de nuevos subsistemas.
● RNF-19: La solución debe permitir evolución funcional sin requerir
rediseño completo de los módulos base de catálogo, pedidos, pagos y
sucursales.
● RNF-20: La estructura del sistema debe favorecer mantenibilidad,
separación de responsabilidades y crecimiento incremental.
```
#### 3.7 Compatibilidad y despliegue

```
● RNF-21: La aplicación cliente debe poder ejecutarse correctamente en
navegadores modernos de uso común en dispositivos móviles y de
escritorio.
```

```
● RNF-22: El backoffice debe operar correctamente en navegadores
web modernos de escritorio.
● RNF-23: La solución debe soportar múltiples sucursales sobre una
misma base de negocio y datos estructurados.
```
#### 3.8 Auditoría y trazabilidad

```
● RNF-24: El sistema debe registrar eventos relevantes del ciclo de vida
del pedido.
● RNF-25: Los cambios de estado y eventos de pago deben ser
trazables para control operativo y resolución de incidencias.
```
#### 3.9 Calidad operativa

```
● RNF-26: El sistema debe mostrar mensajes de error claros ante fallos.
● RNF-27: El sistema debe evitar acciones ambiguas o duplicadas ante
múltiples intentos sobre una misma operación crítica.
● RNF-28: La información mostrada en cliente y backoffice debe
mantenerse alineada respecto al estado actual del pedido.
● RNF-29: La aplicación cliente deberá actualizar automáticamente el
estado del pedido mediante pooling, sin requerir recarga manual.
● RNF-30: La interfaz de backoffice deberá reflejar en tiempo real la
llegada de nuevos pedidos y los cambios de estado relevantes.
● RNF-31: El mecanismo de actualización en tiempo real deberá limitar
la visualización de eventos a la sucursal correspondiente del usuario
interno autenticado.
● RNF-32: Ante fallas temporales de conectividad, la interfaz de
backoffice deberá poder recuperar la sincronización de pedidos al
restablecer la conexión.
```

## Diseño del Sistema

### 1. Objetivo del sistema

La presente sección tiene como objetivo definir la estructura técnica y
conceptual del sistema a partir de los requisitos relevados, estableciendo una base
sólida para su implementación y evolución.
Se busca:
● traducir los RF y RNF en una solución técnica coherente
● definir la arquitectura general del sistema
● identificar los módulos principales y sus responsabilidades
● establecer un modelo conceptual inicial del dominio
● definir los flujos principales de operación
● preparar la solución para una evolución futura hacia una plataforma
multi-pizzería

### 2. Principios de diseño

```
El diseño del sistema se basa en los siguientes principios:
● Simplicidad inicial: la primera versión prioriza el problema principal
sin introducir complejidad innecesaria.
● Monolito modular: se adopta una arquitectura monolítica con
separación clara de módulos para facilitar evolución.
● Separación de responsabilidades: cliente, backoffice y backend se
encuentran desacoplados funcionalmente.
● Evolución incremental: el sistema está pensado para crecer por
etapas sin necesidad de rediseño completo.
● Escalabilidad conceptual: aunque la primera versión está enfocada
en una pizzería, el diseño contempla su evolución hacia múltiples
comercios.
● Consistencia operativa: las reglas de negocio y estados del sistema
deben mantenerse coherentes en todos los flujos.
```

```
● Bajo acoplamiento: los módulos deben interactuar de manera
controlada y bien definida.
```
### 3. Arquitectura general del sistema

#### 3.1 Estilo arquitectónico

El sistema se implementará bajo una arquitectura de tipo **monolito modular** ,
en la cual un único backend centraliza la lógica de negocio, organizado en módulos
independientes.
Este enfoque permite:
● reducir complejidad inicial
● acelerar el desarrollo de la primera versión
● facilitar el mantenimiento
● permitir una evolución futura hacia arquitecturas más distribuidas si
fuese necesario

#### 3.2 Contenedores del sistema

El sistema se compone de los siguientes contenedores principales:
**● Aplicación cliente (PWA - Vercel):** interfaz utilizada por el cliente
para consultar el menú, realizar pedidos y efectuar pagos.
**● Aplicación backoffice (web - Vercel):** interfaz utilizada por el
personal del local para gestionar pedidos y su estado.
**● Backend (Render):** Núcleo del sistema, responsable de:
○ lógica del negocio
○ gestión de pedidos
○ integración con pagos
○ coordinación entre cliente y backoffice
**● Base de datos:** almacena la información persistente del sistema.
**● Proveedor de pagos externo:** servicio externo encargado de
procesar los pagos.
**● Storage bucket (Cloudflare R2):** Servicio externo encargado de
almacenar multimedia.


#### 3.3 Comunicación entre componentes

```
● Cliente ↔ Backend → API HTTP
● Backoffice ↔ Backend → API HTTP
● Backend ↔ Base de datos → conexión persistente
● Backend ↔ Proveedor de pagos → integración externa
● Backend → Backoffice → actualización en tiempo real
● Cliente → Backend → consultas periódicas (polling)
```
#### 3.4 Criterio de despliegue

El sistema se desplegará en infraestructura cloud, separando:
● Frontend cliente
● Frontend backoffice
● Backend
● Base de datos
● Almacenamiento multimedia (Amazon S3)
Esto permite:
● escalabilidad independiente


```
● facilidad de mantenimiento
● disponibilidad continua
```
#### 3.5 Criterio de escalabilidad

La escalabilidad se plantea en tres dimensiones:
**● Funcional:** incorporación de nuevos módulos (stock, cocina, métricas,
etc.)
**● Modular:** extensión de funcionalidades sin afectar el núcleo
**● Organizativa:** evolución hacia soporte de múltiples pizzerías

### 4. Diseño por subsistemas

#### 4.1 Subsistema Cliente

Responsable de:
● selección de sucursal
● consulta de menú
● armado de pedido
● pago
● seguimiento del pedido
Características:
● interfaz simple y mobile-first
● actualización mediante polling
● sin necesidad de autenticación en v

#### 4.2 Subsistema Backoffice

● visualización de pedidos
● actualización de estados
● control operativo
Características:
● interfaz en tiempo real
● filtrado y ordenamiento de pedidos
● acceso restringido por sucursal


#### 4.3 Subsistema de pedidos: Nucleo del sistema

```
● creación del pedido (post pago aprobado)
● gestión del ciclo de vida
● control de estados
```
#### 4.4 Subsistema de catálogo

```
● gestión de productos
● categorías
● precios
● disponibilidad por sucursal
```
#### 4.5 Subsistema de pagos

El sistema soporta dos modalidades de pago presencial en caja: Efectivo y
MercadoPago QR Modelo Atenidido. En el caso del QR, cada sucursal tiene
asociado un QR fijo impreso en caja. Al confirmar un pedido con esta modalidad, el
backend realiza un POST a la API de MercadoPago para registrar el monto
pendiente en el QR de la sucursal. El cliente escanea el QR y completa el pago
desde su dispositivo. La confirmación llega al sistema vía webhook, siguiendo el
mismo flujo que los pagos digitales.
● iniciar pagos
● recibir confirmación
● validar aprobación
● habilitar creación del pedido

#### 4.6 Subsistema de sucursales

```
● identificar puntos de operación
● asociar pedidos
● definir disponibilidad
```
#### 4.7 Subsistema de usuarios internos

```
● acceso al sistema
● gestión por sucursal
```

```
● control de permisos
```
#### 4.8 Extensibilidad futura (multi-pizzeria)

El sistema se diseña contemplando:
● incorporación de múltiples pizzerías
● separación lógica por comercio
● gestión independiente por negocio
● posibilidad de modelo de plataforma

### 5. Modelo de dominio inicial

#### 5.1 Entidades principales

```
● Pizzería (futuro)
● Sucursal
● Producto
● Categoría
● Pedido
● DetallePedido
● Pago
● UsuarioInterno
● Cliente
```
#### 5.2 Relaciones

```
● Un Pedido pertenece a una Sucursal
● Un Pedido contiene múltiples Detalles
● Cada Detalle referencia a un Producto
● Un Producto pertenece a una Categoría
● Un Pedido se vincula a un Pago
● Un UsuarioInterno opera sobre una Sucursal
```

#### 5.3 Consideraciones futuras

```
● incorporación de entidad Pizzería como nivel superior
● asociación Pedido → Pizzería + Sucursal
● segregación de datos por comercio
```
### 6. Flujos principales del sistema

#### 6.1 Flujo de compra

```
● el cliente define sucursal
● selecciona productos
● inicia el pago
```

```
● pago aprobado
● se crea el pedido
● se visualiza el seguimiento del pedido
● el pedido queda disponible en el backoffice
```
#### 6.2 Flujo operativo

```
● el local recibe el pedido
● actualiza estado
● el cliente visualiza los cambios
```
#### 6.3 Flujos alternativos

```
● pago rechazado
● cancelación solicitada
● cambio de sucursal
● pedido manual desde caja: el operador selecciona el producto,
modalidad (delivery/takeaway) y medio de pago. Si elige Efectivo, el
pedido pasa directo a RECEIVED. Si elige MPQR, el sistema activa el
```

```
QR de la sucursal con el monto y espera confirmación del webhook
antes de pasar a RECEIVED.
```
### 7. Estados del pedido

```
Estados definidos:
● recibido
● en preparación
● en camino
● entregado (terminal)
● cancelado (terminal)
```

```
Reglas:
● transición secuencial
● estados finales no reversibles
● visibilidad completa para cliente
```
### 8. Diseño de datos (nivel conceptual)

```
Se define:
● persistencia de pedidos, pagos, productos y sucursales
● integridad entre entidades
● trazabilidad de estados
● consistencia entre pagos y pedidos
```
### 9. Seguridad y acceso

```
● acceso público para cliente
● acceso autenticado para backoffice
● restricción por sucursal
● preparación para multi-comercio
```
### 10. Decisiones técnicas iniciales

```
● Monolito modular
● Frontend PWA para cliente (React + Vite)
● Frontend web para backoffice (React + Vite )
```

```
● Backend centralizado (Java Spring Boot)
● PostgreSQL
● Integración con MercadoPago
● Polling cliente / realtime backoffice
● QR Atendido de MercadoPago para pedidos en caja
```
### 11. Riesgos y decisiones abiertas

```
● Evolución hacia multi-pizzería
● Definición de módulos futuros
● Logística y envíos
● Modelo de monetización
● Gestión avanzada de usuarios
```
### 12. Relación con planificación

```
El diseño sirve como base para:
● definición de sprints
● priorización de funcionalidades
● implementación incremental
```

## Planificación

### 1. Introducción

La presente etapa tiene como objetivo organizar y estructurar el desarrollo del
sistema a partir de las definiciones obtenidas en las etapas de análisis y diseño,
estableciendo una hoja de ruta clara para su implementación incremental.
El enfoque adoptado se basa en una planificación iterativa e incremental,
estructurada en Sprints, donde cada iteración permite construir una porción
funcional del sistema, validarla y evolucionarla progresivamente.
A diferencia de un enfoque tradicional, el desarrollo del sistema estará
asistido por herramientas de Inteligencia Artificial, lo que introduce un cambio
significativo en la dinámica de construcción del software. En este contexto, el foco
de la planificación no se centra unicamente en la codificación manual, sino en:
● la correcta definición de tareas,
● la precisión en la especificación de requerimientos,
● la calidad de los prompts utilizados para guiar la generación de código,
● y la validación técnica de los resultados obtenidos.
Este enfoque permite acelerar el desarrollo sin comprometer la calidad,
siempre que se mantenga un control riguroso sobre la coherencia del sistema, las
reglas de negocio y las decisiones de diseño previamente establecidas.
La planificación contempla:
● la definición de sprints orientados a entregables funcionales,
● la organización del trabajo en tareas claras y verificables,
● la priorización de funcionalidades críticas del sistema,
● y la validación progresiva de cada módulo implementado.
Asimismo, se incorpora un Sprint 0, cuyo objetivo es preparar el entorno de
desarrollo, establecer convenciones de trabajo y definir las bases necesarias para
una implementación eficiente asistida por la IA.
Esta etapa resulta fundamental para garantizar que el desarrollo posterior se
realice de manera ordenada, consistente y alineada con los objetivos del proyecto.


### 2. Introducción al Product Backlog

El presente Product Backlog organiza el trabajo de desarrollo del sistema
LaRoka a partir de las definiciones establecidas en las etapas de análisis y diseño.
Contempla la totalidad de los requisitos funcionales, reglas de negocio, decisiones
arquitectónicas y criterios de calidad relevados, traducidos en historias de usuario
priorizadas y agrupadas en sprints de implementación incremental.
El backlog está ordenado de acuerdo al siguiente criterio de priorización:
● Arquitectura base, convenciones, infraestructura y CI/CD.
● Núcleo del dominio: entidades centrales y su persistencia.
● Flujo principal end-to-end: el camino crítico del negocio operativo.
● Extras y mejoras: funcionalidades complementarias al flujo principal.
● Futuro evolutivo: extensiones hacia la plataforma multi-pizzería.

### 3. Stack Tecnológico e Infraestructura

```
Componente Tecnología Detalle
Backend Java Spring Boot Monolito^ modular.^ Spring^ Data^
JPA + Hibernate
Base de Datos PostgreSQL Render^ managed^ PostgreSQL.^
Esquema versionado con Flyway
Frontend Cliente React + Vite (PWA) Deploy^ en^ Vercel.^ Mobile-first^
Frontend Backoffice React + Vite Autenticación^ por^ rol^ y^ sucursal^
Storage multimedia Cloudflare R2 Imagenes^ de^ catálogo^
Proveedor de Pagos MercadoPago Integración^ via^ adapter.^ Webhook^
para confirmación de pagos
Hosting Backend Render Deploy^ automático^ desde^ main.^
Docker desde dia 1
CI/CD GitHub Actions Build^ +^ tests.^ Deploy^ automático^ a^
producción al mergear main
Comunicación Realtime WebSockets Backend^ →^ Backoffice^
Polling Cliente HTTP Polling Cliente^ consulta^ estado^ de^ pedido^
periódicamente
```

### 4. Arquitectura modular

El sistema se implementa como monolito modular. Cada módulo encapsula
su propia lógica de negocio, acceso a datos y exposición de API, con separación
explícita de capas internas.

#### 4.1 Estructura de capas por módulo

```
● Controller: exposición de endpoints REST
● Service: lógica de negocio y orquestación. Acceso a repositorios
● Repository: acceso a datos vía Spring Data JPA
● DTO: objetos de transferencia para entrada y salida de la API
● Mapper: conversión entre entidades y DTOs
● Exception: excepciones tipadas por módulo. Manejadas de forma
centralizada
```
#### 4.2 Módulos identificados

```
Módulo Paquete base Responsabilidad
Branch com.laroka.branch Gestión^ de^ sucursales^ y^ su^
disponibilidad operativa
Catalog com.laroka.catalog Productos,^ categorías,^ precios^
y disponibilidad por sucursal
Order com.laroka.order Ciclo^ de^ vida^ completo^ del^
pedido y sus estados
Payment com.laroka.payment Iniciación,^ confirmación^ y^
trazabilidad de pagos con MP
Auth com.laroka.auth Autenticación^ y^ autorización^ de^
usuarios internos
StaffUser com.laroka.staffuser Usuarios^ internos,^ roles^ y^
asignación por sucursal
Notification com.laroka.notification Eventos^ y^ notificaciones^ en^
tiempo real hacia el backoffice
Pizzeria com.laroka.pizzeria Entidad^ raíz^ para^ evolución^
multi-pizzería. Presente desde
v1 en datos.
```

#### 4.3 Manejo centralizado de errores

Se implementa un GlobalExceptionHandler mediante @ControllerAdvice que
intercepta todas las excepciones del sistema y retorna respuestas estructuradas con
código HTTP apropiado, mensaje descriptivo y timestamp. Ningún controller maneja
excepciones directamente.

#### 4.4 Integración externa vía Adapter

Toda integración con servicios externos (MercadoPago, Cloudflare R2) se
encapsula detrás de una interfaz de puerto. La implementación concreta vive en una
clase Adapter. Esto permite cambiar el proveedor sin modificar la lógica de negocio.

### 5. Definition of Done

Una historia de usuario se considera completada cuando cumple la totalidad
de los siguientes criterios:
**Categoría Criterio
Código** El código está commiteado en la rama correspondiente y
mergeado a develop vía Pull Request
**Arquitectura** Respeta la separación de responsabilidades definida
**Base de Datos** Todo cambio de esquema está implementado como migración
Flyway versionada
**ORM** No se utiliza ddl-auto=create/update en ningún ambiente
**DTOs** La API nunca expone entidades JPA. Se usan DTOs
**Errores** Excepciones tipadas y manejadas por GlobalExceptionHandler
**Reglas de negocio** Implementadas y validadas
**Tests** El build de CI pasa sin errores. Los tests unitarios del servicio
cubren el caso principal y al menos un caso alternativo
**Variables de entorno** No existe ninguna credencial, URL ni API key hardcodeada
**Seguridad** Los endpoints del backoffice requieren autenticación. Los del
cliente son públicos
**Revisión** El código fue revisado (self-review o pair) antes del merge


### 6. Convenciones de Trabajo con IA

#### 6.1 Convención de prompts

El desarrollo del sistema está asistido por herramientas de Inteligencia
Artificial. Las siguientes convenciones garantizan coherencia y calidad en los
resultados generados.
**Aspecto Convención
Contexto base** No^ repetir^ stack,^ arquitectura^ ni^ reglas^ de^ negocio^ en^
los prompts. Ese contexto vive en CLAUDE.md y
archivos del repo
**Granularidad** Un^ prompt^ por^ capa.^ No^ pedir^ Controller^ +^
Service + ... en un solo prompt. Generar y
validar capa por capa
**Referencia a requisitos** Indicar^ el^ ID^ de^ la^ HU^ o^ RN^ relevante^
**Migraciones** Nunca^ generar^ esquema^ con^ ddl-auto.^ Siempre^
generar el script Flyway como tarea separada
explícita
**Tests** Generarlos^ como^ tarea^ independiente^ indicando^
únicamente los casos a cubrir
**Validación** Todo^ código^ generado^ por^ IA^ es^ revisado^ antes^
del commit. No se acepta output sin verificar
contra las convenciones del proyecto

#### 6.2 Infraestructura de Contexto para IA

```
● CLAUDE.md
Archivo leído automáticamente por Claude en cada sesión. Contiene el
contexto fijo e inamovible del proyecto. Elimina la necesidad de repetir
contexto base en cada prompt. Es la fuente de verdad del proyecto.
● Archivos de contexto por módulo
Cada módulo puede contener un CONTEXT.md con sus entidades, RN
específicas y decisiones de diseño propias. Al trabajar sobre un
módulo, se provee ese archivo como contexto adicional. Reduce el
ruido de información irrelevante de otros módulos.
● Commands reutilizables
Definición de comandos para tareas estructurales repetibles. Por
```

ejemplo: **_/new-module_** genera la estructura completa de un módulo
respetando las convenciones del proyecto. Evita reescribir el mismo
prompt estructural en cada iteración.
**● Agents para tareas específicas**
Agentes dedicados a tareas concretas y repetibles:
○ Agente de revisión
○ Agente de migración
○ Agente de tests
**● Principio rector**
Los prompts son tácticos. El CLAUDE.md y los archivos de contexto
son estratégicos. Sin la capa estratégica, la calidad del output depende
de cuán bien se redactó el prompt del día.


### 7. Product Backlog

```
El backlog está ordenado por prioridad de implementación. Cada historia de
usuario incluye sus criterios de aceptación, prioridad y sprint asignado.
```
#### Sprint 0 – Fundaciones Técnicas y DevOps

Objetivo: preparar el entorno de desarrollo, establecer convenciones y
construir la base sobre la que se implementará todo el sistema.
**ID Historia de Usuario Criterios de aceptación Prioridad Sprint
US-00-01** Como^ equipo,^ necesito^ un^
repositorio Git con
estructura de ramas
definida para trabajar de
forma ordenada.
Rama main, develop y convención
feature/xxx. Branch protection en
main. README inicial con
descripción del proyecto y stack
**Alta** S0
**US-00-02** Como^ equipo,^ necesito^ un^
pipeline de CI conrfigurado
para garanitzar que el build
no se rompa en cada PR.
El pipeline incluye step de
linting antes de los tests
GitHub Actions ejecuta build de
Spring Boot en cada PR a develop.
Si el build falla, el PR no puede
mergearse.
Si linting falla, el build falla.
**Alta** S0
**US-00-03** Como^ equipo,^ necesito^
Docker configurado desde
el día 1 para garantizar
portabilidad del entorno
Dockerfile del backend funcional.
docker-compose.yml para desarrollo
local con PostgreSQL. El build de CI
usa Docker
**Alta** S0
**US-00-04** Como^ equipo,^ necesito^ el^
proyecto Spring Boot
inicializado con la
estructura modular definida
Proyecto generado con Spring
Initializer. Paquetes base de cada
módulo creado. Dependencias:
Spring Web, Spring Data JPA,
PostgreSQL Driver, Flyway, Spring
Security, Lombok
**Alta** S0
**US-00-05** Como^ equipo,^ necesito^
Flyway configurado para
versionar todos los
cambios de esquema de
base de datos
Flyway integrado en Spring Boot.
Primera migración V1__init.sql
ejecuta sin errores. ddl-auto=validate
en todos los ambientes
**Alta** S0
**US-00-06** Como^ equipo,^ necesito^ un^
GlobalExceptionHandler
configurado para
centralizar el manejo de
errores de la API
Clase anotada con
@ControllerAdvice. Maneja
RuntimeExc..., EntityNotFoundExc,
MethodArgumentNotValidExc.
Respuesta estructurada con status,
message y timestamp.
**Alta** S0
**US-00-07** Como^ equipo,^ necesito^ las^
variables de entorno
externalizadas para que
DB_PASS, MERCADOPAGO_KEY,
R2 credentials. Archivo .env.example
en el repo
**Alta** S0


#### Sprint 1 – Dominio Base: Pizzería, Sucursales y Catálogo (Backend)

**ID Historia de Usuario Criterios de Aceptación Prioridad Sprint
US-01-01** Como^ sistema,^ necesito^ la^
entidad Pizzeria persistida
desde v1 para soportar la
evolución multi-comercio
Entidad Pizzeria con id, name,
createdAt, updatedAt. Migración
Flyway V1 incluye tabla pizzeria con
nombre “LaRoka”. Todos los modulos
de dominio referencian a pizzeria_id.
CRUD completo. DTOs para request
y response. Service maneja
entidades, Mapper convierte en el
Controller
**Alta S1
US-01-02** Como^ sistema,^ necesito^ la^
entidad Sucursal
persistida y asociada a su
pizzeria
Entidad Branch con id, name address
(VARCHAR), active, pizzeria_id,
createdAt, updatedAt, FK a pizzeria.
Migracion V2 incluye las 3
sucursales. CRUD completo y DTOs
separados. BranchController para
backoffice con autenticación
**Alta S1
US-01-03** Como^ cliente,^ necesito^ ver^
el listado de sucursales
disponibles para
seleccionar dónde hacer
mi pedido
GET /branches retorna lista de
sucursales con active=true.
Respuesta incluye id, name y
address. BranchClientController
público sin autenticación.
BranchPublicDTO
**Alta S1
US-01-04** Como^ sistema,^ necesito^ la^
entidad Categoria
persistida para organizar
el catalogo de productos
Entidad Category con id, name,
pizzeria_id, createdAt, updatedAt, FK
pizzeria. Migracion V3. CRUD
completo. DTOs. CategoryController
para backoffice
**Alta S1**
ninguna credencial esté
hardcodeada
**US-00-08** Como^ equipo,^ necesito^ los^
proyectos React
inicializados y deployados
en Vercel para tener el
pipeline frontend operativo
Dos proyectos Vite+React:
laroka-client y laroka-backoffice.
Deploy automático desde Vercel
conectado al repo. Build exitoso en
Vercel
**Alta** S0
**US-00-09** Como^ equipo,^ necesito^
Cloudflare R2 configurado
con dos buckets separados
para dev y prod
Bucket R2 creado. Credenciales
configuradas como variables de
entorno. Buckets laroka-dev y
laroka-prod creados. Variable
R2_BUCKET_NAME determina
bucket activo por ambiente. Test de
upload exitoso.
**Media** S0
**US-00-10** Como^ equipo,^ necesito^
logs estructurados y health
check configurados para
tener observabilidad básica
desde el primer deploy
Spring Boot emite logs en formato
JSON. GET /actuator/health retorna
status del sistema. Render usa el
health check para monitorear el
contenedor. Dependencia Spring
Boot Actuator agregada.
**Media** S0


**US-01-05** Como^ sistema,^ necesito^ la^
entidad Producto
persistida para construir el
catálogo
Entidad Product con id, name,
description, price, imageUrl,
available, category_id, branch_id,
pizzeria_id, createdAt, updatedAt.
FKs a category y branch. Migracion
V4. CRUD completo con DTOs.
ProductController para backoffice
**Alta S1
US-01-06** Como^ cliente,^ necesito^ ver^
el menu de una sucursal
para elegir mis productos
GET /branches/{id}/menu retorna
productos con available=true
agrupados por categoria. Respuesta:
lista de MenuCategoryDTO con
nombre de categoria y lisa de
MenuProductDTO.
ProductClientController publico.
**Alta S1
US-01-07** Como^ equipo,^ necesito^
unit tests para los
servicios del Sprint 1
Unit tests con JUnit 5 y Mockito para
todos los services. Cada servicio
cubre: caso exitoso, entidad no
encontrada lanza
EntityNotFoundException, validación
de reglas de negocio. Tests no
requieren base de datos real (repos
mockeados). Corren en CI sin infra.
BackendApplicationTests
reemplazado por test trivial que no
levanta contexto completo
**Alta S1
US-01-08** Como^ personal^ del^ local,^
necesito poder marcar un
producto como no
disponible para que el
cliente no lo vea en el
menu
PATCH
/backoffice/products/{id}/availability
con body {available: boolean}.
Requiere autenticacion. El cambio es
inmediato y se refleja en GET
/branches/{id}/menu. Retorna DTO
actualizado
**Alta S1
US-01-09** Como^ cliente,^ necesito^
poder marcar una sucursal
como favorita para no
re-seleccionarla cada vez
El frontend persiste el id de la
sucursal en localStorage bajo
“laroka_preferred_branch”. Al
ingresar al sistema, si existe una
sucursal guardada, se preselecciona
automaticamente. El cliente puede
cambiarla en cualquier momento.
**Media S1
US-01-10** Como^ equipo,^ necesito^ la^
API documetnada con
OpenAPI para facilitar el
desarrollo del frontend
Dependencia agregada al pom.xml.
GET /swagger-ui.html disponible en
local y en ambiente de desarrollo.
Todos los controllers tienen Tag.
Todos los endpoints tienen Operation
con descripcion y respuestas
documentadas. Swagger
deshabilitado en produccion via
variable de entorno
**Alta S1
US-01-1 1** Como^ sistema,^ necesito^
validacion de entrada en
todos los endpoints para
garantizar integridad de
los datos
Todos los RequestDTOs tienen
NotNull, NotBlank, Size, DecimalMin.
Controllers usan Valid en
RequestBody. Validacion fallida
retorna 400 con mensaje descriptivo
**Alta S1**


```
por campo via
GlobalExceptionHandler. Se aplica a
todos los modulos existentes y
futuros
```
#### Sprint 1-F – Selección de Sucursal y Menú (Frontend Client)

**ID Historia de Usuario Criterios de Aceptación Prioridad Sprint
US-01-F-01** Como^ cliente,^ necesito^ ver^
y seleccionar una sucursal
al ingresar a la aplicación
Pantalla inicial muestra lista de
sucursales consumiendo GET
/branches. Si existe sucursal en
localStorage se preselecciona. UI
mobile-first. Al seleccionar una
sucursal navega al menú. Manejo de
error si la API no responde
**Alta S1-F
US-01-F-02** Como^ usuario,^ necesito^
poder alternar entre tema
claro y oscuro según mi
preferencia
Sistema de tokens de diseño
implementado con CSS variables en
index.css. Variables definidas para
bg-primary, bg-secondary- bg-card,
text-primary, text-secondary, accent,
border. Tema oscuro como default.
Preferencia persistida en localStorage
bajo “laroka-theme”. Todos los
componentes usan var(--token).
Cambio de tema sin recargo de
pantalla
**Media S1-F
US-01-F-03** Como^ cliente,^ necesito^ ver^
el menú de la sucursal
seleccionada organizado
por categorías
Pantalla de menú consume GET
/branches/{id}/menu. Productos
agrupados por categoria con scroll.
Muestra nombre, descripcion, precio
e imagen. Solo productos disponibles.
Estado de carga visible mientras se
obtienen los datos
**Alta S1-F
US-01-F-04** Como^ equipo,^ necesito^ el^
proyecto backoffice con
estructura base y
navegación definida
Proyecto backoffice con React Router
configurado. Rutas definidas: /login
(publica), /orders (protegida),
/orders/:id (protegida). Layout base
con header y área de contenido.
Pantalla de login como entry point.
Estructura de carpetas: pages/,
components/, services/, hooks/.
**Alta S1-F
US-01-F-05** Como^ cliente,^ necesito^
que las imagenes del
menu carguen de forma
optimizada para una
experiencia fluida sin
esperas visibles
Configurar ‘vite-plugin-pwa’ con
Service Worker: estrategia CacheFirst
para imagenes de R2 y NetworkFirst
para llamadas a la API. Preload de
imágenes del menú al seleccionar
sucursal, antes de navegar a la
pantalla del menú, consumiendo GET
/branches/{id}/menu en background.
Skeleton placeholder en cada
**Alta S1-F**


```
ProductCard mientras la imagen
carga, con fade-in suave al
completarse. Sin librerias adicionales
para el placeholder. Desde la
segunda visita las imagenes cargan
desde caché sin request a R2
```
#### Sprint 2 – Autenticación y Usuarios Internos

**ID Historia de Usuario Criterios de Aceptación Prioridad Sprint
US-02-01** Como^ personal^ del^ local,^
necesito poder iniciar
sesión en el backoffice
con mis credenciales para
acceder al sistema
POST /auth/login con body
{email,password}. Retorna JWT con
userId, role y branchId en el payload.
Credenciales incorrectas retornan 401
con mensaje claro. Endpoint público.
Password validada contra hash
BCrypt.
**Alta S2
US-02-02** Como^ sistema,^ necesito^
que cada usuario interno
esté asociado a una
sucursal específica para
restringir su acceso
operativo
Entidad StaffUser con id, name,
email, passwordHash, role (STAFF /
ADMIN), branch_id, createdAt,
updatedAt. FK a branch. Migración
V5. El JWT incluye banchId y role en
el payload (RN-11)
**Alta S2
US-02-03** Como^ sistema,^ necesito^
que Spring Security esté
configurado con rutas
púbicas y protegidas
claramente definidas
SecurityFilterChain define
explicitamente: rutas publicas:
GET /branches/, GET
/branches/{id}/menu, POST /orders,
GET /orders/{id}/status, POST
/payments/webhook, GET
/actuator/health, POST /auth/login,
GET /swagger-ui.html, GET
/v3/api-docs/. Rutas protegidas: todo
/backoffice/**. Cualquier ruta no
listada requiere autenticacion por
defecto. Filtro JWT valida token en
cada request protegida. Token
invalido retorna 401. Token valido
pero rol/sucursal incorrectos retorna
403.
**Alta S2
US-02-04** Como^ administrador,^
necesito poder crear
usuarios internos
asignados a una sucursal
para gestionar el acceso
al sistema
POST /backoffice/staff-user. Requiere
rol ADMIN. Crea usuario con role
STAFF o ADMIN y branch_id.
Password hasheado con BCrypt. No
se retorna passwordHash en la
respuesta
**Media S2
US-02-05** Como^ sistema,^ necesito^
verificar que toda la
comunicación se realice
Verificar que Render y Vercel sirven
backend y frontends sobre HTTPS.
Ningun endpoint crítico responde
**Alta S2**


```
por canales seguros sobre HTTP plano. URLs de
producción documentadas en el
README
US-02-06 Como^ equipo,^ necesito^
unit tests para
AuthService y
StaffUserService
Tests con JUnit 5 y Mockito.
AuthService cubre: login exitoso
retorna JWT, credenciales incorrectas
lanzan exception, usuario no
encontrado lanza exception.
StaffUserServiec cubre: creación
exitosa, email duplicado lanza
exception. Sin base de datos real
Alta S2
US-02-07 Como^ sistema,^ necesito^
CORS configurados para
permitir requests desde
los dominios de los
frontends
CorsConfiguration en Spring Boot
permite requests desde dominios de
Vercel. Métodos permitidos: GET,
POST, PATCH, DELETE. Headers
permitidos: Authorization,
Content-Type. En local permite
localhost:5173 y localhost:5174.
Configurado via variable de entorno.
Alta S2
US-02-08 Como^ sistema,^ necesito^
que los tokens JWT
tengan expiración definida
y el usuario sea notificado
cuando su sesión expira.
JWT tiene TTL configurable via
variable de entorno (default 8hs).
Token expirado retorna 401 con
mensaje. El backoffice detecta el 401
y redirige al login mostrando mensaje
claro al operador. No se implementa
refresh token en v1.
Media S2
```
#### Sprint 2-F – Autenticación Backoffice (Frontend Backoffice)

**ID Historia de Usuario Criterios de aceptación Prioridad Sprint
US-02-F-01** Como^ personal^ del^ loca,^
necesito una pantalla de
login en el backoffice para
autenticarme
Formulario con email y password.
POST /auth/login al submit. JWT
guardado en localStorage.
Credenciales incorrectas muestran
mensaje de error. Redirección a
/orders tras login exitoso
**Alta S2-F
US-02-F-02** Como^ sistema,^ necesito^
que las rutas protegidas
del backoffice redirijan al
login si el usuario no está
autenticado
Route guard verifica existencia y
validez del JWT antes de renderizar
rutas protegidas. Si no hay token o
expiró redirige a /login con mensaje.
Al hacer logout se elimina el token y
se redirige a /login.
**Alta S2-F**

#### Sprint 3 – Pedidos: Creación y Ciclo de Vida

```
ID Historias de Usuario Criterio de Aceptación
.
Prioridad Sprint
```

**US-03-01** Como^ sistema,^ necesito^ las^
entidades Order y
OrderItem persistidas con
todas sus relaciones
Entidad Order con id, createdAt, status
(enum), totalAmount (BigDecimal),
orderType (enum:
DELIFERY/TAKEAWAY), origin (enum:
CLIENT / BACKOFFICE),
customerName, customerPhone,
deliveryAddress(nullable), notes,
branch_id, pizzeria_id, createdAt,
updatedAt.
Entidad OrderItem con id, quantifty,
unitPrice, subtotal, order_id,
product_id. Migraciones V6
**Alta S3
US-03-02** Como^ cliente,^ necesito^
poder crear un pedido con
los productos seleccionados
para enviarlo a la sucursal
elegida
POST /orders. Body incluye:
branch_id, orderType, customerName,
customerPhone, deliveryAddress,
items, notes. Pedido creador en estado
PENDING_PAYMENT. Total calculado
automaticamente sumando quantity *
price vigente del producto. Requiere al
menos 1 item. Total mayor a cero.
Retorna orderId y status. Respuesta
incluye confirmación con: orderId,
status, totalAmount, orderType, items
con nombre y cantidad, branchName.
(RN-03, RN-04)
**Alta S3
US-03-03** Como^ sistema,^ necesito^ que^
un pedido solo exista
formalmente cuando su
pago ha sido aprobado
El pedido pasa a RECEIVED solo
cuando el webhook de MercadoPago
confirma el pago. Si orderType=CASH
el pedido pasa directamente a
RECEIVED al crearse. Antes de
RECEIVED el pedido existe como
PENDING_PAYMENTpero no es
visible en backoffice
**Alta S3
US-03-04** Como^ sistema,^ necesito^ un^
historial de cambios de
estado del pedido con fecha
y hora para garantizar
trazabilidad
Entidad OrderStatusHistory con id,
order_id, from status, toStatus,
changedAt. Migración V7. Cada
transición de estado genera un registro
automáticamente en el servicio.
(RN-12, RNF-24)
**Alta S3
US-03-05** Como^ sistema,^ necesito^ que^
las transiciones de estado
del pedido sean
secuenciales y controladas
Transiciones válidas:
RECEIVED → IN_PREPARATION →
ON_THE_WAY → DELIVERED.
Estados terminales: DELIVERED y
CANCELLED no admiten más
transiciones.
CANCELLATION_REQUESTED es
estado intermedio entre
IN_PREPARATION y CANCELLED.
Transición inválida retorna 422. Lógica
encapsulada en OrderStateMachine o
método del servicio.
**Alta S3
US-03-06** Como^ cliente,^ necesito^
poder consultar el estado
actual de mi pedido para
GET /orders/{id}/status. Retorna: status
actual, historial de estados con
timestamps, orderType, branchName.
**Alta S3**


```
hacer seguimiento Accesible sin autenticación mediante el
ID del pedido. Si el pedido no existe
retorna 404.
US-03-07 Como^ sistema,^ necesito^
evitar la duplicación
involuntaria de pedidos ante
reintentos del cliente
Header-X-Idempotency-Key requerido
en POST /orders. Mismo key dentro de
5 min retorna el pedido ya creado sin
duplicar (200 en lugar de 201). Key
expirado o nuevo genera nuevo
pedido.
Alta S3
US-03-08 Como^ equipo,^ necesito^ unit^
tests para OrderService
Tests JUnit y Mockito. Cubre: creación
exitosa con cálculo correcto de total,
pedido sin items lanza
BusinessException, transición de
estado válida actualiza correctamente,
transicion invalida lanza
BusinessException, idempotency key
duplicada retorna pedido existente,
sucursal no encontrada lanza
EntityNotFoundException
Pedido sin customerName o sin
customerPhone retorna 400.
Alta S3
US-03-09 Como^ sistema,^ necesito^
poder definir si mi pedido es
para delivery o takeaway
OrderType enum con valores
DELIVERY y TAKEAWAY. Si
DELIVERY: deliveryAddress
obligatorio, validado con NotBlank. Si
TAKEAWAY: deliveryAddress ignorado,
contact obligatorio. Validación retorna
400 si orderType=DELIVERY y
deliveryAddress está vacio. Migración
V8 agrega columna delivery_address
VARCHAR nullable a tabla order.
Alta S3
US-03-10 Como^ sistema,^ necesito^ que^
el precio registrado en el
pedido corresponda al valor
vigente del producto al
momento de la confirmación
Al crear OrderItem se copia el precio
efectivo del producto
(BranchProduct.priceOverride si existe,
sino Product.price) en el campo
unitPrice del OrderItem. El subtotal se
calcula como quantity * unitPrice.
Cambios futuros en precio no afectan
OrderItems existentes (RN-05).
Alta S3
```
#### Sprint 3-F – Carrito y Pedido (Frontend Client)

**ID Historia**^ **de**^ **Usuario**^ **Criterios**^ **de**^ **Aceptación**^ **Prioridad**^ **Sprint**^
**US-03-F-01** Como^ cliente,^ necesito^
poder armar mi pedido
agregando productos al
carrito
Botón agregar en cada producto del
menú. Carrito persiste en estado local
(Context API o Zustand). Permite
modificar cantidades y eliminar
productos. Total calculado
automáticamente. Carrito visible como
overlay o pantalla dedicada.
**Alta S3-F**


**US-03-F-02** Como^ cliente,^ necesito^
poder ingresar notas y
seleccionar modalidad del
pedido antes de confirmar
Pantalla de confirmación pre-pago
muestra: resumen del pedido, campo
customerName (NotBlank, obligatorio),
campo customerPhone (NotBlank,
obligatorio), campo de notas (opcional),
selector DELIVERY/TAKEAWAY, campo
de dirección (visible y obligatorio si
DELIVERY)
**Alta S3-F
US-03-F-03** Como^ cliente,^ necesito^ ver^
el estado de mi pedido en
tiempo real después de
confirmarlo
Pantalla de seguimiento consume GET
/orders/{id}/status cada 15 seg (polling).
Muestra estado actual, historial de
estados y timestamps. Refleja
correctamente READY_FOR_PICKUP
para pedidos TAKEAWAY y
ON_THE_WAY para pedidos
DELIVERY. Se actualiza
automáticamente sin recarga manual
**Alta S3-F**

### 8. Resumen de Sprints

### 9. Trazabilidad Requisitos → Backlog