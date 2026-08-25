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

package com.google.adk.automation.engine.cli;

import com.google.adk.automation.engine.exec.FlowExecutor;
import com.google.adk.automation.engine.model.AiAgentStepConfig;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowRun;
import com.google.adk.automation.engine.model.LoopOnItemsStepConfig;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.RouterStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.TriggerConfig;
import com.google.adk.automation.sdk.PieceRegistry;
import java.util.List;
import java.util.Map;

/**
 * Builds and runs a demo flow end to end, printing each step's result — a smoke test for the
 * engine, in the same spirit as the {@code helloworld} sample's {@code HelloWorldRun}. The flow
 * exercises all four M1 step types: {@code compute} (PIECE, via the built-in {@code code} piece),
 * {@code summarize} (AI_AGENT, a real {@code LlmAgent} call — requires model credentials to be
 * configured, same as any ADK sample using {@code LlmAgent}), {@code route} (ROUTER), and {@code
 * loop} (LOOP_ON_ITEMS).
 *
 * <p>Run with: {@code mvn -pl contrib/samples/AgenticAutomation/engine exec:java}
 */
public final class RunSampleFlowCli {
  private RunSampleFlowCli() {}

  public static void main(String[] args) {
    PieceRegistry pieceRegistry = PieceRegistry.fromServiceLoader();
    FlowExecutor flowExecutor = new FlowExecutor(pieceRegistry);
    FlowDefinition flow = buildSampleFlow();

    FlowRun run = flowExecutor.execute(flow, Map.of("triggeredBy", "cli")).blockingGet();

    System.out.println("Flow run " + run.id() + " finished with status " + run.status());
    for (StepResult result : run.stepResults()) {
      System.out.printf(
          "  [%s] %s (%s) -> %s%n",
          result.status(), result.stepId(), result.stepType(), result.output());
      if (result.errorMessage() != null) {
        System.out.println("      error: " + result.errorMessage());
      }
    }
    if (run.pauseMetadata() != null) {
      System.out.println("  paused: " + run.pauseMetadata());
    }
  }

  private static FlowDefinition buildSampleFlow() {
    StepConfig compute =
        new PieceStepConfig(
            "compute",
            "Compute",
            "summarize",
            "code",
            "evaluate",
            Map.of("expression", "'The answer is ' + (a * b)", "variables", Map.of("a", 6, "b", 7)),
            null);

    StepConfig summarize =
        new AiAgentStepConfig(
            "summarize",
            "Summarize",
            "route",
            "Rewrite this message in one enthusiastic sentence: {{steps.compute.output.result}}",
            "gemini-3.6-flash",
            List.of());

    StepConfig route =
        new RouterStepConfig(
            "route",
            "Route on length",
            List.of(
                new RouterStepConfig.Branch(
                    "steps.compute.output.result.length() > 5", "long-branch")),
            "short-branch");

    StepConfig longBranch =
        new PieceStepConfig(
            "long-branch",
            "Long Branch",
            "loop",
            "code",
            "evaluate",
            Map.of("expression", "'long message branch taken'"),
            null);
    StepConfig shortBranch =
        new PieceStepConfig(
            "short-branch",
            "Short Branch",
            "loop",
            "code",
            "evaluate",
            Map.of("expression", "'short message branch taken'"),
            null);

    StepConfig loop =
        new LoopOnItemsStepConfig(
            "loop",
            "Loop Over Items",
            null,
            "['alpha', 'beta', 'gamma']",
            List.of(
                new PieceStepConfig(
                    "loop-body",
                    "Format Item",
                    null,
                    "code",
                    "evaluate",
                    Map.of("expression", "'Item: ' + loopItem + ' at index ' + loopIndex"),
                    null)));

    return new FlowDefinition(
        "sample-flow",
        "Sample Flow",
        TriggerConfig.manual("compute"),
        List.of(compute, summarize, route, longBranch, shortBranch, loop));
  }
}
