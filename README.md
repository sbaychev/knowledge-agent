# Knowledge Agent

A Java 25 / Spring Boot service for order lifecycle management and AI-assisted order support. It combines a conventional REST API and PostgreSQL-backed domain model with a read-only LLM agent that retrieves transactional facts through application-owned tools.

> **Project status:** Active development. The order, shipment, payment, refund, and conversation flows use application-owned tools and PostgreSQL-backed storage. The agent also supports an optional RAG knowledge base for versioned policy and process documents. Automated testing and operational hardening are in progress; run the test suite before treating a branch as release-ready.

## Features

### Order-management API

- Create and retrieve orders and list a customer's orders.
- Transition orders into processing or cancel eligible orders.
- Dispatch shipments and mark shipments as delivered.
- Register captured payments and request partial or full refunds.
- Use an idempotency key when requesting a refund.
- Validate incoming requests and return structured API errors.

### AI order-support agent

- `POST /api/v1/agent/chat` accepts a user message and optional `conversationId`.
- Returns a JSON response containing `conversationId` and a Markdown-capable `answer`.
- Uses Spring AI `ChatClient` with provider-neutral settings (`LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`) and a provider-specific adapter profile.
- Supports follow-up questions with a bounded `MessageWindowChatMemory` backed by Spring AI `JdbcChatMemoryRepository` and PostgreSQL.
- Uses the conversation history to resolve references such as "its tracking number", but calls the relevant tool for current transactional facts.
- Persists conversation metadata and status in PostgreSQL; a conversation can be closed through a dedicated endpoint.
- Rejects attempts to continue a closed conversation with HTTP `409 Conflict`.
- Is read-only: the model cannot create, cancel, ship, or refund orders.

The four application-owned tools are:

| Tool | Information returned |
|---|---|
| `lookup_order` | Order status, customer ID, total, currency, and item details |
| `lookup_shipment_by_order` | Shipment status, carrier, tracking number, and shipping/delivery timestamps |
| `lookup_payment_by_order` | Payment status, amount, currency, refunded amount, and capture timestamp |
| `lookup_refunds_by_order` | Recent refund records and lifecycle timestamps |

The model has no database credentials and does not connect directly to PostgreSQL. Tool calls are executed by the application and return bounded results to the model.

### RAG knowledge base

An optional `rag` profile adds retrieval-augmented generation (RAG) for policy and process questions. It is separate from the transactional tools above: the knowledge base answers questions about documented rules, while order-specific shipment, payment, and refund status must still come from the live transactional tools.

- Loads versioned Markdown documents from `src/main/resources/knowledge/`; the current examples are `shipping-policy.md`, `refund-policy.md`, and `warranty-policy.md`.
- Uses an OpenAI-compatible embedding endpoint independently of the chat-model provider. The current configuration uses `text-embedding-3-small` with 1,536 dimensions.
- Stores document embeddings and source metadata in PostgreSQL with pgvector. The schema is managed by Flyway; the Spring AI vector-store schema auto-initializer is disabled.
- Exposes the read-only `search_knowledge_base` tool. It returns document excerpts with source name, source ID, version, and chunk metadata so the model can formulate an answer grounded in retrieved content.
- The current retrieval defaults are `top-k: 5` and `similarity-threshold: 0.35`; tune them using retrieval evaluation rather than changing them solely to improve one example answer.
- The policy files are demonstration content, not authoritative customer policy. Replace them with approved documents before using the agent for real customer support.

The RAG retrieval result is evidence for the answer, not a substitute for a transactional lookup. For example, a return-window question can be answered from the Refund Policy; the refund status for a particular order must be checked with the refund tools.

### MCP server: tools, resources and prompts

The optional **mcp-server** profile exposes a Streamable HTTP MCP endpoint at **/mcp**. This adapter is separate from the REST endpoints but delegates to the same application-owned read-only capabilities.

