# SLA and Email Configuration

## SLA

- Default `REQUEST` duration: 72 hours (`SLA_REQUEST_HOURS`, fallback `72`).
- Default `INCIDENT` duration: 24 hours (`SLA_INCIDENT_HOURS`, fallback `24`).
- Warning threshold: `sla_policies.warning_percent` per policy, fallback `SLA_WARNING_PERCENT`, then `80`.
- Initial calendar mode is 24x7. `sla_policies.calendar_code`, `calendar_policy`, and `business_hours_only` are stored so a business-calendar provider can replace the simple due-date calculation later.

SLA responses include:

- `code`: `ON_TRACK`, `WARNING`, `BREACHED`, `MET`, or `MET_LATE`.
- `text`: human-readable Spanish status.
- `icon`: ASCII icon marker such as `[OK]`, `[!]`, or `[X]`.
- `colorClass`: UI class; the UI also renders text and icon so state is not color-only.

## Email Notifications

Events currently queued and attempted:

- `case.created`
- `ticket.created`
- `ticket.assigned`
- `ticket.resolved`

Environment variables:

- `EMAIL_DELIVERY_MODE`: `log` by default, `smtp` for real SMTP, `disabled` to force failure records.
- `EMAIL_FROM`: sender address, default `mesa-ayuda@localhost`.
- `EMAIL_MAX_ATTEMPTS`: default `3`.
- `EMAIL_RETRY_DELAY_MINUTES`: default `15`.
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_AUTH`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_STARTTLS`, `SMTP_TIMEOUT_MS`: used only when `EMAIL_DELIVERY_MODE=smtp`.

Every email creates a row in `notifications`; each send attempt creates a row in `notification_attempts`. `POST /notifications/retry` lets an ADMIN process pending email retries whose `next_attempt_at` is due.
