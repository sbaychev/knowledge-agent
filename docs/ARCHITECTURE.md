# Architecture and runtime model

## Why this is a modular monolith

Knowledge Agent deliberately starts as **one Spring Boot application** with explicit package boundaries. The learning goal is to follow a request through an LLM, application-owned tools, domain services, persistence, policy knowledge, and observability without adding network hops that do not teach a new concept.

This is a decision for this POC, not a claim that one process is always the right deployment model. Split a capability into another process only when independent scaling, ownership, deployment, failure isolation, or security boundaries justify it.

The current repository implements an order-support scenario. It does not yet implement the full policy/approval workflow from the long-term roadmap. LLM-facing tools remain read-only even though the REST API has operations that can mutate order, shipment, payment, and refund state.

## System context

~~~mermaid
flowchart TD
    User[User / API client] -->|HTTP JSON| API[Spring MVC controllers]
    API --> Domain[Application and domain services]
    Domain --> Repos[Spring Data JPA repositories]
    Repos --> DB[(PostgreSQL + Flyway)]

    API --> Agent[Order-support agent]
    Agent --> Limits[Execution deadline and bounded executor]
    Limits --> Client[Spring AI ChatClient]
    Client <--> Model[Configured chat-model provider]
    Client <--> Memory[Bounded message-window memory]
    Memory --> MemoryDB[(PostgreSQL chat-memory table)]
    Client --> Tools[Application-owned read-only tools]
    Tools --> Repos

    Client --> KTool[Knowledge search tool - optional]
    KTool --> Retrieval[Knowledge retrieval service]
    Retrieval --> Vectors[(pgvector)]
    Corpus[Versioned Markdown corpus] --> Ingest[Ingestion + chunking]
    Ingest --> Vectors
    Ingest --> Sources[(knowledge_sources)]

    MCP[MCP Streamable HTTP endpoint - optional profile] --> MCPTools[MCP tool adapter]
    MCPTools --> Tools
    MCP --> MCPResources[Policy resource provider]
    MCP --> MCPPrompts[Order investigation prompt]

    API --> Metrics[Micrometer + correlation logging]
    Agent --> Metrics
    Tools --> Metrics
~~~

The model does not receive database credentials. It requests capabilities from the application, and the application decides whether and how to execute them.

## Request flow

~~~mermaid
sequenceDiagram
    participant U as API client
    participant C as AgentChatController
    participant S as Agent service
    participant D as Execution deadline
    participant L as Spring AI ChatClient
    participant M as Chat model
    participant T as Application tool
    participant R as Repository
    participant P as PostgreSQL

    U->>C: POST /api/v1/agent/chat
    C->>S: message + optional conversationId
    S->>D: execute bounded operation
    D->>L: Build prompt + bounded conversation history
    L->>M: User request + available tools
    M-->>L: Tool call (for example lookup_order)
    L->>T: Validate arguments and invoke tool method
    T->>R: Read current transaction state
    R->>P: SELECT
    P-->>R: Current order facts
    R-->>T: Domain result
    T-->>L: Bounded textual tool result
    L->>M: Tool result + conversation context
    M-->>L: Final response
    L-->>D: Answer
    D-->>S: Answer
    S-->>C: conversationId + answer
    C-->>U: HTTP JSON response + correlation header
~~~

The number of model/tool rounds is bounded. The model chooses the exact tool sequence, so tests should verify grounded facts and forbidden behavior rather than brittle full-answer wording.

## Transactional facts versus policy knowledge

Treat these as different sources of truth:

- **Transactional tools** answer what the system currently knows about a specific order, shipment, payment, or refund.
- **RAG retrieval** finds relevant excerpts in a versioned document corpus. It does not establish live order status or payment state.
- **MCP resources** expose allow-listed policy documents as readable resources for compatible MCP clients. The repository's resources are illustrative.
- **MCP prompts** offer reusable instructions. A prompt is not a policy engine, authorization check, or proof that a tool call happened.

A text fragment returned by the model is not a durable record of which facts were actually retrieved. If audit-grade proof is required, add a separate execution-event store instead of reconstructing an audit log from model prose.

## Package responsibility map