- **Tools:** lookup_order, lookup_shipment_by_order, lookup_payment_by_order and lookup_refunds_by_order.
- **Resources:** knowledge://policies lists the available demonstration policy files; knowledge://policy/{policyId} reads one allow-listed Markdown file.
- **Prompt:** investigate-order generates a reusable, parameterized read-only investigation procedure for a positive numeric order ID.
- **Transport:** the server is configured explicitly for Streamable HTTP and synchronous annotated methods.
- **Security default:** the MCP listener binds to loopback. Configure MCP_SERVER_BIND_ADDRESS deliberately if a different interface is needed; do not expose the endpoint without authentication/network controls.

A tool is a callable capability, a resource is reference data, and a prompt is reusable instruction. None of these is a policy engine. The model cannot refund or otherwise mutate an order through the current MCP tools.

The policy documents are demo content and must not be used as the source of an actual customer eligibility decision. Current order/shipment/payment/refund facts still come from the transactional lookup tools.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the request flow, context/memory boundaries and the current security gaps.

### Observability and error handling

- `X-Correlation-ID` is accepted when valid, generated when absent or invalid, and returned in the HTTP response header.
- The correlation ID is added to logging MDC and removed after the request to avoid leaking context across reused servlet threads.
- Micrometer instrumentation records agent request counts/durations and tool-call counts/durations, labelled by bounded outcome/tool values.
- AI provider failures are mapped to structured API errors rather than returning raw upstream exception messages.
- Actuator health and metrics endpoints are configured in the application. If the Prometheus registry dependency is enabled, the Prometheus scrape endpoint is available too.

**Memory persistence boundary:** bounded chat-memory messages are stored in PostgreSQL and survive application restarts. Closing a conversation clears its stored memory. Spring AI 2.0.1 `JdbcChatMemoryRepository` filters assistant messages containing tool calls and `ToolResponseMessage` records, so this is contextual memory, not a complete audit transcript of every model/tool exchange. If full tool-call replay or an immutable audit history is required, implement a separate conversation event/history store rather than relying on `ChatMemoryRepository`.

## Technology stack

- Java 25
- Spring Boot 4.1.1
- Spring AI 2.0.1
- Spring MVC and Jakarta Bean Validation
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway migrations and seed data
- Docker Compose for local PostgreSQL
- Spring Boot Actuator and Micrometer
- Spring AI JDBC chat-memory repository
- Spring AI pgvector vector store and OpenAI-compatible embeddings for RAG
- JUnit 5, Mockito, AssertJ, and Testcontainers
- An LLM endpoint compatible with the selected Spring AI provider adapter; the current local setup uses an Anthropic-compatible proxy on port `6655`

## Architecture

```mermaid
flowchart TD
    Client[HTTP client / frontend] --> API[Spring MVC REST API]

    API --> Domain[Order, shipment, payment and refund services]
    Domain --> JPA[Spring Data JPA]
    JPA --> DB[(PostgreSQL)]

    API --> ConversationAPI[Conversation lifecycle API]
    ConversationAPI --> ConversationService[ConversationService]
    ConversationService --> ConversationRepo[ConversationRepository]
    ConversationRepo --> DB

    API --> Agent[Order-support agent]
    Agent --> ChatClient[Spring AI ChatClient]
    ChatClient <--> Adapter[Provider-specific Spring AI adapter]
    Adapter <--> LLM[Configured LLM endpoint]
    ChatClient --> Memory[Bounded MessageWindowChatMemory]
    Memory --> MemoryDB[(SPRING_AI_CHAT_MEMORY in PostgreSQL)]
    ChatClient --> Tools[Read-only tools]
    ChatClient --> KnowledgeTool[search_knowledge_base]

    KnowledgeTool --> Retrieval[Knowledge retrieval]
    Retrieval --> VectorStore[pgvector vector store]
    Corpus[Versioned Markdown corpus] --> Ingestion[Knowledge ingestion]
    Ingestion --> VectorStore
    Embeddings[OpenAI-compatible embedding endpoint] <--> Ingestion
    Embeddings <--> Retrieval

    Tools --> OrderTool[lookup_order]
    Tools --> ShipmentTool[lookup_shipment_by_order]
    Tools --> PaymentTool[lookup_payment_by_order]
    Tools --> RefundTool[lookup_refunds_by_order]

    OrderTool --> DB
    ShipmentTool --> DB
    PaymentTool --> DB
    RefundTool --> DB
```

