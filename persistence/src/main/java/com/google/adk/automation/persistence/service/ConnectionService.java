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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.automation.persistence.PersistenceJson;
import com.google.adk.automation.persistence.Transactions;
import com.google.adk.automation.persistence.crypto.CredentialCipher;
import com.google.adk.automation.persistence.entity.ConnectionEntity;
import com.google.adk.automation.persistence.repository.ConnectionRepository;
import com.google.adk.automation.sdk.Connection;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stores and retrieves {@link Connection} credentials, encrypting/decrypting them via {@link
 * CredentialCipher} so the credential map is never persisted in plaintext.
 */
public final class ConnectionService {
  private final EntityManagerFactory entityManagerFactory;
  private final CredentialCipher cipher;
  private final ConnectionRepository connectionRepository = new ConnectionRepository();

  public ConnectionService(EntityManagerFactory entityManagerFactory, CredentialCipher cipher) {
    this.entityManagerFactory = entityManagerFactory;
    this.cipher = cipher;
  }

  public Connection save(
      String owner, String pieceId, String displayName, Map<String, Object> credentials) {
    String id = UUID.randomUUID().toString();
    byte[] encrypted = cipher.encrypt(PersistenceJson.toJson(credentials));

    Transactions.run(
        entityManagerFactory,
        entityManager -> {
          connectionRepository.insert(
              entityManager,
              new ConnectionEntity(id, owner, pieceId, displayName, encrypted, Instant.now()));
          return null;
        });

    return new Connection(id, pieceId, credentials);
  }

  public Connection load(String connectionId) {
    return Transactions.run(
        entityManagerFactory,
        entityManager -> {
          ConnectionEntity entity =
              connectionRepository
                  .findById(entityManager, connectionId)
                  .orElseThrow(
                      () -> new IllegalStateException("No such connection: " + connectionId));
          Map<String, Object> credentials =
              PersistenceJson.fromJson(
                  cipher.decrypt(entity.getEncryptedCredential()),
                  new TypeReference<Map<String, Object>>() {});
          return new Connection(entity.getId(), entity.getPieceId(), credentials);
        });
  }
}
