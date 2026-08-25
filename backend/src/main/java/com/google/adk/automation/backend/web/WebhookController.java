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
import com.google.adk.automation.persistence.entity.FlowRunEntity;
import com.google.adk.automation.persistence.entity.WebhookRegistrationEntity;
import com.google.adk.automation.persistence.service.FlowRunService;
import com.google.adk.automation.persistence.service.WebhookRegistrationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The public HTTP entry point for {@code WEBHOOK}-trigger flows: {@code POST
 * /api/webhooks/{flowId}/{token}}. The token is minted by {@code WebhookRegistrationService} when
 * the flow is published (see {@code FlowPublicationService}) and rotates on every republish, so a
 * superseded URL stops working.
 *
 * <p>Response mode (set at publish time, currently always {@code SYNC} — see {@code
 * FlowPublicationService}) decides whether this waits for the run to finish ({@code SYNC}) or
 * returns 202 with the run id immediately while the run continues in the background ({@code
 * IMMEDIATE}).
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
  private final WebhookRegistrationService webhookRegistrationService;
  private final FlowRunService flowRunService;

  public WebhookController(
      WebhookRegistrationService webhookRegistrationService, FlowRunService flowRunService) {
    this.webhookRegistrationService = webhookRegistrationService;
    this.flowRunService = flowRunService;
  }

  @PostMapping("/{flowId}/{token}")
  public ResponseEntity<Map<String, Object>> trigger(
      @PathVariable String flowId,
      @PathVariable String token,
      @RequestBody(required = false) Map<String, Object> payload) {
    WebhookRegistrationEntity registration =
        webhookRegistrationService
            .findByToken(token)
            .filter(r -> r.getFlowId().equals(flowId))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active webhook for this flow/token"));

    Map<String, Object> triggerPayload = payload == null ? Map.of() : payload;

    if (registration.getResponseMode() == WebhookRegistrationEntity.ResponseMode.IMMEDIATE) {
      String runId =
          flowRunService.runAsync(flowId, triggerPayload, FlowRunEntity.TriggerSource.WEBHOOK);
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(Map.of("runId", runId, "status", "RUNNING"));
    }

    FlowRun run = flowRunService.run(flowId, triggerPayload, FlowRunEntity.TriggerSource.WEBHOOK);
    return ResponseEntity.ok(Map.of("runId", run.id(), "status", run.status().name()));
  }
}