The model chooses whether tools are needed. The application executes each selected tool and returns its result to the model. A conversation ID carries context across requests; it does not replace a fresh tool lookup for current transactional data.

## Prerequisites

- JDK 25
- Docker Engine / Docker Desktop with Docker Compose
- Maven installed and available as `mvn`
- A local LLM proxy running on port `6655`, configured with an available model and compatible tool calling
- `curl` and `jq` for the API examples and the live evaluation script
- [`glow`](https://github.com/charmbracelet/glow) to render Markdown responses and pretty-printed JSON in the terminal
- IntelliJ IDEA or another Java IDE (optional)

## Run locally

### 1. Start PostgreSQL

From the repository root:

```bash
docker compose up -d
docker compose ps | glow -
```

The Compose configuration and Spring datasource settings must agree on the host port, database name, username, and password. The development configuration connects to PostgreSQL at `localhost:5432` and uses the `knowledge_agent` database. Keep local credentials out of committed files if you change them.

### 2. Configure the local profile and LLM endpoint

The configuration is split into three layers:

- `src/main/resources/application.yml` contains shared application, datasource, JPA, Flyway, server, Actuator, logging, and agent settings.
- `src/main/resources/application-local.yml` contains local datasource and LLM endpoint defaults using provider-neutral `LLM_*` variables.
- `src/main/resources/application-anthropic.yml` maps `knowledge-agent.llm.*` into Spring AI Anthropic-specific properties.

For the local LLM proxy, activate both the `local` and `anthropic` profiles. In IntelliJ, open **Run → Edit Configurations → Environment variables** and set:

```dotenv
SPRING_PROFILES_ACTIVE=local,anthropic
LLM_BASE_URL=http://localhost:6655/anthropic
LLM_API_KEY=local-LLM-proxy
LLM_MODEL=<model alias supported by your proxy>
LLM_TIMEOUT=30s
LLM_MAX_TOKENS=2048
```

`local-LLM-proxy` is only a placeholder for a local proxy that accepts a dummy bearer token. Use the credential required by your proxy if authentication is enabled. `LLM_MODEL` must match an alias actually supported by the running proxy; do not assume that a public provider model ID is available.

The neutral local settings are equivalent to:

```yaml
knowledge-agent:
  llm:
    base-url: ${LLM_BASE_URL:http://localhost:6655/anthropic}
    api-key: ${LLM_API_KEY:local-LLM-proxy}
    model: ${LLM_MODEL}
    timeout: ${LLM_TIMEOUT:30s}
    max-tokens: ${LLM_MAX_TOKENS:2048}
```

The provider adapter maps these settings to the selected protocol's Spring AI properties. To use another provider, add the corresponding Spring AI starter and adapter profile; changing only the URL is not sufficient when the upstream protocol differs. The endpoint must support the capabilities used by the agent, particularly tool calling.

### 3. Configure embeddings for the RAG profile (optional)

RAG uses a separate OpenAI-compatible embedding endpoint; it does not require the chat model itself to be OpenAI. Keep the chat-provider profiles you already use and add `rag`. For the local Hyperspace proxy on port `6655`, the example below assumes you use the documented `local` and `anthropic` chat profiles:

```dotenv
SPRING_PROFILES_ACTIVE=local,anthropic,rag
EMBEDDING_BASE_URL=http://localhost:6655/openai/v1
EMBEDDING_API_KEY=local-embedding-proxy
EMBEDDING_MODEL=text-embedding-3-small
```

Use the API key accepted by your proxy. `EMBEDDING_BASE_URL` must be the OpenAI-compatible API base URL ending in `/openai/v1`, not the individual `/embeddings` endpoint. If you use another chat provider, keep that provider's required profile enabled alongside `rag`.

The embedding model must produce 1,536-dimensional vectors to match the `knowledge_vectors` schema. The RAG profile configures pgvector with schema initialization disabled because Flyway owns the database schema. Start PostgreSQL and apply migrations before starting the application with RAG enabled.

Do not commit actual credentials or secrets. `application-local.yml` should contain only environment-variable references and harmless local defaults. If your local files differ from these examples, merge the settings instead of replacing unrelated configuration.

### 4. Start the service


```bash
mvn spring-boot:run
```

Check application health and render the JSON response clearly:

```bash
curl -sS http://localhost:8080/actuator/health \
  | jq '.' \
  | glow -
```

Build and run the tests:

```bash
set -o pipefail
mvn clean verify 2>&1 | glow -
```

Integration tests that use Testcontainers require Docker to be available. Resolve compiler errors, failing tests, or context-startup failures before pushing; a successful compilation alone does not prove that integration tests are green.


### 5. Start the MCP server (optional)

The MCP server is enabled through the mcp-server Spring profile. It uses the same application and database; it is not a separate microservice. Ensure the local database and the local chat-provider settings from the previous step are available, then run:

~~~bash
SPRING_PROFILES_ACTIVE=local,anthropic,mcp-server mvn spring-boot:run
~~~

The configured endpoint is:

- MCP Streamable HTTP: http://127.0.0.1:8080/mcp
- Default bind address: 127.0.0.1 (loopback only)

The server offers the same four read-only transaction lookup capabilities as the chat agent, three policy resources (catalog plus URI template), and the investigate-order prompt. The resources and prompt are available without enabling the optional rag profile; the separate vector-search capability requires rag.

Run a protocol smoke test from another terminal:

~~~bash
MCP_BASE_URL=http://127.0.0.1:8080/mcp bash scripts/smoke-test-mcp.sh
~~~

To run the local preflight and Maven verification:

~~~bash
bash scripts/check-local.sh --database
bash scripts/verify.sh
~~~

MCP is a protocol adapter, not authentication or authorization by itself. Keep the default loopback bind unless you have deliberately configured network access, identity and authorization.


## API overview

All endpoints use the `/api/v1` prefix.

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/orders` | Create an order |
| `GET` | `/orders/{orderId}` | Retrieve an order with its items |
| `GET` | `/customers/{customerId}/orders` | List a customer's orders |
| `POST` | `/orders/{orderId}/processing` | Start order processing |
| `POST` | `/orders/{orderId}/cancellation` | Cancel an eligible order |
| `POST` | `/orders/{orderId}/shipment` | Dispatch an order |
| `GET` | `/shipments/{shipmentId}` | Retrieve a shipment |
| `GET` | `/orders/{orderId}/shipment` | Retrieve an order's shipment |
| `POST` | `/shipments/{shipmentId}/delivery` | Mark a shipment as delivered |
| `POST` | `/orders/{orderId}/payment` | Register a captured payment |
| `GET` | `/payments/{paymentId}` | Retrieve a payment |
| `GET` | `/orders/{orderId}/payment` | Retrieve an order's payment |
| `POST` | `/payments/{paymentId}/refunds` | Request a refund; requires `Idempotency-Key` |
| `GET` | `/refunds/{refundId}` | Retrieve a refund |
| `POST` | `/agent/chat` | Send a message and receive an answer plus `conversationId` |
| `POST` | `/agent/conversations/{conversationId}/close` | Close a conversation and delete its stored chat memory |
| `GET` | `/actuator/health` | Application health |
| `GET` | `/actuator/metrics` | List available Micrometer metrics |
| `GET` | `/actuator/metrics/{metricName}` | Inspect a specific metric |

### Terminal output conventions

Use `jq` to format JSON, and `glow` to render Markdown. For structured JSON API responses, use:

```bash
curl -sS http://localhost:8080/api/v1/orders/1 \
  | jq '.' \
  | glow -
```

For the agent's Markdown answer, extract the string rather than printing the whole JSON envelope:

```bash
printf '%s\n' "$AGENT_RESPONSE" | jq -r '.answer' | glow -
```

This keeps `conversationId` and other envelope fields separate from the human-readable answer. If `glow` is not installed, the JSON can still be formatted with `jq '.'`; Markdown answers can be printed with `jq -r '.answer'`.

### Example: ask for a complete order summary

The seeded Order #1 is intended for local smoke testing. In the current seed data it contains a laptop and a USB-C docking station, has a total of EUR 720.00, and is in `SHIPPED` status. Its shipment is `IN_TRANSIT` with carrier `DHL` and tracking number `DHL-DEMO-ORDER-1`. The payment is `CAPTURED`; no refund records are seeded for this order. The model should retrieve each relevant fact via its tool rather than relying on the prompt or a hard-coded answer.

```bash
MESSAGE='Give me a complete status summary for order 1, including its items, shipment, payment and refund history.'

FIRST_RESPONSE=$(curl -sS \
  -X POST http://localhost:8080/api/v1/agent/chat \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: order-summary-demo-001' \
  -d "$(jq -n --arg message "$MESSAGE" '{message: $message}')")

# Print the JSON metadata envelope in pretty format.
printf '%s\n' "$FIRST_RESPONSE" \
  | jq '{conversationId: .conversationId}' \
  | glow -

# Render the actual Markdown answer.
printf '%s\n' "$FIRST_RESPONSE" \
  | jq -r '.answer' \
  | glow -

CONVERSATION_ID=$(printf '%s\n' "$FIRST_RESPONSE" | jq -r '.conversationId')
printf 'Conversation ID: %s\n' "$CONVERSATION_ID"
```

The response envelope has this shape (the answer below is illustrative; natural-language formatting can vary by model):

```json
{
  "conversationId": "<generated-uuid>",
  "answer": "Markdown answer returned by the agent"
}
```

### Example: ask a documented policy question with RAG

With PostgreSQL running, the policy corpus loaded, and the application started with the `rag` profile, ask a question whose answer is present in the documents:

```bash
MESSAGE='What is the return window according to the documented policy?'

curl -sS \
  -X POST http://localhost:8080/api/v1/agent/chat \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg message "$MESSAGE" '{message: $message}')" \
  | jq -r '.answer' \
  | glow -
```

For the demonstration Refund Policy version 1.0, the supported return window is **30 calendar days from delivery**. A good response should answer that directly and identify the source and version; it should not dump raw `SOURCE`/`EXCERPT` blocks. The exact prose can vary by model. The policy is illustrative and must not be treated as a real customer policy.

### Example: follow up using the conversation ID

Ask for the tracking number without repeating the order ID. The agent should resolve "its" from the conversation history and call `lookup_shipment_by_order` for the current shipment facts.

```bash
SECOND_RESPONSE=$(curl -sS \
  -X POST http://localhost:8080/api/v1/agent/chat \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-ID: order-followup-001" \
  -d "$(jq -n \
    --arg conversationId "$CONVERSATION_ID" \
    --arg message 'What is its tracking number?' \
    '{conversationId: $conversationId, message: $message}')")

printf '%s\n' "$SECOND_RESPONSE" \
  | jq '{conversationId: .conversationId}' \
  | glow -

printf '%s\n' "$SECOND_RESPONSE" \
  | jq -r '.answer' \
  | glow -
```

### Example: close a conversation

```bash
CLOSE_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' \
  -X POST \
  "http://localhost:8080/api/v1/agent/conversations/$CONVERSATION_ID/close")

printf 'HTTP_STATUS=%s\n' "$CLOSE_STATUS"
```

A successful close returns `204 No Content`. A later chat request using that closed ID returns `409 Conflict`; an unknown conversation ID returns `404 Not Found`.

To display the JSON error body from a rejected follow-up, keep the HTTP status separate from the JSON stream:

```bash
ERROR_FILE=$(mktemp)
HTTP_STATUS=$(curl -sS -o "$ERROR_FILE" -w '%{http_code}' \
  -X POST http://localhost:8080/api/v1/agent/chat \
  -H 'Content-Type: application/json' \
  -d "$(jq -n \
    --arg conversationId "$CONVERSATION_ID" \
    --arg message 'What is the order status?' \
    '{conversationId: $conversationId, message: $message}')")

printf 'HTTP_STATUS=%s\n' "$HTTP_STATUS"
jq '.' "$ERROR_FILE" | glow -
rm -f "$ERROR_FILE"
```

### Example: inspect agent metrics

The custom metric names are `knowledge.agent.requests`, `knowledge.agent.request.duration`, `knowledge.agent.tool.calls`, and `knowledge.agent.tool.duration`. Metric data appears after relevant requests have been processed.

```bash
curl -sS 'http://localhost:8080/actuator/metrics/knowledge.agent.requests' \
  | jq '.' \
  | glow -

curl -sS \
  'http://localhost:8080/actuator/metrics/knowledge.agent.tool.calls?tag=tool:lookup_order' \
  | jq '.' \
  | glow -
```

Use `X-Correlation-ID` to tie a client request to the application logs. Valid IDs are echoed back; missing or invalid IDs are replaced with a generated UUID. Do not use order IDs, conversation IDs, user input, or other high-cardinality values as metric tags.

### Example: create an order

```bash
curl -sS -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": 1,
    "currency": "EUR",
    "items": [
      {
        "productId": "SKU-API-001",
        "productName": "API test product",
        "quantity": 2,
        "unitPrice": 49.50
      }
    ]
  }' \
  | jq '.' \
  | glow -
