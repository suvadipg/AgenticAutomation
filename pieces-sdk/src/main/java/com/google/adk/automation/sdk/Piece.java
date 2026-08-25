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

import java.util.List;
import java.util.Optional;

/**
 * A bundle of {@link ActionDefinition}s and {@link TriggerDefinition}s for one integration
 * (activepieces' unit of packaging, normally one npm package per piece). In this SDK, one piece is
 * one Java class implementing this interface, discovered via {@link PieceRegistry} through {@code
 * java.util.ServiceLoader} — the Java-idiomatic equivalent of "one package per piece" without
 * dynamic classloading.
 */
public interface Piece {
  /** Stable identifier, unique across the registry (e.g. {@code "http"}, {@code "slack"}). */
  String id();

  String displayName();

  String description();

  /**
   * Typed properties required to authenticate with this integration (API key, OAuth2 token, ...),
   * or empty if the piece needs no auth (as with the built-in {@code http}/{@code delay}/ {@code
   * code} pieces). Kept as a plain {@link PropertyMap} rather than a separate auth-type hierarchy
   * for M1 — none of the M1 standard pieces need auth, so a fuller {@code AuthDefinition} (OAuth2
   * flow metadata, etc.) is deferred until a piece actually needs one.
   */
  default Optional<PropertyMap> authProps() {
    return Optional.empty();
  }

  List<ActionDefinition> actions();

  List<TriggerDefinition> triggers();
}
