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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.automation.backend.web.FlowRunSummary.StepResultSummary;
import com.google.adk.automation.persistence.entity.FlowRunEntity;
import com.google.adk.automation.persistence.entity.StepResultEntity;
import com.google.adk.automation.persistence.service.FlowRunService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only run status: {@code GET /api/flow-runs/{runId}}. Poll-based — no SSE/live streaming in
 * M3 (see the module README for what's deferred).
 */
@RestController
@RequestMapping("/api/flow-runs")
public class FlowRunController {
  private final FlowRunService flowRunService;
  private final ObjectMapper objectMapper;

  public FlowRunController(FlowRunService flowRunService, ObjectMapper objectMapper) {
    this.flowRunService = flowRunService;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public java.util.List<FlowRunSummary> listByFlow(@RequestParam String flowId) {
    return flowRunService.listRuns(flowId).stream().map(run -> get(run.getId())).toList();
  }

  @GetMapping("/{runId}")
  public FlowRunSummary get(@PathVariable String runId) {
    FlowRunEntity run =
        flowRunService
            .findRun(runId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such run: " + runId));

    return new FlowRunSummary(
        run.getId(),
        run.getFlowId(),
        run.getFlowVersionId(),
        run.getStatus().name(),
        run.getTriggerSource().name(),
        flowRunService.getStepResults(runId).stream().map(this::toSummary).toList());
  }

  /**
   * Resumes a {@code PAUSED} run at the top-level {@code PIECE}/{@code AI_AGENT} step it paused at.
   * Rejects (409) a run that isn't paused, or that paused inside a {@code LOOP_ON_ITEMS}/ {@code
   * PARALLEL} child — see {@code FlowRunService.resume}'s javadoc for why that case isn't resumable
   * yet.
   */
  @PostMapping("/{runId}/resume")
  public FlowRunSummary resume(
      @PathVariable String runId, @RequestBody(required = false) Map<String, Object> payload) {
    try {
      flowRunService.resume(runId, payload == null ? Map.of() : payload);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
    }
    return get(runId);
  }

  private StepResultSummary toSummary(StepResultEntity result) {
    try {
      return new StepResultSummary(
          result.getStepId(),
          result.getStepType(),
          result.getStatus().name(),
          result.getErrorMessage(),
          result.getIterationIndex(),
          objectMapper.readTree(result.getInputJson()),
          objectMapper.readTree(result.getOutputJson()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Corrupt stored JSON for step " + result.getStepId(), e);
    }
  }
}
