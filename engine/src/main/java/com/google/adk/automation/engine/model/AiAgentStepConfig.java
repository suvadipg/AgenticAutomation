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

package com.google.adk.automation.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A step backed by a real ADK {@code LlmAgent}, built fresh per execution from this config ({@code
 * AiAgentStepExecutor} does not depend on the (upstream, not-yet-public) YAML config-agent loader).
 * {@code instruction} may itself contain {@code {{...}}} expressions, resolved the same way
 * piece-step inputs are.
 */
public final class AiAgentStepConfig extends StepConfig {
  private final String instruction;
  private final String model;
  private final ImmutableList<String> allowedTools;

  @JsonCreator
  public AiAgentStepConfig(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("nextStep") @Nullable String nextStep,
      @JsonProperty("instruction") String instruction,
      @JsonProperty("model") String model,
      @JsonProperty("allowedTools") List<String> allowedTools) {
    super(id, name, nextStep);
    this.instruction = instruction;
    this.model = model;
    this.allowedTools = ImmutableList.copyOf(allowedTools);
  }

  @Override
  public StepType type() {
    return StepType.AI_AGENT;
  }

  public String instruction() {
    return instruction;
  }

  public String model() {
    return model;
  }

  /**
   * Qualified piece-action names (e.g. {@code "http.request"}) this agent may call as tools, each
   * resolved via {@code PieceRegistry} and wrapped as a {@code PieceActionTool}.
   */
  public ImmutableList<String> allowedTools() {
    return allowedTools;
  }
}