| Package | Responsibility | Keep out |
|---|---|---|
| **api** | HTTP routes, request DTOs, validation, public response shape | Prompt construction, direct repository queries |
| **order** | Order model, state transitions, domain invariants, persistence mapping | LLM-specific choices |
| **shipment** | Shipment state and lifecycle rules | Model prompts |
| **payment** | Payment/refund model, amount validation, idempotency, persistence | Direct model-generated SQL |
| **conversation** | Conversation identity/status and lifecycle | Treating chat memory as a full audit log |
| **agent** | Bounded execution, tool-round limits, deadlines | Database business rules |
| **tools** | Narrow model-facing capabilities delegated to application services/repositories | Authorization bypasses, unbounded data dumps |
| **knowledge** | Policy corpus ingestion, source tracking, retrieval under the rag profile | Transactional status lookups |
| **mcp** | MCP transport adapters: tools, resources, reusable prompts | Direct state-changing domain operations |
| **observability** | Correlation IDs, bounded metric labels, durations and failure outcomes | PII/high-cardinality labels |
| **config** | Spring wiring and configurable execution limits | Runtime business decisions |

## Agent execution boundaries

The current execution controls are complementary, not interchangeable.

1. **HTTP validation** rejects malformed public requests before model invocation.
2. **Execution deadline** applies a time limit and runs agent work on a bounded executor with a finite queue. Queue saturation is reported instead of accumulating unbounded tasks.
3. **Spring AI per-tool and total tool-call limits** cap call volume in a model turn.
4. **BoundedToolCallingAdvisor** counts tool-calling rounds and rejects another over-limit batch before that batch executes.
5. **Domain services** enforce business rules for REST actions. The model cannot override them with instructions.
6. **API error mapping** turns known timeouts, capacity errors, model failures, validation errors and domain conflicts into stable HTTP error envelopes.

A cancelled Java Future does not guarantee that every underlying network operation can be forcibly stopped. Keep connect/read/response timeouts configured at the relevant client boundary as well.

Default limits are in src/main/resources/application.yml:

| Setting | Default | Purpose |
|---|---:|---|
| agent.limits.max-iterations | 8 | Maximum model tool-calling rounds |
| agent.limits.max-tool-calls | 15 | Application-level setting for the total-call cap |
| spring.ai.tools.limits.max-calls-per-tool-default | 8 | Maximum calls to one tool in a turn |
| spring.ai.tools.limits.max-total-tool-calls | 15 | Total calls across tools in a turn |
| agent.execution.timeout | 90s | Configured synchronous wait limit |
| agent.execution.pool.core-size | 4 | Core worker count |
| agent.execution.pool.max-size | 8 | Maximum workers |
| agent.execution.pool.queue-capacity | 16 | Bounded pending queue |

These are development defaults, not load-test-derived production recommendations. Tune them using observed latency, provider limits, database capacity, and back-pressure tests.

## Conversation memory is not execution state

The application has persistent conversation metadata and bounded chat memory, but these concepts should not be collapsed.

- **Conversation metadata** records conversation identity/status and whether it may be continued.
- **Chat memory** supplies a limited recent message window to help resolve conversational references.
- **Current facts** are re-read through transactional tools when needed.
- **Execution state** records a model/tool workflow and allows it to resume after a process restart or human approval.
- **Audit history** records the security principal, requested tool, normalized arguments, policy decision, execution outcome, timestamps and correlation identifiers.

The current JDBC chat-memory repository is contextual memory, not an immutable audit transcript. Closing a conversation clears its stored chat memory. For multi-user exposure, bind each conversation to an authenticated principal and verify ownership on every continuation/close request. A conversation UUID alone is not an authorization token.

## MCP capability model

The optional mcp-server Spring profile exposes a Streamable HTTP MCP server at /mcp. It is a transport adapter around the same application-owned, read-only capability layer.

### Tools

| MCP name | Responsibility | Mutates domain state? |
|---|---|---|
| lookup_order | Read order, customer reference, total and items | No |
| lookup_shipment_by_order | Read shipment status and tracking facts | No |
| lookup_payment_by_order | Read payment status and amounts | No |
| lookup_refunds_by_order | Read refund history | No |

The MCP adapter delegates to the same tool classes used by the chat agent; it does not maintain a second copy of business logic.

### Resources

- **knowledge://policies** describes the policy documents available to the MCP client.
- **knowledge://policy/{policyId}** reads one allow-listed Markdown policy source.
- Policy IDs map to fixed classpath resources. A caller-supplied URI segment is never concatenated into an arbitrary file path.
- Resource content is explicitly labelled demonstration policy and must be replaced/approved before production use.

### Prompt

