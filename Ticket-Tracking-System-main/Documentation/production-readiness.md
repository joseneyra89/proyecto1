# Production Readiness - Mesa de Ayuda

## Alcance funcional

La iteracion final deja un flujo base de mesa de ayuda con usuarios `USER`, tecnicos `TECH` y administradores `ADMIN`. El sistema permite crear casos de servicio `REQUEST` o `INCIDENT`, convertirlos en tickets, asignarlos, actualizarlos, resolverlos, medir SLA, enviar notificaciones por correo y consultar metricas operativas por tipo.

## Modelo de datos final

Tablas principales en schema `p2_sandbox`:

| Tabla | Proposito |
| --- | --- |
| `roles` | Catalogo `USER`, `TECH`, `ADMIN`. |
| `app_users` | Entidad base de usuarios; mantiene historicos con `is_active` y `deleted_at`. |
| `sites` | Sedes activas/inactivas. |
| `locations` | Ubicaciones por sede: aula, laboratorio o area. |
| `sla_policies` | Politicas por tipo/prioridad con `resolution_minutes`, `warning_percent`, `calendar_code`, `business_hours_only`. |
| `service_case` | Caso base de servicio con `type` `REQUEST`/`INCIDENT`, solicitante, sede, ubicacion, estado y vencimiento SLA. |
| `ticket` | Ticket operativo asociado a un `service_case`, tecnico asignado, estado, prioridad, vencimiento y resolucion. |
| `ticket_updates` | Historial de actualizaciones con visibilidad `PUBLIC` o `INTERNAL`. |
| `notifications` | Cola y estado de notificaciones por email. |
| `notification_attempts` | Registro de intentos de envio, errores y provider message id. |
| `audit_logs` | Bitacora de eventos sensibles. |
| `auth_sessions` | Sesiones activas por token opaco. |
| `password_reset_tokens` | Tokens de recuperacion local. |
| `permissions` / `role_permissions` | Matriz persistida de permisos. |

Compatibilidad: las tablas legacy (`employees`, `ticket_requests`, `tickets`, etc.) siguen existiendo. La migracion crea usuarios/casos/tickets equivalentes y conserva referencias `legacy_*`.

## Endpoints implementados

Publicos:

- `POST /login`
- `POST /password/forgot`
- `POST /password/reset`

Autenticacion/perfil:

- `POST /logout`
- `GET /me`
- `PATCH /me`

Catalogos:

- `GET /sites`
- `GET /sites/{siteId}/locations`
- `GET /users/technicians`

Casos y colas:

- `POST /service-cases`
- `GET /service-cases`
- `GET /service-cases/{caseId}`
- `GET /queues/service-cases?type=REQUEST|INCIDENT`

Tickets:

- `GET /tickets`
- `GET /tickets/{ticketId}`
- `POST /service-cases/{caseId}/tickets`
- `PATCH /tickets/{ticketId}`
- `POST /tickets/{ticketId}/assign`
- `POST /tickets/{ticketId}/resolve`

Dashboard y notificaciones:

- `GET /dashboard/technician?dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD`
- `POST /notifications/retry`

Administracion:

- `GET /admin/users`
- `PATCH /admin/users/{userId}`
- `PATCH /admin/users/{userId}/status`

Aliases legacy temporales:

- `/client/requests`
- `/user/requests`
- `/technician/*`

## Matriz de permisos

La politica runtime es deny-by-default. Si una ruta no esta en `AccessPolicy`, se rechaza.

| Permiso | USER | TECH | ADMIN |
| --- | --- | --- | --- |
| `AUTH_LOGOUT` | si | si | si |
| `PROFILE_READ` | si | si | si |
| `PROFILE_UPDATE` | si | si | si |
| `METADATA_READ` | si | si | si |
| `SERVICE_CASE_CREATE` | si | no | si |
| `SERVICE_CASE_READ` | si | si | si |
| `QUEUE_READ` | no | si | si |
| `DASHBOARD_TECH_READ` | no | si | si |
| `TICKET_CREATE` | no | si | si |
| `TICKET_READ` | si | si | si |
| `TICKET_UPDATE` | no | si | si |
| `TICKET_ASSIGN` | no | si | si |
| `TICKET_RESOLVE` | no | si | si |
| `NOTIFICATIONS_RETRY` | no | no | si |
| `ADMIN_USERS_READ` | no | no | si |
| `ADMIN_USERS_UPDATE_PROFILE` | no | no | si |
| `ADMIN_USERS_UPDATE_STATUS` | no | no | si |

Reglas adicionales en servicios:

- `USER` solo ve sus propios casos/tickets.
- `USER` solo ve `ticket_updates.PUBLIC`.
- `TECH` ve colas y opera tickets.
- `ADMIN` puede operar como superusuario.

