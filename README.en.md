[中文](README.md) | [English](README.en.md)

# ZhuaTech AutoCare · Vehicle Repair Workshop Management

<img src="frontend/public/brand/logo.jpg" height="48" alt="ZhiHua Technology logo">

**Public source for learning 1.0.0 / non-commercial use** · **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/).

Java 21, Spring Boot, Vue 3 and MySQL support vehicle intake, repair estimates, recorded customer consent, technician assignment, parts issues and returns, quality review, payment records and handover. Independent workshops and implementation teams can run this source to study the workflow and evaluate its limits. The operator and administration interfaces support Chinese and English.

The project's own source is for personal learning, technical research and non-commercial exchange. Commercial use requires written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. The root [LICENSE](LICENSE) is a non-commercial source license, **not an OSI open-source license**. Third-party licenses remain separate.

## From intake to handover

1. A service advisor maintains customers and vehicles, records mileage, complaints and intake condition, and creates a job. Mileage cannot decrease; one vehicle cannot have two unfinished jobs. Jobs retain vehicle/customer snapshots when master records change.
2. An estimate snapshots labor/part names, units, quantities and prices. Submitting freezes its contents. After obtaining external customer consent, an advisor records the evidence reference and acceptance. **Consent is a manual record; there is no online customer approval or electronic signature.** Additional work needs its own accepted estimate and does not replace earlier authorization.
3. Assign an enabled technician from the same department and start work. Parts operators issue only against accepted lines, within authorized quantities. Return unused parts against the original issue before completing the line; the original issue cost is restored to inventory.
4. The assigned technician checks actual consumption and completes each item. Labor charges use agreed quantities; parts charges use net issued quantities. A part line may consume less than authorized with an explanation, and the unused quantity is not billed. Completed items freeze against further issues/returns.
5. All accepted items must be complete and draft/presented estimates resolved before quality review. A reviewer performs physical checks outside the software and records pass or rework. The application does not inspect the vehicle.
6. After quality passes, a cashier records money actually received, partial payments and any discount. An incorrect payment entry can be reversed against the original before handover. **Reversal does not transfer or refund bank funds.** Full settlement is required before recording vehicle collection; delivered jobs freeze.

## Implemented operator and administration features

| Area | Available behavior |
| --- | --- |
| Customers and vehicles | Create/edit/delete where allowed, enable/disable, owner association, mileage protection and authorized repair history |
| Labor and parts | Reference services, base units, prices, reorder levels, reference protection and atomic JSON part imports |
| Jobs | Intake updates before estimates, state filtering, assignment, start and cancellation before work |
| Estimates | Draft update/delete, submitted snapshots, manual acceptance/rejection references and independent additions |
| Inventory | Receipts, moving-average cost, authorized issues, original-issue returns, stock counts with quantity/value snapshots and retained movements |
| Repair and quality | Assigned-technician execution, item completion, unresolved-authorization checks, physical-check pass/rework records |
| Settlement | Manual payment entries, partial payment, linked reversal, discount, outstanding balance and settlement-gated handover |
| Queries | Dashboard, search/sort/pagination, intake-date reports, CSV, estimate and settlement printing |
| Administration | Accounts, passwords, roles, current permissions, departments/scopes, registered menu settings, dictionaries, parameters and audit |
| Infrastructure | Flyway on an empty database, health checks, Compose and automated tests |

The administrator creates separate service-advisor, technician, parts-operator, quality-reviewer and cashier accounts. Department roles see their workshop; the built-in technician role sees only assigned jobs. Job details, history, dashboard and movements apply that restriction. Reference customer/vehicle/part masters remain department-visible; technician access is not limited to one customer's master record. HTTP endpoints independently enforce permissions, regardless of hidden menus.

## Limits and unimplemented integrations

- One company, one currency with two decimal places, base-unit parts and one stock inventory per department. Currency cannot change after a job or stock movement exists; there is no currency conversion.
- A parts receipt is an actual stock record, not purchase approval, supplier payables or a tax entry. Estimate quantities cap authorization; settlement uses completed labor and consumed parts. Billed amount less parts cost excludes wages, taxes and overhead, and is not accounting profit.
- No appointments, service reminders, labor timer, VIN decoding, OCR, parking location, automatic diagnosis, insurance claims, tax invoices, payment channels, online consent, electronic signatures, attachments, tenant isolation, cross-department transfers, lot/serial stock or general ledger. VIN is optional text, not verified identity. External consent evidence must be retained separately.
- Rework can add a new repair record or separately authorized estimate; completed items cannot be rewritten. No post-delivery refunds or linked return repairs are implemented. Delivered jobs reject business/financial changes; after-sales and actual refunds need a separate process or extension.
- All business/administration writes serialize through a database lock. Each reference, job, movement and audit resource class has a 10,000-row hard limit; exceeding it rejects the read rather than silently truncating reports. Large-load and long-term real-workshop operation have not been verified.
- Login rate limits and sessions are single-process. Multiple instances require shared sessions and coordinated rate limiting. No production-readiness, external integration or commercial customer claim is made.

