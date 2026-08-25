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

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.automation.engine.model.AiAgentStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import com.google.adk.automation.sdk.ActionDefinition;
import com.google.adk.automation.sdk.FlowPausedException;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.tools.BaseTool;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs an {@link AiAgentStepConfig} as a real ADK {@code LlmAgent} turn: builds the agent fresh
 * from the resolved instruction/model/tools, runs it through a {@code Runner} bound to the shared
 * per-{@code FlowRun} session (see {@link ExecutionContext}), and folds the final response text
 * into the step's output.
 *
 * <p><b>Not safe to run concurrently against the same session.</b> A fresh {@code Runner} is built
 * per invocation (below), and {@code Runner.runAsync}'s same-session serialization is scoped to one
 * {@code Runner} *instance* — it provides no protection across separate instances sharing one
 * {@code InMemorySessionService} session. Concurrent {@code AI_AGENT} steps against this executor's
 * shared session could hit {@code InMemorySessionService}'s read-copy-modify-write {@code
 * appendEvent} (silently dropping one side's history) — this is why {@code ParallelStepExecutor}
 * rejects {@code AI_AGENT} steps inside its concurrently-run branches rather than risk it.
 */
public final class AiAgentStepExecutor implements StepExecutor {
  private static final String SYSTEM_INSTRUCTION =
      "You are one step in an automated workflow. Complete the task described in the user "
          + "message, using the available tools if they help, and give a concise final answer.";

  private final ExpressionResolver expressionResolver;

  public AiAgentStepExecutor(ExpressionResolver expressionResolver) {
    this.expressionResolver = expressionResolver;
  }

  @Override
  public Single<StepResult> execute(StepConfig config, ExecutionContext context) {
    AiAgentStepConfig step = (AiAgentStepConfig) config;
    Instant startedAt = Instant.now();
    return Single.fromCallable(() -> runAgent(step, context, startedAt))
        .subscribeOn(Schedulers.io())
        .onErrorReturn(error -> toFailureOrPause(step, startedAt, error));
  }

  private StepResult runAgent(AiAgentStepConfig step, ExecutionContext context, Instant startedAt) {
    String resolvedInstruction =
        String.valueOf(expressionResolver.resolve(step.instruction(), context));
    Map<String, Object> stepInput = Map.of("instruction", resolvedInstruction);

    List<BaseTool> tools = new ArrayList<>();
    for (String qualifiedName : step.allowedTools()) {
      int dot = qualifiedName.indexOf('.');
      if (dot < 0) {
        throw new IllegalArgumentException(
            "Tool ref must be 'pieceId.actionName': " + qualifiedName);
      }
      String pieceId = qualifiedName.substring(0, dot);
      String actionName = qualifiedName.substring(dot + 1);
      ActionDefinition action =
          context
              .pieceRegistry()
              .getAction(pieceId, actionName)
              .orElseThrow(
                  () -> new IllegalStateException("No such piece action: " + qualifiedName));
      tools.add(new PieceActionTool(pieceId, action, null));
    }

    LlmAgent agent =
        LlmAgent.builder()
            .name(sanitize(step.id()))
            .instruction(SYSTEM_INSTRUCTION)
            .model(step.model())
            .tools(tools)
            .build();

    Runner runner =
        new Runner(
            agent,
            context.appName(),
            context.artifactService(),
            context.sessionService(),
            context.memoryService());

    Content userMessage =
        Content.builder()
            .role("user")
            .parts(ImmutableList.of(Part.builder().text(resolvedInstruction).build()))
            .build();

    List<Event> events =
        Lists.newArrayList(
            runner
                .runAsync(
                    context.userId(), context.flowRunId(), userMessage, RunConfig.builder().build())
                .blockingIterable());

    return StepResult.succeeded(
        step.id(), StepType.AI_AGENT, stepInput, extractOutput(events), startedAt);
  }

  private static Map<String, Object> extractOutput(List<Event> events) {
    StringBuilder text = new StringBuilder();
    for (Event event : events) {
      if (event.finalResponse() && event.content().isPresent()) {
        event
            .content()
            .get()
            .parts()
            .orElse(ImmutableList.of())
            .forEach(part -> part.text().ifPresent(text::append));
      }
    }
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("text", text.toString());
    return output;
  }

  private static String sanitize(String id) {
    return id.replaceAll("[^a-zA-Z0-9_]", "_");
  }

  private static StepResult toFailureOrPause(
      AiAgentStepConfig step, Instant startedAt, Throwable error) {
    Map<String, Object> stepInput = Map.of("instruction", step.instruction());
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof FlowPausedException paused) {
        return StepResult.paused(
            step.id(), StepType.AI_AGENT, stepInput, paused.metadata(), startedAt);
      }
    }
    return StepResult.failed(step.id(), StepType.AI_AGENT, stepInput, error, startedAt);
  }
}
