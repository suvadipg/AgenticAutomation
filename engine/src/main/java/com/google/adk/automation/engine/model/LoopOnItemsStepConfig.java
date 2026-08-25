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
 * Iterates {@code itemsExpression} (a JEXL expression that must evaluate to a {@link
 * java.util.List}) and, for each item, runs {@code loopBody} with {@code {{loopItem}}}/{@code
 * {{loopIndex}}} bound in scope.
 *
 * <p>{@code loopBody} is a plain ordered list executed sequentially per iteration, not a linked
 * chain: nested {@code nextStep} pointers on body steps are ignored, and a {@link RouterStepConfig}
 * nested inside a loop body is out of scope for M1. This is a deliberate simplification — it covers
 * the common "do these N steps for each item" case without building general recursive chain
 * traversal inside a loop before there's a concrete need for it.
 */
public final class LoopOnItemsStepConfig extends StepConfig {
  private final String itemsExpression;
  private final ImmutableList<StepConfig> loopBody;

  @JsonCreator
  public LoopOnItemsStepConfig(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("nextStep") @Nullable String nextStep,
      @JsonProperty("itemsExpression") String itemsExpression,
      @JsonProperty("loopBody") List<StepConfig> loopBody) {
    super(id, name, nextStep);
    this.itemsExpression = itemsExpression;
    this.loopBody = ImmutableList.copyOf(loopBody);
  }

  @Override
  public StepType type() {
    return StepType.LOOP_ON_ITEMS;
  }

  public String itemsExpression() {
    return itemsExpression;
  }

  public ImmutableList<StepConfig> loopBody() {
    return loopBody;
  }
}
