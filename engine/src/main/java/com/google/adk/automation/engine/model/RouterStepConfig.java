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
 * A conditional branch. Unlike other step types, a router's own {@link #nextStep()} is unused
 * (always {@code null}) — the executor dispatches to the first {@link Branch} whose {@code
 * conditionExpression} evaluates truthy, or to {@link #defaultNextStep()} if none match.
 */
public final class RouterStepConfig extends StepConfig {
  private final ImmutableList<Branch> branches;
  private final @Nullable String defaultNextStep;

  @JsonCreator
  public RouterStepConfig(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("branches") List<Branch> branches,
      @JsonProperty("defaultNextStep") @Nullable String defaultNextStep) {
    super(id, name, /* nextStep= */ null);
    this.branches = ImmutableList.copyOf(branches);
    this.defaultNextStep = defaultNextStep;
  }

  @Override
  public StepType type() {
    return StepType.ROUTER;
  }

  public ImmutableList<Branch> branches() {
    return branches;
  }

  public @Nullable String defaultNextStep() {
    return defaultNextStep;
  }

  /** One branch: a JEXL boolean condition and the step id to jump to if it matches. */
  public record Branch(String conditionExpression, String nextStep) {}
}
