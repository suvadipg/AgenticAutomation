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

import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowRun;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.TriggerConfig;
import com.google.adk.automation.persistence.entity.FlowRunEntity;
import com.google.adk.automation.persistence.entity.StepResultEntity;
import com.google.adk.automation.persistence.repository.FlowRunRepository;
import com.google.adk.automation.persistence.service.FlowRunService;
import com.google.adk.automation.persistence.service.FlowVersionService;
import com.google.adk.automation.sdk.PieceRegistry;
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
 * Proves {@code FlowRunService} always executes a flow's LOCKED version and persists a {@code
 * step_results} row per step as it happens (via {@code FlowRunListener}), not only once the run
 * finishes. Deliberately built on the {@code code} piece only (no HTTP/LLM calls) so it stays fast
 * and network-independent, matching M1's {@code FlowExecutorTest} design principle of decoupling
 * deterministic-step tests from LLM flakiness.
 *
 * <p>Requires Docker (Testcontainers spins up a real Postgres); see the module README.
 */
@Testcontainers
final class FlowRunServiceTest {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private static EntityManagerFactory entityManagerFactory;
  private static FlowRunService flowRunService;
  private static final FlowRunRepository FLOW_RUN_REPOSITORY = new FlowRunRepository();

  @BeforeAll
  static void setUp() {
    entityManagerFactory =
        PersistenceUnitProvider.create(
            new PostgresConfig(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    FlowVersionService flowVersionService = new FlowVersionService(entityManagerFactory);
    flowRunService =
        new FlowRunService(
            entityManagerFactory, flowVersionService, PieceRegistry.fromServiceLoader());

    String flowId = FLOW_ID;
    flowVersionService.upsertDraft(flowId, "Two Step Flow", "test-owner", twoStepFlow());
    flowVersionService.publish(flowId);
  }

  @AfterAll
  static void tearDown() {
    entityManagerFactory.close();
  }

  private static final String FLOW_ID = "flow-" + UUID.randomUUID();

  @Test
  void runManual_persistsAFlowRunRowAndOneStepResultRowPerStep() {
    FlowRun run = flowRunService.runManual(FLOW_ID, Map.of());

    assertThat(run.status()).isEqualTo(FlowRun.Status.SUCCEEDED);
    assertThat(run.stepResults()).hasSize(2);

    List<StepResultEntity> persistedSteps = flowRunService.getStepResults(run.id());
    assertThat(persistedSteps).hasSize(2);
    assertThat(persistedSteps.stream().map(StepResultEntity::getStepId).toList())
        .containsExactly("double", "echo");
    assertThat(
            persistedSteps.stream()
                .allMatch(s -> s.getStatus() == StepResultEntity.Status.SUCCEEDED))
        .isTrue();
    assertThat(PersistenceJson.fromJson(persistedSteps.get(0).getOutputJson(), Map.class))
        .isEqualTo(Map.of("result", "value=42"));
    assertThat(PersistenceJson.fromJson(persistedSteps.get(1).getOutputJson(), Map.class))
        .isEqualTo(Map.of("result", "got:value=42"));

    FlowRunEntity persistedRun =
        Transactions.run(
            entityManagerFactory,
            entityManager -> FLOW_RUN_REPOSITORY.findById(entityManager, run.id()).orElseThrow());
    assertThat(persistedRun.getStatus()).isEqualTo(FlowRunEntity.Status.SUCCEEDED);
    assertThat(persistedRun.getFinishedAt()).isNotNull();
  }

  private static FlowDefinition twoStepFlow() {
    StepConfig doubleStep =
        new PieceStepConfig(
            "double",
            "Double",
            "echo",
            "code",
            "evaluate",
            Map.of("expression", "'value=' + (2 * 21)"),
            null);
    StepConfig echoStep =
        new PieceStepConfig(
            "echo",
            "Echo",
            null,
            "code",
            "evaluate",
            Map.of(
                "expression",
                "'got:' + prev",
                "variables",
                Map.of("prev", "{{steps.double.output.result}}")),
            null);
    return new FlowDefinition(
        "two-step-flow",
        "Two Step Flow",
        TriggerConfig.manual("double"),
        List.of(doubleStep, echoStep));
  }
}