**investigate-order** accepts a positive numeric orderId and produces a reusable read-only procedure. It tells the consuming model to call current lookup tools, distinguish evidence from inference, and avoid order/payment/refund mutations. The prompt provides instructions; it does not execute tool calls by itself.

The integration test and scripts/smoke-test-mcp.sh exercise the initialize handshake, tool discovery/invocation, resource discovery/read, resource-template discovery and prompt discovery/rendering. For the canonical protocol contract and settings, see [Spring AI MCP Server Annotations](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html) and [Spring AI MCP overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html).

**Not implemented yet:** remote MCP client orchestration, authenticated multi-tenant MCP access, policy-based tool authorization, human approval/resumption, mutation tools over MCP, resource subscriptions/notifications, and a durable agent execution event log. The MCP client starter is present as an option; the dependency alone does not mean a remote MCP client is configured.

## Persistence model

Flyway migrations in src/main/resources/db/migration own schema changes and seed records. Hibernate schema generation is validation-only.

Current persistence responsibilities include:
- customers and orders with item details;
- shipment lifecycle state;
- payments and refunds, including an idempotency key for refund requests;
- conversation metadata and status;
- Spring AI JDBC chat-memory rows;
- pgvector embeddings and source metadata when the rag profile is enabled.

The vector table stores searchable text chunks/embeddings and their metadata. The knowledge_sources table records each source's version/checksum and the ingested chunk count. Re-ingesting an identical source version/content can skip unnecessary embedding work.

Treat existing Flyway migrations as immutable once shared. Add a new numbered migration instead of editing one that has already run on a persistent database.

## RAG ingestion and retrieval

RAG is activated separately through the rag profile.

1. **KnowledgeCorpusLoader** reads configured classpath Markdown files.
2. **KnowledgeIngestionService** validates source identifiers, computes a SHA-256 checksum, splits source content into chunks and attaches source/version/chunk metadata.
3. The embedding model produces vectors in the dimension configured by the pgvector schema.
4. The vector store persists chunks; source bookkeeping is stored in knowledge_sources.
5. **KnowledgeRetrievalService** validates the query and applies configured top-K and similarity-threshold values.
6. **KnowledgeSearchTool** returns bounded excerpts and source metadata to the model.

The current example uses 1,536-dimensional embeddings and text-embedding-3-small defaults. A different embedding model must use the same dimensionality as the schema or introduce a deliberate schema/migration change. Chat-model and embedding-model configuration are independent: an Anthropic-compatible chat endpoint can coexist with an OpenAI-compatible embeddings endpoint.

The current policy documents are demonstration content. Before real deployment, replace them with approved policy, establish retention/versioning rules, test source replacement/deletion semantics, evaluate retrieval precision/recall, and check that answers cite only retrieved material. Retrieval scores alone do not prove that a passage supports the generated answer.

## Observability

- The servlet correlation filter accepts a valid X-Correlation-ID or creates one, echoes it in the response, and places it in the logging MDC for the request.
- MDC is cleaned after the request so pooled servlet threads do not leak a previous request's value.
- Agent request and tool-call timers/counters use bounded outcome and tool labels.
- The application does not use user input, conversation IDs, order IDs, payment references or tracking numbers as metric labels.
- Actuator exposes health and metrics endpoints. Prometheus scraping is available when the registry configuration is enabled.

Correlation IDs help connect log events; they are not a distributed trace. OpenTelemetry propagation/exporter deployment is a separate configuration concern and should be tested with the actual collector/exporter when tracing is enabled.

## Current security posture and explicit gaps

The implementation is a local-development POC, not a production security boundary.

Implemented protections include input validation at APIs, constrained read-only model tools, bounded tool output, domain validation for direct REST mutations, refund idempotency, configurable execution limits, a restrictive MCP default bind address, and validated request correlation IDs.

Before exposing it to real users, add or verify:
- authentication and authorization, including conversation ownership;
- secrets management and TLS at the deployment boundary;
- tenant-aware repository/service queries;
- rate limits for public endpoints and budget/usage controls for model calls;
- data minimization/redaction for prompts, logs and chat memory;
- a retention policy for conversation records and memory;
- policy/approval enforcement before any future mutation capability is exposed to an LLM or MCP client;
- durable idempotency across retries and restarts for every externally visible mutation;
- operational alerting and tested backup/restore procedures.

The demonstration policy corpus is not an authoritative refund/return policy. Do not use it to make real customer eligibility decisions.
