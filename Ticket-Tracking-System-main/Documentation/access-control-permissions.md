# Access Control Permission Matrix

Deny-by-default is enforced in `com.businessName.security.AuthMiddleware`: a route must be public or mapped to a permission in `AccessPolicy`; otherwise it is rejected.

| Permission | USER | TECH | ADMIN |
| --- | --- | --- | --- |
| `AUTH_LOGOUT` | yes | yes | yes |
| `PROFILE_READ` | yes | yes | yes |
| `PROFILE_UPDATE` | yes | yes | yes |
| `USER_REQUEST_CREATE` | yes | no | yes |
| `USER_REQUEST_READ` | yes | no | yes |
| `USER_REQUEST_UPDATE` | yes | no | yes |
| `USER_REQUEST_CANCEL` | yes | no | yes |
| `METADATA_READ` | yes | yes | yes |
| `SERVICE_CASE_CREATE` | yes | no | yes |
| `SERVICE_CASE_READ` | yes | yes | yes |
| `TECH_REQUEST_POOL_READ` | no | yes | yes |
| `QUEUE_READ` | no | yes | yes |
| `DASHBOARD_TECH_READ` | no | yes | yes |
| `TICKET_CREATE` | no | yes | yes |
| `TICKET_READ` | yes | yes | yes |
| `TICKET_UPDATE` | no | yes | yes |
| `TICKET_ASSIGN` | no | yes | yes |
| `TICKET_RESOLVE` | no | yes | yes |
| `NOTIFICATIONS_RETRY` | no | no | yes |
| `ADMIN_USERS_READ` | no | no | yes |
| `ADMIN_USERS_CREATE` | no | no | yes |
| `ADMIN_USERS_UPDATE_PROFILE` | no | no | yes |
| `ADMIN_USERS_UPDATE_STATUS` | no | no | yes |
| `CATALOG_MANAGE` | no | no | yes |

## Public Endpoints

- `POST /login`
- `POST /password/forgot`
- `POST /password/reset`

## Protected Endpoint Groups

- `GET /me`, `PATCH /me`: authenticated profile access.
- `GET /sites`, `GET /sites/{siteId}/locations`: active metadata for forms.
- `POST /sites`, `PATCH /sites/{siteId}`, `PATCH /sites/{siteId}/status`, `POST /sites/{siteId}/locations`, `PATCH /locations/{locationId}`, `PATCH /locations/{locationId}/status`: site/location catalog management for `ADMIN`.
- `GET /dashboard/technician`: technician/admin metrics by REQUEST and INCIDENT with SLA percentages for a date range.
- `POST /service-cases`, `GET /service-cases`, `GET /service-cases/{caseId}`: service case lifecycle scoped by role.
- `GET /queues/service-cases`: technician/admin queues for cases without tickets.
- `GET /tickets`, `GET /tickets/{ticketId}`, `POST /service-cases/{caseId}/tickets`, `PATCH /tickets/{ticketId}`, `POST /tickets/{ticketId}/assign`, `POST /tickets/{ticketId}/resolve`: ticket operations. USER read access is additionally scoped in the service layer to own tickets and PUBLIC updates.
- `POST /notifications/retry`: email retry pipeline processing for `ADMIN`.
- `/user/requests`: user request lifecycle for `USER` and `ADMIN`.
- `/technician/*`: technician workflow for `TECH` and `ADMIN`.
- `/admin/users`: user listing, creation and administration for `ADMIN`.

Legacy `/client/requests` routes remain as aliases for `/user/requests` during migration.
