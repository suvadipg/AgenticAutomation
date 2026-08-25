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

import com.google.adk.automation.engine.model.LoopOnItemsStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import com.google.common.collect.ImmutableList;
import io.reactivex.rxjava3.core.Single;
import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Resolves {@link LoopOnItemsStepConfig#itemsExpression()} to a {@link List} and, sequentially for
 * each item, runs {@link LoopOnItemsStepConfig#loopBody()} (a plain ordered sequence — see that
 * class's javadoc for why nested chains/routers in a loop body are out of scope for M1), with
 * {@code {{loopItem}}}/{@code {{loopIndex}}} bound for the duration of that iteration. Every body
 * {@link StepResult} is tagged with its iteration index and folded into the loop step's own result;
 * a failure or pause in any iteration's body short-circuits the whole loop.
 */
public final class LoopOnItemsStepExecutor implements StepExecutor {
  private final ExpressionResolver expressionResolver;
  private final StepExecutorRegistry registry;

  public LoopOnItemsStepExecutor(
      ExpressionResolver expressionResolver, StepExecutorRegistry registry) {
    this.expressionResolver = expressionResolver;
    this.registry = registry;
  }

  @Override
  public Single<StepResult> execute(StepConfig config, ExecutionContext context) {
    LoopOnItemsStepConfig step = (LoopOnItemsStepConfig) config;
    Instant startedAt = Instant.now();

    Object itemsRaw = expressionResolver.evaluate(step.itemsExpression(), context);
    List<?> items = asList(itemsRaw);
    if (items == null) {
      return Single.just(
          StepResult.failed(
              step.id(),
              StepType.LOOP_ON_ITEMS,
              Map.of(),
              new IllegalStateException("itemsExpression did not evaluate to a List: " + itemsRaw),
              startedAt));
    }

    return runIterations(step, items, 0, context, new ArrayList<>(), startedAt);
  }

  /**
   * A JEXL bracket literal (e.g. {@code ['a', 'b']}) evaluates to a Java array — a typed array
   * ({@code String[]}) for homogeneous elements, a primitive array ({@code int[]}) for numeric
   * literals, or {@code Comparable[]} for mixed types — never a {@link List}, so {@code
   * itemsExpression} must accept arrays too, not just values that already happen to be a {@link
   * List} (e.g. one threaded through from {@code {{trigger.output.items}}}).
   */
  private static @Nullable List<?> asList(Object itemsRaw) {
    if (itemsRaw instanceof List<?> list) {
      return list;
    }
    if (itemsRaw != null && itemsRaw.getClass().isArray()) {
      int length = Array.getLength(itemsRaw);
      List<Object> list = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        list.add(Array.get(itemsRaw, i));
      }
      return list;
    }
    return null;
  }

  private Single<StepResult> runIterations(
      LoopOnItemsStepConfig step,
      List<?> items,
      int index,
      ExecutionContext context,
      List<StepResult> childResults,
      Instant startedAt) {
    if (index >= items.size()) {
      Map<String, Object> output = new LinkedHashMap<>();
      output.put("iterationCount", childResults.size());
      return Single.just(
          StepResult.succeeded(step.id(), StepType.LOOP_ON_ITEMS, Map.of(), output, startedAt));
    }

    context.setBinding("loopItem", items.get(index));
    context.setBinding("loopIndex", index);

    return runBody(step.loopBody(), 0, context)
        .flatMap(
            bodyResults -> {
              context.clearBinding("loopItem");
              context.clearBinding("loopIndex");

              for (StepResult bodyResult : bodyResults) {
                StepResult tagged = bodyResult.withIterationIndex(index);
                childResults.add(tagged);
                context.recordStepResult(tagged);
                context.appendToHistory(tagged);

                if (tagged.status() == StepResult.Status.PAUSED) {
                  return Single.just(
                      StepResult.paused(
                          step.id(),
                          StepType.LOOP_ON_ITEMS,
                          Map.of(),
                          tagged.pauseMetadata(),
                          startedAt));
                }
                if (tagged.status() == StepResult.Status.FAILED) {
                  return Single.just(
                      StepResult.failed(
                          step.id(),
                          StepType.LOOP_ON_ITEMS,
                          Map.of(),
                          new RuntimeException(
                              "Iteration "
                                  + index
                                  + " step '"
                                  + tagged.stepId()
                                  + "' failed: "
                                  + tagged.errorMessage()),
                          startedAt));
                }
              }

              return runIterations(step, items, index + 1, context, childResults, startedAt);
            });
  }

  /** Runs {@code body} in order, recording each step's result so later body steps can see it. */
  private Single<List<StepResult>> runBody(
      ImmutableList<StepConfig> body, int index, ExecutionContext context) {
    if (index >= body.size()) {
      return Single.just(new ArrayList<>());
    }
    StepConfig bodyStep = body.get(index);
    StepExecutor executor = registry.executorFor(bodyStep.type());
    return executor
        .execute(bodyStep, context)
        .flatMap(
            result -> {
              if (result.status() != StepResult.Status.SUCCEEDED) {
                List<StepResult> onlyThis = new ArrayList<>();
                onlyThis.add(result);
                return Single.just(onlyThis);
              }
              context.recordStepResult(result);
              return runBody(body, index + 1, context)
                  .map(
                      rest -> {
                        List<StepResult> all = new ArrayList<>();
                        all.add(result);
                        all.addAll(rest);
                        return all;
                      });
            });
  }
}
