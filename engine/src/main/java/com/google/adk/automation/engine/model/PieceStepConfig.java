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
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A step that calls one {@link com.google.adk.automation.sdk.ActionDefinition} on a piece. {@code
 * input} values may contain {@code {{...}}} expressions, resolved by {@code ExpressionResolver}
 * against prior step outputs before the action is invoked.
 */
public final class PieceStepConfig extends StepConfig {
  private final String pieceId;
  private final String actionName;
  private final ImmutableMap<String, Object> input;
  private final @Nullable String connectionId;

  @JsonCreator
  public PieceStepConfig(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("nextStep") @Nullable String nextStep,
      @JsonProperty("pieceId") String pieceId,
      @JsonProperty("actionName") String actionName,
      @JsonProperty("input") Map<String, Object> input,
      @JsonProperty("connectionId") @Nullable String connectionId) {
    super(id, name, nextStep);
    this.pieceId = pieceId;
    this.actionName = actionName;
    this.input = ImmutableMap.copyOf(input);
    this.connectionId = connectionId;
  }

  @Override
  public StepType type() {
    return StepType.PIECE;
  }

  public String pieceId() {
    return pieceId;
  }

  public String actionName() {
    return actionName;
  }

  public ImmutableMap<String, Object> input() {
    return input;
  }

  public @Nullable String connectionId() {
    return connectionId;
  }
}
