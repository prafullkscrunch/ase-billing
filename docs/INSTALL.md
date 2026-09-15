# Install & IntelliJ setup

Written for a Windows machine that already has Java 24 and MySQL Workbench.

---

## 1. What you need to install

| Software | Version | Do you have it? |
|---|---|---|
| **JDK 21 (Temurin)** | 21 LTS | You have 24 — install 21 as well, see below |
| **MySQL Server** | 8.0 or 8.4 | **Probably not.** Workbench is only the GUI client |
| **Node.js** | 20 LTS or 22 | Needed for the frontend |
| IntelliJ IDEA | Community is fine | — |
| Maven | — | **Don't install it.** The project ships a wrapper |

### Why JDK 21 and not your 24

I built and tested the project on both. It passes on 24. But:

- Spring Boot 3.3 is certified against Java 17 to 22. Running on 24 works today and is not covered if something breaks later.
- Java 21 is LTS with support into the 2030s. Java 24 stopped receiving updates after six months.
- This is a billing system that produces GST records. Boring and supported beats current.

Both can sit on the machine at once — Java is not exclusive. IntelliJ picks the JDK per project, and nothing else you run is affected.

Get Temurin 21 from `adoptium.net/temurin/releases/?version=21`, pick **Windows x64 JDK .msi**. During install, turn on "Set JAVA_HOME variable". Then in a new terminal:

```
java -version
```

If it still prints 24, that's fine — IntelliJ will use 21 regardless of what the system default is.

### MySQL Server — the thing you're most likely missing

MySQL Workbench is a client. It draws diagrams and runs queries against a server; it is not a server. If you have never started a MySQL service on this machine, you don't have one.

Check first. Press `Win+R`, type `services.msc`, look for a service named `MySQL80` or similar. If it's there and running, skip ahead to section 3.

If it isn't, get the **MySQL Installer for Windows** from `dev.mysql.com/downloads/installer/` and choose the *Server only* setup type (you already have Workbench). During setup:

- Config type: **Development Computer**
- Port: **3306**
- Authentication: **Use Strong Password Encryption**
- Set a root password and write it down
- **Configure MySQL Server as a Windows Service** — leave ticked, and leave "Start at System Startup" ticked

### Node.js

`nodejs.org`, take the **LTS** installer. Only needed when you start on the frontend, so you can defer it.

---

## 2. Opening the project in IntelliJ

The repository has a backend and a frontend side by side. IntelliJ handles Java projects best when the Maven project is the root it opens.

**Open the backend:**

`File > Open` and select the **`ase-billing/backend/pom.xml`** file — not the folder. IntelliJ asks "Open as Project"; say yes. It will download dependencies on first open, which takes a few minutes.

**The frontend** is a separate Node project. Either open `ase-billing/frontend` in a second IntelliJ window, or just use VS Code for it. Don't try to force both into one IntelliJ module.

### Three settings that matter

**a. Project SDK → 21**

`File > Project Structure > Project`. Set **SDK** to your Temurin 21. Set **Language level** to 21. If 21 isn't in the dropdown, choose `Add SDK > JDK` and point it at `C:\Program Files\Eclipse Adoptium\jdk-21...`.

**b. Enable annotation processing**

`File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors` → tick **Enable annotation processing**.

This one bites people. The project uses Lombok to generate getters and setters. Maven is already configured correctly (see below), but IntelliJ's own compiler has a separate switch. Without it, the editor shows hundreds of red "cannot resolve method getInvoiceNumber" errors even though `.\mvnw.cmd test` passes.

**c. Lombok plugin**

`File > Settings > Plugins`, search Lombok, install if it isn't already bundled. Recent IntelliJ versions include it.

### A note on what I had to fix for newer JDKs

JDK 23 changed a javac default: annotation processors found on the classpath are no longer run unless you declare them explicitly. Lombok is exactly that kind of processor, so on JDK 23+ it silently does nothing and every generated method reports "cannot find symbol".

`pom.xml` now declares the processor path explicitly:

```xml
<annotationProcessorPaths>
  <path>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>${lombok.version}</version>
  </path>
</annotationProcessorPaths>
```

This is the correct configuration on every JDK from 17 up, so it costs nothing and means the project builds whether you use 21 or 24. Lombok is also pinned to 1.18.38, above the 1.18.34 that Spring Boot 3.3.4 would otherwise pull in.

---

