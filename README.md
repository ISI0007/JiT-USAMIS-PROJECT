# USAMIS — University Student Academic Management Information System
**Jinling Institute of Technology (金陵科技学院)**

A full-stack Role-Based Academic MIS built with:
- **Backend:** Java 17 + Jakarta Servlets + JDBC + PostgreSQL
- **Frontend:** Pure HTML5 / CSS3 / Vanilla JS (no frameworks)
- **Security:** BCrypt, RBAC, SQL injection prevention, Audit Log
- **Build:** Maven + Apache Tomcat 10

---

## Project Structure

```
usamis/
├── java/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/usamis/
│       │   ├── dao/
│       │   │   ├── UserDAO.java          ← Auth + user CRUD
│       │   │   ├── StudentDAO.java       ← Student CRUD + search
│       │   │   └── AcademicDAO.java      ← Course, Enrollment, Grade, Fee, Audit
│       │   ├── filter/
│       │   │   └── AuthFilter.java       ← Session auth on /api/*
│       │   ├── model/
│       │   │   └── Models.java           ← All domain models + DTOs
│       │   ├── servlet/
│       │   │   ├── LoginServlet.java     ← POST /api/auth/login|logout
│       │   │   ├── StudentServlet.java   ← REST /api/students
│       │   │   ├── UserServlet.java      ← REST /api/users
│       │   │   └── AcademicServlets.java ← Course, Enrollment, Grade, Fee, Audit, Dashboard
│       │   └── util/
│       │       ├── AppContextListener.java ← DB pool init on startup
│       │       ├── DatabaseConnection.java ← HikariCP pool singleton
│       │       ├── JsonUtil.java           ← Gson + response helpers
│       │       ├── PasswordUtil.java       ← BCrypt hash/verify
│       │       └── ValidationUtil.java     ← Input sanitization
│       ├── resources/
│       │   ├── schema.sql    ← Full PostgreSQL schema
│       │   ├── seed.sql      ← Sample data
│       │   ├── db.properties ← DB credentials (gitignore this!)
│       │   └── logback.xml   ← Logging config
│       └── webapp/
│           ├── WEB-INF/web.xml
│           └── index.html    ← Frontend SPA
└── .gitignore
```

---

## REST API Reference

| Method | Endpoint                   | Permission        | Description                     |
|--------|----------------------------|-------------------|---------------------------------|
| POST   | /api/auth/login            | Public            | Login — returns session cookie  |
| POST   | /api/auth/logout           | Any               | Invalidate session              |
| GET    | /api/auth/me               | Any               | Current logged-in user          |
| GET    | /api/dashboard             | Any               | Dashboard statistics            |
| GET    | /api/students              | All (own for stu) | List students                   |
| GET    | /api/students/{id}         | All               | Get single student              |
| POST   | /api/students              | Admin, Registrar  | Create student                  |
| PUT    | /api/students/{id}         | Admin, Registrar  | Update student                  |
| DELETE | /api/students/{id}         | Admin, Registrar  | Deactivate student              |
| GET    | /api/students/at-risk      | Admin, Registrar  | GPA < 2.5                       |
| GET    | /api/courses               | All               | List courses                    |
| POST   | /api/courses               | Admin, Registrar  | Create course                   |
| PUT    | /api/courses/{id}          | Admin, Registrar  | Update course                   |
| DELETE | /api/courses/{id}          | Admin             | Cancel course                   |
| GET    | /api/enrollments           | All               | List enrollments                |
| POST   | /api/enrollments           | Admin, Registrar  | Enroll student in course        |
| DELETE | /api/enrollments/{id}      | Admin, Registrar  | Drop enrollment                 |
| GET    | /api/grades                | All               | List grades                     |
| POST   | /api/grades                | Admin, Lecturer   | Enter grade                     |
| PUT    | /api/grades/{id}           | Admin, Lecturer   | Update grade                    |
| GET    | /api/fees                  | Admin, Finance    | List fee records                |
| GET    | /api/fees/defaulters       | Admin, Finance    | Fee defaulters                  |
| POST   | /api/fees                  | Admin, Finance    | Create fee record               |
| POST   | /api/fees/{id}/pay         | Admin, Finance    | Record payment                  |
| GET    | /api/users                 | Admin             | List system users               |
| POST   | /api/users                 | Admin             | Create user                     |
| PUT    | /api/users/{id}            | Admin             | Update user status              |
| DELETE | /api/users/{id}            | Admin             | Deactivate user                 |
| GET    | /api/audit                 | Admin             | Paginated audit log             |
| GET    | /api/health                | Public            | Health check                    |

