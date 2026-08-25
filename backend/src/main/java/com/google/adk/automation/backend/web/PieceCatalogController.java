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

package com.google.adk.automation.backend.web;

import com.google.adk.automation.sdk.PieceMetadata;
import com.google.adk.automation.sdk.PieceRegistry;
import com.google.common.collect.ImmutableList;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The piece catalog: every action/trigger + its typed properties, for a future frontend to render a
 * palette and auto-generate property forms from. {@link PieceMetadata} is explicitly designed to be
 * Jackson-serializable through Spring's default converter (see its test in {@code pieces-sdk}),
 * unlike the engine's execution model.
 */
@RestController
@RequestMapping("/api/pieces")
public class PieceCatalogController {
  private final PieceRegistry pieceRegistry;

  public PieceCatalogController(PieceRegistry pieceRegistry) {
    this.pieceRegistry = pieceRegistry;
  }

  @GetMapping
  public ImmutableList<PieceMetadata> list() {
    return pieceRegistry.allMetadata();
  }

  @GetMapping("/{pieceId}")
  public PieceMetadata get(@PathVariable String pieceId) {
    return pieceRegistry
        .get(pieceId)
        .map(PieceMetadata::of)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such piece: " + pieceId));
  }
}
