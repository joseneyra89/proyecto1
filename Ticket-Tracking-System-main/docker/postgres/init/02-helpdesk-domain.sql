CREATE SCHEMA IF NOT EXISTS p2_sandbox;

-- Non-destructive compatibility columns for legacy catalogs/users.
ALTER TABLE p2_sandbox.employee_type
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE p2_sandbox.ticket_category
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE p2_sandbox.ticket_status
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE p2_sandbox.employees
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

UPDATE p2_sandbox.employee_type
SET description = 'Usuario'
WHERE type_id = 1
  AND description = 'Cliente';

CREATE TABLE IF NOT EXISTS p2_sandbox.roles (
    role_id SMALLSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE CHECK (code IN ('USER', 'TECH', 'ADMIN')),
    name VARCHAR(80) NOT NULL,
    description VARCHAR(250),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS p2_sandbox.app_users (
    user_id BIGSERIAL PRIMARY KEY,
    legacy_employee_id INTEGER UNIQUE,
    role_id SMALLINT NOT NULL REFERENCES p2_sandbox.roles(role_id),
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    email VARCHAR(160),
    phone VARCHAR(40),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS p2_sandbox.sites (
    site_id SERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(250),
    city VARCHAR(80) DEFAULT 'Canete',
    province VARCHAR(80) DEFAULT 'Canete',
    country VARCHAR(2) NOT NULL DEFAULT 'PE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS p2_sandbox.locations (
    location_id BIGSERIAL PRIMARY KEY,
    site_id INTEGER NOT NULL REFERENCES p2_sandbox.sites(site_id),
    parent_location_id BIGINT REFERENCES p2_sandbox.locations(location_id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('AULA', 'LAB', 'AREA')),
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    floor VARCHAR(20),
    description VARCHAR(250),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS p2_sandbox.sla_policies (
    sla_policy_id SERIAL PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    case_type VARCHAR(20) NOT NULL CHECK (case_type IN ('REQUEST', 'INCIDENT')),
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    response_minutes INTEGER NOT NULL CHECK (response_minutes > 0),
    resolution_minutes INTEGER NOT NULL CHECK (resolution_minutes > 0),
    business_hours_only BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE SEQUENCE IF NOT EXISTS p2_sandbox.service_case_number_seq START WITH 1;
CREATE SEQUENCE IF NOT EXISTS p2_sandbox.ticket_number_seq START WITH 1;

CREATE TABLE IF NOT EXISTS p2_sandbox.service_case (
    case_id BIGSERIAL PRIMARY KEY,
    case_number VARCHAR(40) NOT NULL DEFAULT ('SC-' || LPAD(nextval('p2_sandbox.service_case_number_seq')::TEXT, 8, '0')),
    type VARCHAR(20) NOT NULL CHECK (type IN ('REQUEST', 'INCIDENT')),
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    requester_user_id BIGINT NOT NULL REFERENCES p2_sandbox.app_users(user_id),
    affected_user_id BIGINT REFERENCES p2_sandbox.app_users(user_id),
    site_id INTEGER REFERENCES p2_sandbox.sites(site_id),
    location_id BIGINT REFERENCES p2_sandbox.locations(location_id),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    legacy_ticket_request_id INTEGER UNIQUE,
    created_by_user_id BIGINT REFERENCES p2_sandbox.app_users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_service_case_number UNIQUE (case_number)
);

CREATE TABLE IF NOT EXISTS p2_sandbox.ticket (
    ticket_id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(40) NOT NULL DEFAULT ('T-' || LPAD(nextval('p2_sandbox.ticket_number_seq')::TEXT, 8, '0')),
    service_case_id BIGINT NOT NULL REFERENCES p2_sandbox.service_case(case_id),
    assigned_to_user_id BIGINT REFERENCES p2_sandbox.app_users(user_id),
    created_by_user_id BIGINT REFERENCES p2_sandbox.app_users(user_id),
    sla_policy_id INTEGER REFERENCES p2_sandbox.sla_policies(sla_policy_id),
    category_code VARCHAR(60),
    summary VARCHAR(180) NOT NULL,
    description TEXT,
    resolution TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    due_at TIMESTAMPTZ,
    legacy_ticket_id INTEGER UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_ticket_number UNIQUE (ticket_number)
);

CREATE TABLE IF NOT EXISTS p2_sandbox.ticket_updates (
    ticket_update_id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES p2_sandbox.ticket(ticket_id),
    author_user_id BIGINT REFERENCES p2_sandbox.app_users(user_id),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    update_type VARCHAR(30) NOT NULL DEFAULT 'COMMENT' CHECK (update_type IN ('COMMENT', 'STATUS_CHANGE', 'ASSIGNMENT', 'SLA', 'SYSTEM')),
    body TEXT NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS p2_sandbox.notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL REFERENCES p2_sandbox.app_users(user_id),
    service_case_id BIGINT REFERENCES p2_sandbox.service_case(case_id),
    ticket_id BIGINT REFERENCES p2_sandbox.ticket(ticket_id),
    type VARCHAR(40) NOT NULL,
    channel VARCHAR(30) NOT NULL DEFAULT 'IN_APP' CHECK (channel IN ('IN_APP', 'EMAIL')),
    subject VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'READ', 'FAILED')),
    read_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS p2_sandbox.audit_logs (
    audit_log_id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES p2_sandbox.app_users(user_id),
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    before_data JSONB,
    after_data JSONB,
    ip_address INET,
    user_agent VARCHAR(250),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_users_username_active
    ON p2_sandbox.app_users (LOWER(username))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_app_users_role_active
    ON p2_sandbox.app_users (role_id, is_active)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_locations_site_code_active
    ON p2_sandbox.locations (site_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_locations_site_type_active
    ON p2_sandbox.locations (site_id, type, is_active)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_case_requester_status
    ON p2_sandbox.service_case (requester_user_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_case_type_priority_status
    ON p2_sandbox.service_case (type, priority, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_case_site_location
    ON p2_sandbox.service_case (site_id, location_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ticket_case_status
    ON p2_sandbox.ticket (service_case_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ticket_assignee_status
    ON p2_sandbox.ticket (assigned_to_user_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ticket_updates_ticket_created
    ON p2_sandbox.ticket_updates (ticket_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_status
    ON p2_sandbox.notifications (recipient_user_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
    ON p2_sandbox.audit_logs (entity_type, entity_id, created_at DESC);

INSERT INTO p2_sandbox.roles (code, name, description)
VALUES
    ('USER', 'Usuario', 'Usuario solicitante de mesa de ayuda'),
    ('TECH', 'Tecnico', 'Tecnico responsable de atender tickets'),
    ('ADMIN', 'Administrador', 'Administrador de catalogos y configuracion')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_active = TRUE,
    deleted_at = NULL,
    updated_at = NOW();

INSERT INTO p2_sandbox.sites (code, name, address, city, province, country)
VALUES
    ('UNDC-MAIN', 'Universidad Nacional de Canete - Sede Principal', NULL, 'Canete', 'Canete', 'PE'),
    ('UNDC-ADMIN', 'Universidad Nacional de Canete - Sede Administrativa', NULL, 'Canete', 'Canete', 'PE')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    address = EXCLUDED.address,
    city = EXCLUDED.city,
    province = EXCLUDED.province,
    country = EXCLUDED.country,
    is_active = TRUE,
    deleted_at = NULL,
    updated_at = NOW();

INSERT INTO p2_sandbox.locations (site_id, type, code, name, floor, description)
SELECT s.site_id, seed.type, seed.code, seed.name, seed.floor, seed.description
FROM (
    VALUES
        ('UNDC-MAIN', 'AULA', 'AULA-GEN-001', 'Aula General 001', '1', 'Aula semilla para clasificar incidencias academicas'),
        ('UNDC-MAIN', 'LAB', 'LAB-GEN-001', 'Laboratorio General 001', '1', 'Laboratorio semilla para soporte tecnico'),
        ('UNDC-ADMIN', 'AREA', 'AREA-SOPORTE', 'Area de Soporte TI', NULL, 'Area responsable de mesa de ayuda')
) AS seed(site_code, type, code, name, floor, description)
JOIN p2_sandbox.sites s ON s.code = seed.site_code
WHERE NOT EXISTS (
    SELECT 1
    FROM p2_sandbox.locations l
    WHERE l.site_id = s.site_id
      AND LOWER(l.code) = LOWER(seed.code)
      AND l.deleted_at IS NULL
);

INSERT INTO p2_sandbox.sla_policies (
    code, name, case_type, priority, response_minutes, resolution_minutes, business_hours_only, is_default
)
VALUES
    ('REQ-MEDIUM-DEFAULT', 'Solicitud media por defecto', 'REQUEST', 'MEDIUM', 240, 2880, TRUE, TRUE),
    ('REQ-HIGH-DEFAULT', 'Solicitud alta por defecto', 'REQUEST', 'HIGH', 120, 1440, TRUE, FALSE),
    ('INC-HIGH-DEFAULT', 'Incidente alto por defecto', 'INCIDENT', 'HIGH', 60, 480, TRUE, TRUE),
    ('INC-CRITICAL-DEFAULT', 'Incidente critico por defecto', 'INCIDENT', 'CRITICAL', 15, 240, FALSE, FALSE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    case_type = EXCLUDED.case_type,
    priority = EXCLUDED.priority,
    response_minutes = EXCLUDED.response_minutes,
    resolution_minutes = EXCLUDED.resolution_minutes,
    business_hours_only = EXCLUDED.business_hours_only,
    is_default = EXCLUDED.is_default,
    is_active = TRUE,
    deleted_at = NULL,
    updated_at = NOW();

INSERT INTO p2_sandbox.app_users (
    legacy_employee_id, role_id, username, password_hash, first_name, last_name, is_active
)
SELECT
    e.employees_id,
    r.role_id,
    e.username,
    'legacy:' || e.pass,
    e.first_name,
    CASE WHEN e.last_name = 'Cliente' THEN 'Usuario' ELSE e.last_name END,
    COALESCE(e.is_active, TRUE)
FROM p2_sandbox.employees e
JOIN p2_sandbox.roles r
    ON r.code = CASE
        WHEN LOWER(e.username) = 'admin' THEN 'ADMIN'
        WHEN e.type_id = 2 THEN 'TECH'
        ELSE 'USER'
    END
ON CONFLICT (legacy_employee_id) DO UPDATE
SET role_id = EXCLUDED.role_id,
    username = EXCLUDED.username,
    password_hash = EXCLUDED.password_hash,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    is_active = EXCLUDED.is_active,
    deleted_at = NULL,
    updated_at = NOW();

INSERT INTO p2_sandbox.service_case (
    case_number,
    type,
    title,
    description,
    requester_user_id,
    affected_user_id,
    site_id,
    status,
    priority,
    legacy_ticket_request_id,
    created_by_user_id
)
SELECT
    'REQ-' || tr.ticket_requests_id,
    'REQUEST',
    LEFT(tr.description, 160),
    tr.description,
    u.user_id,
    u.user_id,
    s.site_id,
    CASE WHEN tr.status_id = 2 THEN 'CLOSED' ELSE 'OPEN' END,
    'MEDIUM',
    tr.ticket_requests_id,
    u.user_id
FROM p2_sandbox.ticket_requests tr
JOIN p2_sandbox.app_users u ON u.legacy_employee_id = tr.employee_id
LEFT JOIN p2_sandbox.sites s ON s.code = 'UNDC-MAIN'
ON CONFLICT (legacy_ticket_request_id) DO UPDATE
SET title = EXCLUDED.title,
    description = EXCLUDED.description,
    requester_user_id = EXCLUDED.requester_user_id,
    affected_user_id = EXCLUDED.affected_user_id,
    site_id = EXCLUDED.site_id,
    status = EXCLUDED.status,
    updated_at = NOW(),
    deleted_at = NULL;

INSERT INTO p2_sandbox.ticket (
    ticket_number,
    service_case_id,
    assigned_to_user_id,
    created_by_user_id,
    sla_policy_id,
    category_code,
    summary,
    description,
    resolution,
    status,
    priority,
    legacy_ticket_id
)
SELECT
    'T-LEG-' || t.tickets_id,
    sc.case_id,
    u.user_id,
    u.user_id,
    sp.sla_policy_id,
    CASE
        WHEN t.category = 1 THEN 'HARDWARE'
        WHEN t.category = 2 THEN 'SOFTWARE'
        ELSE 'GENERAL'
    END,
    LEFT(t.ticket_comments, 180),
    t.ticket_comments,
    t.resolution,
    CASE WHEN t.status_id = 2 THEN 'RESOLVED' ELSE 'IN_PROGRESS' END,
    'MEDIUM',
    t.tickets_id
FROM p2_sandbox.tickets t
JOIN p2_sandbox.service_case sc ON sc.legacy_ticket_request_id = t.ticket_requests_id
JOIN p2_sandbox.app_users u ON u.legacy_employee_id = t.employee_id
LEFT JOIN p2_sandbox.sla_policies sp ON sp.code = 'REQ-MEDIUM-DEFAULT'
ON CONFLICT (legacy_ticket_id) DO UPDATE
SET service_case_id = EXCLUDED.service_case_id,
    assigned_to_user_id = EXCLUDED.assigned_to_user_id,
    created_by_user_id = EXCLUDED.created_by_user_id,
    sla_policy_id = EXCLUDED.sla_policy_id,
    category_code = EXCLUDED.category_code,
    summary = EXCLUDED.summary,
    description = EXCLUDED.description,
    resolution = EXCLUDED.resolution,
    status = EXCLUDED.status,
    updated_at = NOW(),
    deleted_at = NULL;

UPDATE p2_sandbox.service_case sc
SET status = CASE WHEN t.status = 'RESOLVED' THEN 'RESOLVED' ELSE 'IN_PROGRESS' END,
    resolved_at = CASE WHEN t.status = 'RESOLVED' THEN COALESCE(sc.resolved_at, t.resolved_at, NOW()) ELSE sc.resolved_at END,
    updated_at = NOW()
FROM p2_sandbox.ticket t
WHERE t.service_case_id = sc.case_id
  AND t.deleted_at IS NULL
  AND sc.deleted_at IS NULL
  AND sc.status NOT IN ('CLOSED', 'CANCELLED');

INSERT INTO p2_sandbox.ticket_updates (
    ticket_id,
    author_user_id,
    visibility,
    update_type,
    body,
    previous_status,
    new_status
)
SELECT
    nt.ticket_id,
    nt.assigned_to_user_id,
    'PUBLIC',
    'STATUS_CHANGE',
    'Resolucion migrada: ' || lt.resolution,
    'IN_PROGRESS',
    'RESOLVED'
FROM p2_sandbox.tickets lt
JOIN p2_sandbox.ticket nt ON nt.legacy_ticket_id = lt.tickets_id
WHERE lt.resolution IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM p2_sandbox.ticket_updates tu
      WHERE tu.ticket_id = nt.ticket_id
        AND tu.update_type = 'STATUS_CHANGE'
        AND tu.body = 'Resolucion migrada: ' || lt.resolution
  );

INSERT INTO p2_sandbox.audit_logs (
    actor_user_id,
    entity_type,
    entity_id,
    action,
    after_data
)
SELECT
    NULL,
    'service_case',
    sc.case_id::TEXT,
    'MIGRATE_LEGACY_TICKET_REQUEST',
    jsonb_build_object('legacy_ticket_request_id', sc.legacy_ticket_request_id)
FROM p2_sandbox.service_case sc
WHERE sc.legacy_ticket_request_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM p2_sandbox.audit_logs al
      WHERE al.entity_type = 'service_case'
        AND al.entity_id = sc.case_id::TEXT
        AND al.action = 'MIGRATE_LEGACY_TICKET_REQUEST'
  );

COMMENT ON TABLE p2_sandbox.app_users IS 'Entidad base unificada de usuarios para roles USER, TECH y ADMIN.';
COMMENT ON TABLE p2_sandbox.service_case IS 'Caso base de mesa de ayuda: REQUEST o INCIDENT.';
COMMENT ON TABLE p2_sandbox.ticket IS 'Ticket operativo relacionado a un service_case.';
COMMENT ON TABLE p2_sandbox.ticket_updates IS 'Historial de comentarios/cambios del ticket con visibilidad PUBLIC o INTERNAL.';
COMMENT ON TABLE p2_sandbox.audit_logs IS 'Registro historico de cambios y migraciones; no se borra fisicamente.';