```

Use a customer ID that exists in your local database. The endpoint expects a valid customer and a non-empty list of valid items. The request JSON is formatted for readability; the response is parsed and pretty-printed before it reaches the terminal.

## Database migrations and seed data

Flyway manages schema creation and local seed data. Migrations are under `src/main/resources/db/migration` and run on application startup. Conversation metadata is created by the conversation migration. Spring AI chat-memory messages are stored separately in `SPRING_AI_CHAT_MEMORY`, managed by a dedicated Flyway migration.

A useful check for order totals is:

```sql
SELECT
    o.id,
    o.status,
    o.total_amount,
    o.currency,
    COUNT(oi.id) AS item_count,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS items_total,
    o.total_amount - COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS difference
FROM orders o
         LEFT JOIN order_items oi ON oi.order_id = o.id
GROUP BY o.id, o.status, o.total_amount, o.currency
ORDER BY o.id;
```

The local development database can be recreated if you intentionally want to discard its state:

```bash
docker compose down -v
docker compose up -d
mvn spring-boot:run
```

> **Warning:** `docker compose down -v` removes Compose-managed volumes and permanently deletes the local PostgreSQL data in those volumes. Do not use it against a database containing data you need. Treat an applied migration as immutable in shared/persistent environments and add a new migration for later changes.

## Testing

The test suite is deliberately split into deterministic unit/model tests, PostgreSQL-backed integration tests, MCP protocol integration tests and live-model evaluation. They answer different questions.

### CI-equivalent verification

Run the same verification lifecycle used by GitHub Actions:

~~~bash
bash scripts/verify.sh
~~~

Or run Maven directly:

~~~bash
mvn --batch-mode --no-transfer-progress clean verify
~~~

Use pipefail when piping terminal output into glow:

~~~bash
set -o pipefail
mvn --batch-mode --no-transfer-progress clean verify 2>&1 | glow -
~~~

The test suite includes domain and tool unit tests, API tests, conversation/memory tests, PostgreSQL/Testcontainers integration tests, model/tool orchestration tests, observability tests, and an MCP Streamable HTTP integration test. Testcontainers needs a working Docker daemon. Do not treat a successful compile as proof that database or Spring context wiring works.

### Focused test suites

~~~bash
bash scripts/test-agent-limits.sh
mvn --batch-mode --no-transfer-progress -Dtest=McpServerHttpIntegrationTest test
mvn --batch-mode --no-transfer-progress -Dtest=SupportPolicyMcpResourcesTest,OrderSupportMcpPromptsTest test
~~~

Inspect Surefire output under target/surefire-reports/ if a test fails. For a Spring context startup failure, read the first meaningful Caused by section, not only the top-level Failed to load ApplicationContext message.

### MCP protocol integration test

McpServerHttpIntegrationTest boots a random-port HTTP server and drives the MCP protocol over HTTP. The application-owned transactional tool dependencies are mocked so this test remains deterministic while exercising the actual MCP transport, session/initialization flow and annotation discovery.

It asserts the following flow:

1. initialize with a supported protocol version;
2. notifications/initialized;
3. tools/list and tools/call;
4. resources/list;
5. resources/templates/list and resources/read;
6. prompts/list and prompts/get.

The test context explicitly excludes database auto-configuration and the JDBC chat-memory repository auto-configuration. Without that last exclusion, the context may try to create JdbcChatMemoryRepository even though no JdbcTemplate is available in this intentionally isolated protocol test.

### Live MCP smoke test

The live test uses the actual PostgreSQL-backed tools. Start the service with the mcp-server profile, then run:

~~~bash
MCP_BASE_URL=http://127.0.0.1:8080/mcp bash scripts/smoke-test-mcp.sh
~~~

This verifies the initialize handshake, tools discovery/call, policy resource discovery/read, resource-template discovery, and prompt discovery/rendering. It requires a running service and the seeded Order #1 data. The script does not call the chat model; it checks the MCP contract directly.

### Local environment preflight

~~~bash
bash scripts/check-local.sh
bash scripts/check-local.sh --database
bash scripts/check-local.sh --running --mcp
~~~

The optional flags require a running Docker database, application health endpoint or MCP endpoint. The MCP preflight is only a reachability check; use smoke-test-mcp.sh for the protocol flow.

More detail, including the boundary each test proves and common failure modes, is documented in [docs/TESTING.md](docs/TESTING.md).

## Live agent evaluation

`scripts/evaluate-agent.sh` is a black-box smoke/evaluation suite for a locally running service and configured LLM provider. It checks the model-plus-tools workflow against the seeded Order #1 data, conversation follow-up resolution, payment lookup, empty refund history, and unknown-order handling. It validates required facts rather than exact prose because model wording can vary.

Run it from the repository root after starting PostgreSQL and the Spring Boot application with the local LLM provider configured:

```bash
bash scripts/evaluate-agent.sh
```

To use a different application URL or increase the request timeout:

```bash
BASE_URL=http://localhost:8080 REQUEST_TIMEOUT_SECONDS=120 bash scripts/evaluate-agent.sh
```

The script makes real LLM requests and may consume provider capacity. It is a development/acceptance check, not a deterministic unit test. Keep the scripted `ChatModel` tests for deterministic tool-calling and CI validation. The script exits non-zero if an expected fact is missing or an API request fails. It prints each Markdown answer with `glow` and the final evaluation report as pretty JSON through `jq` and `glow`.

## Developer scripts

All scripts are designed to run from the repository root and work with bash. glow is used when installed for readable Markdown/JSON output, but is not required.

| Script | Purpose | Requires |
|---|---|---|
| scripts/check-local.sh | Check the toolchain; optionally check Docker/PostgreSQL, app health, or MCP reachability | Java 25, Maven, curl; jq/Docker when selected |
| scripts/verify.sh | Run the same clean verify Maven lifecycle as CI | Java 25, Maven |
| scripts/test-agent-limits.sh | Run focused execution limit/deadline/observability regression tests | Java 25, Maven |
| scripts/smoke-test-mcp.sh | Exercise MCP initialize, tools, resources and prompt over HTTP | Running mcp-server profile, seeded PostgreSQL, curl, jq |
| scripts/evaluate-agent.sh | Evaluate real model + tools against seeded order facts; optional RAG cases | Running agent and configured chat provider; curl, jq |

Use bash scripts directly; executable mode is not required:

~~~bash
bash scripts/check-local.sh --database
bash scripts/verify.sh
BASE_URL=http://127.0.0.1:8080 bash scripts/evaluate-agent.sh
MCP_BASE_URL=http://127.0.0.1:8080/mcp bash scripts/smoke-test-mcp.sh
~~~

The live evaluation may consume provider capacity. The MCP smoke test calls the MCP protocol directly and does not ask the model to reason. Keep both of these outside deterministic CI unless the provider and environment are deliberately part of the test contract.

## Agent execution and tool limits

The agent uses multiple layers of execution protection:

- AgentExecutionDeadline bounds the duration of an agent request.
- The agentExecutionExecutor uses a bounded worker pool and queue. Rejected work is mapped to a structured API error.
- Spring AI enforces the maximum number of calls per tool and maximum total tool calls within one model turn.
- BoundedToolCallingAdvisor enforces the maximum number of tool-calling rounds per turn. An over-limit batch is rejected before it executes.
- Tool-limit breaches use a structured HTTP 422 response with error code AGENT_TOOL_LIMIT_EXCEEDED.

Default configuration:

| Setting | Default | Meaning |
|---|---:|---|
| spring.ai.tools.limits.max-calls-per-tool-default | 8 | Maximum calls per tool in one turn |
| spring.ai.tools.limits.max-total-tool-calls | 15 | Maximum calls across all tools in one turn |
| agent.limits.max-iterations | 8 | Maximum tool-calling rounds in one turn |
| spring.ai.tools.limits.on-limit-exceeded | THROW | Required for structured limit error handling |
| agent.execution.timeout | 90s | Configured synchronous wait limit |
| agent.execution.pool.max-size | 8 | Maximum agent worker threads |
| agent.execution.pool.queue-capacity | 16 | Maximum queued work items |

Tune these values using load measurements and upstream timeout behavior; they are not a benchmark-derived production configuration.

### Focused limit tests

~~~bash
bash scripts/test-agent-limits.sh
~~~

These tests cover limit configuration, the bounded tool-calling advisor, execution deadlines, queue behavior and observability. For the full suite use bash scripts/verify.sh.


## Development principles

- Keep business rules in domain/services, not in controllers or model prompts.
- Give the model explicit tools instead of direct access to the database.
- Use conversation history for reference resolution; use tools for current transactional facts.
- Keep tool names and metric tags bounded; never tag metrics with order IDs, conversation IDs, or user-supplied values.
- Prefer read-only tools; validate and authorize any future mutation tool in application code.
- Keep provider-neutral settings externalized and protocol-specific mappings in adapter profiles.
- Test failure paths, concurrency, idempotency, conversation closure, and tool execution—not just successful HTTP requests.
- Never log API keys, full prompts, full model responses, tracking numbers, payment data, or other transactional details unnecessarily.
- Do not commit secrets, generated build output, local database files, or machine-specific IDE configuration.

## Roadmap

The implementation status and next milestones are tracked in [docs/ROADMAP.md](docs/ROADMAP.md). The roadmap distinguishes implemented work from future work and preserves the original goal: learn the complete agent plumbing in one modular Spring Boot application.

Near-term next steps:

- [x] Fix MCP HTTP integration context wiring and test resource/prompt discovery.
- [x] Expose allow-listed policy documents as MCP resources.
- [x] Add a reusable read-only order investigation prompt.
- [x] Add live MCP smoke test and local preflight/CI verification scripts.
- [ ] Add a durable execution/event model before implementing long-running approval flows.
- [ ] Add explicit authenticated principal/authorization and conversation ownership checks.
- [ ] Add policy evaluation and human approval before exposing any state-changing agent tool.
- [ ] Add a full-fidelity audit history separate from bounded chat memory.
- [ ] Add retrieval/answer-grounding evaluation for RAG.
- [ ] Add a configured remote MCP client only when there is an MCP service to connect to.

Do not add Kafka/Redis/multiple services merely to make the architecture diagram look distributed. Introduce external infrastructure only when a concrete workflow requires it.

## Security notes

- Keep local credentials out of Git. Commit only environment-variable references and harmless development defaults.
- Review `git status` and `git diff --cached` before pushing to ensure no API keys, proxy tokens, service keys, personal data, or local configuration have been staged.
- The example datasource credentials are for local development only and must not be reused in a deployed environment.
- Treat tool arguments and model output as untrusted. Enforce input validation, authorization, and business rules in application code.
- Persisted chat memory is not an authorization boundary. If the API is exposed to multiple users, bind each conversation to an authenticated principal and enforce ownership before allowing reuse or closure. Also define a retention policy for stored chat-memory rows; do not keep conversation context indefinitely by default.

## License

This project is licensed under the MIT License.

Copyright (c) 2026 Stefan Baychev.