## 3. Create the database

Open MySQL Workbench, connect to your local server, and run:

```sql
CREATE DATABASE ase_billing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'ase'@'localhost' IDENTIFIED BY 'pick-a-password';
GRANT ALL PRIVILEGES ON ase_billing.* TO 'ase'@'localhost';
FLUSH PRIVILEGES;
```

Leave the database empty. Flyway creates every table on first run from `V1__schema.sql` and loads the master data from `V2__seed_masters.sql`.

---

## 4. Run configuration

The project ships with one at `.run/ASE Backend.run.xml`, which IntelliJ picks up automatically — it appears in the run dropdown as **ASE Backend**.

Before the first run, put your database password in it: `Run > Edit Configurations > ASE Backend > Environment variables`, change `DB_PASSWORD` to what you set above.

Then hit run. You should see Flyway apply two migrations and Tomcat start on port 8080.

To set it up by hand instead: `Run > Edit Configurations > + > Spring Boot`, main class `com.ase.billing.AseBillingApplication`, and these environment variables:

```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ase_billing
DB_USER=ase
DB_PASSWORD=pick-a-password
```

---

## 5. Verify

Run the tests first — they need no database. Use the **wrapper**, not `mvn`:

```powershell
cd backend
.\mvnw.cmd test
```

`mvnw.cmd` is a Maven wrapper committed into the project. On first run it downloads
Maven 3.9.9 into your user profile and uses that; you never install Maven yourself,
and everyone building this project uses the identical version. It is a PowerShell
script, so a path with spaces and parentheses is fine.

Expect **12 tests, 0 failures**. Eleven check the calculation engine against figures from the real bills; one boots the whole Spring context against an in-memory database.

If the 11 calculation tests pass but the context test fails, the problem is configuration, not your code. If everything fails with "cannot find symbol", annotation processing is off — go back to section 2b.

Then start the app and confirm the seed data landed:

```sql
USE ase_billing;
SELECT code, name FROM services LIMIT 5;
SELECT name, rate_per_container FROM transport_routes;
```

You should see the four routes at 27,000 / 29,750 / 12,000 / 12,000.

---

## 6. Docker alternative

If installing MySQL Server on Windows turns into a fight, run it in Docker instead. `docker-compose.yml` is in the project root:

```
docker compose up -d
```

That gives you MySQL 8.4 on port 3306 with the database and the `ase` user already created, and you can still point Workbench at it. Data persists in a named volume, so stopping the container doesn't lose anything.

---

## 7. Troubleshooting

**`mvn : The term 'mvn' is not recognized...`**

You don't have standalone Maven, and you don't need it. Use the wrapper:

```powershell
cd backend
.\mvnw.cmd test
```

Note the leading `.\` — PowerShell will not run a script in the current directory
without it. From `cmd.exe`, plain `mvnw.cmd test` works.

IntelliJ bundles its own Maven, but only for use *inside* the IDE — it is not added
to your system PATH, which is why `mvn` fails in a terminal. Inside IntelliJ you can
skip the terminal entirely: open the **Maven** tool window on the right, expand
`ase-billing > Lifecycle`, and double-click **test**.

If you later want a global `mvn` command anyway: `winget install Apache.Maven`, then
reopen the terminal. It is genuinely optional.

**"cannot find symbol: method getX()" in the editor but `.\mvnw.cmd test` passes**
IntelliJ annotation processing is off. Section 2b.

**"Communications link failure" on startup**
MySQL isn't running. Check `services.msc` for MySQL80, or `docker compose ps`.

**"Access denied for user 'ase'@'localhost'"**
The `DB_PASSWORD` in the run configuration doesn't match what you set in section 3.

**"Unsupported class file major version"**
A JDK mismatch between IntelliJ's project SDK and what Maven is using. Set both to 21.

**Flyway: "Validate failed: migration checksum mismatch"**
You edited a migration file after it had already run. During development, drop the database and recreate it. Once real bills exist, never edit an applied migration — add `V3__...` instead.

**Port 8080 already in use**
Another app has it. Add `SERVER_PORT=8081` to the run configuration's environment variables.

**A note on your project path**

`D:\New folder (2)\...` will work, but move the project somewhere like
`D:\projects\ase-billing` when you get a chance. Spaces and parentheses in paths
still trip up occasional Java and Node tooling, and Windows' 260-character path limit
gets close once `node_modules` is in play.
