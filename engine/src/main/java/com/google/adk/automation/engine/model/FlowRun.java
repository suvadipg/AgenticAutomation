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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The result of one execution of a {@link FlowDefinition}: an ordered {@link StepResult} history
 * plus a terminal {@link Status}. M1 builds and returns this in memory; M2 persists it
 * incrementally as steps complete (see the project plan's persistence section) so a UI can show
 * live progress rather than only a finished run.
 */
public final class FlowRun {
  /** Terminal state of a flow run. */
  public enum Status {
    SUCCEEDED,
    FAILED,
    PAUSED
  }

  private final String id;
  private final String flowId;
  private final Status status;
  private final ImmutableMap<String, Object> triggerPayload;
  private final ImmutableList<StepResult> stepResults;
  private final @Nullable PauseMetadata pauseMetadata;
  private final Instant startedAt;
  private final Instant finishedAt;

  private FlowRun(
      String id,
      String flowId,
      Status status,
      Map<String, Object> triggerPayload,
      List<StepResult> stepResults,
      @Nullable PauseMetadata pauseMetadata,
      Instant startedAt,
      Instant finishedAt) {
    this.id = id;
    this.flowId = flowId;
    this.status = status;
    this.triggerPayload = ImmutableMap.copyOf(triggerPayload);
    this.stepResults = ImmutableList.copyOf(stepResults);
    this.pauseMetadata = pauseMetadata;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
  }

  public static FlowRun succeeded(
      String id,
      String flowId,
      Map<String, Object> triggerPayload,
      List<StepResult> stepResults,
      Instant startedAt) {
    return new FlowRun(
        id, flowId, Status.SUCCEEDED, triggerPayload, stepResults, null, startedAt, Instant.now());
  }

  public static FlowRun failed(
      String id,
      String flowId,
      Map<String, Object> triggerPayload,
      List<StepResult> stepResults,
      Instant startedAt) {
    return new FlowRun(
        id, flowId, Status.FAILED, triggerPayload, stepResults, null, startedAt, Instant.now());
  }

  public static FlowRun paused(
      String id,
      String flowId,
      Map<String, Object> triggerPayload,
      List<StepResult> stepResults,
      PauseMetadata pauseMetadata,
      Instant startedAt) {
    return new FlowRun(
        id,
        flowId,
        Status.PAUSED,
        triggerPayload,
        stepResults,
        pauseMetadata,
        startedAt,
        Instant.now());
  }

  public String id() {
    return id;
  }

  public String flowId() {
    return flowId;
  }

  public Status status() {
    return status;
  }

  public ImmutableMap<String, Object> triggerPayload() {
    return triggerPayload;
  }

  public ImmutableList<StepResult> stepResults() {
    return stepResults;
  }

  public @Nullable PauseMetadata pauseMetadata() {
    return pauseMetadata;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant finishedAt() {
    return finishedAt;
  }
}
