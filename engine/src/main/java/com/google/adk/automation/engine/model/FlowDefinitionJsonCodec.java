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

package com.google.adk.automation.engine.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts a {@link FlowDefinition} to/from the JSON text stored in {@code
 * flow_versions.definition_json} by the persistence module.
 *
 * <p>The engine's model classes ({@link StepConfig} and its subtypes, {@link FlowDefinition},
 * {@link TriggerConfig}) use non-bean-style accessors (e.g. {@code id()}, not {@code getId()}), so
 * the shared {@link ObjectMapper} here is configured to serialize via direct field access instead
 * of relying on getter-name conventions — construction for deserialization still goes through each
 * class's {@code @JsonCreator} constructor, since fields are final.
 */
public final class FlowDefinitionJsonCodec {
  private static final ObjectMapper MAPPER = buildMapper();

  private FlowDefinitionJsonCodec() {}

  public static String toJson(FlowDefinition flow) {
    try {
      return MAPPER.writeValueAsString(flow);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize FlowDefinition " + flow.id(), e);
    }
  }

  public static FlowDefinition fromJson(String json) {
    try {
      return MAPPER.readValue(json, FlowDefinition.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize FlowDefinition JSON", e);
    }
  }

  private static ObjectMapper buildMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    mapper.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.CREATOR, Visibility.ANY);
    return mapper;
  }
}
