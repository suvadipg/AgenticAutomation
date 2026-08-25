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

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.automation.engine.exec.PieceActionTool;
import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import com.google.adk.automation.sdk.Property;
import com.google.adk.automation.sdk.PropertyMap;
import com.google.adk.events.Event;
import com.google.adk.memory.InMemoryMemoryService;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Proves the core architectural bet of this design: an {@link ActionDefinition}, wrapped as a
 * {@link PieceActionTool}, can be handed to a real {@code LlmAgent} and invoked via genuine ADK
 * function-calling — not just called directly by the deterministic engine.
 *
 * <p>Requires model credentials to be configured (same as any ADK sample using {@code LlmAgent});
 * see the module README.
 */
final class PieceActionToolTest {
  @Test
  void llmAgent_callsWrappedPieceActionAsATool() {
    AtomicReference<String> receivedMessage = new AtomicReference<>();
    ActionDefinition recordCall = new RecordCallAction(receivedMessage);
    PieceActionTool tool = new PieceActionTool("test", recordCall, null);

    LlmAgent agent =
        LlmAgent.builder()
            .name("tool_calling_test_agent")
            .instruction(
                "You must call the 'test_recordCall' tool exactly once, passing the exact "
                    + "string 'hello-from-agent' as the 'message' argument. Do not do anything else.")
            .model("gemini-3.6-flash")
            .tools(List.of(tool))
            .build();

    InMemorySessionService sessionService = new InMemorySessionService();
    Runner runner =
        new Runner(
            agent,
            "agentic-automation-test",
            new InMemoryArtifactService(),
            sessionService,
            new InMemoryMemoryService());
    sessionService
        .createSession("agentic-automation-test", "test-user", null, "test-session")
        .blockingGet();

    Content userMessage =
        Content.builder()
            .role("user")
            .parts(ImmutableList.of(Part.builder().text("Please proceed.").build()))
            .build();

    List<Event> events =
        Lists.newArrayList(
            runner
                .runAsync("test-user", "test-session", userMessage, RunConfig.builder().build())
                .blockingIterable());

    assertThat(events).isNotEmpty();
    assertThat(receivedMessage.get()).isEqualTo("hello-from-agent");
  }

  /** A minimal piece action that records the message it was called with. */
  private static final class RecordCallAction implements ActionDefinition {
    private final AtomicReference<String> receivedMessage;

    RecordCallAction(AtomicReference<String> receivedMessage) {
      this.receivedMessage = receivedMessage;
    }

    @Override
    public String name() {
      return "recordCall";
    }

    @Override
    public String displayName() {
      return "Record Call";
    }

    @Override
    public String description() {
      return "Records the message it is called with, for test verification.";
    }

    @Override
    public PropertyMap props() {
      return PropertyMap.of(
          Property.shortText("message", "Message", "The message to record.", /* required= */ true));
    }

    @Override
    public Single<Map<String, Object>> execute(ActionContext context) {
      return Single.fromCallable(
          () -> {
            String message = context.require("message", String.class);
            receivedMessage.set(message);
            return Map.of("received", message);
          });
    }
  }
}
