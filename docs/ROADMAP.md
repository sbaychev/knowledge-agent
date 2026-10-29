# Knowledge Agent roadmap

This roadmap distinguishes capabilities already present in the repository from the next architecture lessons. The north star remains the original Knowledge String sequence: build a normal application first, then understand agent plumbing one boundary at a time.

The deployment shape remains a **single Spring Boot modular monolith** until an independently deployable process solves an actual requirement. Do not add Kafka, Redis or another service just because it commonly appears in agent architecture diagrams. PostgreSQL/pgvector and the bounded in-process executor are enough for the current scope.

## Milestone 0 — a real application before AI

**Status: implemented foundation**

- [x] Domain model for customers, orders, line items, shipments, payments and refunds.
- [x] REST controllers with validated request DTOs and structured error responses.
- [x] Persistence with Spring Data JPA, PostgreSQL, Flyway migrations and seeded development data.
- [x] Domain checks for lifecycle transitions, amounts and refund idempotency.
- [x] Application-owned lookup tools for current transactional facts.

The application must remain useful and testable without an LLM. Domain invariants belong in Java services/entities and the database, not only in a prompt.

## Milestone 1 — deterministic AI wiring and limits

**Status: implemented, continue hardening**

- [x] Spring AI ChatClient integration.
- [x] Tool calling over application-owned lookup capabilities.
- [x] Scripted-model tests for tool hand-off.
- [x] Bounded tool-call counts and tool-calling rounds.
- [x] Bounded executor, queue rejection and configurable execution deadline.
- [x] Stable API error mapping for provider failure, timeouts and agent limits.
- [x] Conversation metadata and bounded JDBC chat memory.
- [ ] Add explicit provider timeout/interrupt tests for each configured provider adapter.
- [ ] Measure resource consumption and queue behavior under load; tune defaults from measurements.
- [ ] Add structured model/tool invocation events to make actual execution explainable.

The project currently relies on Spring AI's orchestration pipeline and a custom advisor that enforces additional limits. It does not yet implement a fully custom ModelGateway + ToolRegistry + AgentState loop; that can be an educational iteration once the existing implementation is green.

## Milestone 2 — MCP as a protocol boundary

**Status: implementation started**

- [x] Streamable HTTP MCP server profile with explicit protocol selection.
- [x] MCP adapters for four read-only transactional tools.
- [x] HTTP integration test for initialize, tool discovery and invocation.
- [x] Read-only policy resources with allow-listed classpath document IDs.
- [x] Parameterized investigate-order prompt with explicit read-only constraints.
- [x] Integration assertions for resource/resource-template and prompt discovery.
- [x] Shell smoke test for protocol flow against the running application.
- [ ] Add client-side configuration and a service abstraction for connecting to a separately managed MCP server.
- [ ] Decide on stateless versus stateful transport based on session use, scale-out and interoperability requirements.
- [ ] Add authentication and authorization before exposing the endpoint outside a trusted local environment.
- [x] Reject unknown policy resource IDs and malformed tool arguments in MCP protocol tests.
- [ ] Add a protocol test for a valid tool name with a missing transactional record.

Important distinction:
- A **tool** exposes a callable capability.
- A **resource** exposes reference data.
- A **prompt** packages a reusable instruction.
- None is an authorization layer or a business policy engine.

## Milestone 3 — context is not memory

**Status: partial**

- [x] Bounded recent-message memory for follow-up reference resolution.
- [x] Separate conversation lifecycle metadata from Spring AI chat memory.
- [x] Transactional facts are refreshed through tools rather than treated as permanently true because they appeared earlier.
- [ ] Add a ContextBuilder that makes message budget, selected memory window, retrieved evidence and security identity explicit.
- [ ] Define truncation/summarization behavior under a hard token budget; preserve the current user message and tool evidence when dropping older history.
- [ ] Add redaction policy for secrets, personal data and unnecessary transactional details before storing/sending.
- [ ] Test memory isolation across conversation IDs and authenticated principals.
- [ ] Add retention and deletion semantics beyond the current conversation-close behavior.

Keep the terms distinct:
- **State** is what the runtime needs to continue execution.
- **Context** is the subset supplied to the model for the current decision.
- **Memory** is persisted information from earlier interactions that may help future decisions.
- **Knowledge** is the external/versioned corpus, not the conversation transcript.
- **Audit history** records what the system actually did.

## Milestone 4 — durable execution and audit trail

**Status: not implemented**

Add this before allowing long-running or human-approved operations.

- [ ] Introduce AgentExecution with stable ID, conversation ID, status, start/end timestamps, iteration count and terminal reason.
- [ ] Introduce ordered AgentExecutionEvent / AgentStep rows for model invocation, tool request, validation result, policy decision, approval wait/resume, tool result and final response.
- [ ] Use a versioned event payload schema; do not persist secrets or unrestricted model payloads by default.
- [ ] Correlate execution events with HTTP correlation ID and bounded metrics/traces.
- [ ] Define idempotent resume behavior so duplicate resume messages cannot execute a pending operation twice.
- [ ] Recover executions left RUNNING after a process restart.

