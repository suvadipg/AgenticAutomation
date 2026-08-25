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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Response DTO for {@code GET /api/flow-runs/{runId}} — see {@link FlowRunController}. */
public record FlowRunSummary(
    String id,
    String flowId,
    String flowVersionId,
    String status,
    String triggerSource,
    List<StepResultSummary> steps) {
  public record StepResultSummary(
      String stepId,
      String stepType,
      String status,
      @Nullable String errorMessage,
      @Nullable Integer iterationIndex,
      JsonNode input,
      JsonNode output) {}
}
