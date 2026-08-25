/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.automation.persistence.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.automation.engine.exec.FlowExecutor;
import com.google.adk.automation.engine.exec.FlowRunListener;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowRun;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import com.google.adk.automation.persistence.PersistenceJson;
import com.google.adk.automation.persistence.Transactions;
import com.google.adk.automation.persistence.entity.FlowRunEntity;
import com.google.adk.automation.persistence.entity.StepResultEntity;
import com.google.adk.automation.persistence.repository.FlowRunRepository;
import com.google.adk.automation.persistence.repository.StepResultRepository;
import com.google.adk.automation.sdk.PieceRegistry;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes a flow's {@link FlowVersionService#getLockedDefinition locked definition} through the
 * engine's {@link FlowExecutor} — trigger-fired runs (manual, cron, or webhook — see {@link #run})
 * always go through here, never the draft — persisting a {@code flow_runs} row up front (status
 * {@code RUNNING}) and one {@code step_results} row per {@link StepResult} as it happens (via
 * {@link FlowRunListener}), so an in-progress run is visible in storage the whole time, not just
 * once it finishes.
 */
public final class FlowRunService {
  private final EntityManagerFactory entityManagerFactory;
  private final FlowVersionService flowVersionService;
  private final PieceRegistry pieceRegistry;
  private final FlowRunRepository flowRunRepository = new FlowRunRepository();
  private final StepResultRepository stepResultRepository = new StepResultRepository();

  public FlowRunService(
      EntityManagerFactory entityManagerFactory,
      FlowVersionService flowVersionService,
      PieceRegistry pieceRegistry) {
    this.entityManagerFactory = entityManagerFactory;
    this.flowVersionService = flowVersionService;
    this.pieceRegistry = pieceRegistry;
  }

  /** Runs the flow's current LOCKED version, triggered manually, and waits for it to finish. */
  public FlowRun runManual(String flowId, Map<String, Object> triggerPayload) {
    return run(flowId, triggerPayload, FlowRunEntity.TriggerSource.MANUAL);
  }

  /**
   * Runs the flow's current LOCKED version and waits for it to finish, recording {@code
   * triggerSource} on the persisted {@code flow_runs} row. The entry point {@code SchedulerService}
   * (CRON) and {@code WebhookController}'s SYNC response mode (WEBHOOK) use.
   */
  public FlowRun run(
      String flowId,
      Map<String, Object> triggerPayload,
      FlowRunEntity.TriggerSource triggerSource) {
    return buildExecution(flowId, triggerPayload, triggerSource).blockingGet();
  }

  /**
   * Starts the run on a background thread and returns its id immediately, without waiting for it to
   * finish. Safe to return early because {@code FlowExecutor} calls {@link
   * FlowRunListener#onRunStarted} — which persists the {@code flow_runs} row — synchronously,
   * before any step runs; only actual step execution happens lazily once the returned {@code
   * Single} is subscribed. This is {@code WebhookController}'s IMMEDIATE response mode.
   */
  public String runAsync(
      String flowId,
      Map<String, Object> triggerPayload,
      FlowRunEntity.TriggerSource triggerSource) {
    AtomicReference<String> runIdHolder = new AtomicReference<>();
    Single<FlowRun> execution =
        buildExecution(flowId, triggerPayload, triggerSource, runIdHolder::set);
    execution.subscribeOn(Schedulers.io()).subscribe(run -> {}, error -> {});
    return runIdHolder.get();
  }

