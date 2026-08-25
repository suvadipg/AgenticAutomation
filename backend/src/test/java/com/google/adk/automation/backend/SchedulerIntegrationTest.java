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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.adk.automation.backend.web.FlowController.UpsertDraftRequest;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowDefinitionJsonCodec;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.TriggerConfig;
import com.google.adk.automation.persistence.entity.FlowRunEntity;
import com.google.adk.automation.persistence.service.FlowRunService;
import java.time.Duration;
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
 * Proves the M3 scheduler acceptance criterion: a published {@code CRON}-trigger flow fires on its
 * own, without any external caller, at (roughly) the configured interval. Uses a 2-second cron
 * expression (Spring's 6-field format, seconds included — see {@code SchedulerService}) so the test
 * doesn't need to wait long, and polls {@code FlowRunService.listRuns} (bypassing HTTP — this test
 * is about the in-process scheduler firing, not the REST layer, which {@link
 * WebhookIntegrationTest} already covers) until at least two runs have landed.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
final class SchedulerIntegrationTest {
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
  @Autowired private FlowRunService flowRunService;

  @Test
  void publishedCronFlow_firesAutomaticallyOnItsOwnSchedule() throws Exception {
    String flowId = "flow-" + UUID.randomUUID();

    ObjectNode definitionNode =
        (ObjectNode)
            objectMapper.readTree(
                FlowDefinitionJsonCodec.toJson(cronTriggeredFlow("*/2 * * * * *")));
    UpsertDraftRequest draftRequest =
        new UpsertDraftRequest("Cron Flow", "test-owner", definitionNode);

    mockMvc
        .perform(
            put("/api/flows/{flowId}/draft", flowId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(draftRequest)))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/flows/{flowId}/publish", flowId)).andExpect(status().isOk());

    // The scheduler fires in the background; poll rather than sleep-then-check-once so the test
    // doesn't depend on exact timing.
    List<FlowRunEntity> runs = awaitAtLeastTwoRuns(flowId, Duration.ofSeconds(15));

    assertThat(runs.size()).isAtLeast(2);
    assertThat(
            runs.stream()
                .allMatch(run -> run.getTriggerSource() == FlowRunEntity.TriggerSource.CRON))
        .isTrue();
    assertThat(runs.stream().allMatch(run -> run.getStatus() == FlowRunEntity.Status.SUCCEEDED))
        .isTrue();
  }

  private List<FlowRunEntity> awaitAtLeastTwoRuns(String flowId, Duration timeout)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    List<FlowRunEntity> runs;
    do {
      runs = flowRunService.listRuns(flowId);
      if (runs.size() >= 2) {
        return runs;
      }
      Thread.sleep(500);
    } while (System.currentTimeMillis() < deadline);
    return runs;
  }

  private static FlowDefinition cronTriggeredFlow(String cronExpression) {
    StepConfig tick =
        new PieceStepConfig(
            "tick", "Tick", null, "code", "evaluate", Map.of("expression", "'tick'"), null);
    return new FlowDefinition(
        "cron-flow", "Cron Flow", TriggerConfig.cron("tick", cronExpression), List.of(tick));
  }
}
