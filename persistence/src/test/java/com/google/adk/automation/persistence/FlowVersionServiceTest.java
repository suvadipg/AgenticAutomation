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

package com.google.adk.automation.persistence;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.TriggerConfig;
import com.google.adk.automation.persistence.entity.FlowVersionEntity;
import com.google.adk.automation.persistence.service.FlowVersionService;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the M2 acceptance criterion: publishing locks a snapshot, and further (unpublished) draft
 * edits never affect what a trigger-fired run executes — only the next publish does.
 *
 * <p>Requires Docker (Testcontainers spins up a real Postgres); see the module README.
 */
@Testcontainers
final class FlowVersionServiceTest {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private static EntityManagerFactory entityManagerFactory;
  private static FlowVersionService flowVersionService;

  @BeforeAll
  static void setUp() {
    entityManagerFactory =
        PersistenceUnitProvider.create(
            new PostgresConfig(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    flowVersionService = new FlowVersionService(entityManagerFactory);
  }

  @AfterAll
  static void tearDown() {
    entityManagerFactory.close();
  }

  @Test
  void draftEditsAfterPublish_doNotAffectTheLockedDefinition_untilPublishedAgain() {
    String flowId = "flow-" + UUID.randomUUID();
    FlowDefinition versionA = flowWithExpression("a-result");
    FlowDefinition versionB = flowWithExpression("b-result");

    flowVersionService.upsertDraft(flowId, "My Flow", "test-owner", versionA);
    FlowVersionEntity lockedV1 = flowVersionService.publish(flowId);
    assertThat(lockedV1.getStatus()).isEqualTo(FlowVersionEntity.Status.LOCKED);
    assertThat(lockedV1.getVersionNumber()).isEqualTo(1);
    assertThat(resultExpression(flowVersionService.getLockedDefinition(flowId)))
        .isEqualTo("a-result");

    // Edit the draft to version B, but do NOT publish yet.
    flowVersionService.upsertDraft(flowId, "My Flow", "test-owner", versionB);

    // A trigger-fired run must still see version A: the draft edit hasn't been published.
    assertThat(resultExpression(flowVersionService.getLockedDefinition(flowId)))
        .isEqualTo("a-result");
    assertThat(resultExpression(flowVersionService.getDraftDefinition(flowId)))
        .isEqualTo("b-result");

    // Now publish the B draft — only *now* does the locked definition change.
    FlowVersionEntity lockedV2 = flowVersionService.publish(flowId);
    assertThat(lockedV2.getVersionNumber()).isEqualTo(2);
    assertThat(resultExpression(flowVersionService.getLockedDefinition(flowId)))
        .isEqualTo("b-result");
  }

  @Test
  void publish_withNoDraft_fails() {
    String flowId = "flow-" + UUID.randomUUID();
    // Create the flow via a draft, but never actually call publish on a *different*, brand-new
    // flow id that has no draft at all.
    assertThrows(IllegalStateException.class, () -> flowVersionService.publish(flowId));
  }

  private static FlowDefinition flowWithExpression(String literal) {
    StepConfig step =
        new PieceStepConfig(
            "step",
            "Step",
            null,
            "code",
            "evaluate",
            Map.of("expression", "'" + literal + "'"),
            null);
    return new FlowDefinition("flow-def", "Flow", TriggerConfig.manual("step"), List.of(step));
  }

  private static String resultExpression(FlowDefinition flow) {
    PieceStepConfig step = (PieceStepConfig) flow.step("step").orElseThrow();
    // Input is "'literal'" (JEXL string literal); strip the surrounding quotes back out.
    String expression = (String) step.input().get("expression");
    return expression.substring(1, expression.length() - 1);
  }
}
