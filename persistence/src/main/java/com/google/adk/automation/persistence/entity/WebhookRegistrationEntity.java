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

/**
 * JPA entity for the {@code webhook_registrations} table: the random token minted when a flow with
 * a {@code WEBHOOK} trigger is published, and the response mode the {@code WebhookController}
 * should use when it's hit.
 */
@Entity
@Table(name = "webhook_registrations")
public class WebhookRegistrationEntity {
  /** How {@code WebhookController} responds to a request. */
  public enum ResponseMode {
    /** Waits for the flow run to finish and returns its result. */
    SYNC,
    /** Returns 202 + the run id immediately; the run continues in the background. */
    IMMEDIATE
  }

  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "flow_id", nullable = false)
  private String flowId;

  @Column(name = "flow_version_id", nullable = false)
  private String flowVersionId;

  @Column(name = "webhook_token", nullable = false, unique = true)
  private String webhookToken;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_mode", nullable = false, length = 16)
  private ResponseMode responseMode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** For JPA/Hibernate only. */
  protected WebhookRegistrationEntity() {}

  public WebhookRegistrationEntity(
      String id,
      String flowId,
      String flowVersionId,
      String webhookToken,
      ResponseMode responseMode,
      Instant createdAt) {
    this.id = id;
    this.flowId = flowId;
    this.flowVersionId = flowVersionId;
    this.webhookToken = webhookToken;
    this.responseMode = responseMode;
    this.createdAt = createdAt;
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

  public String getWebhookToken() {
    return webhookToken;
  }

  public ResponseMode getResponseMode() {
    return responseMode;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
