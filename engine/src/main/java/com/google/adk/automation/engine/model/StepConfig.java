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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

/**
 * One node in a flow's step chain. Flows are a **linked chain**, not a general DAG (matching
 * activepieces' {@code trigger.nextAction} model): each step names the single next step to run, or
 * {@code null} to end the flow. {@link RouterStepConfig} is the one exception — it ignores its own
 * {@link #nextStep()} and instead dispatches to a branch's target (see that class).
 *
 * <p>Annotated for direct Jackson (de)serialization — this is what {@code FlowDefinitionJsonCodec}
 * uses to turn a {@code FlowDefinition} into the {@code flow_versions.definition_json} blob the
 * persistence module stores, and back. Persisted JSON currently mirrors this Java model directly
 * rather than going through a separate DTO layer; if the stored format ever needs to evolve
 * independently of the execution model, introduce one then.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = PieceStepConfig.class, name = "PIECE"),
  @JsonSubTypes.Type(value = AiAgentStepConfig.class, name = "AI_AGENT"),
  @JsonSubTypes.Type(value = RouterStepConfig.class, name = "ROUTER"),
  @JsonSubTypes.Type(value = LoopOnItemsStepConfig.class, name = "LOOP_ON_ITEMS"),
  @JsonSubTypes.Type(value = ParallelStepConfig.class, name = "PARALLEL")
})
public abstract class StepConfig {
  private final String id;
  private final String name;
  private final @Nullable String nextStep;

  protected StepConfig(String id, String name, @Nullable String nextStep) {
    this.id = id;
    this.name = name;
    this.nextStep = nextStep;
  }

  public abstract StepType type();

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public @Nullable String nextStep() {
    return nextStep;
  }
}
