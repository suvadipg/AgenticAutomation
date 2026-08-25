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

import com.google.common.collect.ImmutableList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * A name-addressable set of {@link Piece}s, the SDK-level analogue of {@code
 * com.google.adk.utils.ComponentRegistry} (same "register by name, look up by name" pattern; a
 * separate class rather than a subclass since piece metadata has no equivalent in {@code
 * ComponentRegistry}).
 *
 * <p>Unlike {@code ComponentRegistry}, this is not a global singleton: tests build a small registry
 * with just the pieces they need via {@link #register}, while production code loads every piece on
 * the classpath via {@link #fromServiceLoader()}.
 */
public final class PieceRegistry {
  private final Map<String, Piece> piecesById = new LinkedHashMap<>();
  private final Map<String, ActionDefinition> actionsByQualifiedName = new LinkedHashMap<>();

  public PieceRegistry() {}

  /**
   * Builds a registry from every {@link Piece} discoverable via {@code ServiceLoader} (i.e. every
   * piece jar with a {@code META-INF/services/com.google.adk.automation.sdk.Piece} entry on the
   * classpath) — the Java analogue of "every installed npm piece package".
   */
  public static PieceRegistry fromServiceLoader() {
    PieceRegistry registry = new PieceRegistry();
    for (Piece piece : ServiceLoader.load(Piece.class)) {
      registry.register(piece);
    }
    return registry;
  }

  public PieceRegistry register(Piece piece) {
    piecesById.put(piece.id(), piece);
    for (ActionDefinition action : piece.actions()) {
      actionsByQualifiedName.put(qualifiedActionName(piece.id(), action.name()), action);
    }
    return this;
  }

  public Optional<Piece> get(String pieceId) {
    return Optional.ofNullable(piecesById.get(pieceId));
  }

  public Optional<ActionDefinition> getAction(String pieceId, String actionName) {
    return Optional.ofNullable(
        actionsByQualifiedName.get(qualifiedActionName(pieceId, actionName)));
  }

  public ImmutableList<Piece> allPieces() {
    return ImmutableList.copyOf(piecesById.values());
  }

  public ImmutableList<PieceMetadata> allMetadata() {
    return piecesById.values().stream()
        .map(PieceMetadata::of)
        .collect(ImmutableList.toImmutableList());
  }

  private static String qualifiedActionName(String pieceId, String actionName) {
    return pieceId + "." + actionName;
  }
}
