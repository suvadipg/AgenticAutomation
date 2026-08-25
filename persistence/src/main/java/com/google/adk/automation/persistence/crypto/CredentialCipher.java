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

package com.google.adk.automation.persistence.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption for {@code connections.encrypted_credential} — piece credentials (API
 * keys, OAuth2 tokens, ...) are never written to the database in plaintext. The 12-byte random IV
 * is stored alongside the ciphertext (prepended) rather than in a separate column, since GCM
 * requires a fresh IV per encryption but doesn't require it to be secret.
 *
 * <p>The key is app-managed for M2 (see {@link #generateKey}/{@link #keyFromBase64}); a KMS-backed
 * key is later hardening, not a blocker for exercising real encrypt/decrypt now.
 */
public final class CredentialCipher {
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  public CredentialCipher(SecretKey key) {
    this.key = key;
  }

  public static SecretKey generateKey() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(256);
      return generator.generateKey();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES not available", e);
    }
  }

  public static SecretKey keyFromBase64(String base64Key) {
    return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
  }

  public static String keyToBase64(SecretKey key) {
    return Base64.getEncoder().encodeToString(key.getEncoded());
  }

  public byte[] encrypt(String plaintext) {
    try {
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      random.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt credential", e);
    }
  }

  public String decrypt(byte[] stored) {
    try {
      ByteBuffer buffer = ByteBuffer.wrap(stored);
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt credential", e);
    }
  }
}
