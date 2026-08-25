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

import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import com.google.adk.automation.sdk.FlowPausedException;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.Map;

/**
 * Calls an {@link ActionDefinition} directly (no LLM round-trip): resolves {@code input} via {@link
 * ExpressionResolver}, invokes {@link ActionDefinition#execute}, and maps the outcome (success /
 * {@link FlowPausedException} / any other error) to a {@link StepResult}.
 */
public final class PieceStepExecutor implements StepExecutor {
  private final ExpressionResolver expressionResolver;

  public PieceStepExecutor(ExpressionResolver expressionResolver) {
    this.expressionResolver = expressionResolver;
  }

  @Override
  public Single<StepResult> execute(StepConfig config, ExecutionContext context) {
    PieceStepConfig step = (PieceStepConfig) config;
    Instant startedAt = Instant.now();
    Map<String, Object> resolvedInput = expressionResolver.resolveMap(step.input(), context);

    ActionDefinition action =
        context
            .pieceRegistry()
            .getAction(step.pieceId(), step.actionName())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No such piece action: " + step.pieceId() + "." + step.actionName()));

    ActionContext actionContext =
        new ActionContext(
            resolvedInput,
            step.connectionId() == null
                ? null
                : context.connection(step.connectionId()).orElse(null));

    return action
        .execute(actionContext)
        .map(
            output ->
                StepResult.succeeded(step.id(), StepType.PIECE, resolvedInput, output, startedAt))
        .onErrorReturn(
            error -> {
              if (error instanceof FlowPausedException paused) {
                return StepResult.paused(
                    step.id(), StepType.PIECE, resolvedInput, paused.metadata(), startedAt);
              }
              return StepResult.failed(step.id(), StepType.PIECE, resolvedInput, error, startedAt);
            });
  }
}
