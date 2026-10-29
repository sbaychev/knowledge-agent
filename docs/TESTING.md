# Testing and verification guide

This document separates deterministic tests, database integration tests, protocol integration tests, and live model evaluation. They answer different questions and must not be substituted for one another.

## 1. Local prerequisites

- JDK 25 (the CI workflow currently uses Temurin JDK 25).
- Maven.
- Docker Engine/Desktop with Compose v2 for PostgreSQL and Testcontainers tests.
- curl and jq for live smoke scripts.
- glow is recommended for readable Markdown/JSON terminal output. The scripts fall back to plain output if glow is absent.

Run toolchain preflight from the repository root:

~~~bash
bash scripts/check-local.sh
~~~

Check the database, application or MCP endpoint as needed:

~~~bash
bash scripts/check-local.sh --database
bash scripts/check-local.sh --running
bash scripts/check-local.sh --running --mcp
~~~

The MCP endpoint probe in check-local.sh checks reachability only. The full protocol smoke test is scripts/smoke-test-mcp.sh.

## 2. CI-equivalent test command

Run the same Maven goal as GitHub Actions:

~~~bash
bash scripts/verify.sh
~~~

Or run it explicitly:

~~~bash
mvn --batch-mode --no-transfer-progress clean verify
~~~

clean verify compiles production and test sources, runs JUnit and executes the configured Maven verification lifecycle. Do not replace this with a compilation-only command when verifying agent-runtime or integration-wiring changes.

For readable output when running Maven directly:

~~~bash
set -o pipefail
mvn --batch-mode --no-transfer-progress clean verify 2>&1 | glow -
~~~

pipefail is important: without it, a failing Maven process can be hidden by a successful Markdown renderer.

## 3. Test layers

### Unit tests

Use JUnit 5, AssertJ and Mockito to validate deterministic logic without calling an LLM provider. Examples include:

- domain state transitions and invalid transitions;
- money/refund amount checks and idempotency conflicts;
- tool validation and missing-record behavior;
- observability outcome/timer/counter tags;
- agent deadline, queue rejection and limit configuration;
- MCP policy-resource allow-listing and prompt argument validation.

A unit test should verify a rule at the narrowest useful boundary. It should not boot a complete Spring application if the behavior is plain Java.

### Spring/context and API integration tests

Spring Boot integration tests validate dependency wiring, HTTP routing, request/response contracts, error mapping, persistence and the conversation lifecycle. Tests using Testcontainers require a functioning Docker daemon; do not skip them because a local container cannot start.

Database integration coverage should use real PostgreSQL when the test depends on PostgreSQL-specific behavior, migrations, JSON/vector columns, indexes or JDBC repository semantics. An in-memory substitute does not prove that Flyway and PostgreSQL agree.

### Scripted model / tool-calling tests

Use a scripted/fake ChatModel for agent tests that need to control returned tool calls and final answers. These tests should be deterministic, offline, and free of paid API usage. Cover multi-tool questions, tool result hand-off, unknown records, model failure, limit enforcement and final-answer handling.

They test the Spring AI/tool orchestration contract. They do not prove that a hosted model will always choose the best tool for arbitrary natural language; that is why a separate live evaluation exists.

### MCP HTTP integration test

McpServerHttpIntegrationTest starts an actual random-port HTTP server and sends JSON-RPC messages through MCP Streamable HTTP. Tool implementation dependencies are mocked at this boundary, keeping the test deterministic while still exercising transport wiring and annotation discovery.

The flow is:

1. initialize with an offered protocol version;
2. notifications/initialized;
3. tools/list and tools/call;
4. resources/list;
5. resources/templates/list and resources/read;
6. prompts/list and prompts/get.

This catches Spring context failures and contract mismatches between annotated beans and MCP protocol messages. It intentionally does not prove the live database or model provider is available. The shell smoke script covers that additional boundary.

### Live end-to-end MCP smoke test

Start PostgreSQL, migrate the database and run the service with the mcp-server profile enabled (plus a valid local chat-provider profile/key required by Spring AI auto-configuration). Then run:

~~~bash
MCP_BASE_URL=http://127.0.0.1:8080/mcp bash scripts/smoke-test-mcp.sh
~~~

