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

package com.google.adk.automation.engine.exec;

import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowRun;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import com.google.adk.automation.sdk.Connection;
import com.google.adk.automation.sdk.PieceRegistry;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Walks a {@link FlowDefinition}'s step chain from its trigger's first step, dispatching each
 * {@link StepConfig} to the matching {@link StepExecutor} and threading one {@link
 * ExecutionContext} through the whole run. This is the top-level entry point for M1: no
 * persistence, no scheduler/webhook — a caller (the CLI, or a test) supplies the trigger payload
 * directly.
 *
 * <p>The walk is a recursive, non-blocking {@code flatMap} chain rather than a loop with a blocking
 * wait between steps, so a single flow run never ties up a thread between steps.
 */
public final class FlowExecutor {
  private final PieceRegistry pieceRegistry;
  private final StepExecutorRegistry stepExecutorRegistry = new StepExecutorRegistry();

  public FlowExecutor(PieceRegistry pieceRegistry) {
    this.pieceRegistry = pieceRegistry;
    ExpressionResolver expressionResolver = new ExpressionResolver();
    stepExecutorRegistry
        .register(StepType.PIECE, new PieceStepExecutor(expressionResolver))
        .register(StepType.AI_AGENT, new AiAgentStepExecutor(expressionResolver))
        .register(StepType.ROUTER, new RouterStepExecutor(expressionResolver))
        .register(
            StepType.LOOP_ON_ITEMS,
            new LoopOnItemsStepExecutor(expressionResolver, stepExecutorRegistry))
        .register(StepType.PARALLEL, new ParallelStepExecutor(stepExecutorRegistry));
  }

  public Single<FlowRun> execute(FlowDefinition flow, Map<String, Object> triggerPayload) {
    return execute(flow, triggerPayload, Map.of(), FlowRunListener.NO_OP);
  }

  public Single<FlowRun> execute(
      FlowDefinition flow,
      Map<String, Object> triggerPayload,
      Map<String, Connection> connections) {
    return execute(flow, triggerPayload, connections, FlowRunListener.NO_OP);
  }

  /**
   * Same as {@link #execute(FlowDefinition, Map, Map)}, but reports the run id and each {@link
   * StepResult} to {@code listener} as they happen — this is the hook the persistence module's
   * {@code FlowRunService} uses to persist the run incrementally instead of only once it finishes.
   */
  public Single<FlowRun> execute(
      FlowDefinition flow,
      Map<String, Object> triggerPayload,
      Map<String, Connection> connections,
      FlowRunListener listener) {
    String runId = UUID.randomUUID().toString();
    listener.onRunStarted(runId);
    ExecutionContext context =
        new ExecutionContext(runId, pieceRegistry, triggerPayload, connections, listener);
    Instant startedAt = Instant.now();
    return walk(flow, flow.trigger().firstStepId(), context, runId, triggerPayload, startedAt);
  }

  /**
   * Resumes a previously {@code PAUSED} run: treats {@code pausedStepId} as now-satisfied (records
   * it as {@code SUCCEEDED} with {@code resumeOutput}, *not* re-executed — resuming a delay means
   * "the wait is over," not "run the delay again"), rehydrates {@code priorHistory} so {@code
   * {{steps.X.output}}} references to already-completed steps still resolve, and continues walking
   * from {@code pausedStepId}'s {@link StepConfig#nextStep()}.
   *
   * <p>Only a top-level {@code PIECE}/{@code AI_AGENT} step is a valid resume point: a {@code
   * ROUTER}'s {@link StepConfig#nextStep()} is unused (its dispatch depends on output a resume
   * can't reconstruct), and a paused {@code LOOP_ON_ITEMS}/{@code PARALLEL} result represents the
   * whole composite step, not the specific nested child that actually paused — that child's
   * identity isn't preserved once execution unwinds (see {@code FlowRunService.resume}'s javadoc
   * for the full reasoning). Both throw {@link IllegalArgumentException}.
   *
   * <p>Also note: this builds a *fresh* {@link ExecutionContext}, so any {@code AI_AGENT} step
   * before the pause loses its ADK session/conversation history across the resume — {@code
   * InMemorySessionService} doesn't survive between separate {@code resume} calls (or a process
   * restart). A later milestone wanting real conversation continuity across a pause would need a
   * durable {@code BaseSessionService} backend, not an in-memory one.
   */
  public Single<FlowRun> resume(
      FlowDefinition flow,
      String runId,
      String pausedStepId,
      List<StepResult> priorHistory,
      Map<String, Object> triggerPayload,
      Map<String, Object> resumeOutput,
      FlowRunListener listener) {
    StepConfig pausedStep =
        flow.step(pausedStepId)
            .orElseThrow(() -> new IllegalStateException("No such step: " + pausedStepId));
    if (pausedStep.type() != StepType.PIECE && pausedStep.type() != StepType.AI_AGENT) {
      throw new IllegalArgumentException(
          "Cannot resume at a "
              + pausedStep.type()
              + " step ('"
              + pausedStepId
              + "') — only PIECE/AI_AGENT steps are valid resume points.");
    }

    ExecutionContext context =
        new ExecutionContext(runId, pieceRegistry, triggerPayload, Map.of(), listener);
    for (StepResult prior : priorHistory) {
      context.recordStepResult(prior);
    }

    Instant startedAt = Instant.now();
    StepResult resumedResult =
        StepResult.succeeded(pausedStepId, pausedStep.type(), Map.of(), resumeOutput, startedAt);
    context.recordStepResult(resumedResult);
    context.appendToHistory(resumedResult);

    return walk(flow, pausedStep.nextStep(), context, runId, triggerPayload, startedAt);
  }

  private Single<FlowRun> walk(
      FlowDefinition flow,
      @Nullable String stepId,
      ExecutionContext context,
      String runId,
      Map<String, Object> triggerPayload,
      Instant startedAt) {
    if (stepId == null) {
      return Single.just(
          FlowRun.succeeded(runId, flow.id(), triggerPayload, context.history(), startedAt));
    }

    StepConfig step =
        flow.step(stepId).orElseThrow(() -> new IllegalStateException("No such step: " + stepId));
    StepExecutor executor = stepExecutorRegistry.executorFor(step.type());

    return executor
        .execute(step, context)
        .flatMap(
            result -> {
              context.recordStepResult(result);
              context.appendToHistory(result);

              if (result.status() == StepResult.Status.PAUSED) {
                return Single.just(
                    FlowRun.paused(
                        runId,
                        flow.id(),
                        triggerPayload,
                        context.history(),
                        result.pauseMetadata(),
                        startedAt));
              }
              if (result.status() == StepResult.Status.FAILED) {
                return Single.just(
                    FlowRun.failed(runId, flow.id(), triggerPayload, context.history(), startedAt));
              }

              return walk(
                  flow, resolveNextStepId(step, result), context, runId, triggerPayload, startedAt);
            });
  }

  private static @Nullable String resolveNextStepId(StepConfig step, StepResult result) {
    if (step.type() == StepType.ROUTER) {
      Object next = result.output().get("nextStep");
      return next == null ? null : String.valueOf(next);
    }
    return step.nextStep();
  }
}
