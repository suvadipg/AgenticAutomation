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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA entity for the {@code flows} table: a named flow and pointers to its current versions. */
@Entity
@Table(name = "flows")
public class FlowEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "owner", nullable = false)
  private String owner;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "current_draft_version_id")
  private @Nullable String currentDraftVersionId;

  @Column(name = "current_locked_version_id")
  private @Nullable String currentLockedVersionId;

  /** For JPA/Hibernate only. */
  protected FlowEntity() {}

  public FlowEntity(String id, String name, String owner, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.owner = owner;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getOwner() {
    return owner;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public @Nullable String getCurrentDraftVersionId() {
    return currentDraftVersionId;
  }

  public void setCurrentDraftVersionId(@Nullable String currentDraftVersionId) {
    this.currentDraftVersionId = currentDraftVersionId;
  }

  public @Nullable String getCurrentLockedVersionId() {
    return currentLockedVersionId;
  }

  public void setCurrentLockedVersionId(@Nullable String currentLockedVersionId) {
    this.currentLockedVersionId = currentLockedVersionId;
  }
}
