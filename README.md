# ASE Billing, Invoicing & SOA

Bill generation, invoice PDFs and GST sales-detail (SOA) workbooks for
Aprameya Shipping Enterprises.

Java 21 / Spring Boot 3.3 / MySQL 8 / Flyway · React 18 / TypeScript / Vite

- `docs/INSTALL.md` — **start here**: JDK, MySQL, IntelliJ setup on Windows
- `docs/SETUP.md` — database, run commands, build order, what not to do
- `docs/BUSINESS_RULES.md` — every rule verified against the historical bills

## Quick start

```bash
# database
mysql -u root -p -e "CREATE DATABASE ase_billing CHARACTER SET utf8mb4;"

# backend  (Maven wrapper - no Maven install needed)
cd backend
export DB_USER=ase DB_PASSWORD='your-password'
./mvnw test && ./mvnw spring-boot:run
# Windows PowerShell:  .\mvnw.cmd test  ;  .\mvnw.cmd spring-boot:run

# frontend
cd ../frontend && npm install && npm run dev
```

Then open **http://localhost:5173** (dev) or **http://localhost:8080** (the jar
serves the built frontend). Login `admin` / `changeme` — change it before real use.

## What it does

- **Bill a shipment** — one header in, a consecutive CNF + T draft pair out
- **Invoices** — search, edit drafts, finalise, duplicate, cancel, print
- **GST sales** — preview and export the SOA workbook from finalised invoices
- **Dashboard** — this month's billing by category

Totals are always computed server-side. A draft can be changed; a finalised
invoice cannot, and only finalised invoices reach the SOA.
