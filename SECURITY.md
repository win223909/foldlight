# Security

Do not post secrets, personal screenshots, visitor records or exploit details in a public issue. Use GitHub's private vulnerability reporting feature on this repository's Security tab when available. If it is unavailable, open a minimal issue requesting a private contact channel without sensitive details.

Scope includes local image handling, Shizuku IPC, desktop capture and the optional analytics service. Sensor availability and non-notarized development builds are documented limitations, not permission bypasses.

Only the latest source revision is maintained. No security support SLA is offered.

## Operator notes

- Keep analytics on loopback behind authenticated Cloudflare Access; the service independently validates JWT signature, issuer, audience, expiry and the configured email.
- Do not publish `.env`, private keys, keystores, provisioning profiles, SQLite data or raw logs.
- The source uses no production credentials. Deployment requires your own configuration and review.
- Development-signed Android packages are beta artifacts. Use your own protected release signing setup for production distribution.
