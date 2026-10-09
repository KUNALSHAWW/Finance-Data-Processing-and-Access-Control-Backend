# Finance Ledger API

A finance backend (Spring Boot 3.5, Java 21, MySQL) with role-based access control and one idea most CRUD backends skip: **you can prove the data was not quietly changed.**

Every change to a financial record or a user goes into a hash-chained audit ledger in the same database transaction. One endpoint, `GET /api/audit/verify`, re-checks the whole ledger and reports:

- an audit entry that was edited or removed,
- an amount changed directly in the database, behind the API's back,
- a user promoted to ADMIN with a SQL `UPDATE`,
- a row inserted or deleted without going through the API.

On top of that, `GET /api/dashboard/insights` flags unusual transactions with an explainable statistic (no black-box model), and every flag carries its reason.

## Try it in two minutes

```bash
cp .env.example .env        # set DB_PASSWORD, JWT_SECRET (32+ chars), BOOTSTRAP_ADMIN_PASSWORD
docker compose up --build
```

Open http://localhost:8080/swagger-ui/index.html, log in with `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` (the first admin is created automatically on an empty database, no code edits), then click **Authorize** and paste the token.

The tamper demo:

```bash
# 1. create a record through the API, then verify: {"valid": true, ...}
# 2. change it behind the API's back:
docker compose exec db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" finance_db -e "update financial_records set amount = 1 where id = 1"'
# 3. verify again: {"valid": false, "findings": [{"entityType":"RECORD","entityId":1,"problem":"MODIFIED"}]}
```

Without Docker: Java 21, Maven, a MySQL 8 database, then set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` and run `./mvnw spring-boot:run`. Configuration is environment-only; no secrets live in the repo.

## Roles

Each cell below is an automated test (`AccessControlTest.roleMatrix`).

| Action | Viewer | Analyst | Admin |
|---|:--:|:--:|:--:|
| List / read records, dashboard summary, monthly trends | yes | yes | yes |
| Anomaly insights, audit log, ledger verification | no | yes | yes |
| Create / update / delete records | no | no | yes |
| Manage users | no | no | yes |

## API

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/login` | public |
| POST, GET | `/api/users`, `/api/users/{id}` | Admin |
| PUT | `/api/users/{id}/activate`, `/deactivate` | Admin |
| DELETE | `/api/users/{id}` | Admin |
| POST, PUT, DELETE | `/api/records`, `/api/records/{id}` | Admin |
| GET | `/api/records`, `/api/records/{id}` | all roles |
| GET | `/api/dashboard/summary`, `/api/dashboard/trends` | all roles |
| GET | `/api/dashboard/insights` | Admin, Analyst |
| GET | `/api/audit`, `/api/audit/verify` | Admin, Analyst |

`GET /api/records` takes `page`, `size` (1-100), `type`, `category`, `from`, `to`. Records are soft-deleted and listed newest first. Errors always use one JSON shape: `{"error": "...", "message": "...", "fieldErrors": {...}}`.

## How the audit ledger works

Each entry stores: sequence number, time, actor, action, entity, a **digest of the entity's state after the change**, the previous entry's hash, and its own hash (SHA-256 over length-prefixed fields).

- Editing, removing or reordering an entry breaks the chain at that entry.
- Because each entry also stores the entity's digest, `verify` can compare every live record and user with the last state the API wrote. A row changed with raw SQL no longer matches (`MODIFIED`), a row nobody audited is `UNAUDITED`, and a vanished row is `MISSING`.
- Audit writes use `Propagation.MANDATORY`, so the entry commits or rolls back together with the change itself. Writers take a row lock on the chain head so concurrent requests cannot fork the chain (tested with 8 threads).

**What it does not protect against.** This is tamper-*evidence*, not tamper-proofing. Someone with full write access to the database who recomputes the entire chain consistently will produce a chain that verifies. To cover that, record `headHash` (returned by `verify`) somewhere the database owner cannot edit, such as a periodic email, a signed commit, or an external timestamp service. For stronger guarantees, also revoke `UPDATE`/`DELETE` on `audit_log` from the application's database user. The password hash and lockout counters are deliberately left out of the user digest because they change without an admin action.

