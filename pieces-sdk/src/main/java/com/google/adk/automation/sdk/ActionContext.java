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
import org.jspecify.annotations.Nullable;

/**
 * The context an {@link ActionDefinition#execute} or {@link TriggerDefinition#poll} call runs with:
 * already-resolved input values (any {@code {{...}}} expressions have been substituted by the
 * engine before this is constructed) and, if the piece declares auth, the {@link Connection} to
 * use.
 */
public final class ActionContext {
  private final ImmutableMap<String, Object> input;
  private final @Nullable Connection connection;

  public ActionContext(Map<String, Object> input, @Nullable Connection connection) {
    this.input = ImmutableMap.copyOf(input);
    this.connection = connection;
  }

  public ImmutableMap<String, Object> input() {
    return input;
  }

  public Optional<Connection> connection() {
    return Optional.ofNullable(connection);
  }

  public Optional<Object> get(String key) {
    return Optional.ofNullable(input.get(key));
  }

  /**
   * Returns the input value for {@code key}, cast to {@code type}.
   *
   * @throws IllegalArgumentException if the key is missing or the value isn't assignable to {@code
   *     type}.
   */
  public <T> T require(String key, Class<T> type) {
    Object value = input.get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing required input '" + key + "'");
    }
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException(
          "Input '" + key + "' expected " + type.getSimpleName() + " but was " + value.getClass());
    }
    return type.cast(value);
  }

  /** Short-circuits the current step into a paused flow run. See {@link FlowPausedException}. */
  public void pause(PauseMetadata metadata) {
    throw new FlowPausedException(metadata);
  }
}
