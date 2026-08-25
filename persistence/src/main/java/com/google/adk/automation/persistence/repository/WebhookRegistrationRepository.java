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

package com.google.adk.automation.persistence.repository;

import com.google.adk.automation.persistence.entity.WebhookRegistrationEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.util.Optional;

/**
 * Thin data-access wrapper around {@link WebhookRegistrationEntity}. See {@link FlowRepository}.
 */
public final class WebhookRegistrationRepository {
  public void insert(EntityManager entityManager, WebhookRegistrationEntity registration) {
    entityManager.persist(registration);
  }

  public Optional<WebhookRegistrationEntity> findByToken(
      EntityManager entityManager, String token) {
    try {
      return Optional.of(
          entityManager
              .createQuery(
                  "SELECT w FROM WebhookRegistrationEntity w WHERE w.webhookToken = :token",
                  WebhookRegistrationEntity.class)
              .setParameter("token", token)
              .getSingleResult());
    } catch (NoResultException e) {
      return Optional.empty();
    }
  }

  public Optional<WebhookRegistrationEntity> findByFlowId(
      EntityManager entityManager, String flowId) {
    return entityManager
        .createQuery(
            "SELECT w FROM WebhookRegistrationEntity w WHERE w.flowId = :flowId ORDER BY"
                + " w.createdAt DESC",
            WebhookRegistrationEntity.class)
        .setParameter("flowId", flowId)
        .setMaxResults(1)
        .getResultStream()
        .findFirst();
  }
}