## How anomaly detection works

For each (type, category) with at least 5 records, a record is flagged when its **modified z-score** exceeds 3.5: `0.6745 * (x - median) / MAD` (Iglewicz and Hoaglin; 3.5 is their recommended cutoff). Median and MAD are used instead of mean and standard deviation because one huge outlier inflates the standard deviation enough to hide itself; a unit test shows a 1,000,000 outlier that mean/stddev cannot catch (for 7 samples its z-score can never exceed 2.27). If most amounts are identical (MAD = 0) it falls back to the mean absolute deviation with the standard 1.253314 factor. Categories are judged only against themselves, so rent is not compared with coffee.

It is a heuristic for "look at this", not a fraud verdict.

## Security notes

- JWT (HS256) access tokens; the secret must be 32+ bytes or the app refuses to start. The token holds only the email; **role and active flag are read from the database on every request**, so deactivating a user takes effect immediately.
- Account lockout: 5 wrong passwords lock the account for 15 minutes (configurable), HTTP 429.
- Missing or bad token returns 401, missing permission returns 403, both as JSON.
- Passwords: BCrypt, 8-72 characters with upper, lower, digit and special character.
- Money is `DECIMAL(19,2)` / `BigDecimal`, validated to be at least 0.01 with at most 2 decimals.
- Schema is owned by Flyway (`V1__init.sql`); Hibernate runs in `validate` mode, so entity and schema drift fails at startup instead of silently altering tables.
- CORS is off unless `CORS_ALLOWED_ORIGINS` is set. Set `DOCS_ENABLED=false` to hide Swagger in production.
- Optimistic locking on records: two admins editing the same record get a 409 instead of a silent overwrite.

**Known limitations.** No refresh tokens or logout/revocation list (deactivation is the revocation mechanism). Lockout is per account, not per IP, so it does not slow a credential-stuffing run across many accounts. Audit writes are serialized by one row lock (fine at this scale; shard per entity type if write volume demands it). Dashboard trends and `verify` are full scans bounded by batch size, not incremental.

## Architecture

```
controller  ->  service  ->  repository  ->  MySQL
   |              |
   |              +--> AuditService (hash chain, verify)
   |              +--> AnomalyDetector (pure function)
JwtFilter -> SecurityContext -> @PreAuthorize
```

```
src/main/java/com/kunal/finance/backend/
  config/      security chain, Swagger, first-admin bootstrap
  security/    JwtUtil, JwtFilter, user details
  controller/  Auth, Users, Records, Dashboard, Audit
  service/     business rules
  audit/       hash chain + verification
  insights/    anomaly detection
  entity/ repository/ dto/ exception/
src/main/resources/db/migration/V1__init.sql
```

## Tests

```bash
mvn verify        # 55 tests, H2 in MySQL mode, runs the same Flyway migration
```

They cover the full role matrix, 401/403/429 behavior, deactivated-token handling, validation and status codes, exact decimal totals (including a category with both income and expense), soft delete, every tampering scenario above, concurrent audit writes, the anomaly math, and first-admin bootstrap. CI runs `mvn verify` on every push (`.github/workflows/ci.yml`). The test database is H2, so the app was also run end to end against MySQL 8 with Docker Compose to check the real migration and `validate` mode.

## What changed from v0.1

The first version had these defects, all fixed and covered by tests: record listing crashed for everyone (`hasRole('ADMIN','ANALYST')`); deactivated users could still log in and use tokens; no way to create the first admin without editing code; 401 was actually 403; a user without a role broke authentication; the record `date` was ignored; the README, Swagger and code disagreed on who may do what; category totals added income and expense together; plain `RuntimeException`s returned 500; money was a `Double`; page size was unbounded; admins could delete themselves; the dashboard loaded the whole table into memory; and the Postman collection contained real-looking credentials and a signed token. The old Swagger screenshots were removed because they showed the previous API.

## License

MIT, see [LICENSE](LICENSE).
