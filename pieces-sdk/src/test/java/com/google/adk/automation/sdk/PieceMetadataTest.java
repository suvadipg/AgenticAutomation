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

import static com.google.common.truth.Truth.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link PieceMetadata} is meant to be returned directly from a REST controller (see the backend
 * module's {@code PieceCatalogController}), so — unlike the engine's execution model, which needs a
 * specially field-visibility-configured mapper — it must serialize correctly through a *plain*,
 * default-configured {@link ObjectMapper}, the same one Spring Boot wires up automatically. This
 * guards the bug that a first attempt at this class had: getters without explicit
 * {@code @JsonProperty} silently serialize to {@code {}} under default (bean-visibility-only)
 * Jackson settings, since none of this SDK's accessors follow the {@code getXxx()} naming
 * convention.
 */
final class PieceMetadataTest {
  private final ObjectMapper plainMapper = new ObjectMapper();

  @Test
  void serialize_withPlainDefaultObjectMapper_producesRealFieldValues() throws Exception {
    Piece piece =
        new Piece() {
          @Override
          public String id() {
            return "test-piece";
          }

          @Override
          public String displayName() {
            return "Test Piece";
          }

          @Override
          public String description() {
            return "A piece for testing metadata serialization.";
          }

          @Override
          public List<ActionDefinition> actions() {
            return List.of();
          }

          @Override
          public List<TriggerDefinition> triggers() {
            return List.of();
          }
        };

    String json = plainMapper.writeValueAsString(PieceMetadata.of(piece));

    assertThat(json).contains("\"id\":\"test-piece\"");
    assertThat(json).contains("\"displayName\":\"Test Piece\"");
    assertThat(json).contains("\"description\":\"A piece for testing metadata serialization.\"");
  }

  @Test
  void serialize_actionWithProperties_includesPropertyFields() throws Exception {
    ActionDefinition action =
        new ActionDefinition() {
          @Override
          public String name() {
            return "doThing";
          }

          @Override
          public String displayName() {
            return "Do Thing";
          }

          @Override
          public String description() {
            return "Does the thing.";
          }

          @Override
          public PropertyMap props() {
            return PropertyMap.of(
                Property.shortText("input", "Input", "The input value.", /* required= */ true));
          }

          @Override
          public io.reactivex.rxjava3.core.Single<java.util.Map<String, Object>> execute(
              ActionContext context) {
            throw new UnsupportedOperationException("not needed for this test");
          }
        };
    Piece piece =
        new Piece() {
          @Override
          public String id() {
            return "test-piece";
          }

          @Override
          public String displayName() {
            return "Test Piece";
          }

          @Override
          public String description() {
            return "desc";
          }

          @Override
          public List<ActionDefinition> actions() {
            return List.of(action);
          }

          @Override
          public List<TriggerDefinition> triggers() {
            return List.of();
          }
        };

    String json = plainMapper.writeValueAsString(PieceMetadata.of(piece));

    assertThat(json).contains("\"name\":\"doThing\"");
    assertThat(json).contains("\"key\":\"input\"");
    assertThat(json).contains("\"type\":\"SHORT_TEXT\"");
    assertThat(json).contains("\"required\":true");
  }
}
