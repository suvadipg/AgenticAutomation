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
 * JPA entity for the {@code flow_versions} table: one immutable snapshot of a {@link FlowEntity}'s
 * definition. {@code definitionJson} is the output of {@code FlowDefinitionJsonCodec.toJson},
 * stored as plain {@code TEXT} rather than Postgres's native {@code jsonb} type — binding a JSON
 * string to a {@code jsonb} column via a plain JDBC PreparedStatement requires a {@code
 * PGobject}/custom Hibernate {@code UserType} to avoid a type-mismatch error, which isn't worth the
 * complexity here since nothing queries *inside* the JSON (no jsonb operators/indexing needed) —
 * the whole blob is always read and written as a unit.
 */
@Entity
@Table(name = "flow_versions")
public class FlowVersionEntity {
  /** DRAFT is editable and never executed by a trigger; LOCKED is immutable and always run. */
  public enum Status {
    DRAFT,
    LOCKED
  }

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "flow_id", nullable = false)
  private String flowId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private Status status;

  @Column(name = "definition_json", nullable = false)
  private String definitionJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private @Nullable Instant publishedAt;

  /** For JPA/Hibernate only. */
  protected FlowVersionEntity() {}

  /** Creates a DRAFT row (the common case — see {@link #lockedCopyOf} for publishing). */
  public FlowVersionEntity(
      String id, String flowId, int versionNumber, String definitionJson, Instant createdAt) {
    this.id = id;
    this.flowId = flowId;
    this.versionNumber = versionNumber;
    this.status = Status.DRAFT;
    this.definitionJson = definitionJson;
    this.createdAt = createdAt;
  }

  /**
   * Publishing never mutates the draft row in place — it copies the draft's current {@code
   * definitionJson} into a brand new, immutable LOCKED row, so the draft stays independently
   * editable afterward.
   */
  public static FlowVersionEntity lockedCopyOf(
      String newId, FlowVersionEntity draft, int versionNumber, Instant publishedAt) {
    FlowVersionEntity locked =
        new FlowVersionEntity(
            newId, draft.flowId, versionNumber, draft.definitionJson, publishedAt);
    locked.status = Status.LOCKED;
    locked.publishedAt = publishedAt;
    return locked;
  }

  public String getId() {
    return id;
  }

  public String getFlowId() {
    return flowId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public Status getStatus() {
    return status;
  }

  public String getDefinitionJson() {
    return definitionJson;
  }

  public void setDefinitionJson(String definitionJson) {
    this.definitionJson = definitionJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public @Nullable Instant getPublishedAt() {
    return publishedAt;
  }
}
