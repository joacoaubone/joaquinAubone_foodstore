# joaquinAubone_foodstore

## Decisiones de diseño adoptadas para avanzar

Las siguientes decisiones se adoptan como **criterios del proyecto** para avanzar. No se presentan como decisiones confirmadas por la cátedra ni como requisitos que ya estuvieran vigentes en la consigna. Se registran para que la implementación y su corrección usen un criterio explícito. Si la cátedra indica otro criterio, se actualizarán antes de cerrar la entrega.

Fecha de registro: 14 de octubre de 2026.

| ID | Decisión adoptada por el proyecto | Estado frente a la cátedra |
| --- | --- | --- |
| D-11 | En desarrollo, Vite reenviará `/api` al backend para que el navegador use un solo origen y las cookies viajen sin CORS adicional. La topología para servir el frontend desde Docker Compose queda pendiente. | Adoptada para desarrollo; Compose pendiente |
| D-14 | Ante un `401` de `fetch`, el cliente llamará a `/api/v1/auth/refresh` una sola vez y repetirá la petición original una sola vez. Si el refresh falla, limpiará la identidad en memoria y enviará al login. Si `EventSource` queda `CLOSED`, aplicará el mismo flujo y creará una nueva conexión tras refrescar. | Propuesta mínima adoptada por el proyecto; pendiente de confirmación |
| D-22 | Los JWT incluirán el claim `tipo` con valor `access` o `refresh`. El filtro de autenticación aceptará únicamente `access`; `POST /api/v1/auth/refresh` aceptará únicamente `refresh`. No se implementa rotación del refresh token, de acuerdo con el alcance descrito en la guía. | Pendiente de confirmación |
| D-23 | El executor BCrypt tendrá `foodstore.bcrypt.max-concurrente=2`, con una cola acotada de 8 trabajos (`4 × concurrencia`) y rechazo inmediato al llenarse (`AbortPolicy`). El límite será configurable; no se permitirá una cola ilimitada. | Valor elegido por el proyecto; no fijado por la guía |
| D-25 | Agregar `GET /api/v1/roles`, público y de solo lectura, con una lista no paginada de los códigos de la tabla `rol`, ordenados y sin datos de usuarios. | Decisión del proyecto; endpoint pendiente de implementación y no confirmada por la cátedra |
| D-28 | `SesionInfo.expira_en` informará el vencimiento del access token (30 minutos), no el vencimiento del refresh token (7 días) ni la duración total de la sesión. | Pendiente de confirmación |
| D-29 | Los errores de validación responderán HTTP 422 con código `CUERPO_INVALIDO` y la lista de campos inválidos. El rechazo por saturación del executor BCrypt responderá HTTP 503 con código `SERVICIO_SATURADO`. | Propuesta mínima adoptada por el proyecto; pendiente de confirmación |

### Criterio adicional del proyecto

Para evitar cuentas duplicadas, si el email ya existe en el registro público o en el alta administrativa, la API responderá HTTP 409 con código `EMAIL_DUPLICADO` en formato RFC 9457 y no creará otra cuenta. Este criterio no es una decisión numerada del Anexo E ni está confirmado por la cátedra.

El registro detallado de decisiones pendientes y adoptadas está en [`docs/decisiones-pendientes.md`](docs/decisiones-pendientes.md). Estas elecciones no resuelven otros puntos del Anexo E.

## Configuración local de identidad

La API requiere PostgreSQL 16 y Redis 7.4 accesibles localmente. Copiá `.env.example` a `.env`, reemplazá todos los marcadores y exportá sus variables antes de iniciar. `JWT_SECRET` debe ser Base64 de al menos 32 bytes aleatorios y `ADMIN_PASSWORD_HASH` un hash BCrypt strength 12; no pongas los valores reales en este README ni en Git. El `.gitignore` excluye `.env` y conserva `.env.example` versionado.

Desde la raíz del repositorio, en macOS/Linux:

```sh
cp .env.example .env
# Editar .env y reemplazar los marcadores antes de continuar.
set -a
source .env
set +a
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=migrador
```

Cuando el migrador termina correctamente, iniciá la API en otra terminal con las mismas variables exportadas:

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=api
```

Para desarrollo sin HTTPS en `localhost`, se puede configurar `AUTH_COOKIE_SECURE=false`; mantener `AUTH_COOKIE_SAME_SITE=Lax`. El proxy de Vite adoptado para D-11 y el refresh/reintento de D-14 quedan para la etapa frontend; la parte de Docker Compose de D-11 sigue pendiente.