## SLA

- `REQUEST`: 72 horas.
- `INCIDENT`: 24 horas.
- Warning: `sla_policies.warning_percent`, fallback `SLA_WARNING_PERCENT`, default `80`.
- Estados: `ON_TRACK`, `WARNING`, `BREACHED`, `MET`, `MET_LATE`.
- La UI muestra texto + icono + color; no depende solo del color.
- `calendar_code`, `calendar_policy` y `business_hours_only` quedan listos para calendarios laborales futuros.

## Eventos de correo

Eventos actuales:

- `case.created`: al crear un caso.
- `ticket.created`: al crear un ticket.
- `ticket.assigned`: al asignar o derivar.
- `ticket.resolved`: al resolver.

Cada evento crea una fila en `notifications` y cada intento crea una fila en `notification_attempts`. El modo default `EMAIL_DELIVERY_MODE=log` no envia por SMTP real; registra salida en logs y marca exito.

## Variables de entorno

Aplicacion:

- `PORT`: puerto Javalin, default `8081`.
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
- `DB_URL`: opcional; si existe sobreescribe host/puerto/db.

SLA:

- `SLA_REQUEST_HOURS`: default `72`.
- `SLA_INCIDENT_HOURS`: default `24`.
- `SLA_WARNING_PERCENT`: default `80`.

Email:

- `EMAIL_DELIVERY_MODE`: `log`, `smtp` o `disabled`.
- `EMAIL_FROM`.
- `EMAIL_MAX_ATTEMPTS`.
- `EMAIL_RETRY_DELAY_MINUTES`.
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_AUTH`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_STARTTLS`, `SMTP_TIMEOUT_MS`.

Docker/web:

- `APP_PORT`: puerto host de API.
- `WEB_PORT`: puerto host del frontend Nginx.
- `DB_HOST_PORT`: puerto host de Postgres.
- `ADMINER_PORT`: puerto host de Adminer.

## Checklist de despliegue

- Confirmar `.env` de destino y secretos reales.
- Ejecutar backup antes de migrar.
- Ejecutar `docker compose config` y revisar puertos.
- Aplicar migraciones `01`, `02`, `03` sobre la base destino.
- Validar `SELECT COUNT(*) FROM p2_sandbox.app_users WHERE is_active = TRUE;`.
- Ejecutar tests: `mvn -B -ntp test -Dtest=EntityTests,AuthorizationPolicyTests,SlaEngineTests,DashboardMetricsTests`.
- Construir imagen: `docker compose build ticket-api`.
- Levantar servicios: `docker compose up -d`.
- Validar login con `admin`, `tech1`, `user1`.
- Crear caso de prueba, crear ticket, resolverlo y confirmar audit/logs/notificaciones.
- Confirmar que `GET /dashboard/technician` responde para TECH/ADMIN y rechaza USER.
- Configurar SMTP real solo despues de probar `EMAIL_DELIVERY_MODE=log`.

## Procedimiento de restauracion

Ver `Documentation/backup-restore-runbook.md` para el paso a paso con `pg_dump`, `pg_restore` y verificacion posterior.

## Tests clave

- `AuthorizationPolicyTests`: deny-by-default, permisos por rol y rutas sensibles.
- `SlaEngineTests`: vencimientos REQUEST/INCIDENT, warning y breach.
- `DashboardMetricsTests`: porcentajes y clasificacion SLA del dashboard.
- Smoke manual recomendado: login, crear caso, crear ticket, asignar, resolver, revisar dashboard y retry de notificaciones.

## Riesgos pendientes y deuda tecnica

- No hay runner automatico de migraciones; los SQL se aplican manualmente o por inicializacion Docker.
- El calendario SLA aun es 24x7; falta proveedor de calendario laboral/feriados.
- Email SMTP no incluye plantillas HTML ni proveedor transaccional.
- El frontend sigue siendo HTML/JS vanilla monolitico; conviene modularizar si crece.
- Falta paginacion backend en listados grandes.
- Las rutas legacy `/client/*`, `/user/requests` y `/technician/*` deben retirarse cuando el frontend/Postman migren por completo.
- Falta observabilidad de produccion: healthcheck HTTP, metricas y correlacion de logs.
- Falta politica formal de retencion para `audit_logs` y `notification_attempts`.

## Pendientes opcionales

- Export CSV/Excel para dashboard.
- Filtros de dashboard por sede y tecnico.
- SLA por prioridad real en vez de duracion fija por tipo.
- Plantillas de correo por evento y branding institucional.
- CI con build, tests y validacion SQL contra Postgres efimero.
