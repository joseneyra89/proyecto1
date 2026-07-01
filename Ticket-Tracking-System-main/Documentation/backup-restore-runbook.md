# Runbook de backup y restauracion

## Contexto

Base Docker actual:

- Contenedor: `ticket-tracking-db`
- Base: `tickets_db`
- Usuario: `tickets_user`
- Schema principal: `p2_sandbox`

## Backup logico

Crear carpeta local:

```powershell
New-Item -ItemType Directory -Force .\backups
```

Generar dump comprimido:

```powershell
docker exec ticket-tracking-db pg_dump -U tickets_user -d tickets_db -Fc -f /tmp/tickets_db.dump
docker cp ticket-tracking-db:/tmp/tickets_db.dump .\backups\tickets_db_$(Get-Date -Format yyyyMMdd_HHmmss).dump
```

Validar que el archivo existe:

```powershell
Get-ChildItem .\backups\*.dump | Sort-Object LastWriteTime -Descending | Select-Object -First 1
```

## Backup SQL plano opcional

```powershell
docker exec ticket-tracking-db pg_dump -U tickets_user -d tickets_db --schema=p2_sandbox -f /tmp/tickets_db.sql
docker cp ticket-tracking-db:/tmp/tickets_db.sql .\backups\tickets_db_$(Get-Date -Format yyyyMMdd_HHmmss).sql
```

## Restauracion en una base limpia

1. Detener consumidores de la API si se restaura sobre un ambiente compartido:

```powershell
docker compose stop ticket-api web
```

2. Crear una base nueva o limpiar la actual. En produccion, preferir restaurar en base nueva y cambiar variables de conexion.

3. Copiar el dump al contenedor:

```powershell
docker cp .\backups\tickets_db_YYYYMMDD_HHMMSS.dump ticket-tracking-db:/tmp/restore.dump
```

4. Restaurar:

```powershell
docker exec ticket-tracking-db pg_restore -U tickets_user -d tickets_db --clean --if-exists /tmp/restore.dump
```

5. Reaplicar migraciones idempotentes si el dump viene de una version anterior:

```powershell
docker exec ticket-tracking-db psql -U tickets_user -d tickets_db -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/02-helpdesk-domain.sql
docker exec ticket-tracking-db psql -U tickets_user -d tickets_db -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/03-auth-access-control.sql
```

6. Levantar servicios:

```powershell
docker compose up -d ticket-api web
```

## Verificacion posterior

```powershell
docker exec ticket-tracking-db psql -U tickets_user -d tickets_db -c "SELECT COUNT(*) FROM p2_sandbox.app_users WHERE deleted_at IS NULL;"
docker exec ticket-tracking-db psql -U tickets_user -d tickets_db -c "SELECT code, case_type, resolution_minutes FROM p2_sandbox.sla_policies ORDER BY code;"
docker exec ticket-tracking-db psql -U tickets_user -d tickets_db -c "SELECT COUNT(*) FROM p2_sandbox.audit_logs;"
```

Smoke funcional:

- Login con `admin` / `pass`.
- Login con `tech1` / `pass`.
- Abrir dashboard tecnico.
- Crear un caso `REQUEST`.
- Crear ticket desde cola.
- Resolver ticket.
- Confirmar filas nuevas en `audit_logs`, `notifications` y `notification_attempts`.

## Rollback

Si una migracion falla:

1. Detener API: `docker compose stop ticket-api`.
2. Restaurar ultimo dump valido con `pg_restore --clean --if-exists`.
3. Levantar API: `docker compose up -d ticket-api`.
4. Registrar incidente operativo con fecha, commit y error de migracion.

## Frecuencia recomendada

- Desarrollo/local: antes de cada migracion manual.
- Produccion inicial: diario y antes de cada despliegue.
- Retencion sugerida: 7 diarios, 4 semanales y 3 mensuales, ajustable a politica institucional.
