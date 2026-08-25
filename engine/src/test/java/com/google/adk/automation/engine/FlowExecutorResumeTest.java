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

package com.google.adk.automation.engine;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.adk.automation.engine.exec.FlowExecutor;
import com.google.adk.automation.engine.exec.FlowRunListener;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowRun;
import com.google.adk.automation.engine.model.LoopOnItemsStepConfig;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import com.google.adk.automation.engine.model.TriggerConfig;
import com.google.adk.automation.sdk.PieceRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the M1 {@code FlowExecutor.resume} contract, using the built-in {@code delay} piece to
 * pause a run: the paused step is recorded as {@code SUCCEEDED} with the resume payload (not
 * re-executed), prior step outputs are still resolvable via {@code {{steps.X.output}}}, and a pause
 * originating inside a {@code LOOP_ON_ITEMS} child is only resumable as the whole composite step
 * (rejected here, mirroring what {@code FlowRunService.resume} rejects at the persistence layer).
 */
final class FlowExecutorResumeTest {
  @Test
  void resume_completesAPausedRun_andPriorStepOutputsStillResolve() {
    FlowDefinition flow = topLevelPauseFlow();
    FlowExecutor executor = new FlowExecutor(PieceRegistry.fromServiceLoader());

    FlowRun paused = executor.execute(flow, Map.of()).blockingGet();
    assertThat(paused.status()).isEqualTo(FlowRun.Status.PAUSED);
    assertThat(paused.stepResults()).hasSize(2);
    StepResult pausedStep = paused.stepResults().get(paused.stepResults().size() - 1);
    assertThat(pausedStep.stepId()).isEqualTo("wait");
    assertThat(pausedStep.stepType()).isEqualTo(StepType.PIECE);

    List<StepResult> priorHistory =
        paused.stepResults().stream().filter(r -> !r.stepId().equals("wait")).toList();

    FlowRun resumed =
        executor
            .resume(
                flow,
                paused.id(),
                "wait",
                priorHistory,
                Map.of(),
                Map.of("resumedBy", "approved"),
                FlowRunListener.NO_OP)
            .blockingGet();

    assertThat(resumed.status()).isEqualTo(FlowRun.Status.SUCCEEDED);
    Map<String, StepResult> byId = new java.util.LinkedHashMap<>();
    for (StepResult result : resumed.stepResults()) {
      byId.put(result.stepId(), result);
    }
    assertThat(byId.get("wait").status()).isEqualTo(StepResult.Status.SUCCEEDED);
    assertThat(byId.get("echo").output().get("result")).isEqualTo("got:value=42:approved");
  }

  @Test
  void resume_rejectsAStepThatIsNotPieceOrAiAgent() {
    FlowDefinition flow = loopPauseFlow();
    FlowExecutor executor = new FlowExecutor(PieceRegistry.fromServiceLoader());

    FlowRun paused = executor.execute(flow, Map.of()).blockingGet();
    assertThat(paused.status()).isEqualTo(FlowRun.Status.PAUSED);
    StepResult pausedStep = paused.stepResults().get(paused.stepResults().size() - 1);
    assertThat(pausedStep.stepId()).isEqualTo("loop");
    assertThat(pausedStep.stepType()).isEqualTo(StepType.LOOP_ON_ITEMS);

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                executor.resume(
                    flow,
                    paused.id(),
                    "loop",
                    List.of(),
                    Map.of(),
                    Map.of(),
                    FlowRunListener.NO_OP));
    assertThat(error).hasMessageThat().contains("LOOP_ON_ITEMS");
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
        "resume-test-flow",
        "Resume Test Flow",
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
        "resume-loop-flow", "Resume Loop Flow", TriggerConfig.manual("loop"), List.of(loop));
  }
}
