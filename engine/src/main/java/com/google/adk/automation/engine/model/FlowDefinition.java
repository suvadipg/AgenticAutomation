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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A named flow: one {@link TriggerConfig} plus the chain of {@link StepConfig}s it starts. In M1
 * this is an in-memory value object; M2 adds a persisted, versioned ({@code DRAFT}/{@code LOCKED})
 * wrapper around the same shape (see the project plan's persistence section).
 */
public final class FlowDefinition {
  private final String id;
  private final String name;
  private final TriggerConfig trigger;

  // Indexed by id for FlowExecutor's O(1) lookups; @JsonIgnore'd in favor of the plain "steps"
  // list below, which is what the @JsonCreator constructor round-trips against.
  @JsonIgnore private final ImmutableMap<String, StepConfig> stepsById;

  @JsonCreator
  public FlowDefinition(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("trigger") TriggerConfig trigger,
      @JsonProperty("steps") List<StepConfig> steps) {
    this.id = id;
    this.name = name;
    this.trigger = trigger;
    Map<String, StepConfig> byId = new LinkedHashMap<>();
    for (StepConfig step : steps) {
      byId.put(step.id(), step);
    }
    this.stepsById = ImmutableMap.copyOf(byId);
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public TriggerConfig trigger() {
    return trigger;
  }

  public ImmutableMap<String, StepConfig> steps() {
    return stepsById;
  }

  public Optional<StepConfig> step(String stepId) {
    return Optional.ofNullable(stepsById.get(stepId));
  }

  @JsonProperty("steps")
  private ImmutableList<StepConfig> stepsForSerialization() {
    return ImmutableList.copyOf(stepsById.values());
  }
}
