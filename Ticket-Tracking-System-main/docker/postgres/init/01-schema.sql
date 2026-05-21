CREATE SCHEMA IF NOT EXISTS p2_sandbox;

CREATE TABLE IF NOT EXISTS p2_sandbox.employee_type (
    type_id INTEGER PRIMARY KEY,
    description VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS p2_sandbox.ticket_status (
    status_id INTEGER PRIMARY KEY,
    description VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS p2_sandbox.ticket_category (
    type_id INTEGER PRIMARY KEY,
    description VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS p2_sandbox.employees (
    employees_id SERIAL PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    pass VARCHAR(100) NOT NULL,
    type_id INTEGER NOT NULL REFERENCES p2_sandbox.employee_type(type_id)
);

CREATE TABLE IF NOT EXISTS p2_sandbox.ticket_requests (
    ticket_requests_id SERIAL PRIMARY KEY,
    employee_id INTEGER NOT NULL REFERENCES p2_sandbox.employees(employees_id) ON DELETE CASCADE,
    description VARCHAR(250) NOT NULL,
    status_id INTEGER NOT NULL DEFAULT 1 REFERENCES p2_sandbox.ticket_status(status_id)
);

CREATE TABLE IF NOT EXISTS p2_sandbox.tickets (
    tickets_id SERIAL PRIMARY KEY,
    employee_id INTEGER NOT NULL REFERENCES p2_sandbox.employees(employees_id) ON DELETE CASCADE,
    ticket_requests_id INTEGER REFERENCES p2_sandbox.ticket_requests(ticket_requests_id) ON DELETE CASCADE,
    category INTEGER NOT NULL REFERENCES p2_sandbox.ticket_category(type_id),
    ticket_comments VARCHAR(250) NOT NULL,
    resolution VARCHAR(250),
    status_id INTEGER NOT NULL DEFAULT 1 REFERENCES p2_sandbox.ticket_status(status_id)
);

INSERT INTO p2_sandbox.employee_type (type_id, description) VALUES
    (1, 'Cliente'),
    (2, 'Tecnico')
ON CONFLICT (type_id) DO NOTHING;

INSERT INTO p2_sandbox.ticket_status (status_id, description) VALUES
    (1, 'Abierto'),
    (2, 'Cerrado')
ON CONFLICT (status_id) DO NOTHING;

INSERT INTO p2_sandbox.ticket_category (type_id, description) VALUES
    (1, 'Hardware'),
    (2, 'Software')
ON CONFLICT (type_id) DO NOTHING;

INSERT INTO p2_sandbox.employees (employees_id, first_name, last_name, username, pass, type_id) VALUES
    (1, 'Ana', 'Cliente', 'cliente1', 'pass', 1),
    (2, 'Mary', 'Brown', 'mb1', 'pass', 1),
    (3, 'Luis', 'Cliente', 'cliente2', 'pass', 1),
    (4, 'Rosa', 'Cliente', 'cliente3', 'pass', 1),
    (5, 'Tomas', 'Tecnico', 'tech1', 'pass', 2),
    (6, 'Mario', 'Tecnico', 'tech2', 'pass', 2),
    (7, 'Admin', 'Mesa', 'admin', 'pass', 2)
ON CONFLICT (employees_id) DO NOTHING;

SELECT setval('p2_sandbox.employees_employees_id_seq', COALESCE((SELECT MAX(employees_id) FROM p2_sandbox.employees), 1), true);

INSERT INTO p2_sandbox.ticket_requests (ticket_requests_id, employee_id, description, status_id) VALUES
    (1, 1, 'No puedo iniciar sesion en mi equipo', 1),
    (2, 3, 'La impresora no responde', 1)
ON CONFLICT (ticket_requests_id) DO NOTHING;

SELECT setval('p2_sandbox.ticket_requests_ticket_requests_id_seq', COALESCE((SELECT MAX(ticket_requests_id) FROM p2_sandbox.ticket_requests), 1), true);
