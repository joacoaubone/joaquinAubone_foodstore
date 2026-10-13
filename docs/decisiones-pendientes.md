# Decisiones pendientes de la cátedra

La consigna Food Store v6 identifica los siguientes puntos como pendientes en el Anexo E (página 85). Hasta recibir una decisión de la cátedra o del responsable del proyecto, no se adopta como normativa ninguna propuesta mínima que aparezca en el PDF. Las especificaciones iniciales cubren únicamente el comportamiento ya confirmado; las tareas afectadas deben esperar la definición correspondiente.

| ID | Tema | Impacto | Estado |
| --- | --- | --- | --- |
| D-11 | Mismo origen en desarrollo y Docker Compose | Alto | Pendiente |
| D-14 | Renovación de sesión y recuperación de SSE al vencer el access token | Medio | Pendiente |
| D-16 | Cobertura de pruebas de reglas y su ponderación en la rúbrica | Medio | Pendiente |
| D-17 | Datos administrativos y organización de la entrega | Medio | Pendiente |
| D-18 | Dimensiones e invalidación de la clave de caché del catálogo | Medio | Pendiente |
| D-19 | Valor de `actor_rol` cuando el usuario tiene múltiples roles o actúa como propietario | Bajo | Pendiente |
| D-20 | Intervalo de publicación del outbox | Bajo | Pendiente |
| D-21 | Roles autorizados para crear pedidos | Bajo | Pendiente |
| D-22 | Diferenciación y uso permitido de access token y refresh token | Medio | Pendiente |
| D-23 | Capacidad y configuración del executor de BCrypt | Bajo | Pendiente |
| D-24 | Alcance del listado de ingresos por período | Medio | Pendiente |
| D-25 | Necesidad de un endpoint para consultar el catálogo de roles | Bajo | Pendiente |
| D-27 | Significado de la mención a la emisión de refresh tokens | Bajo | Pendiente |
| D-28 | Vencimiento que informa `SesionInfo.expira_en` | Bajo | Pendiente |
| D-29 | Códigos internos para errores de validación y saturación del executor | Medio | Pendiente |
| D-30 | Permisos efectivos del rol STOCK para crear/editar productos | Medio | Pendiente |
| D-31 | Estructura de respuesta para colecciones paginadas | Medio | Pendiente |
| D-32 | Tipos de evento y campos del payload para cambios de estado | Medio | Pendiente |
| D-33 | Campos de pedido y pago que deben devolverse al cliente | Medio | Pendiente |
| D-34 | Política de reintentos y correlación para todas las tareas programadas | Bajo | Pendiente |

Las decisiones D-01 a D-10, D-12 a D-15 y D-26 figuran como resueltas en la guía y forman parte de la línea base del proyecto. Este registro debe actualizarse cuando se reciban definiciones; cualquier cambio de comportamiento también debe reflejarse en las especificaciones y pruebas antes de implementar el área afectada.
