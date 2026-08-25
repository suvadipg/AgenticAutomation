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

package com.google.adk.automation.backend;

import static com.google.common.truth.Truth.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.adk.automation.backend.web.FlowController.UpsertDraftRequest;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowDefinitionJsonCodec;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.TriggerConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end proof of the M3 webhook acceptance criterion: a webhook POST produces a real,
 * persisted {@code FlowRun}. Drives the whole stack — {@code FlowController} to author/publish a
 * flow, {@code WebhookController} to trigger it, {@code FlowRunController} to read back the result
 * — through real Spring MVC dispatch (@{@code AutoConfigureMockMvc}: real controllers, real service
 * beans, a real Testcontainers Postgres; only the raw socket/HTTP layer is mocked, which isn't
 * essential to proving persistence end to end).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
final class WebhookIntegrationTest {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("agentic-automation.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("agentic-automation.datasource.username", POSTGRES::getUsername);
    registry.add("agentic-automation.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void webhookPost_runsTheFlowAndPersistsAResult() throws Exception {
    String flowId = "flow-" + UUID.randomUUID();

    // FlowDefinition is only reliably serializable through FlowDefinitionJsonCodec's special
    // field-visibility mapper (see FlowSummary's javadoc) — never through the plain, default
    // ObjectMapper Spring wires up. Get the correct JSON text from the codec first, then parse
    // that (parsing a JSON string into a tree doesn't depend on bean-visibility rules) into the
    // JsonNode the request DTO expects.
    ObjectNode definitionNode =
        (ObjectNode) objectMapper.readTree(FlowDefinitionJsonCodec.toJson(webhookTriggeredFlow()));
    UpsertDraftRequest draftRequest =
        new UpsertDraftRequest("Webhook Flow", "test-owner", definitionNode);

    mockMvc
        .perform(
            put("/api/flows/{flowId}/draft", flowId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(draftRequest)))
        .andExpect(status().isOk());

    String publishBody =
        mockMvc
            .perform(post("/api/flows/{flowId}/publish", flowId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode publishJson = objectMapper.readTree(publishBody);
    String webhookToken = publishJson.get("webhookToken").asText();
    assertThat(webhookToken).isNotEmpty();

    String webhookResponseBody =
        mockMvc
            .perform(
                post("/api/webhooks/{flowId}/{token}", flowId, webhookToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("source", "curl"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode webhookJson = objectMapper.readTree(webhookResponseBody);
    assertThat(webhookJson.get("status").asText()).isEqualTo("SUCCEEDED");
    String runId = webhookJson.get("runId").asText();
    assertThat(runId).isNotEmpty();

    String runBody =
        mockMvc
            .perform(get("/api/flow-runs/{runId}", runId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode runJson = objectMapper.readTree(runBody);
    assertThat(runJson.get("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(runJson.get("triggerSource").asText()).isEqualTo("WEBHOOK");
    assertThat(runJson.get("steps")).hasSize(1);
    assertThat(runJson.get("steps").get(0).get("stepId").asText()).isEqualTo("greet");
  }

  private static FlowDefinition webhookTriggeredFlow() {
    StepConfig greet =
        new PieceStepConfig(
            "greet",
            "Greet",
            null,
            "code",
            "evaluate",
            Map.of("expression", "'hello from webhook'"),
            null);
    return new FlowDefinition(
        "webhook-flow", "Webhook Flow", TriggerConfig.webhook("greet"), List.of(greet));
  }
}
