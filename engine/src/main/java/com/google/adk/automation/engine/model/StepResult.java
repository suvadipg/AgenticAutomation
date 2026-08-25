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

import com.google.adk.automation.sdk.PauseMetadata;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of executing one {@link StepConfig}, recorded into {@code ExecutionContext} (for
 * later steps' {@code {{steps.stepId.output...}}} interpolation) and into the owning {@code
 * FlowRun}'s history.
 */
public final class StepResult {
  /** Terminal state of one step execution. */
  public enum Status {
    SUCCEEDED,
    FAILED,
    PAUSED
  }

  private final String stepId;
  private final StepType stepType;
  private final Status status;
  private final ImmutableMap<String, Object> input;
  private final ImmutableMap<String, Object> output;
  private final @Nullable String errorMessage;
  private final @Nullable PauseMetadata pauseMetadata;
  private final @Nullable Integer iterationIndex;
  private final Instant startedAt;
  private final Instant finishedAt;

  private StepResult(
      String stepId,
      StepType stepType,
      Status status,
      Map<String, Object> input,
      Map<String, Object> output,
      @Nullable String errorMessage,
      @Nullable PauseMetadata pauseMetadata,
      @Nullable Integer iterationIndex,
      Instant startedAt,
      Instant finishedAt) {
    this.stepId = stepId;
    this.stepType = stepType;
    this.status = status;
    this.input = ImmutableMap.copyOf(input);
    this.output = ImmutableMap.copyOf(output);
    this.errorMessage = errorMessage;
    this.pauseMetadata = pauseMetadata;
    this.iterationIndex = iterationIndex;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
  }

  public static StepResult succeeded(
      String stepId,
      StepType stepType,
      Map<String, Object> input,
      Map<String, Object> output,
      Instant startedAt) {
    return new StepResult(
        stepId,
        stepType,
        Status.SUCCEEDED,
        input,
        output,
        null,
        null,
        null,
        startedAt,
        Instant.now());
  }

  public static StepResult failed(
      String stepId,
      StepType stepType,
      Map<String, Object> input,
      Throwable error,
      Instant startedAt) {
    return new StepResult(
        stepId,
        stepType,
        Status.FAILED,
        input,
        Map.of(),
        String.valueOf(error.getMessage()),
        null,
        null,
        startedAt,
        Instant.now());
  }

  public static StepResult paused(
      String stepId,
      StepType stepType,
      Map<String, Object> input,
      PauseMetadata pauseMetadata,
      Instant startedAt) {
    return new StepResult(
        stepId,
        stepType,
        Status.PAUSED,
        input,
        Map.of(),
        null,
        pauseMetadata,
        null,
        startedAt,
        Instant.now());
  }

  public StepResult withIterationIndex(int iterationIndex) {
    return new StepResult(
        stepId,
        stepType,
        status,
        input,
        output,
        errorMessage,
        pauseMetadata,
        iterationIndex,
        startedAt,
        finishedAt);
  }

  public String stepId() {
    return stepId;
  }

  public StepType stepType() {
    return stepType;
  }

  public Status status() {
    return status;
  }

  public ImmutableMap<String, Object> input() {
    return input;
  }

  public ImmutableMap<String, Object> output() {
    return output;
  }

  public @Nullable String errorMessage() {
    return errorMessage;
  }

  public @Nullable PauseMetadata pauseMetadata() {
    return pauseMetadata;
  }

  public @Nullable Integer iterationIndex() {
    return iterationIndex;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant finishedAt() {
    return finishedAt;
  }
}