  /**
   * Resumes a run that's currently {@code PAUSED} at a resumable point (see {@link
   * FlowRunEntity#getPausedAtStepId}). Rehydrates the run's prior {@code step_results} rows back
   * into engine {@code StepResult}s (see {@link #toEngineStepResult}) so {@code {{steps.X.output}}}
   * references still work, then continues via {@link FlowExecutor#resume} against the *exact* flow
   * version the run originally started on (not necessarily still the flow's current locked version
   * — see {@link FlowVersionService#getDefinitionByVersionId}).
   *
   * @throws IllegalStateException if the run isn't {@code PAUSED}, or it paused at a point that
   *     isn't individually resumable — a pause originating inside a {@code LOOP_ON_ITEMS}/{@code
   *     PARALLEL} child's nested execution, whose specific identity isn't preserved once execution
   *     unwinds (only the outer composite step's {@code PAUSED} result is; see {@link
   *     #resumableStepId})
   */
  public FlowRun resume(String runId, Map<String, Object> resumePayload) {
    FlowRunEntity runEntity =
        findRun(runId).orElseThrow(() -> new IllegalStateException("No such run: " + runId));
    if (runEntity.getStatus() != FlowRunEntity.Status.PAUSED) {
      throw new IllegalStateException(
          "Run is not paused (status=" + runEntity.getStatus() + "): " + runId);
    }
    String pausedStepId = runEntity.getPausedAtStepId();
    if (pausedStepId == null) {
      throw new IllegalStateException(
          "Run "
              + runId
              + " paused inside a LOOP_ON_ITEMS/PARALLEL child step, which isn't individually"
              + " resumable yet.");
    }

    FlowDefinition definition =
        flowVersionService.getDefinitionByVersionId(runEntity.getFlowVersionId());
    Map<String, Object> triggerPayload =
        PersistenceJson.fromJson(
            runEntity.getTriggerPayloadJson(), new TypeReference<Map<String, Object>>() {});
    List<StepResult> priorHistory =
        getStepResults(runId).stream()
            .filter(entity -> !entity.getStepId().equals(pausedStepId))
            .map(FlowRunService::toEngineStepResult)
            .toList();

    FlowRunListener listener =
        new FlowRunListener() {
          @Override
          public void onStepCompleted(StepResult result) {
            Transactions.run(
                entityManagerFactory,
                entityManager -> {
                  stepResultRepository.insert(entityManager, toEntity(runId, result));
                  return null;
                });
          }
        };

    FlowRun run =
        new FlowExecutor(pieceRegistry)
            .resume(
                definition,
                runId,
                pausedStepId,
                priorHistory,
                triggerPayload,
                resumePayload,
                listener)
            .blockingGet();

    finalizeRun(run);
    return run;
  }

  public List<StepResultEntity> getStepResults(String flowRunId) {
    return Transactions.run(
        entityManagerFactory,
        entityManager -> stepResultRepository.findByFlowRunId(entityManager, flowRunId));
  }

  public java.util.Optional<FlowRunEntity> findRun(String flowRunId) {
    return Transactions.run(
        entityManagerFactory,
        entityManager -> flowRunRepository.findById(entityManager, flowRunId));
  }

  public List<FlowRunEntity> listRuns(String flowId) {
    return Transactions.run(
        entityManagerFactory,
        entityManager -> flowRunRepository.findByFlowId(entityManager, flowId));
  }

  private Single<FlowRun> buildExecution(
      String flowId,
      Map<String, Object> triggerPayload,
      FlowRunEntity.TriggerSource triggerSource) {
    return buildExecution(flowId, triggerPayload, triggerSource, runId -> {});
  }

  private Single<FlowRun> buildExecution(
      String flowId,
      Map<String, Object> triggerPayload,
      FlowRunEntity.TriggerSource triggerSource,
      java.util.function.Consumer<String> onRunIdMinted) {
    String flowVersionId = flowVersionService.getLockedVersionId(flowId);
    FlowDefinition definition = flowVersionService.getLockedDefinition(flowId);

    AtomicReference<String> runIdRef = new AtomicReference<>();
    FlowRunListener listener =
        new FlowRunListener() {
          @Override
          public void onRunStarted(String runId) {
            runIdRef.set(runId);
            Transactions.run(
                entityManagerFactory,
                entityManager -> {
                  flowRunRepository.insert(
                      entityManager,
                      new FlowRunEntity(
                          runId,
                          flowId,
                          flowVersionId,
                          triggerSource,
                          PersistenceJson.toJson(triggerPayload),
                          Instant.now()));
                  return null;
                });
            onRunIdMinted.accept(runId);
          }

          @Override
          public void onStepCompleted(StepResult result) {
            Transactions.run(
                entityManagerFactory,
                entityManager -> {
                  stepResultRepository.insert(entityManager, toEntity(runIdRef.get(), result));
                  return null;
                });
          }
        };

    return new FlowExecutor(pieceRegistry)
        .execute(definition, triggerPayload, Map.of(), listener)
        .doOnSuccess(this::finalizeRun)
        .doOnError(
            error -> {
              String runId = runIdRef.get();
              if (runId != null) {
                finalizeRunOnUnexpectedError(runId, error);
              }
            });
  }