---

## Setup & Run

### 1. Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL 15+
- Apache Tomcat 10.x

The Maven build was authored for Java 17. Use a Java 17 or newer JDK and verify
the active tools with `java -version` and `mvn -version`.

### 2. Database Setup
```bash
# Create database and user
psql -U postgres
CREATE DATABASE usamis;
CREATE USER usamis_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE usamis TO usamis_user;
\q

# Apply schema and seed data
psql -U usamis_user -d usamis -f src/main/resources/schema.sql
psql -U usamis_user -d usamis -f src/main/resources/seed.sql
```

### 3. Configure local DB credentials
Create the ignored local configuration from the committed example:
```bash
cp src/main/resources/db.properties.example src/main/resources/db.properties
```

Then edit `src/main/resources/db.properties` with your local values:
```properties
db.url=jdbc:postgresql://localhost:5432/usamis
db.user=usamis_user
db.password=your_secure_password
```

Never commit `db.properties`, passwords, or connection strings. The repository
only contains `db.properties.example` with placeholder values.

### 4. Build
```bash
cd java/
mvn clean package -DskipTests
```

### 5. Run locally
```bash
# Option A: run with the Maven Tomcat plugin
mvn tomcat10:run
```

For a separately installed Tomcat 10 server:
```bash
# Copy the WAR to Tomcat's webapps directory
cp target/usamis.war $TOMCAT_HOME/webapps/

# Start Tomcat using its normal startup script.
```

### 6. Access
- Frontend: `http://localhost:8080/usamis/`
- API:      `http://localhost:8080/usamis/api/health`

---

## Demo Accounts (seed data)

| Role           | Username   | Password  |
|----------------|------------|-----------|
| Administrator  | admin001   | admin123  |
| Registrar      | reg001     | reg123    |
| Lecturer       | lec001     | lec123    |
| Finance Officer| fin001     | fin123    |
| Student        | stu001     | stu123    |

> **Note:** The seed.sql uses pre-computed BCrypt hashes. For the demo frontend,
> authentication is simulated client-side. In production, all auth goes through
> `POST /api/auth/login`.

These accounts are intentionally disposable demo data. Change or remove them
before any shared or public deployment.

## GitHub and hosting

GitHub can host this source code, but GitHub Pages cannot run the Java servlet
backend or PostgreSQL database. A live deployment needs a Tomcat-compatible
host plus PostgreSQL (or a container platform that supports both). Until such
a deployment is configured and its health endpoint is checked, the only
verified run path is local: `http://localhost:8080/usamis/` and
`http://localhost:8080/usamis/api/health`.

---

## Security Features

| Feature              | Implementation                                   |
|----------------------|--------------------------------------------------|
| Password hashing     | BCrypt cost=12 (`jbcrypt`)                       |
| SQL injection        | All queries use `PreparedStatement`              |
| Session management   | Server-side sessions, HttpOnly cookies           |
| RBAC                 | `AuthFilter` + per-servlet permission checks     |
| Audit trail          | Every write logged to `audit_log` table          |
| Input validation     | `ValidationUtil` + DB constraints as last resort |
| Rate limiting        | In-memory counter (use Redis in production)      |
| Least privilege      | Users get minimum permissions for their role     |

---

## Architecture Decision Records

**ADR-001: Jakarta Servlets over Spring**
Chosen for simplicity and alignment with the course scope. Spring adds ~20 dependencies
and annotation magic that obscures what's happening. Servlets make the HTTP→Java
mapping explicit and teachable.

**ADR-002: HikariCP for connection pooling**
Opening a new JDBC connection per request costs ~50ms. HikariCP reduces this to <1ms
by keeping a pool of warm connections. Pool size=10 handles ~50 concurrent users.

**ADR-003: BCrypt cost=12**
Cost 12 means ~300ms per hash. Too slow for bulk operations, perfect for login.
Prevents GPU-based brute force even if the hash is leaked.

**ADR-004: Soft deletes everywhere**
No `DELETE FROM` on core data. Status flags (`Inactive`, `Dropped`, `Cancelled`)
preserve referential integrity and maintain a complete audit trail.

**ADR-005: Single-file frontend**
`index.html` is the deployed frontend entry point. Keep the static assets
versioned with the application so the WAR remains self-contained.

---

© 2024 Jinling Institute of Technology — USAMIS v1.0
Course: Management Information Systems · Database Systems
