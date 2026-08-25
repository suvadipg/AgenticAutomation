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

package com.google.adk.automation.persistence.service;

import com.google.adk.automation.persistence.Transactions;
import com.google.adk.automation.persistence.entity.WebhookRegistrationEntity;
import com.google.adk.automation.persistence.repository.WebhookRegistrationRepository;
import jakarta.persistence.EntityManagerFactory;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Mints and looks up webhook tokens. A new token is minted every time a flow with a {@code WEBHOOK}
 * trigger is published (see the backend module's {@code FlowPublicationService}) — republishing
 * rotates the token, so an old URL stops working once superseded.
 */
public final class WebhookRegistrationService {
  private final EntityManagerFactory entityManagerFactory;
  private final WebhookRegistrationRepository webhookRegistrationRepository =
      new WebhookRegistrationRepository();
  private final SecureRandom random = new SecureRandom();

  public WebhookRegistrationService(EntityManagerFactory entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;
  }

  public WebhookRegistrationEntity register(
      String flowId, String flowVersionId, WebhookRegistrationEntity.ResponseMode responseMode) {
    WebhookRegistrationEntity registration =
        new WebhookRegistrationEntity(
            UUID.randomUUID().toString(),
            flowId,
            flowVersionId,
            generateToken(),
            responseMode,
            Instant.now());
    Transactions.run(
        entityManagerFactory,
        entityManager -> {
          webhookRegistrationRepository.insert(entityManager, registration);
          return null;
        });
    return registration;
  }

  public Optional<WebhookRegistrationEntity> findByToken(String token) {
    return Transactions.run(
        entityManagerFactory,
        entityManager -> webhookRegistrationRepository.findByToken(entityManager, token));
  }

  private String generateToken() {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
