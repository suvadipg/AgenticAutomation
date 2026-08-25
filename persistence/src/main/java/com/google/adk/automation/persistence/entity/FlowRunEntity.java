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
 * JPA entity for the {@code flow_runs} table. Unlike the engine's in-memory {@code FlowRun.Status}
 * (which only models terminal outcomes, since it's only ever constructed once a run finishes), this
 * adds {@link Status#RUNNING} — {@code FlowRunService} inserts a row with that status *before*
 * calling {@code FlowExecutor}, then updates it to a terminal status once the run completes, so an
 * in-progress run is visible in storage the whole time it's executing.
 */
@Entity
@Table(name = "flow_runs")
public class FlowRunEntity {
  /** Run status as persisted — adds RUNNING, absent from the engine's terminal-only status. */
  public enum Status {
    RUNNING,
    SUCCEEDED,
    FAILED,
    PAUSED
  }

  /** Where the run was started from. */
  public enum TriggerSource {
    MANUAL,
    CRON,
    WEBHOOK
  }

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "flow_id", nullable = false)
  private String flowId;

  @Column(name = "flow_version_id", nullable = false)
  private String flowVersionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_source", nullable = false, length = 16)
  private TriggerSource triggerSource;

  @Column(name = "trigger_payload_json", nullable = false)
  private String triggerPayloadJson;

  @Column(name = "pause_metadata_json")
  private @Nullable String pauseMetadataJson;

  /**
   * Set only when {@link #status} is {@link Status#PAUSED} *and* the pause originated in a
   * top-level {@code PIECE}/{@code AI_AGENT} step (not nested inside a {@code LOOP_ON_ITEMS}/
   * {@code PARALLEL} child) — see {@code FlowRunService.resume}'s javadoc for why the nested case
   * can't be resumed yet. Null means "paused, but not resumable via {@code POST
   * /api/flow-runs/{id}/resume}".
   */
  @Column(name = "paused_at_step_id")
  private @Nullable String pausedAtStepId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private @Nullable Instant finishedAt;

  /** For JPA/Hibernate only. */
  protected FlowRunEntity() {}

  public FlowRunEntity(
      String id,
      String flowId,
      String flowVersionId,
      TriggerSource triggerSource,
      String triggerPayloadJson,
      Instant startedAt) {
    this.id = id;
    this.flowId = flowId;
    this.flowVersionId = flowVersionId;
    this.status = Status.RUNNING;
    this.triggerSource = triggerSource;
    this.triggerPayloadJson = triggerPayloadJson;
    this.startedAt = startedAt;
  }

  public String getId() {
    return id;
  }

  public String getFlowId() {
    return flowId;
  }

  public String getFlowVersionId() {
    return flowVersionId;
  }

  public Status getStatus() {
    return status;
  }

  public TriggerSource getTriggerSource() {
    return triggerSource;
  }

  public String getTriggerPayloadJson() {
    return triggerPayloadJson;
  }

  public @Nullable String getPauseMetadataJson() {
    return pauseMetadataJson;
  }

  public @Nullable String getPausedAtStepId() {
    return pausedAtStepId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public @Nullable Instant getFinishedAt() {
    return finishedAt;
  }

  public void markFinished(
      Status terminalStatus, @Nullable String pauseMetadataJson, Instant finishedAt) {
    markFinished(terminalStatus, pauseMetadataJson, null, finishedAt);
  }

  public void markFinished(
      Status terminalStatus,
      @Nullable String pauseMetadataJson,
      @Nullable String pausedAtStepId,
      Instant finishedAt) {
    this.status = terminalStatus;
    this.pauseMetadataJson = pauseMetadataJson;
    this.pausedAtStepId = pausedAtStepId;
    this.finishedAt = finishedAt;
  }
}
