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

/**
 * JPA entity for the {@code connections} table. {@code encryptedCredential} is AES-GCM ciphertext
 * (via {@code CredentialCipher}) of the credential map serialized as JSON — never stored in
 * plaintext.
 */
@Entity
@Table(name = "connections")
public class ConnectionEntity {
  @Id
  @Column(name = "id")
  private String id;

  @Column(name = "owner", nullable = false)
  private String owner;

  @Column(name = "piece_id", nullable = false)
  private String pieceId;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "encrypted_credential", nullable = false)
  private byte[] encryptedCredential;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** For JPA/Hibernate only. */
  protected ConnectionEntity() {}

  public ConnectionEntity(
      String id,
      String owner,
      String pieceId,
      String displayName,
      byte[] encryptedCredential,
      Instant createdAt) {
    this.id = id;
    this.owner = owner;
    this.pieceId = pieceId;
    this.displayName = displayName;
    this.encryptedCredential = encryptedCredential;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getOwner() {
    return owner;
  }

  public String getPieceId() {
    return pieceId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public byte[] getEncryptedCredential() {
    return encryptedCredential;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
