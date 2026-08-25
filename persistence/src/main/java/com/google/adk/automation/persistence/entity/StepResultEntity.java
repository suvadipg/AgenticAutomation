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

package com.google.adk.automation.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * JPA entity for the {@code step_results} table — one row per {@code
 * com.google.adk.automation.engine.model.StepResult}, written by {@code FlowRunService} as each one
 * is produced (see {@code FlowRunListener}), covering both top-level steps and, tagged with {@code
 * iterationIndex}, loop-body children.
 */
@Entity
@Table(name = "step_results")
public class StepResultEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "flow_run_id", nullable = false)
  private String flowRunId;

  @Column(name = "step_id", nullable = false)
  private String stepId;

  @Column(name = "step_type", nullable = false, length = 16)
  private String stepType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private Status status;

  @Column(name = "input_json", nullable = false)
  private String inputJson;

  @Column(name = "output_json", nullable = false)
  private String outputJson;

  @Column(name = "error_message")
  private @Nullable String errorMessage;

  @Column(name = "iteration_index")
  private @Nullable Integer iterationIndex;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at", nullable = false)
  private Instant finishedAt;

  /** Mirrors {@code com.google.adk.automation.engine.model.StepResult.Status}. */
  public enum Status {
    SUCCEEDED,
    FAILED,
    PAUSED
  }

  /** For JPA/Hibernate only. */
  protected StepResultEntity() {}

  public StepResultEntity(
      String id,
      String flowRunId,
      String stepId,
      String stepType,
      Status status,
      String inputJson,
      String outputJson,
      @Nullable String errorMessage,
      @Nullable Integer iterationIndex,
      Instant startedAt,
      Instant finishedAt) {
    this.id = id;
    this.flowRunId = flowRunId;
    this.stepId = stepId;
    this.stepType = stepType;
    this.status = status;
    this.inputJson = inputJson;
    this.outputJson = outputJson;
    this.errorMessage = errorMessage;
    this.iterationIndex = iterationIndex;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
  }

  public String getId() {
    return id;
  }

  public String getFlowRunId() {
    return flowRunId;
  }

  public String getStepId() {
    return stepId;
  }

  public String getStepType() {
    return stepType;
  }

  public Status getStatus() {
    return status;
  }

  public String getInputJson() {
    return inputJson;
  }

  public String getOutputJson() {
    return outputJson;
  }

  public @Nullable String getErrorMessage() {
    return errorMessage;
  }

  public @Nullable Integer getIterationIndex() {
    return iterationIndex;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }
}