The script completes the protocol handshake, discovers the four read-only transactional tools, calls lookup_order against seeded order 1, reads the Refund Policy resource, and renders the investigate-order prompt. It requires a running service and seeded database. Unlike the JUnit integration test, it uses the actual database-backed tool beans.

MCP server profile example using the repository's local Anthropic-compatible proxy:

~~~bash
SPRING_PROFILES_ACTIVE=local,anthropic,mcp-server mvn spring-boot:run
~~~

The server binds to loopback by default. MCP_SERVER_BIND_ADDRESS can override the bind address intentionally. Do not expose the endpoint outside a trusted local environment without appropriate network restrictions and authentication.

### Live LLM evaluation

scripts/evaluate-agent.sh sends real HTTP requests to a running agent and validates required facts from seeded data. It checks a combined order summary, follow-up reference resolution, payment information, empty refund history, unknown order behavior and optional RAG grounding scenarios.

~~~bash
BASE_URL=http://127.0.0.1:8080 bash scripts/evaluate-agent.sh
~~~

This script may consume provider capacity and is sensitive to provider latency, model changes and natural-language variation. It checks facts, not exact full response strings. A failure can indicate an application regression, incorrect provider/model configuration, transient provider issue or changed model behavior; inspect the failed case before changing the application based on one run.

Enable RAG cases only when the app runs with rag and its embedding endpoint is configured:

~~~bash
EVALUATE_RAG=true BASE_URL=http://127.0.0.1:8080 bash scripts/evaluate-agent.sh
~~~

## 4. Focused suites

To test bounded execution and observability:

~~~bash
bash scripts/test-agent-limits.sh
~~~

To run one class with Maven:

~~~bash
mvn --batch-mode --no-transfer-progress -Dtest=BoundedToolCallingAdvisorTest test
mvn --batch-mode --no-transfer-progress -Dtest=McpServerHttpIntegrationTest test
mvn --batch-mode --no-transfer-progress -Dtest=SupportPolicyMcpResourcesTest,OrderSupportMcpPromptsTest test
~~~

To see full stack traces:

~~~bash
mvn --batch-mode --no-transfer-progress -e -Dtest=McpServerHttpIntegrationTest test
~~~

Inspect reports under target/surefire-reports/. For Spring context failure, read the first meaningful Caused by section rather than stopping at Failed to load ApplicationContext; the top-level exception is often a wrapper around a missing bean or invalid auto-configuration.

## 5. Common failure modes

- **Missing JdbcTemplate in an isolated MCP context:** the MCP transport test intentionally disables database auto-configuration. Disable JDBC chat-memory repository auto-configuration in that test context too; otherwise its starter may create a repository bean requiring a JdbcTemplate that the test explicitly does not provide.
- **Docker/Testcontainers startup failure:** confirm docker info works and that the Docker context points to a running daemon. A green compile is not a substitute for the database integration suite.
- **Agent live evaluation times out:** distinguish provider/network timeout from application deadline or queue rejection. Correlation IDs and structured error codes should be used to trace the request.
- **Order assertions fail after local testing:** local API calls can mutate the database. The live suite expects seeded order 1 facts. Recreate the local database only when losing local data is acceptable.
- **RAG answer is empty or ungrounded:** confirm rag is enabled, Flyway applied the pgvector migration, the embedding endpoint is available and returns vectors of configured dimensions, and the source documents were ingested.
- **MCP tools/resources/prompts do not appear:** check active profiles, annotation scanning, protocol selection, startup logs and MCP_BASE_URL. Streamable HTTP is configured explicitly; adding an MCP starter does not prove the correct transport is active.
- **Output looks hard to read:** use jq for JSON and glow for Markdown. Keep raw HTTP status codes separate from response bodies so an error page does not break JSON parsing.

## 6. CI discipline

Before opening or updating a pull request:

1. Run bash scripts/verify.sh.
2. Run the relevant focused suite.
3. For MCP bean changes, make sure the JUnit transport test passes; for runtime changes, run a live smoke test where practical.
4. Do not weaken a failing test by removing assertions or converting a protocol test into a mocked method test.
5. Record which verification commands were run. Do not claim a test passes until it has actually completed.

The GitHub Actions workflow currently runs Java 25 and mvn --batch-mode --no-transfer-progress clean verify. Keep local and CI commands aligned.
