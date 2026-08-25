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

package com.google.adk.automation.persistence;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.adk.automation.persistence.crypto.CredentialCipher;
import java.util.Arrays;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

final class CredentialCipherTest {
  @Test
  void encrypt_thenDecrypt_roundTripsPlaintext() {
    CredentialCipher cipher = new CredentialCipher(CredentialCipher.generateKey());

    byte[] encrypted = cipher.encrypt("{\"apiKey\":\"secret-123\"}");

    assertThat(cipher.decrypt(encrypted)).isEqualTo("{\"apiKey\":\"secret-123\"}");
  }

  @Test
  void encrypt_usesARandomIvEachCall_soCiphertextDiffersForTheSamePlaintext() {
    CredentialCipher cipher = new CredentialCipher(CredentialCipher.generateKey());

    byte[] first = cipher.encrypt("same-plaintext");
    byte[] second = cipher.encrypt("same-plaintext");

    assertThat(Arrays.equals(first, second)).isFalse();
  }

  @Test
  void decrypt_withADifferentKey_fails() {
    CredentialCipher encryptingCipher = new CredentialCipher(CredentialCipher.generateKey());
    CredentialCipher wrongKeyCipher = new CredentialCipher(CredentialCipher.generateKey());
    byte[] encrypted = encryptingCipher.encrypt("secret");

    assertThrows(IllegalStateException.class, () -> wrongKeyCipher.decrypt(encrypted));
  }

  @Test
  void keyToBase64_thenKeyFromBase64_roundTripsTheKeyItself() {
    SecretKey original = CredentialCipher.generateKey();
    SecretKey restored = CredentialCipher.keyFromBase64(CredentialCipher.keyToBase64(original));

    byte[] encrypted = new CredentialCipher(original).encrypt("hello");

    assertThat(new CredentialCipher(restored).decrypt(encrypted)).isEqualTo("hello");
  }
}
