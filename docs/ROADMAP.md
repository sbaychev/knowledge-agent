# Knowledge Agent Roadmap

This roadmap distinguishes capabilities already present in the repository from the next architecture lessons. The end target remains the original Knowledge Nugget sequence: build a normal application first, then work about the agent plumbing one boundary at a time.

The deployment shape remains a **single Spring Boot modular monolith** until an independently deployable process solves an actual requirement. Not adding Kafka, Redis or another service just because it is a generally appearing pattern in agent architecture. PostgreSQL/pgvector and the bounded in-process executor are enough for the current scope. Working step by step, by pealing the different layers, rather than try to do it all at once, where it does not make sense to be putting boundaries on moving targets.

Main is to use previous learned experience in building AI / complex solutions, where unless you have a base that works, complexity does not matter unless it is needed.

## Milestone 0 - a real application before AI

**Status: implemented foundation**

- [x] Domain model for customers, orders, line items, shipments, payments and refunds.
- [x] REST controllers with validated request DTOs and structured error responses.
- [x] Persistence with Spring Data JPA, PostgreSQL, Flyway migrations and seeded development data.
- [x] Domain checks for lifecycle transitions, amounts and refund idempotency.
- [x] Application-owned lookup tools for current transactional facts.

The application must remain useful and testable without an LLM. Domain invariants belong in Java services/entities and the database, not only in a prompt.

## Milestone 1 - deterministic AI wiring and limits

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

## Milestone 2 - MCP as a protocol boundary

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

## Milestone 3 - context is not memory

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

**Status: initial lifecycle persistence implemented; detailed events pending**

- [x] Introduce `AgentExecution` with a stable ID, conversation ID,
  lifecycle status, start/end timestamps and normalized failure code.
- [x] Persist ordered start and terminal lifecycle events in PostgreSQL.
- [x] Expose the execution ID in the agent chat response.
- [x] Link execution records to conversations and HTTP correlation IDs.
- [x] Version event payloads and reject invalid lifecycle transitions.
- [x] Add PostgreSQL integration tests for successful and failed executions.
- [ ] Add model invocation events with bounded, non-sensitive metadata.
- [ ] Add tool invocation events with tool name, outcome and duration.
- [ ] Persist validation results and policy decisions when those stages
  are implemented.
- [ ] Define idempotent execution resume semantics before human approval.
- [ ] Recover or explicitly reconcile executions left `RUNNING` after
  process restart.
- [ ] Define retention and deletion rules for executions and their events.

The current implementation captures the outer execution lifecycle only.
It does not yet provide a complete audit trail of the model/tool loop.
Do not enable agent-driven mutations on the assumption that this
lifecycle record alone proves which business actions took place.

## Milestone 5 - policy and human approval for mutations

**Status: first persisted refund-approval slice implemented; general policy engine and agent integration pending**

The REST API has order/refund operations, but current agent-facing tools intentionally do not expose them. Keep that safety boundary. The refund-specific approval path below is a human-facing API workflow; it does not enable the model to issue a refund.


The first implementation is intentionally refund-specific. It creates a pending approval and requires a distinct APPROVER identity before calling the existing RefundService. The resulting domain refund remains REQUESTED; approval does not claim that a payment provider has completed the refund. The old direct refund endpoint is disabled even for APPROVER callers, so it cannot bypass the four-eyes workflow. No agent-facing or MCP mutation tool is enabled by this milestone yet.


- [ ] Define tool metadata with stable name, input schema, risk class, idempotency requirement and whether approval is required.
- [ ] Validate tool arguments before execution, including schema and domain constraints.
- [x] Resolve authenticated AGENT and APPROVER principals for refund approvals; disable the legacy direct-refund bypass; keep authorization separate from agent policy.
- [ ] Bind conversations and executions to an authenticated principal and tenant; enforce ownership on continuation and close.
- [ ] Evaluate policy in application code; never let model text decide whether an operation is authorized.
- [ ] Return a machine-readable decision and reason: ALLOW, DENY or REQUIRE_APPROVAL.
- [x] Persist refund approval requests with immutable payment/amount/currency/reason data, requester, approver, expiry, status and timestamps.
- [x] Persist ordered approval events in a separate append-only PostgreSQL event table with versioned JSONB payloads.
- [ ] Generalize the approval aggregate to other mutation types only after their domain contracts are defined.
- [x] On refund approval, revalidate refundable capacity through the existing locked RefundService transaction immediately before creating the refund request.
- [ ] Add an explicit application policy evaluator with machine-readable ALLOW, DENY and REQUIRE_APPROVAL decisions.
- [x] Require an idempotency key for approval creation and derive the downstream refund idempotency key from the approval ID.
- [ ] Define idempotent resume and reconciliation semantics for asynchronous provider calls, process restarts and ambiguous timeouts.
- [x] Test role denial, self-approval denial, expiry, rejection, duplicate requests, duplicate approval retries and concurrent duplicate approvals.
- [ ] Add concurrent approve-versus-reject race tests and policy-revalidation tests.
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

## Milestone 6 - robust RAG

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

## Milestone 7 - observability and operations

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

## Milestone 8 - externalization only when justified

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
