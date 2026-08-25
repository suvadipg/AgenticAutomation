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
import org.jspecify.annotations.Nullable;

/**
 * How a {@link FlowDefinition} starts. {@code kind} is metadata only in M1 — {@code FlowExecutor}
 * runs the same way regardless of kind, given a trigger payload from the caller; a real scheduler
 * (for {@link Kind#CRON}) and HTTP listener (for {@link Kind#WEBHOOK}) that actually produce that
 * payload and call the executor are M3 work (see the project plan's trigger subsystem section).
 */
public final class TriggerConfig {
  /** How the flow is started. */
  public enum Kind {
    MANUAL,
    CRON,
    WEBHOOK
  }

  private final Kind kind;
  private final String firstStepId;
  private final @Nullable String cronExpression;

  @JsonCreator
  private TriggerConfig(
      @JsonProperty("kind") Kind kind,
      @JsonProperty("firstStepId") String firstStepId,
      @JsonProperty("cronExpression") @Nullable String cronExpression) {
    this.kind = kind;
    this.firstStepId = firstStepId;
    this.cronExpression = cronExpression;
  }

  public static TriggerConfig manual(String firstStepId) {
    return new TriggerConfig(Kind.MANUAL, firstStepId, null);
  }

  public static TriggerConfig cron(String firstStepId, String cronExpression) {
    return new TriggerConfig(Kind.CRON, firstStepId, cronExpression);
  }

  public static TriggerConfig webhook(String firstStepId) {
    return new TriggerConfig(Kind.WEBHOOK, firstStepId, null);
  }

  public Kind kind() {
    return kind;
  }

  public String firstStepId() {
    return firstStepId;
  }

  public @Nullable String cronExpression() {
    return cronExpression;
  }
}
