# Optional log-based analytics

This module is independent of the web demo. It reads only a dedicated nginx JSON log, stores bounded visitor details in SQLite and serves an authenticated admin page on loopback.

```sh
python3 -m venv .venv
.venv/bin/pip install -r analytics/requirements.lock
.venv/bin/python -m unittest discover -s analytics/tests -v
cp analytics/.env.example analytics/.env
# Fill your own settings, then export them before running analytics/app.py.
```

Required environment values: `DUO_ACCESS_ISSUER`, `DUO_ACCESS_AUD`, `DUO_ADMIN_EMAIL`. Optional `DUO_DATA_DIR`, `DUO_LOG_DIR`, `DUO_PORT`. The app does not automatically read `.env`; use your process manager's environment-file support. Bind remains 127.0.0.1.

Put it behind Cloudflare Access and restrict your own admin policy. The service independently verifies RS256 JWT signature, issuer, audience, timestamps and configured email. Missing or forged credentials fail closed. The repository contains no Access tenant, audience, account, server credentials or visitor data.

A dedicated nginx log must supply `at` (Unix seconds), `ip`, `method`, `path`, `status`, `ua`, `referrer` (host only) and `country` as JSON fields; see the tests for exact fixtures. Never trust proxy IP headers directly from arbitrary Internet clients. Configure a trusted local tunnel/proxy boundary yourself.

Successful homepage GETs count as visits; this version recognizes Mac DMG GETs as download requests. Range requests/retries are separate requests, IPs are not unique people, UA-based device/bot classification is approximate, and retention is at most 90 days / one million details. Do not claim APK download counts without extending the parser/tests.

The test-only preview server bypasses authentication with synthetic temporary data on localhost; never deploy the tests directory. Use your own process manager and environment file. No production server installation script is included.
