CREATE SCHEMA IF NOT EXISTS p2_sandbox;

ALTER TABLE p2_sandbox.app_users
    ADD COLUMN IF NOT EXISTS notification_email VARCHAR(160),
    ADD COLUMN IF NOT EXISTS job_title VARCHAR(120),
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

UPDATE p2_sandbox.app_users
SET notification_email = COALESCE(notification_email, email),
    job_title = COALESCE(job_title,
        CASE
            WHEN username = 'admin' THEN 'Administrador de mesa de ayuda'
            WHEN role_id = (SELECT role_id FROM p2_sandbox.roles WHERE code = 'TECH') THEN 'Tecnico de soporte'
            ELSE 'Usuario institucional'
        END);

CREATE TABLE IF NOT EXISTS p2_sandbox.auth_sessions (
    session_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES p2_sandbox.app_users(user_id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    ip_address INET,
    user_agent VARCHAR(250)
);

CREATE TABLE IF NOT EXISTS p2_sandbox.password_reset_tokens (
    reset_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES p2_sandbox.app_users(user_id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    ip_address INET,
    user_agent VARCHAR(250)
);

CREATE TABLE IF NOT EXISTS p2_sandbox.permissions (
    permission_id SMALLSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(250) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS p2_sandbox.role_permissions (
    role_id SMALLINT NOT NULL REFERENCES p2_sandbox.roles(role_id),
    permission_id SMALLINT NOT NULL REFERENCES p2_sandbox.permissions(permission_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_active
    ON p2_sandbox.auth_sessions (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_active
    ON p2_sandbox.password_reset_tokens (user_id, expires_at)
    WHERE used_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_app_users_active_profile
    ON p2_sandbox.app_users (is_active, deleted_at, LOWER(username));

INSERT INTO p2_sandbox.permissions (code, description)
VALUES
    ('AUTH_LOGOUT', 'Cerrar la sesion autenticada'),
    ('PROFILE_READ', 'Leer el perfil propio'),
    ('PROFILE_UPDATE', 'Actualizar el perfil propio'),
    ('USER_REQUEST_CREATE', 'Crear solicitudes propias de mesa de ayuda'),
    ('USER_REQUEST_READ', 'Leer solicitudes propias de mesa de ayuda'),
    ('USER_REQUEST_UPDATE', 'Actualizar solicitudes propias de mesa de ayuda'),
    ('USER_REQUEST_CANCEL', 'Cancelar solicitudes propias de mesa de ayuda'),
    ('TECH_REQUEST_POOL_READ', 'Leer cola de solicitudes pendientes'),
    ('TICKET_CREATE', 'Crear tickets sobre casos de servicio'),
    ('TICKET_READ', 'Leer tickets operativos'),
    ('TICKET_UPDATE', 'Actualizar tickets operativos'),
    ('TICKET_ASSIGN', 'Asignar o derivar tickets operativos'),
    ('TICKET_RESOLVE', 'Resolver tickets operativos'),
    ('METADATA_READ', 'Leer sedes, ubicaciones y metadatos activos'),
    ('SERVICE_CASE_CREATE', 'Crear casos de servicio REQUEST o INCIDENT'),
    ('SERVICE_CASE_READ', 'Leer casos de servicio segun alcance del rol'),
    ('QUEUE_READ', 'Leer colas de casos sin ticket'),
    ('ADMIN_USERS_READ', 'Listar y leer usuarios'),
    ('ADMIN_USERS_UPDATE_PROFILE', 'Actualizar perfil de usuarios'),
    ('ADMIN_USERS_UPDATE_STATUS', 'Activar o inactivar usuarios sin borrado duro')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description,
    is_active = TRUE;

INSERT INTO p2_sandbox.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM p2_sandbox.roles r
JOIN p2_sandbox.permissions p ON p.code IN (
    'AUTH_LOGOUT',
    'PROFILE_READ',
    'PROFILE_UPDATE',
    'USER_REQUEST_CREATE',
    'USER_REQUEST_READ',
    'USER_REQUEST_UPDATE',
    'USER_REQUEST_CANCEL',
    'METADATA_READ',
    'SERVICE_CASE_CREATE',
    'SERVICE_CASE_READ',
    'TICKET_READ'
)
WHERE r.code = 'USER'
ON CONFLICT DO NOTHING;

INSERT INTO p2_sandbox.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM p2_sandbox.roles r
JOIN p2_sandbox.permissions p ON p.code IN (
    'AUTH_LOGOUT',
    'PROFILE_READ',
    'PROFILE_UPDATE',
    'METADATA_READ',
    'SERVICE_CASE_READ',
    'TECH_REQUEST_POOL_READ',
    'QUEUE_READ',
    'TICKET_CREATE',
    'TICKET_READ',
    'TICKET_UPDATE',
    'TICKET_ASSIGN',
    'TICKET_RESOLVE'
)
WHERE r.code = 'TECH'
ON CONFLICT DO NOTHING;

INSERT INTO p2_sandbox.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM p2_sandbox.roles r
JOIN p2_sandbox.permissions p ON p.is_active = TRUE
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE p2_sandbox.auth_sessions IS 'Sesiones server-side para tokens opacos de autenticacion.';
COMMENT ON TABLE p2_sandbox.password_reset_tokens IS 'Tokens de recuperacion de contrasena de un solo uso.';
COMMENT ON TABLE p2_sandbox.role_permissions IS 'Matriz persistida de permisos por rol; la politica runtime usa deny-by-default.';
