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

import com.google.adk.automation.engine.model.AiAgentStepConfig;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowDefinitionJsonCodec;
import com.google.adk.automation.engine.model.LoopOnItemsStepConfig;
import com.google.adk.automation.engine.model.ParallelStepConfig;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.RouterStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepType;
import com.google.adk.automation.engine.model.TriggerConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Round-trips a {@link FlowDefinition} covering all four M1 step types through {@link
 * FlowDefinitionJsonCodec} — this is the mechanism the persistence module relies on to store a flow
 * as {@code flow_versions.definition_json} and reload something {@code FlowExecutor} can still run.
 */
final class FlowDefinitionJsonCodecTest {
  @Test
  void toJson_thenFromJson_reproducesAnEquivalentFlowDefinition() {
    StepConfig fetch =
        new PieceStepConfig(
            "fetch",
            "Fetch",
            "route",
            "http",
            "request",
            Map.of("method", "GET", "url", "http://example.invalid"),
            "conn-1");
    StepConfig ai =
        new AiAgentStepConfig(
            "ai",
            "Summarize",
            "route",
            "Summarize {{steps.fetch.output.body}}",
            "gemini-3.6-flash",
            List.of("http.request"));
    StepConfig route =
        new RouterStepConfig(
            "route", "Route", List.of(new RouterStepConfig.Branch("true", "loop")), "loop");
    StepConfig loop =
        new LoopOnItemsStepConfig(
            "loop",
            "Loop",
            null,
            "['a', 'b']",
            List.of(
                new PieceStepConfig(
                    "loop-body",
                    "Body",
                    null,
                    "code",
                    "evaluate",
                    Map.of("expression", "loopItem"),
                    null)));

    FlowDefinition original =
        new FlowDefinition(
            "flow-1",
            "My Flow",
            TriggerConfig.cron("fetch", "0 * * * *"),
            List.of(fetch, ai, route, loop));

    String json = FlowDefinitionJsonCodec.toJson(original);
    FlowDefinition restored = FlowDefinitionJsonCodec.fromJson(json);

    assertThat(restored.id()).isEqualTo("flow-1");
    assertThat(restored.name()).isEqualTo("My Flow");
    assertThat(restored.trigger().kind()).isEqualTo(TriggerConfig.Kind.CRON);
    assertThat(restored.trigger().cronExpression()).isEqualTo("0 * * * *");
    assertThat(restored.trigger().firstStepId()).isEqualTo("fetch");
    assertThat(restored.steps().keySet()).containsExactly("fetch", "ai", "route", "loop");

    PieceStepConfig restoredFetch = (PieceStepConfig) restored.step("fetch").orElseThrow();
    assertThat(restoredFetch.type()).isEqualTo(StepType.PIECE);
    assertThat(restoredFetch.pieceId()).isEqualTo("http");
    assertThat(restoredFetch.actionName()).isEqualTo("request");
    assertThat(restoredFetch.input())
        .isEqualTo(Map.of("method", "GET", "url", "http://example.invalid"));
    assertThat(restoredFetch.connectionId()).isEqualTo("conn-1");
    assertThat(restoredFetch.nextStep()).isEqualTo("route");

    AiAgentStepConfig restoredAi = (AiAgentStepConfig) restored.step("ai").orElseThrow();
    assertThat(restoredAi.instruction()).isEqualTo("Summarize {{steps.fetch.output.body}}");
    assertThat(restoredAi.model()).isEqualTo("gemini-3.6-flash");
    assertThat(restoredAi.allowedTools()).containsExactly("http.request");

    RouterStepConfig restoredRoute = (RouterStepConfig) restored.step("route").orElseThrow();
    assertThat(restoredRoute.branches()).hasSize(1);
    assertThat(restoredRoute.branches().get(0).conditionExpression()).isEqualTo("true");
    assertThat(restoredRoute.branches().get(0).nextStep()).isEqualTo("loop");
    assertThat(restoredRoute.defaultNextStep()).isEqualTo("loop");

    LoopOnItemsStepConfig restoredLoop =
        (LoopOnItemsStepConfig) restored.step("loop").orElseThrow();
    assertThat(restoredLoop.itemsExpression()).isEqualTo("['a', 'b']");
    assertThat(restoredLoop.loopBody()).hasSize(1);
    StepConfig restoredBody = restoredLoop.loopBody().get(0);
    assertThat(restoredBody).isInstanceOf(PieceStepConfig.class);
    assertThat(restoredBody.id()).isEqualTo("loop-body");
  }

  @Test
  void parallelStepConfig_roundTripsNestedBranchesThroughJson() {
    StepConfig branchA =
        new PieceStepConfig(
            "branch-a", "Branch A", null, "code", "evaluate", Map.of("expression", "'a'"), null);
    StepConfig branchB =
        new PieceStepConfig(
            "branch-b", "Branch B", null, "code", "evaluate", Map.of("expression", "'b'"), null);
    StepConfig parallel =
        new ParallelStepConfig(
            "fanout", "Fan Out", "join", List.of(List.of(branchA), List.of(branchB)));

    FlowDefinition original =
        new FlowDefinition(
            "flow-2", "Parallel Flow", TriggerConfig.manual("fanout"), List.of(parallel));

    FlowDefinition restored =
        FlowDefinitionJsonCodec.fromJson(FlowDefinitionJsonCodec.toJson(original));

    ParallelStepConfig restoredParallel =
        (ParallelStepConfig) restored.step("fanout").orElseThrow();
    assertThat(restoredParallel.type()).isEqualTo(StepType.PARALLEL);
    assertThat(restoredParallel.nextStep()).isEqualTo("join");
    assertThat(restoredParallel.branches()).hasSize(2);
    assertThat(restoredParallel.branches().get(0)).hasSize(1);
    assertThat(restoredParallel.branches().get(0).get(0).id()).isEqualTo("branch-a");
    assertThat(restoredParallel.branches().get(1).get(0).id()).isEqualTo("branch-b");
  }
}
