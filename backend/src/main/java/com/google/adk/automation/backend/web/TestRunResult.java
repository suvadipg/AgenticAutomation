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

package com.google.adk.automation.backend.web;

import com.google.adk.automation.engine.model.FlowRun;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Response DTO for {@code POST /api/flows/{flowId}/test-run} — runs the flow's DRAFT definition in
 * memory (see {@code FlowController}), with no persisted {@code flow_runs} row behind it. Unlike
 * {@code FlowSummary}/{@code FlowRunSummary} (which wrap persistence entities), this wraps the
 * engine's {@code FlowRun}/{@code StepResult} directly — safe here because every field below is a
 * plain type (String/Map/Integer) or a record, not one of the engine's non-bean-getter classes, so
 * Jackson's default (de)serialization handles it without needing {@code FlowDefinitionJsonCodec}'s
 * special mapper.
 */
public record TestRunResult(String status, List<StepResultView> steps) {
  public static TestRunResult of(FlowRun run) {
    List<StepResultView> steps = run.stepResults().stream().map(StepResultView::of).toList();
    return new TestRunResult(run.status().name(), steps);
  }

  public record StepResultView(
      String stepId,
      String stepType,
      String status,
      @Nullable String errorMessage,
      @Nullable Integer iterationIndex,
      Map<String, Object> input,
      Map<String, Object> output) {
    static StepResultView of(com.google.adk.automation.engine.model.StepResult result) {
      return new StepResultView(
          result.stepId(),
          result.stepType().name(),
          result.status().name(),
          result.errorMessage(),
          result.iterationIndex(),
          result.input(),
          result.output());
    }
  }
}
