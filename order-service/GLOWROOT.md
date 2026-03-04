# Glowroot APM – Order Service

[Glowroot](https://glowroot.org/) is a Java APM agent used to track **slow transactions**, **traces**, and **JVM metrics** for the order-service.

The agent is configured to use the **local path** `order-service/glowroot/glowroot.jar`. Place the Glowroot agent JAR there (e.g. extract `glowroot.jar` from [glowroot-0.14.4-dist.zip](https://github.com/glowroot/glowroot/releases/download/v0.14.4/glowroot-0.14.4-dist.zip) into `order-service/glowroot/`).

---

## 1. Local run (Maven)

Use the Maven profile **`glowroot`** so the JVM starts with `-javaagent` pointing at `order-service/glowroot/glowroot.jar`:

```bash
cd order-service
mvn spring-boot:run -Pglowroot
```

Without the profile, no agent is loaded:

```bash
mvn spring-boot:run
```

**Glowroot UI:** http://localhost:4000 (embedded collector)

---

## 2. Docker Compose

**docker-compose.yml** runs order-service **with** the Glowroot agent when `glowroot.jar` is present.

1. Place `glowroot.jar` in `order-service/glowroot/` (e.g. extract from [glowroot-0.14.4-dist.zip](https://github.com/glowroot/glowroot/releases/download/v0.14.4/glowroot-0.14.4-dist.zip)).
2. From the repo root run:

```bash
docker compose up --build
```

**Glowroot UI:** http://localhost:4000 — open this URL to view APM traces, JVM metrics, and slow transactions for the order-service. The embedded collector binds to `0.0.0.0` (see `admin.json`) so the UI is reachable from the host.

If you need to **disable** Glowroot in Docker (e.g. to avoid startup issues when the JAR is missing), comment out `JAVA_TOOL_OPTIONS` and the `volumes` entry for `./order-service/glowroot:/opt/glowroot` in the `order-service` section of docker-compose.yml.

---

## 3. Configuration summary

| Where        | What |
|-------------|------|
| **pom.xml** | `glowroot.agent.path` = `${project.basedir}/glowroot/glowroot.jar`. Profile **glowroot** sets `glowroot.jvmArgs` to `-javaagent:${glowroot.agent.path}`; `spring-boot-maven-plugin` uses it as `jvmArguments`. |
| **Docker**  | Agent enabled via `JAVA_TOOL_OPTIONS` and `volumes` in **docker-compose.yml**; ensure `glowroot.jar` is in `order-service/glowroot/`. |

Ensure `order-service/glowroot/glowroot.jar` exists when using the `-Pglowroot` Maven profile or when Glowroot is enabled in docker-compose.

---

## 4. Troubleshooting

- **"An error occurred" / `UnsupportedOperationException` in Glowroot UI (e.g. on percentiles):** Known issue in the embedded collector UI (e.g. `ImmutableCollection.addAll` in `TransactionCommonService.getPercentileAggregates`). It does not affect agent data collection. Use other UI sections (traces, JVM, errors) or try a different Glowroot version; the application and Create Order flow are unaffected.
