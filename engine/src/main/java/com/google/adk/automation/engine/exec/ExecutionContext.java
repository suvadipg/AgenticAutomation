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

import com.google.adk.artifacts.BaseArtifactService;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.sdk.Connection;
import com.google.adk.automation.sdk.PieceRegistry;
import com.google.adk.memory.BaseMemoryService;
import com.google.adk.memory.InMemoryMemoryService;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.InMemorySessionService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.MapContext;

/**
 * Everything one {@code FlowExecutor.execute} call threads through the step chain: the
 * trigger/step-output state that {@code ExpressionResolver} interpolates {@code {{...}}}
 * expressions against, plus the run-scoped ADK services ({@code SessionService}/{@code
 * ArtifactService}/{@code MemoryService}) that every {@code AI_AGENT} step's {@code Runner} shares
 * — all {@code AI_AGENT} steps within one flow run use the **same session id**, so conversation
 * state can carry across them.
 *
 * <p>{@code stepResultsById} and {@code history} are thread-safe ({@link ConcurrentHashMap} / a
 * synchronized list) because {@code PARALLEL} steps run their branches concurrently against this
 * same, shared context (see {@code ParallelStepExecutor}) — plain {@code HashMap}/{@code ArrayList}
 * would corrupt under concurrent {@code recordStepResult}/{@code appendToHistory} calls from
 * different branch threads. {@code extraBindings} is deliberately *not* made concurrent-safe the
 * same way: {@code loopItem}/{@code loopIndex} are mutated as shared, *sequential* scratch state by
 * {@code LoopOnItemsStepExecutor} — this is exactly why {@code ParallelStepExecutor} rejects {@code
 * LOOP_ON_ITEMS} (and {@code AI_AGENT}, for the shared-ADK- session reason described on {@code
 * AiAgentStepExecutor}) inside a branch rather than trying to make binding mutation
 * concurrency-safe too.
 */
public final class ExecutionContext {
  private static final String APP_NAME = "agentic-automation";
  private static final String USER_ID = "flow-run";

  private final String flowRunId;
  private final PieceRegistry pieceRegistry;
  private final ImmutableMap<String, Connection> connectionsById;
  private final ImmutableMap<String, Object> triggerPayload;
  private final Map<String, StepResult> stepResultsById = new ConcurrentHashMap<>();
  private final Map<String, Object> extraBindings = new LinkedHashMap<>();
  private final List<StepResult> history = Collections.synchronizedList(new ArrayList<>());
  private final FlowRunListener listener;

  private final BaseSessionService sessionService;
  private final BaseArtifactService artifactService;
  private final BaseMemoryService memoryService;

  public ExecutionContext(
      String flowRunId,
      PieceRegistry pieceRegistry,
      Map<String, Object> triggerPayload,
      Map<String, Connection> connectionsById) {
    this(flowRunId, pieceRegistry, triggerPayload, connectionsById, FlowRunListener.NO_OP);
  }

  public ExecutionContext(
      String flowRunId,
      PieceRegistry pieceRegistry,
      Map<String, Object> triggerPayload,
      Map<String, Connection> connectionsById,
      FlowRunListener listener) {
    this.flowRunId = flowRunId;
    this.pieceRegistry = pieceRegistry;
    this.triggerPayload = ImmutableMap.copyOf(triggerPayload);
    this.connectionsById = ImmutableMap.copyOf(connectionsById);
    this.listener = listener;
    this.sessionService = new InMemorySessionService();
    this.artifactService = new InMemoryArtifactService();
    this.memoryService = new InMemoryMemoryService();
    this.sessionService
        .createSession(APP_NAME, USER_ID, /* state= */ null, flowRunId)
        .blockingGet();
  }

  public String flowRunId() {
    return flowRunId;
  }

  public String appName() {
    return APP_NAME;
  }

  public String userId() {
    return USER_ID;
  }

  public PieceRegistry pieceRegistry() {
    return pieceRegistry;
  }

  public BaseSessionService sessionService() {
    return sessionService;
  }

  public BaseArtifactService artifactService() {
    return artifactService;
  }

  public BaseMemoryService memoryService() {
    return memoryService;
  }

  public ImmutableMap<String, Object> triggerPayload() {
    return triggerPayload;
  }

  public Optional<Connection> connection(String connectionId) {
    return Optional.ofNullable(connectionsById.get(connectionId));
  }

  /**
   * Updates the interpolation map only ({@code {{steps.stepId...}}} lookups) — not the run history.
   */
  public void recordStepResult(StepResult result) {
    stepResultsById.put(result.stepId(), result);
  }

  public Optional<StepResult> stepResult(String stepId) {
    return Optional.ofNullable(stepResultsById.get(stepId));
  }

  /**
   * Appends a result to the run's ordered history, independent of {@link #recordStepResult}. Call
   * this once per "real" step outcome that should show up in the final {@code FlowRun}: once for
   * every top-level step, and once per loop iteration's child step (tagged with its iteration
   * index) — but not for the intermediate, not-yet-tagged {@link #recordStepResult} calls a loop
   * body makes between its own steps purely for interpolation.
   */
  public void appendToHistory(StepResult result) {
    history.add(result);
    listener.onStepCompleted(result);
  }

  public ImmutableList<StepResult> history() {
    return ImmutableList.copyOf(history);
  }

  /**
   * Binds an extra top-level variable (e.g. {@code loopItem}/{@code loopIndex}) for expressions.
   */
  public void setBinding(String key, Object value) {
    extraBindings.put(key, value);
  }

  public void clearBinding(String key) {
    extraBindings.remove(key);
  }

  /**
   * Builds the JEXL variable scope for expression evaluation: {@code trigger.output.*}, {@code
   * steps.<id>.output.*} / {@code steps.<id>.status}, plus any extra bindings. JEXL resolves {@code
   * foo.bar} against a {@link Map} as {@code foo.get("bar")}, so nested nested maps work without
   * any custom property resolver.
   */
  public JexlContext toJexlContext() {
    Map<String, Object> root = new LinkedHashMap<>(extraBindings);
    root.put("trigger", Map.of("output", triggerPayload));

    Map<String, Object> steps = new LinkedHashMap<>();
    stepResultsById.forEach(
        (stepId, result) ->
            steps.put(stepId, Map.of("output", result.output(), "status", result.status().name())));
    root.put("steps", steps);

    return new MapContext(root);
  }
}
