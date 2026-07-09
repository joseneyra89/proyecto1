CREATE SCHEMA IF NOT EXISTS p2_sandbox;

ALTER TABLE p2_sandbox.service_case
    ADD COLUMN IF NOT EXISTS organization VARCHAR(180),
    ADD COLUMN IF NOT EXISTS reported_by VARCHAR(180),
    ADD COLUMN IF NOT EXISTS business_service VARCHAR(180);

ALTER TABLE p2_sandbox.ticket
    ADD COLUMN IF NOT EXISTS resolver_team VARCHAR(180),
    ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMPTZ;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'p2_sandbox.ticket'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE p2_sandbox.ticket DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'p2_sandbox.service_case'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE p2_sandbox.service_case DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

UPDATE p2_sandbox.ticket
SET status = 'NEW'
WHERE status = 'OPEN';

UPDATE p2_sandbox.service_case
SET status = 'NEW'
WHERE status = 'OPEN';

ALTER TABLE p2_sandbox.ticket
    ALTER COLUMN status SET DEFAULT 'NEW',
    ADD CONSTRAINT chk_ticket_workflow_status
        CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'SCHEDULED', 'RESOLVED', 'CLOSED', 'CANCELLED'));

ALTER TABLE p2_sandbox.service_case
    ALTER COLUMN status SET DEFAULT 'NEW',
    ADD CONSTRAINT chk_service_case_workflow_status
        CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'SCHEDULED', 'RESOLVED', 'CLOSED', 'CANCELLED'));

UPDATE p2_sandbox.service_case sc
SET organization = COALESCE(sc.organization, s.name)
FROM p2_sandbox.sites s
WHERE s.site_id = sc.site_id;

UPDATE p2_sandbox.service_case sc
SET reported_by = COALESCE(sc.reported_by, u.username)
FROM p2_sandbox.app_users u
WHERE u.user_id = sc.created_by_user_id;

UPDATE p2_sandbox.service_case
SET business_service = COALESCE(business_service, 'Mesa de Ayuda');

UPDATE p2_sandbox.ticket
SET resolver_team = COALESCE(resolver_team, 'Mesa de Ayuda');

CREATE INDEX IF NOT EXISTS idx_ticket_resolved_auto_close
    ON p2_sandbox.ticket (resolved_at)
    WHERE status = 'RESOLVED' AND deleted_at IS NULL;

INSERT INTO p2_sandbox.permissions (code, description)
VALUES ('MANAGEMENT_REPORT_READ', 'Consultar y exportar el informe de gestion por tecnico')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description,
    is_active = TRUE;

INSERT INTO p2_sandbox.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM p2_sandbox.roles r
JOIN p2_sandbox.permissions p ON p.code = 'MANAGEMENT_REPORT_READ'
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
