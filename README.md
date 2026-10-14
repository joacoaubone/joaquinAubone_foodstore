# joaquinAubone_foodstore

## Decisiones de diseño adoptadas para avanzar

Las siguientes decisiones se adoptan como **criterios del proyecto** para implementar la Etapa 2. No se presentan como decisiones confirmadas por la cátedra ni como requisitos que ya estuvieran vigentes en la consigna. Se registran para que la implementación y su corrección usen un criterio explícito. Si la cátedra indica otro criterio, se actualizarán antes de cerrar la entrega.

Fecha de registro: 14 de octubre de 2026.

| ID | Decisión adoptada por el proyecto | Estado frente a la cátedra |
| --- | --- | --- |
| D-22 | Los JWT incluirán el claim `tipo` con valor `access` o `refresh`. El filtro de autenticación aceptará únicamente `access`; `POST /api/v1/auth/refresh` aceptará únicamente `refresh`. No se implementa rotación del refresh token, de acuerdo con el alcance descrito en la guía. | Pendiente de confirmación |
| D-23 | El executor BCrypt tendrá `foodstore.bcrypt.max-concurrente=2`, con una cola acotada de 8 trabajos (`4 × concurrencia`) y rechazo inmediato al llenarse (`AbortPolicy`). El límite será configurable; no se permitirá una cola ilimitada. | Valor elegido por el proyecto; no fijado por la guía |
| D-28 | `SesionInfo.expira_en` informará el vencimiento del access token (30 minutos), no el vencimiento del refresh token (7 días) ni la duración total de la sesión. | Pendiente de confirmación |
| D-29 | Los errores de validación responderán HTTP 422 con código `CUERPO_INVALIDO` y la lista de campos inválidos. El rechazo por saturación del executor BCrypt responderá HTTP 503 con código `SERVICIO_SATURADO`. | Propuesta mínima adoptada por el proyecto; pendiente de confirmación |

### Criterio adicional del proyecto

Para evitar cuentas duplicadas, si el email ya existe en el registro público o en el alta administrativa, la API responderá HTTP 409 con código `EMAIL_DUPLICADO` en formato RFC 9457 y no creará otra cuenta. Este criterio no es una decisión numerada del Anexo E ni está confirmado por la cátedra.

El registro detallado de decisiones pendientes y adoptadas está en [`docs/decisiones-pendientes.md`](docs/decisiones-pendientes.md). Estas elecciones no resuelven otros puntos del Anexo E.
