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
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test of the M1 acceptance chain: MANUAL trigger -> PIECE (HTTP, against a local test
 * server) -> AI_AGENT (a real {@code LlmAgent} call classifying the fetched value) -> ROUTER (two
 * branches) -> LOOP_ON_ITEMS.
 *
 * <p>Requires model credentials to be configured (same as any ADK sample using {@code LlmAgent});
 * see the module README. The AI_AGENT step is given a strict single-word-answer instruction to keep
 * routing deterministic, but this test is still ultimately dependent on the model following that
 * instruction.
 */
final class FlowExecutorTest {
  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/value",
        exchange -> {
          byte[] response = "42".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.getResponseBody().close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void execute_runsFullChain_pieceAiAgentRouterAndLoop() {
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/value";
    FlowDefinition flow = buildFlow(url);

    FlowExecutor executor = new FlowExecutor(PieceRegistry.fromServiceLoader());
    FlowRun run = executor.execute(flow, Map.of("source", "test")).blockingGet();

    assertThat(run.status()).isEqualTo(FlowRun.Status.SUCCEEDED);

    Map<String, StepResult> byId = new java.util.LinkedHashMap<>();
    for (StepResult result : run.stepResults()) {
      if (result.iterationIndex() == null) {
        byId.put(result.stepId(), result);
      }
    }

    assertThat(byId.get("fetch").output().get("body")).isEqualTo("42");
    assertThat(byId).containsKey("interpret");
    assertThat(byId.get("route").output().get("nextStep")).isEqualTo("even-branch");
    assertThat(byId.get("even-branch").output().get("result")).isEqualTo("even branch taken");

    long loopIterations =
        run.stepResults().stream()
            .filter(r -> r.stepId().equals("loop-body") && r.iterationIndex() != null)
            .count();
    assertThat(loopIterations).isEqualTo(2);
  }

  private static FlowDefinition buildFlow(String url) {
    StepConfig fetch =
        new PieceStepConfig(
            "fetch",
            "Fetch Value",
            "interpret",
            "http",
            "request",
            Map.of("method", "GET", "url", url),
            null);

    StepConfig interpret =
        new AiAgentStepConfig(
            "interpret",
            "Interpret Value",
            "route",
            "The number is {{steps.fetch.output.body}}. Respond with exactly one word: "
                + "EVEN or ODD. No other text.",
            "gemini-3.6-flash",
            List.of());

    StepConfig route =
        new RouterStepConfig(
            "route",
            "Route on Parity",
            List.of(
                new RouterStepConfig.Branch(
                    "steps.interpret.output.text.toUpperCase().contains('EVEN')", "even-branch")),
            "odd-branch");

    StepConfig evenBranch =
        new PieceStepConfig(
            "even-branch",
            "Even Branch",
            "loop",
            "code",
            "evaluate",
            Map.of("expression", "'even branch taken'"),
            null);
    StepConfig oddBranch =
        new PieceStepConfig(
            "odd-branch",
            "Odd Branch",
            "loop",
            "code",
            "evaluate",
            Map.of("expression", "'odd branch taken'"),
            null);

    StepConfig loop =
        new LoopOnItemsStepConfig(
            "loop",
            "Loop Over Items",
            null,
            "['alpha', 'beta']",
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
        "test-flow",
        "Test Flow",
        TriggerConfig.manual("fetch"),
        List.of(fetch, interpret, route, evenBranch, oddBranch, loop));
  }
}
