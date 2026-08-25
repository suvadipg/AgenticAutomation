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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An ordered, key-addressable set of {@link Property} definitions for one action or trigger.
 *
 * <p>Order is preserved (backed by a {@link LinkedHashMap}) so the future property-form renderer
 * shows fields in the order the piece author declared them.
 */
public final class PropertyMap {
  private final ImmutableMap<String, Property<?>> properties;

  private PropertyMap(Map<String, Property<?>> properties) {
    this.properties = ImmutableMap.copyOf(properties);
  }

  public static PropertyMap of(Property<?>... properties) {
    Map<String, Property<?>> byKey = new LinkedHashMap<>();
    for (Property<?> property : properties) {
      byKey.put(property.key(), property);
    }
    return new PropertyMap(byKey);
  }

  public static PropertyMap empty() {
    return new PropertyMap(Map.of());
  }

  public Optional<Property<?>> get(String key) {
    return Optional.ofNullable(properties.get(key));
  }

  public ImmutableMap<String, Property<?>> asMap() {
    return properties;
  }
}
