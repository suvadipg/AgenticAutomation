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

import com.google.adk.automation.engine.model.StepType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps each {@link StepType} to the {@link StepExecutor} that runs it. Populated in two phases by
 * {@code FlowExecutor}'s constructor (create the (empty) registry, construct each executor passing
 * the registry in — {@link LoopOnItemsStepExecutor} needs it to dispatch its loop body — then
 * {@link #register} each one) so {@code LOOP_ON_ITEMS} can dispatch back into the same registry,
 * including to itself for a nested loop.
 */
public final class StepExecutorRegistry {
  private final Map<StepType, StepExecutor> executors = new EnumMap<>(StepType.class);

  public StepExecutorRegistry register(StepType type, StepExecutor executor) {
    executors.put(type, executor);
    return this;
  }

  public StepExecutor executorFor(StepType type) {
    StepExecutor executor = executors.get(type);
    if (executor == null) {
      throw new IllegalStateException("No executor registered for step type: " + type);
    }
    return executor;
  }
}
