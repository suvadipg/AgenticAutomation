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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generic JSON (de)serialization for the plain value types this module stores as text columns:
 * trigger payloads and step input/output maps (ordinary {@code Map<String,Object>}, no special
 * handling needed) and {@code com.google.adk.automation.sdk.PauseMetadata} (non-bean-style
 * accessors like {@code reason()}, same issue {@code FlowDefinitionJsonCodec} solves for the
 * engine's model classes, so this mapper is configured the same way: field-visible, getters off).
 */
public final class PersistenceJson {
  private static final ObjectMapper MAPPER = buildMapper();

  private PersistenceJson() {}

  public static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize " + value.getClass(), e);
    }
  }

  public static <T> T fromJson(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize JSON as " + type, e);
    }
  }

  public static <T> T fromJson(String json, TypeReference<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize JSON as " + type, e);
    }
  }

  private static ObjectMapper buildMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    mapper.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);
    return mapper;
  }
}
