# Agentic Automation

A low-code workflow automation engine — inspired by [activepieces](https://github.com/activepieces/activepieces)
(trigger → chained integration "pieces" → visual builder) — built on the
[Google Agent Development Kit (ADK) for Java](https://github.com/google/adk-java), where each
flow step is either a deterministic **piece** action or an AI-agent step backed by a real ADK
`LlmAgent`.

**This is an incubating, multi-module project, not a minimal single-file sample.** The rest of
`contrib/samples/` follows the convention described in [`contrib/README.md`](../../README.md) of
small, focused examples; this project is intentionally larger in scope (a full flow engine,
eventually a Postgres-backed backend and a visual builder frontend) and is expected to outgrow
`contrib/samples/` as later milestones land — see the full design/milestone plan referenced from
the project history for where this is headed.

## Status: Milestone 5

Core engine + piece SDK (M1), Postgres persistence and DRAFT/LOCKED flow versioning (M2), a REST
API + trigger subsystem (M3), a visual flow builder frontend (M4), and a concurrent `PARALLEL`
step type plus durable pause/resume for top-level steps (M5). What exists:

- **`pieces-sdk`** — the Java authoring API for "pieces" (typed, form-renderable `Property`-based
  actions/triggers — the Java analogue of activepieces' `createAction`/`createTrigger`).
  `PieceMetadata` is explicitly kept serializable through a *plain* Jackson `ObjectMapper` (not a
  specially configured one — see below), since it's meant to be returned directly by a REST
  controller.
- **`pieces-standard`** — three built-in pieces used to exercise the engine end to end: `http`
  (make an HTTP request), `delay` (pause the run), `code` (evaluate a JEXL expression).
- **`engine`** — the flow domain model (`StepType`: `PIECE` / `AI_AGENT` / `ROUTER` /
  `LOOP_ON_ITEMS` / `PARALLEL`) and `FlowExecutor`, which walks a flow's step chain, resolving
  `{{...}}` expressions between steps. The model classes are Jackson-annotated
  (`FlowDefinitionJsonCodec`) so a `FlowDefinition` round-trips to/from JSON for storage — but,
  unlike `PieceMetadata`, only through that codec's specially configured (field-visibility)
  `ObjectMapper`, not a plain default one; see "Two different Jackson configurations" below.
  `FlowExecutor` accepts an optional `FlowRunListener` so a caller can observe each `StepResult`
  as it happens, not just once the run finishes. `PARALLEL` (M5) runs its branches concurrently —
  see "Concurrency in PARALLEL steps" below for why branches are restricted to `PIECE`/`ROUTER`
  steps, and why `ExecutionContext`'s step-result map/history had to become thread-safe for it.
  `FlowExecutor.resume` (M5) continues a `PAUSED` run from a top-level `PIECE`/`AI_AGENT` step —
  see "Durable pause/resume" below.
- **`persistence`** — Postgres-backed storage: `flows` / `flow_versions` (DRAFT/LOCKED) /
  `flow_runs` / `step_results` / `connections` / `webhook_registrations` tables (Flyway
  `V1__init.sql`), plain JPA/Hibernate entities (no Spring dependency), `FlowVersionService`
  (draft upsert / publish / lock — a trigger-fired run always executes the LOCKED version, never
  the draft, even after further unpublished edits), `FlowRunService` (runs a flow's locked version
  through `FlowExecutor`, persisting a `flow_runs` row up front and one `step_results` row per
  step as it completes; `run` waits for completion, `runAsync` returns the run id immediately and
  continues in the background; `resume` rehydrates a paused run's prior history and continues it —
  see "Durable pause/resume" below), and `WebhookRegistrationService` (mints/looks up webhook
  tokens). Connection credentials are AES-GCM encrypted at rest (`CredentialCipher`).
- **`backend`** — a Spring Boot REST API + trigger subsystem: `FlowController` (draft/publish/get),
  `PieceCatalogController` (`GET /api/pieces`), `FlowRunController` (poll-based run status —
  `GET /api/flow-runs/{runId}`, `GET /api/flow-runs?flowId=...`, `POST
  /api/flow-runs/{runId}/resume`), `WebhookController` (`POST /api/webhooks/{flowId}/{token}`,
  SYNC or IMMEDIATE response mode), and `SchedulerService`
  (Spring `ThreadPoolTaskScheduler`-backed cron registration for published `CRON`-trigger flows).
  `FlowPublicationService` ties publish to trigger provisioning: publishing a `WEBHOOK`-trigger
  flow mints a token, publishing a `CRON`-trigger flow registers it with the scheduler.
  `FlowController` also exposes `POST /api/flows/{flowId}/test-run`, which runs the flow's DRAFT
  definition in memory (no publish required, no persisted `flow_runs` row) for the builder's
  "try it out while editing" flow — every trigger-fired run still always uses the LOCKED version.
- **`frontend`** — a React + TypeScript + Vite app using [React Flow](https://reactflow.dev/)
  (`@xyflow/react`) for the canvas: a piece palette (driven by `GET /api/pieces`, so a new piece
  never needs a frontend change), drag-to-connect wiring (including per-branch handles on router
  nodes), an auto-generated property panel (`PropertyField` switches on `PropertyType`, mirroring
  the backend's discriminator), Save Draft/Publish, and a run panel that polls for the latest
  triggered run, colors canvas nodes by step status, and offers a Resume button when the latest
  run is `PAUSED` (see "Durable pause/resume" below).

**Deliberately out of scope** (no concrete consumer yet, or real engine support missing): an
OAuth2/connections UI (none of the built-in pieces need auth), SSE/live run streaming (the run
status endpoint and the frontend's run panel are both poll-based), a cancel endpoint, resuming a
run paused inside a `LOOP_ON_ITEMS`/`PARALLEL` child (only top-level `PIECE`/`AI_AGENT` pauses are
resumable — see "Durable pause/resume" below), loading an existing flow into the
builder (the frontend is session-local — Save Draft pushes local state to the backend, but nothing
pulls a flow back in), and rendering `LOOP_ON_ITEMS` bodies as nested canvas nodes (the property
panel has a compact list editor for loop bodies instead — see `flowDraft.ts`'s module doc).

### Two different Jackson configurations, on purpose

- `FlowDefinitionJsonCodec` (in `engine`) uses a specially configured `ObjectMapper`
  (field-visible, getters off) because the engine's execution-model classes
  (`StepConfig`/`FlowDefinition`/etc.) use non-bean-style accessors like `id()`, not `getId()`.
  **Never** pass a `FlowDefinition` through a plain/default `ObjectMapper` (e.g. Spring's
  auto-configured one) — it will silently serialize to `{}`. `FlowController` works around this by
  typing its request DTO's definition field as a generic `JsonNode` and routing it through the
  codec explicitly; see that class's javadoc.
- `PieceMetadata` (in `pieces-sdk`) is the opposite: it's meant to be returned directly by a REST
  controller, so its getters carry explicit `@JsonProperty` annotations and it serializes
  correctly through a *plain* `ObjectMapper` — see `PieceMetadataTest`, which specifically guards
  against a regression here (a first attempt at this class had the same silent-`{}` bug the
  engine's model has, before those annotations were added).

### Concurrency in PARALLEL steps

`ParallelStepExecutor` runs each branch of a `PARALLEL` step on its own thread (subscribed on
RxJava's `Schedulers.io()`) and waits for all of them via `Single.zip` — genuine concurrency,
verified in `ParallelStepExecutorTest` by asserting two branches' execution *time windows*
actually overlap, not just that both eventually ran. Two consequences worth knowing before
extending this:

- `ExecutionContext`'s step-result map and history list are deliberately `ConcurrentHashMap` /
  a synchronized list, not plain `HashMap`/`ArrayList` — concurrent branches both call
  `recordStepResult`/`appendToHistory` on the *same* shared context.
- A `PARALLEL` branch may only contain `PIECE`/`ROUTER` steps — `AI_AGENT` and `LOOP_ON_ITEMS`
  (and nested `PARALLEL`) are rejected up front, before any branch runs, with a clear error. This
  isn't an arbitrary restriction: investigating it surfaced a real, previously-undocumented hazard
  in ADK's `Runner`/`InMemorySessionService` — same-session serialization in `Runner.runAsync` is
  scoped to one `Runner` *instance*, and `AiAgentStepExecutor` builds a fresh `Runner` per step, so
  two concurrent `AI_AGENT` steps sharing one flow-run session would have no protection against
  `InMemorySessionService`'s read-copy-modify-write `appendEvent` silently dropping one side's
  history. `LOOP_ON_ITEMS` is excluded for a simpler reason: it mutates the shared
  `loopItem`/`loopIndex` bindings as sequential scratch state, which two concurrent loops would
  stomp on. See `ParallelStepConfig` and `AiAgentStepExecutor`'s javadoc for the full detail.

### Durable pause/resume

A `PIECE`/`AI_AGENT` step can pause a run mid-flow (e.g. the built-in `delay` piece's `wait`
action) by calling `ActionContext.pause(PauseMetadata)`. `FlowRunService` (in `persistence`)
persists the paused step's id onto the `flow_runs` row (`paused_at_step_id`) whenever the pause
originated at a top-level `PIECE`/`AI_AGENT` step, and `POST /api/flow-runs/{runId}/resume`
(optionally with a JSON body — e.g. `{"approved": true}` for a human-approval gate) resumes it:
`FlowRunService.resume` loads the run's *original* flow version (via
`FlowVersionService.getDefinitionByVersionId`, not `getLockedDefinition` — if the flow was
republished while the run sat paused, resuming must still continue against the exact version the
run started on), rehydrates its prior `step_results` rows back into engine `StepResult`s so
`{{steps.X.output}}` references to already-completed steps keep resolving, then calls
`FlowExecutor.resume`, which marks the paused step `SUCCEEDED` with the resume payload as its
output — **not** re-executed, since resuming a delay means "the wait is over," not "run the delay
again" — and continues walking from that step's `nextStep()`.

Only a top-level `PIECE`/`AI_AGENT` step is a valid resume point; resuming is rejected (409 from
the REST endpoint, `IllegalStateException` from `FlowRunService.resume`) for a run that isn't
`PAUSED`, or one that paused inside a `LOOP_ON_ITEMS`/`PARALLEL` child. That's not an arbitrary
restriction: `FlowExecutor.walk` only records the *outer* composite step's `PAUSED` `StepResult`
into a run's persisted history — the specific nested child that actually paused has no identity
left once execution unwinds out of `LoopOnItemsStepExecutor`/`ParallelStepExecutor`, so there's
nothing to resume *from*. `FlowRunServiceResumeTest` and `FlowExecutorResumeTest` cover both the
happy path (pause → persist → resume → complete, with prior step outputs still resolving) and this
rejection. Also note `FlowExecutor.resume` builds a *fresh* `ExecutionContext` per call, so an
`AI_AGENT` step's ADK session/conversation history doesn't survive across a resume —
`InMemorySessionService` doesn't persist between separate `resume` calls (or a process restart); a
durable `BaseSessionService` backend would be needed for real conversation continuity across a
pause.

While building this, resuming inside a loop turned up a real, unrelated pre-existing bug:
`LoopOnItemsStepExecutor` checked `itemsExpression`'s evaluated result with `instanceof List`, but
a JEXL bracket literal (`['a', 'b']`) actually evaluates to a Java array (typed, e.g. `String[]`,
or primitive, e.g. `int[]`, never a `List`) — so every array-literal `itemsExpression` was silently
rejected as `LOOP_ON_ITEMS`'s items, which the M1 `FlowExecutorTest` never caught because its loop
step sits after an `AI_AGENT` step that always fails first without live model credentials. Fixed by
having `LoopOnItemsStepExecutor` accept any Java array too (via reflection), not just values that
already happen to be a `List`.

### Running the tests that need external services

`FlowVersionServiceTest`/`FlowRunServiceTest`/`FlowRunServiceResumeTest` (`persistence`) and
`WebhookIntegrationTest`/`SchedulerIntegrationTest` (`backend`) use Testcontainers to spin up a
real Postgres, so they require Docker running locally:

```shell
./mvnw -pl contrib/samples/AgenticAutomation/persistence -am test
./mvnw -pl contrib/samples/AgenticAutomation/backend -am test
```

For a Postgres instance that survives between runs (e.g. for manual poking-around with `psql`,
not needed by the tests themselves), see `docker-compose.yml` in this directory.

### Running the backend for real

```shell
docker compose up -d   # local Postgres
./mvnw -pl contrib/samples/AgenticAutomation/backend -am spring-boot:run
```

Then, e.g.: `curl -X PUT localhost:8081/api/flows/my-flow/draft -H 'Content-Type: application/json' -d '{"name":"My Flow","owner":"me","definition":{...}}'` followed by
`curl -X POST localhost:8081/api/flows/my-flow/publish`.

## Running the frontend

```shell
cd contrib/samples/AgenticAutomation/frontend
npm install
npm run dev      # Vite dev server on :5173, proxies /api/* to the backend on :8081 — see vite.config.ts
npm test         # vitest — pure flowDraft graph-logic tests, no backend/browser needed
npm run build    # tsc -b && vite build — type-checks and bundles
```

The backend (and, transitively, Docker for its Postgres) needs to be running for the piece
palette and any save/publish/run action to actually work — with no backend reachable, the app
still renders (toolbar, empty canvas with just the trigger node, palette showing "Loading
pieces…", run panel showing its empty states), which is what was verified in this environment.

### Two different "flow state" representations, on purpose

`FlowDraft` (`state/flowDraft.ts`) is the single source of truth the whole builder edits — a
step's `nextStep`/`branches`/`defaultNextStep` fields *are* the wiring. React Flow's nodes/edges
are never held as independent state; `toReactFlow` derives them fresh from `FlowDraft` every
render, and `FlowCanvas` translates gestures (drag a connection, delete an edge) back into
`FlowDraft` updates (`connectSteps`, `disconnectEdge`) rather than mutating edges directly. This
avoids the classic "nodes and edges drift out of sync with the real model" bug class in
graph-editor UIs.

## The ADK-specific design point

An `ActionDefinition`'s `execute(ActionContext)` method is deliberately shaped to match
`com.google.adk.tools.BaseTool#runAsync(Map, ToolContext)`. That lets `PieceActionTool` (in
`engine`) wrap any piece action as a real ADK tool with almost no adapter code — so the exact same
action can be called directly by the deterministic engine for a `PIECE` step, **and** handed to an
`LlmAgent` as a function-calling tool for an `AI_AGENT` step, with zero duplicated logic.

## Running the demo

```shell
./mvnw -pl contrib/samples/AgenticAutomation/pieces-sdk,contrib/samples/AgenticAutomation/pieces-standard,contrib/samples/AgenticAutomation/engine -am test
mvn -pl contrib/samples/AgenticAutomation/engine exec:java
```

The demo flow (`RunSampleFlowCli`) includes an `AI_AGENT` step, so running it (and the engine's
`FlowExecutorTest`/`PieceActionToolTest`) requires model credentials configured — the same
`GOOGLE_API_KEY` (or Vertex AI) setup any ADK sample using `LlmAgent` needs.
