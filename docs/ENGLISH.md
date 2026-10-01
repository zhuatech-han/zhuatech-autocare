# ZhuaTech AutoCare — workshop management

Shanghai Rujing Zhihua Information Technology Co., Ltd. (上海如静知华信息科技有限公司) · https://www.zhuatech.cn/ · WeChat zhuatech / zhuatech2.

Non-commercial source edition. Written authorization is required for commercial use, paid deployment or commercial derivative work. This is not an OSI open-source license. See LICENSE and third-party notices.

## Install

Java 21 / Spring Boot 4.0.7, Vue 3.5.40 / Vite 8.1.5, Node 24.19+, MySQL 8.4, Flyway, Docker Compose v2, Nginx 1.29.

Copy `.env.example` to `.env`; set separate MYSQL_ROOT_PASSWORD, DATABASE_PASSWORD and ADMIN_PASSWORD. The initial admin password must contain uppercase, lowercase and digits, 12–72 characters. There is no shared default password.

```sh
docker compose up -d --build --wait
```

Open http://127.0.0.1:8097/ and choose English. Health: `/actuator/health`. Override WEB_PORT if needed. The database and backend have no host ports. SEED_DEMO=true creates fictional masters only on an empty database, with no stock, payments or staff passwords. Changing environment variables never resets existing accounts.

## Actual workshop flow

Create customers, vehicles, labor services and parts. Create separate service-advisor, technician, parts-operator, quality-reviewer and cashier accounts. Department roles see their own workshop; the built-in technician role sees only assigned jobs.

Receive actual stock with quantity, unit cost and evidence reference. Intake a vehicle with mileage, complaint and condition. Mileage cannot go backwards and one vehicle cannot have two unfinished jobs. Present an estimate; record customer consent only after obtaining genuine external evidence. This is a manual record, not a customer portal or electronic signature.

Assign a technician and start work. Only accepted estimate lines can issue parts. Additional work needs a separate accepted estimate. Return unused parts against the original issue before completing the line; original inventory cost is restored. Labor charges use agreed quantities; parts charges use net issued quantities after completion. Items freeze when completed.

All accepted lines must be completed and pending estimates resolved before quality review. Perform physical checks outside the software and record pass/rework. A passed job can receive partial manual payments, payment-entry reversals and a settlement discount. A reversal does not transfer bank funds. Outstanding balance must be zero before handover. Delivered jobs freeze; post-delivery refunds and linked rework are not implemented.

Vehicle history, reports filtered by intake date, CSV and printing are available. Reports include quality-passed jobs; billed less parts cost excludes labor wages, taxes and overhead. One company, one two-decimal currency, one parts inventory per department. Currency locks after the first job or stock entry.

## Boundaries and operation

No online payment, electronic signature, OCR, VIN decoding, insurance, tax invoice, automatic diagnosis, appointment calendar, SaaS tenant isolation, purchase payables, lot/serial stock or general ledger. VIN is optional text. All stock and business writes serialize through a database lock; each list/reference class has a 10,000-row hard limit. Large-scale load is unverified.

For public deployment use a trusted HTTPS reverse proxy and COOKIE_SECURE=true. Do not expose the database, share admin accounts or host public demos on real customer data. Compose uses isolated internal TLS trust settings; external MySQL requires sslMode=verify-full and a trusted CA. Keep secrets and backups outside Git. Back up and test restoration before upgrading, never edit an applied Flyway migration. Production volumes must not be removed by `down -v`.

Tests: `mvn -f backend/pom.xml spotless:check test package`; frontend `npm ci`, `npm run format:check`, `npm run lint`, `npm test`, `npm run build`; `docker compose config --quiet`; `python3 scripts/release-check.py`. MySQL/browser/restore evidence is documented in VALIDATION.md.

Use Issues/PR for reproducible, anonymized reports. Commercial licensing, customization, deployment and integration: https://www.zhuatech.cn/ or WeChat zhuatech / zhuatech2. Never upload customer data or credentials.

## Actual operator screens

![English workshop overview](screenshots/english.png)

![Mobile workshop overview](screenshots/mobile.png)
