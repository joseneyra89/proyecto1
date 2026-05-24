# Helpdesk Data Model Migration Summary

## Scope

Migration `docker/postgres/init/02-helpdesk-domain.sql` adds the first iteration of the helpdesk domain model while keeping the legacy tables in place.

## Legacy To New Mapping

| Legacy table | New table | Mapping notes |
| --- | --- | --- |
| `employee_type` | `roles` | `type_id = 1` maps to `USER`; `type_id = 2` maps to `TECH`, except username `admin`, which maps to `ADMIN`. |
| `employees` | `app_users` | Users are copied with `legacy_employee_id`. Legacy plain passwords are stored as `legacy:<pass>` in `password_hash` until a proper password reset/hash migration is implemented. |
| `ticket_requests` | `service_case` | Every legacy request becomes a `REQUEST` service case with `legacy_ticket_request_id`. Status `1` maps to `OPEN`; status `2` maps to `CLOSED`. |
| `tickets` | `ticket` | Every legacy ticket becomes a new operational ticket linked to its migrated `service_case` through `ticket_requests_id`. |
| `tickets.resolution` | `ticket_updates` | Existing resolutions are copied into a public `STATUS_CHANGE` update. |

## New Domain Tables

- `roles`: catalog for `USER`, `TECH`, and `ADMIN`.
- `app_users`: unified user profile entity.
- `sites`: support sites/sedes.
- `locations`: classroom/lab/area locations with optional hierarchy.
- `service_case`: base case with type `REQUEST` or `INCIDENT`.
- `ticket`: operational ticket related to a service case.
- `ticket_updates`: public/internal ticket history.
- `sla_policies`: SLA response and resolution policies.
- `notifications`: in-app/email notification records.
- `audit_logs`: immutable audit/migration trail.

## Compatibility

- Legacy routes under `/client/requests` remain active as aliases.
- New frontend calls use `/user/requests`.
- Legacy tables are not dropped or renamed.
- Legacy catalogs and `employees` gain `is_active` and `deleted_at` columns for non-destructive inactivation.
- New catalogs and users use `is_active` plus `deleted_at` for soft delete/inactivation.

## Follow-up Required

- Replace `legacy:<pass>` values with real password hashes after introducing a password reset or rehash flow.
- Move service logic from `ticket_requests`/`tickets` to `service_case`/`ticket`.
- Update Postman/Cucumber fixtures from `client` naming to `user` naming once legacy aliases are no longer needed.
- Replace hardcoded seed sites/locations when the definitive institutional sede list is available.
