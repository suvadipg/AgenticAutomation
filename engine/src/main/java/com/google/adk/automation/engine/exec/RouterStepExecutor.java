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

import com.google.adk.automation.engine.model.RouterStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Evaluates each {@link RouterStepConfig.Branch} condition in order and records the chosen next
 * step id under the {@code "nextStep"} output key (or omits it if nothing matched and there's no
 * default) — {@code FlowExecutor} reads that key instead of {@link StepConfig#nextStep()} to decide
 * where to go after a router step.
 */
public final class RouterStepExecutor implements StepExecutor {
  private final ExpressionResolver expressionResolver;

  public RouterStepExecutor(ExpressionResolver expressionResolver) {
    this.expressionResolver = expressionResolver;
  }

  @Override
  public Single<StepResult> execute(StepConfig config, ExecutionContext context) {
    RouterStepConfig step = (RouterStepConfig) config;
    Instant startedAt = Instant.now();
    return Single.fromCallable(
        () -> {
          String chosenNextStep = step.defaultNextStep();
          String matchedCondition = null;
          for (RouterStepConfig.Branch branch : step.branches()) {
            if (expressionResolver.evaluateCondition(branch.conditionExpression(), context)) {
              chosenNextStep = branch.nextStep();
              matchedCondition = branch.conditionExpression();
              break;
            }
          }

          Map<String, Object> output = new LinkedHashMap<>();
          if (chosenNextStep != null) {
            output.put("nextStep", chosenNextStep);
          }
          if (matchedCondition != null) {
            output.put("matchedCondition", matchedCondition);
          }
          return StepResult.succeeded(step.id(), StepType.ROUTER, Map.of(), output, startedAt);
        });
  }
}