## Actual running screens

These are screenshots of the running application with fictional learning records, not real workshop customers or payment evidence.

| Login | Operator dashboard |
| --- | --- |
| ![Login](docs/screenshots/login.png) | ![Operator dashboard](docs/screenshots/operator-home.png) |

The login supports language selection. The dashboard shows job states, recent jobs and stock warnings.

The job detail combines intake facts, separately authorized estimates, assignment, parts issues/returns and completion.

![Job, estimates and parts](docs/screenshots/job-detail.png)

| Administration accounts | Roles and permissions |
| --- | --- |
| ![Accounts](docs/screenshots/accounts.png) | ![Permissions](docs/screenshots/permissions.png) |

Accounts assign staff roles and departments. Permissions configure registered functions and data scopes; backend checks remain authoritative.

Reports summarize quality-passed jobs, money records and parts cost by intake date, with CSV export.

![Workshop reports](docs/screenshots/reports.png)

Vehicle history shows only jobs within the current account's access, including technician assignment restrictions.

![Vehicle history](docs/screenshots/vehicle-history.png)

The existing English and narrow-screen views demonstrate the same workshop interface.

![English dashboard](docs/screenshots/english.png)

![Mobile dashboard](docs/screenshots/mobile.png)

## Architecture, directories and requirements

```text
Browser → Nginx /api proxy → Spring Boot → MySQL 8.4
                              JPA validation + Flyway migrations
```

| Layer | Runtime and dependencies |
| --- | --- |
| Backend | Java 21, Maven 3.9, Spring Boot 4.0.7, Security, JPA, Flyway, MariaDB Connector/J 3.5.10 |
| Frontend | Node.js 24.19.0+, Vue 3.5.40, Vite 8.1.5, ESLint, Prettier and Node tests |
| Deployment | Docker Engine, BuildKit, Compose v2, MySQL 8.4 and Nginx 1.29 |
| Verification | JUnit/MockMvc with H2 MySQL mode; separate real-MySQL workflow and restore validation; Python 3 |

Exact dependencies are in the Maven descriptor and frontend lock file. Application containers run as non-root; the backend runtime reuses the official Maven/Temurin Java 21 image and includes build tools, so it is larger than a dedicated JRE image.

```text
backend/    Authentication, administration, intake, estimates, stock and payments
  src/main/resources/db/migration/    Versioned SQL
  src/test/                          Unit and HTTP/transaction tests
frontend/   Chinese/English operator and administration UI, brand assets, Nginx
compose.yaml    Health dependencies, configurable ports and persistent MySQL volume
.env.example    Configuration names with blank credentials
docs/           Operations, architecture/API, deployment, validation and screenshots
scripts/        Real-MySQL workflow and release checks
LICENSE         Own-source non-commercial license
```

The backend reloads account state and roles from the database. Sessions use HttpOnly, SameSite Strict cookies and a 30-minute idle timeout; writes require CSRF. Passwords use BCrypt with 12 rounds, and login failures are rate-limited. Request identifiers and payload checks protect inventory/payment retries; failed transactions roll back together.

## Installation and database initialization

From the repository root:

```sh
cp .env.example .env
# Set three independent strong passwords before starting:
# MYSQL_ROOT_PASSWORD, DATABASE_PASSWORD and ADMIN_PASSWORD.
docker compose config --quiet
docker compose up -d --build --wait
```

