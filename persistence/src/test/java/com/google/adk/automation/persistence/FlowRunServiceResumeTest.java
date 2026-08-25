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
import com.google.adk.automation.engine.model.FlowRun;
import com.google.adk.automation.engine.model.LoopOnItemsStepConfig;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.TriggerConfig;
import com.google.adk.automation.persistence.entity.FlowRunEntity;
import com.google.adk.automation.persistence.entity.StepResultEntity;
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
 * Full pause -> persist -> resume -> complete round trip through {@link FlowRunService#resume},
 * plus the two rejection paths: resuming a run that isn't paused, and resuming a run that paused
 * inside a {@code LOOP_ON_ITEMS} child (not individually resumable — see {@link
 * FlowRunService#resume}'s javadoc).
 *
 * <p>Requires Docker (Testcontainers spins up a real Postgres); see the module README.
 */
@Testcontainers
final class FlowRunServiceResumeTest {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private static EntityManagerFactory entityManagerFactory;
  private static FlowRunService flowRunService;

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

    flowVersionService.upsertDraft(
        TOP_LEVEL_PAUSE_FLOW_ID, "Pause Flow", "test-owner", topLevelPauseFlow());
    flowVersionService.publish(TOP_LEVEL_PAUSE_FLOW_ID);

    flowVersionService.upsertDraft(
        LOOP_PAUSE_FLOW_ID, "Loop Pause Flow", "test-owner", loopPauseFlow());
    flowVersionService.publish(LOOP_PAUSE_FLOW_ID);
  }

  @AfterAll
  static void tearDown() {
    entityManagerFactory.close();
  }

  private static final String TOP_LEVEL_PAUSE_FLOW_ID = "pause-flow-" + UUID.randomUUID();
  private static final String LOOP_PAUSE_FLOW_ID = "loop-pause-flow-" + UUID.randomUUID();

  @Test
  void resume_completesAPausedRun_andPersistsTheResumedStep() {
    FlowRun paused = flowRunService.runManual(TOP_LEVEL_PAUSE_FLOW_ID, Map.of());
    assertThat(paused.status()).isEqualTo(FlowRun.Status.PAUSED);

    FlowRunEntity pausedEntity = flowRunService.findRun(paused.id()).orElseThrow();
    assertThat(pausedEntity.getStatus()).isEqualTo(FlowRunEntity.Status.PAUSED);
    assertThat(pausedEntity.getPausedAtStepId()).isEqualTo("wait");

    FlowRun resumed = flowRunService.resume(paused.id(), Map.of("resumedBy", "approved"));
    assertThat(resumed.status()).isEqualTo(FlowRun.Status.SUCCEEDED);

    FlowRunEntity finishedEntity = flowRunService.findRun(paused.id()).orElseThrow();
    assertThat(finishedEntity.getStatus()).isEqualTo(FlowRunEntity.Status.SUCCEEDED);

    List<StepResultEntity> persistedSteps = flowRunService.getStepResults(paused.id());
    assertThat(persistedSteps.stream().map(StepResultEntity::getStepId).toList())
        .containsExactly("compute", "wait", "echo");
    StepResultEntity echo =
        persistedSteps.stream().filter(s -> s.getStepId().equals("echo")).findFirst().orElseThrow();
    assertThat(PersistenceJson.fromJson(echo.getOutputJson(), Map.class))
        .isEqualTo(Map.of("result", "got:value=42:approved"));
  }

  @Test
  void resume_rejectsARunThatIsNotPaused() {
    FlowRun paused = runManualExpectingPause(TOP_LEVEL_PAUSE_FLOW_ID);
    FlowRun succeeded = flowRunService.resume(paused.id(), Map.of());
    assertThat(succeeded.status()).isEqualTo(FlowRun.Status.SUCCEEDED);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class, () -> flowRunService.resume(succeeded.id(), Map.of()));
    assertThat(error).hasMessageThat().contains("not paused");
  }

  @Test
  void resume_rejectsARunPausedInsideALoopOnItemsChild() {
    FlowRun paused = flowRunService.runManual(LOOP_PAUSE_FLOW_ID, Map.of());
    assertThat(paused.status()).isEqualTo(FlowRun.Status.PAUSED);
    assertThat(flowRunService.findRun(paused.id()).orElseThrow().getPausedAtStepId()).isNull();

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class, () -> flowRunService.resume(paused.id(), Map.of()));
    assertThat(error).hasMessageThat().contains("not individually resumable");
  }

  private static FlowRun runManualExpectingPause(String flowId) {
    FlowRun paused = flowRunService.runManual(flowId, Map.of());
    assertThat(paused.status()).isEqualTo(FlowRun.Status.PAUSED);
    return paused;
  }

  private static FlowDefinition topLevelPauseFlow() {
    StepConfig compute =
        new PieceStepConfig(
            "compute",
            "Compute",
            "wait",
            "code",
            "evaluate",
            Map.of("expression", "'value=' + (2 * 21)"),
            null);
    StepConfig wait =
        new PieceStepConfig("wait", "Wait", "echo", "delay", "wait", Map.of("seconds", 0.01), null);
    StepConfig echo =
        new PieceStepConfig(
            "echo",
            "Echo",
            null,
            "code",
            "evaluate",
            Map.of(
                "expression",
                "'got:' + prev + ':' + resumed",
                "variables",
                Map.of(
                    "prev", "{{steps.compute.output.result}}",
                    "resumed", "{{steps.wait.output.resumedBy}}")),
            null);
    return new FlowDefinition(
        "top-level-pause-flow",
        "Top Level Pause Flow",
        TriggerConfig.manual("compute"),
        List.of(compute, wait, echo));
  }

  private static FlowDefinition loopPauseFlow() {
    StepConfig loop =
        new LoopOnItemsStepConfig(
            "loop",
            "Loop With Pause",
            null,
            "['only-item']",
            List.of(
                new PieceStepConfig(
                    "loop-wait",
                    "Loop Wait",
                    null,
                    "delay",
                    "wait",
                    Map.of("seconds", 0.01),
                    null)));
    return new FlowDefinition(
        "loop-pause-flow", "Loop Pause Flow", TriggerConfig.manual("loop"), List.of(loop));
  }
}
