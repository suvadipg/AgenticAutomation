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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.automation.backend.publish.FlowPublicationService;
import com.google.adk.automation.engine.exec.FlowExecutor;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowDefinitionJsonCodec;
import com.google.adk.automation.persistence.service.FlowVersionService;
import com.google.adk.automation.sdk.PieceRegistry;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Flow CRUD and publish. Never accepts or returns a raw {@code FlowDefinition}/{@code StepConfig}
 * through Spring's default Jackson converter — see {@link FlowSummary}'s javadoc for why — so
 * {@link UpsertDraftRequest#definition} is typed as a generic {@link JsonNode} (Jackson's tree
 * model deserializes that regardless of bean-visibility rules) and re-serialized through the
 * injected default {@link ObjectMapper} before being handed to {@link FlowDefinitionJsonCodec},
 * which does understand the engine's model shape.
 */
@RestController
@RequestMapping("/api/flows")
public class FlowController {
  private final FlowVersionService flowVersionService;
  private final FlowPublicationService flowPublicationService;
  private final PieceRegistry pieceRegistry;
  private final ObjectMapper objectMapper;

  public FlowController(
      FlowVersionService flowVersionService,
      FlowPublicationService flowPublicationService,
      PieceRegistry pieceRegistry,
      ObjectMapper objectMapper) {
    this.flowVersionService = flowVersionService;
    this.flowPublicationService = flowPublicationService;
    this.pieceRegistry = pieceRegistry;
    this.objectMapper = objectMapper;
  }

  @PutMapping("/{flowId}/draft")
  public FlowSummary upsertDraft(
      @PathVariable String flowId, @RequestBody UpsertDraftRequest request) {
    FlowDefinition definition = parseDefinition(request.definition());
    flowVersionService.upsertDraft(flowId, request.name(), request.owner(), definition);
    return requireFlowSummary(flowId);
  }

  @PostMapping("/{flowId}/publish")
  public FlowPublicationService.PublishResult publish(@PathVariable String flowId) {
    try {
      return flowPublicationService.publish(flowId);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
    }
  }

  @GetMapping("/{flowId}")
  public FlowSummary get(@PathVariable String flowId) {
    return requireFlowSummary(flowId);
  }

  /**
   * Runs the flow's DRAFT definition in memory — no publish required, no {@code flow_runs} row
   * persisted — so the builder UI can let someone try a flow out while still editing it. Every
   * trigger-fired run (webhook/cron/manual via {@code FlowRunService}) always uses the LOCKED
   * version instead; this endpoint is the one deliberate exception.
   */
  @PostMapping("/{flowId}/test-run")
  public TestRunResult testRun(
      @PathVariable String flowId,
      @RequestBody(required = false) Map<String, Object> triggerPayload) {
    FlowDefinition draft;
    try {
      draft = flowVersionService.getDraftDefinition(flowId);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
    }
    Map<String, Object> payload = triggerPayload == null ? Map.of() : triggerPayload;
    return TestRunResult.of(new FlowExecutor(pieceRegistry).execute(draft, payload).blockingGet());
  }

  private FlowDefinition parseDefinition(JsonNode definitionNode) {
    try {
      return FlowDefinitionJsonCodec.fromJson(objectMapper.writeValueAsString(definitionNode));
    } catch (JsonProcessingException | IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid flow definition", e);
    }
  }

  private FlowSummary requireFlowSummary(String flowId) {
    return flowVersionService
        .findFlow(flowId)
        .map(FlowSummary::of)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such flow: " + flowId));
  }

  public record UpsertDraftRequest(String name, String owner, JsonNode definition) {}
}