Open [http://127.0.0.1:8097/](http://127.0.0.1:8097/) and select English. Health: [http://127.0.0.1:8097/actuator/health](http://127.0.0.1:8097/actuator/health). Default account: `admin`; password: your `ADMIN_PASSWORD`. There is no shared default password. The password must be 12–72 characters, contain uppercase, lowercase and digits, and fit within 72 UTF-8 bytes.

The backend and database do not publish host ports. Binding defaults to localhost; change `WEB_PORT` for a conflict without stopping unrelated applications.

The `zhuatech_autocare` database contains 19 business/administration tables plus Flyway history. `V1__workshop_schema.sql` defines tables, indexes, foreign keys and quantity constraints. JPA validates the schema rather than creating it. On an empty database, initialization creates administration directories, roles, dictionaries, settings and the administrator; restarts preserve existing data/passwords. Changing initialization environment variables does not reset accounts.

`SEED_DEMO=true` adds fictional customers, vehicles, labor and parts only on an empty database. It does not create stock, repair jobs, payments or staff passwords. Create staff accounts and record actual stock receipts before the workflow.

## Configuration and local development

| Name | Purpose |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` / `DATABASE_PASSWORD` | Required database root and application-account passwords |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Initial empty-database administrator; default username `admin` |
| `SEED_DEMO` | Empty-database fictional master data, default `false` |
| `WEB_PORT` / `BIND_ADDRESS` | Default `8097` / `127.0.0.1` |
| `COOKIE_SECURE` | `false` for local HTTP, `true` behind trusted HTTPS |
| `DATABASE_URL` / `DATABASE_USER` | Database connection overrides for directly running the backend |

Use Java 21, Maven 3.9 and a separate MySQL database for development. Supply database and administrator variables, then in one terminal from the repository root:

```sh
cd backend
mvn spring-boot:run
```

In another terminal from the repository root:

```sh
cd frontend
npm ci
npm run dev
```

Vite binds localhost port 5173 and proxies the backend at 8080. The default Compose database has no host port; direct backend development needs a separately reachable test database. Production uses Nginx service-name routing with SPA fallback, without a hardcoded localhost API. Docker Maven builds execute tests.

## Tests and verification

From the repository root, with required local configuration set:

```sh
cd backend
mvn spotless:check test package
cd ../frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
python3 scripts/release-check.py
git diff --check
# Only against a dedicated disposable learning/test instance:
# Set BASE_URL and ADMIN_PASSWORD to that instance first.
python3 scripts/smoke.py
```

Backend tests cover full repair, separate additional authorization, original-issue return cost, payment reversal, unpaid handover rejection, atomic imports, concurrent over-issue prevention, CSRF, roles and department scopes. H2 integration results do not replace real MySQL testing. The smoke script creates fictional masters/accounts and checks consent, stock, rework, payment correction, handover, history and technician access. Never run it against customer data.

[Validation record](docs/VALIDATION.md) documents prior checks and remaining limits. Report only checks actually executed for a release; browser, persistence and independent restoration require separate validation.

## Deployment, upgrades and recovery

Use a trusted HTTPS reverse proxy, `COOKIE_SECURE=true`, restricted staff accounts and protected backups. Do not expose the business database or share administrators. A public demonstration needs its own data isolation, abuse controls and cleanup; this repository has no demo-tenant implementation.

Compose uses MariaDB Connector/J against MySQL with `sslMode=trust` within its isolated network. External MySQL must use TLS, a trusted CA and `sslMode=verify-full`. Keep credentials, customer data, backups and unredacted logs out of Git.

Before upgrading, back up and verify independent restoration. Add new Flyway versions; never edit applied migrations, delete migration history or bypass validation with automatic schema creation. Do not use `down -v` on business volumes.

A protected backup on your authorized server:

```sh
umask 077
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers zhuatech_autocare' > autocare-backup.sql
```

Restore only into a separate, confirmed recovery project with the matching database/application version. Start its empty MySQL and wait for health, import the backup, then start backend/frontend. Verify original logins, table count, jobs, estimates, stock, payments and Flyway. Restored account hashes retain the original password; recovery `ADMIN_PASSWORD` does not overwrite them. Clean up only the disposable recovery resources after verification. See [deployment and recovery](docs/DEPLOYMENT.md).

For deliberate restarts, restart MySQL and wait for health, then backend and wait for health, then frontend so its upstream resolves the current backend. Health failures require inspecting this project's redacted logs. A 401 means sign in again; a 403 requires checking role, department, assignment and CSRF; a 409 requires checking state, balance, stock, original issue, count snapshot and references. After a timeout inspect the original entry. Retry the same request identifier/content only while that action is still permitted; never duplicate actual money records.

## Documentation, feedback and licensing

[Operations](docs/OPERATIONS.md) covers intake through handover and corrections. [Architecture/API](docs/ARCHITECTURE.md) describes tables, scopes, prices, states and inputs. These detailed manuals are in Chinese. [Third-party notices](docs/THIRD_PARTY.md) preserve dependency copyrights and licenses.

Use repository Issues/PRs for reproducible, anonymized reports and contributions with validation/licensing information. Send security reports privately through the company contact before sharing a minimal reproduction; do not publish credentials or customer logs. The software is provided as-is; commercial suitability, implementation and support require a written agreement. The root [LICENSE](LICENSE) governs the project's own source; no free-commercial-use MIT/Apache license is offered.

## Contact ZhiHua Technology

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** — [https://www.zhuatech.cn/](https://www.zhuatech.cn/).

For commercial licensing, customization, private deployment and system integration:

- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)

Contact information does not change the source license or third-party terms.
