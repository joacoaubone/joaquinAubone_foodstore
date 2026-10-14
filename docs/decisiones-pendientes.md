# Estado de decisiones del Anexo E

La consigna Food Store v6 identifica los siguientes puntos como pendientes en el Anexo E (página 85). El proyecto puede adoptar un criterio explícito para avanzar, pero eso no lo convierte en una decisión confirmada por la cátedra ni en un requisito que ya estuviera vigente en el PDF. Las decisiones adoptadas por el proyecto están detalladas en el README; si la cátedra indica otro criterio, se actualizarán antes de cerrar la entrega.

| ID | Tema | Impacto | Estado |
| --- | --- | --- | --- |
| D-11 | Mismo origen en desarrollo y Docker Compose | Alto | Vite adoptado en desarrollo; Compose pendiente |
| D-14 | Renovación de sesión y recuperación de SSE al vencer el access token | Medio | Propuesta mínima adoptada por el proyecto; pendiente de confirmación |
| D-16 | Cobertura de pruebas de reglas y su ponderación en la rúbrica | Medio | Pendiente |
| D-17 | Datos administrativos y organización de la entrega | Medio | Pendiente |
| D-18 | Dimensiones e invalidación de la clave de caché del catálogo | Medio | Pendiente |
| D-19 | Valor de `actor_rol` cuando el usuario tiene múltiples roles o actúa como propietario | Bajo | Pendiente |
| D-20 | Intervalo de publicación del outbox | Bajo | Pendiente |
| D-21 | Roles autorizados para crear pedidos | Bajo | Pendiente |
| D-22 | Diferenciación y uso permitido de access token y refresh token | Medio | Adoptada por el proyecto; pendiente de confirmación |
| D-23 | Capacidad y configuración del executor de BCrypt | Bajo | Adoptada por el proyecto; valor no fijado por la guía |
| D-24 | Alcance del listado de ingresos por período | Medio | Pendiente |
| D-25 | Necesidad de un endpoint para consultar el catálogo de roles | Bajo | Endpoint público adoptado; implementación pendiente, no confirmado por la cátedra |
| D-27 | Significado de la mención a la emisión de refresh tokens | Bajo | Pendiente |
| D-28 | Vencimiento que informa `SesionInfo.expira_en` | Bajo | Adoptada por el proyecto; pendiente de confirmación |
| D-29 | Códigos internos para errores de validación y saturación del executor | Medio | Propuesta mínima adoptada por el proyecto; pendiente de confirmación |
| D-30 | Permisos efectivos del rol STOCK para crear/editar productos | Medio | Pendiente |
| D-31 | Estructura de respuesta para colecciones paginadas | Medio | Pendiente |
| D-32 | Tipos de evento y campos del payload para cambios de estado | Medio | Pendiente |
| D-33 | Campos de pedido y pago que deben devolverse al cliente | Medio | Pendiente |
| D-34 | Política de reintentos y correlación para todas las tareas programadas | Bajo | Pendiente |

Las decisiones D-01 a D-10, D-12, D-13, D-15 y D-26 figuran como resueltas en la guía y forman parte de la línea base del proyecto. Para D-11 solo se adoptó la solución de Vite en desarrollo; Compose continúa pendiente. Las decisiones adoptadas por el proyecto no se deben confundir con las confirmadas por la cátedra; cualquier respuesta nueva debe reflejarse también en las especificaciones y pruebas del área afectada. El detalle de D-11, D-14 y D-25 está en el README.