  private void finalizeRun(FlowRun run) {
    Transactions.run(
        entityManagerFactory,
        entityManager -> {
          FlowRunEntity entity =
              flowRunRepository
                  .findById(entityManager, run.id())
                  .orElseThrow(
                      () -> new IllegalStateException("Missing flow_runs row: " + run.id()));
          entity.markFinished(
              toEntityStatus(run.status()),
              run.pauseMetadata() == null ? null : PersistenceJson.toJson(run.pauseMetadata()),
              resumableStepId(run),
              run.finishedAt());
          return null;
        });
  }

  /**
   * The step id a paused run can be resumed from, or {@code null} if unresumable. {@code
   * FlowExecutor} stops walking the instant a step pauses, so the *last* entry in {@code
   * run.stepResults()} is always the one that caused the pause — but only if it's a top-level
   * {@code PIECE}/{@code AI_AGENT} step is that id individually meaningful: a paused {@code
   * LOOP_ON_ITEMS}/{@code PARALLEL} result here represents the whole composite step, not the
   * specific nested child that actually paused (that child's identity isn't preserved anywhere once
   * execution unwinds) — see {@code FlowRunService.resume}'s javadoc.
   */
  private static String resumableStepId(FlowRun run) {
    if (run.status() != FlowRun.Status.PAUSED || run.stepResults().isEmpty()) {
      return null;
    }
    StepResult last = run.stepResults().get(run.stepResults().size() - 1);
    return (last.stepType() == StepType.PIECE || last.stepType() == StepType.AI_AGENT)
        ? last.stepId()
        : null;
  }

  private void finalizeRunOnUnexpectedError(String runId, Throwable error) {
    Transactions.run(
        entityManagerFactory,
        entityManager -> {
          flowRunRepository
              .findById(entityManager, runId)
              .ifPresent(
                  entity -> entity.markFinished(FlowRunEntity.Status.FAILED, null, Instant.now()));
          return null;
        });
  }

  private static StepResultEntity toEntity(String flowRunId, StepResult result) {
    return new StepResultEntity(
        UUID.randomUUID().toString(),
        flowRunId,
        result.stepId(),
        result.stepType().name(),
        StepResultEntity.Status.valueOf(result.status().name()),
        PersistenceJson.toJson(result.input()),
        PersistenceJson.toJson(result.output()),
        result.errorMessage(),
        result.iterationIndex(),
        result.startedAt(),
        result.finishedAt());
  }

  private static FlowRunEntity.Status toEntityStatus(FlowRun.Status status) {
    return switch (status) {
      case SUCCEEDED -> FlowRunEntity.Status.SUCCEEDED;
      case FAILED -> FlowRunEntity.Status.FAILED;
      case PAUSED -> FlowRunEntity.Status.PAUSED;
    };
  }

  /**
   * The reverse of {@link #toEntity} — used by {@link #resume} to rehydrate a paused run's prior
   * history back into an {@link com.google.adk.automation.engine.exec.ExecutionContext} so {@code
   * {{steps.X.output}}} references to already-completed steps still resolve after resuming. Only
   * handles {@code SUCCEEDED}/{@code FAILED} rows: {@code StepResultEntity} has no {@code
   * pauseMetadata} column (that lives on {@code FlowRunEntity}, one per run, not one per step), so
   * a {@code PAUSED} row can't be fully reconstructed — callers must filter those out first, which
   * {@link #resume} does (it handles the one paused row specially, not via rehydration).
   */
  private static StepResult toEngineStepResult(StepResultEntity entity) {
    StepType stepType = StepType.valueOf(entity.getStepType());
    Map<String, Object> input =
        PersistenceJson.fromJson(
            entity.getInputJson(), new TypeReference<Map<String, Object>>() {});
    Map<String, Object> output =
        PersistenceJson.fromJson(
            entity.getOutputJson(), new TypeReference<Map<String, Object>>() {});
    return switch (entity.getStatus()) {
      case SUCCEEDED ->
          StepResult.succeeded(entity.getStepId(), stepType, input, output, entity.getStartedAt());
      case FAILED ->
          StepResult.failed(
              entity.getStepId(),
              stepType,
              input,
              new RuntimeException(entity.getErrorMessage()),
              entity.getStartedAt());
      case PAUSED ->
          throw new IllegalStateException(
              "Cannot rehydrate a PAUSED step_results row (no persisted PauseMetadata to"
                  + " reconstruct it from): "
                  + entity.getStepId());
    };
  }
}