Do not reconstruct audit history later from chat text. Model answers summarize events; they are not a substitute for event records.

## Milestone 5 — policy and human approval for mutations

**Status: not implemented; must precede LLM mutation tools**

The REST API has order/refund operations, but current agent-facing tools intentionally do not expose them. Keep that safety boundary until the following are available.

- [ ] Define tool metadata with stable name, input schema, risk class, idempotency requirement and whether approval is required.
- [ ] Validate tool arguments before execution, including schema and domain constraints.
- [ ] Resolve an authenticated principal and enforce authorization separately from agent policy.
- [ ] Evaluate policy in application code; never let model text decide whether an operation is authorized.
- [ ] Return a machine-readable decision and reason: ALLOW, DENY or REQUIRE_APPROVAL.
- [ ] Persist an approval request with normalized argument snapshot, requestor, approver, expiry, status and audit timestamps.
- [ ] On approval, revalidate current domain state and policy immediately before execution.
- [ ] Require idempotency keys and deduplicate execution across retries, timeouts and resumes.
- [ ] Test denied, expired, rejected, duplicate and concurrent approvals.
- [ ] Only then expose a controlled mutation tool to Spring AI or MCP.

~~~mermaid
sequenceDiagram
    participant U as User
    participant A as Agent runtime
    participant P as Policy and authorization
    participant D as Durable execution store
    participant H as Human approver
    participant T as Domain tool

    U->>A: Request a refund
    A->>P: Evaluate identity, order, amount and operation
    P-->>A: REQUIRE_APPROVAL
    A->>D: Persist execution and approval request
    A-->>U: Pending approval ID
    H->>D: Approve or reject
    D->>P: Revalidate principal, current state and policy
    P-->>D: ALLOW or DENY
    D->>T: Execute with idempotency key only if allowed
    T-->>D: Durable result
    D-->>U: Final status
~~~

## Milestone 6 — robust RAG

**Status: basic ingestion/retrieval implemented, quality evaluation pending**

- [x] Versioned Markdown corpus and content checksums.
- [x] Token-oriented chunking with source/chunk metadata.
- [x] pgvector storage owned by Flyway migrations.
- [x] Query validation, configurable top-K and similarity threshold.
- [x] Read-only knowledge-search tool.
- [ ] Add fixed evaluation questions with known source/answer spans, including unanswerable questions.
- [ ] Measure retrieval recall@K, source correctness and answer groundedness separately.
- [ ] Test source updates/deletions, including failure halfway through re-indexing to prevent stale/mixed versions.
- [ ] Define duplicate document IDs, ingestion retries, source rollback and index rebuild behavior.
- [ ] Review metadata filters and tenant isolation before loading mixed-trust corpora.
- [ ] Add evidence formatting and source-citation validation in the answer path.

Do not tune the similarity threshold to one successful prompt. Use a small versioned evaluation corpus, compare retrieval evidence, then measure the downstream effect on answers.

## Milestone 7 — observability and operations

**Status: basic metrics/correlation present**

- [x] Request correlation ID generation/validation/response header.
- [x] MDC cleanup after requests.
- [x] Agent/tool metrics with bounded labels.
- [x] Actuator health and metrics endpoints.
- [ ] Add end-to-end tracing with a real OTel collector and verify context propagation through HTTP/model calls.
- [ ] Add latency/token/cost metrics only when the provider exposes reliable usage values.
- [ ] Add dashboards and alert thresholds from measured baselines.
- [ ] Add rate limiting, provider budget protection and circuit-breaking when there are real remote boundaries to protect.
- [ ] Verify graceful shutdown behavior with in-flight agent calls.
- [ ] Add backup/restore exercises and persistent-volume procedures.

## Milestone 8 — externalization only when justified

**Status: intentionally deferred**

- [ ] Add a remote MCP client connector when there is a separately managed MCP service to connect to.
- [ ] Introduce a message broker only if an asynchronous approval/notification workflow needs durable event delivery.
- [ ] Consider Redis only for a measured use case such as distributed idempotency, rate limiting or ephemeral coordination that cannot be handled adequately by the persistence model.
- [ ] Split a capability into a separate service only when ownership, deployment, scaling, latency isolation or security calls for that separation.

Do not add Kafka just to model a future workflow in advance. Keep synchronous steps clear until workflow durability requirements are real.

## Definition of done for each milestone

Every milestone should leave the project in a state where another developer can:

1. read the architecture notes and understand the boundary;
2. run deterministic tests locally;
3. run an integration/smoke check at the boundary that changed;
4. see a meaningful failure when a contract is broken;
5. identify what is implemented versus planned;
6. reproduce the CI command; and
7. understand security/trust assumptions without inferring them from a prompt.
