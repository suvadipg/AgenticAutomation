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

package com.google.adk.automation.sdk;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;

/**
 * A stored credential for a piece (API key, OAuth2 token, basic auth, ...).
 *
 * <p>M1 keeps this as a plain in-memory value object with no encryption or persistence; M2 adds a
 * {@code connections} table with credentials encrypted at rest (see the project plan).
 */
public final class Connection {
  private final String id;
  private final String pieceId;
  private final ImmutableMap<String, Object> credentials;

  public Connection(String id, String pieceId, Map<String, Object> credentials) {
    this.id = id;
    this.pieceId = pieceId;
    this.credentials = ImmutableMap.copyOf(credentials);
  }

  public String id() {
    return id;
  }

  public String pieceId() {
    return pieceId;
  }

  public ImmutableMap<String, Object> credentials() {
    return credentials;
  }

  public Optional<Object> credential(String key) {
    return Optional.ofNullable(credentials.get(key));
  }
}
