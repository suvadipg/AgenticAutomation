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

import com.google.adk.automation.engine.model.StepResult;

/**
 * Callback invoked once per {@link StepResult} as a {@code FlowExecutor} run produces it — covering
 * both top-level steps and, tagged with an iteration index, each loop-body child (see {@link
 * ExecutionContext#appendToHistory}, the single call site for both).
 *
 * <p>M1 has no listener (the in-memory {@code FlowRun} returned at the end already carries the full
 * history). This hook exists for the persistence module (M2), whose {@code FlowRunService}
 * implements it to write each {@code StepResult} to the {@code step_results} table as it happens,
 * rather than only once the whole run finishes — which is what lets a future UI (M4) show live,
 * in-progress run status instead of only completed ones.
 */
@FunctionalInterface
public interface FlowRunListener {
  FlowRunListener NO_OP = result -> {};

  void onStepCompleted(StepResult result);

  /** Called once, before the first step executes, with the run id {@code FlowExecutor} minted. */
  default void onRunStarted(String runId) {}
}
