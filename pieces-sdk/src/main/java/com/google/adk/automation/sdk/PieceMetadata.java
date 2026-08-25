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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A Jackson-serializable snapshot of a {@link Piece}'s shape: every action/trigger it exposes and
 * every property each one accepts. This is the JSON schema a future frontend consumes to render a
 * piece palette and auto-generate per-step property forms — adding a new piece never requires
 * frontend code changes, only a new {@link Piece} implementation.
 */
public final class PieceMetadata {
  private final String id;
  private final String displayName;
  private final String description;
  private final ImmutableList<PropertyMetadata> authProperties;
  private final ImmutableList<ActionMetadata> actions;
  private final ImmutableList<TriggerMetadata> triggers;

  @JsonCreator
  public PieceMetadata(
      @JsonProperty("id") String id,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("description") String description,
      @JsonProperty("authProperties") List<PropertyMetadata> authProperties,
      @JsonProperty("actions") List<ActionMetadata> actions,
      @JsonProperty("triggers") List<TriggerMetadata> triggers) {
    this.id = id;
    this.displayName = displayName;
    this.description = description;
    this.authProperties = ImmutableList.copyOf(authProperties);
    this.actions = ImmutableList.copyOf(actions);
    this.triggers = ImmutableList.copyOf(triggers);
  }

  public static PieceMetadata of(Piece piece) {
    List<PropertyMetadata> authProperties =
        piece.authProps().map(PieceMetadata::toPropertyMetadata).orElseGet(List::of);

    List<ActionMetadata> actions =
        piece.actions().stream()
            .map(
                action ->
                    new ActionMetadata(
                        action.name(),
                        action.displayName(),
                        action.description(),
                        toPropertyMetadata(action.props())))
            .toList();

    List<TriggerMetadata> triggers =
        piece.triggers().stream()
            .map(
                trigger ->
                    new TriggerMetadata(
                        trigger.name(),
                        trigger.displayName(),
                        trigger.description(),
                        trigger.type(),
                        toPropertyMetadata(trigger.props())))
            .toList();

    return new PieceMetadata(
        piece.id(), piece.displayName(), piece.description(), authProperties, actions, triggers);
  }

  private static List<PropertyMetadata> toPropertyMetadata(PropertyMap props) {
    return props.asMap().values().stream().map(PropertyMetadata::of).toList();
  }

  @JsonProperty("id")
  public String id() {
    return id;
  }

  @JsonProperty("displayName")
  public String displayName() {
    return displayName;
  }

  @JsonProperty("description")
  public String description() {
    return description;
  }

  @JsonProperty("authProperties")
  public ImmutableList<PropertyMetadata> authProperties() {
    return authProperties;
  }

  @JsonProperty("actions")
  public ImmutableList<ActionMetadata> actions() {
    return actions;
  }

  @JsonProperty("triggers")
  public ImmutableList<TriggerMetadata> triggers() {
    return triggers;
  }

  /** Metadata for one {@link ActionDefinition}. */
  public static final class ActionMetadata {
    private final String name;
    private final String displayName;
    private final String description;
    private final ImmutableList<PropertyMetadata> properties;

    @JsonCreator
    public ActionMetadata(
        @JsonProperty("name") String name,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("properties") List<PropertyMetadata> properties) {
      this.name = name;
      this.displayName = displayName;
      this.description = description;
      this.properties = ImmutableList.copyOf(properties);
    }

    @JsonProperty("name")
    public String name() {
      return name;
    }

    @JsonProperty("displayName")
    public String displayName() {
      return displayName;
    }

    @JsonProperty("description")
    public String description() {
      return description;
    }

    @JsonProperty("properties")
    public ImmutableList<PropertyMetadata> properties() {
      return properties;
    }
  }

  /** Metadata for one {@link TriggerDefinition}. */
  public static final class TriggerMetadata {
    private final String name;
    private final String displayName;
    private final String description;
    private final TriggerType type;
    private final ImmutableList<PropertyMetadata> properties;

    @JsonCreator
    public TriggerMetadata(
        @JsonProperty("name") String name,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("type") TriggerType type,
        @JsonProperty("properties") List<PropertyMetadata> properties) {
      this.name = name;
      this.displayName = displayName;
      this.description = description;
      this.type = type;
      this.properties = ImmutableList.copyOf(properties);
    }

    @JsonProperty("name")
    public String name() {
      return name;
    }

    @JsonProperty("displayName")
    public String displayName() {
      return displayName;
    }

    @JsonProperty("description")
    public String description() {
      return description;
    }

    @JsonProperty("type")
    public TriggerType type() {
      return type;
    }

    @JsonProperty("properties")
    public ImmutableList<PropertyMetadata> properties() {
      return properties;
    }
  }

  /** Metadata for one {@link Property}. */
  public static final class PropertyMetadata {
    private final String key;
    private final String displayName;
    private final String description;
    private final boolean required;
    private final PropertyType type;
    private final @Nullable Object defaultValue;
    private final ImmutableList<DropdownOptionMetadata> options;

    @JsonCreator
    public PropertyMetadata(
        @JsonProperty("key") String key,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("required") boolean required,
        @JsonProperty("type") PropertyType type,
        @JsonProperty("defaultValue") @Nullable Object defaultValue,
        @JsonProperty("options") List<DropdownOptionMetadata> options) {
      this.key = key;
      this.displayName = displayName;
      this.description = description;
      this.required = required;
      this.type = type;
      this.defaultValue = defaultValue;
      this.options = ImmutableList.copyOf(options);
    }

    static PropertyMetadata of(Property<?> property) {
      List<DropdownOptionMetadata> options =
          property.options().stream()
              .map(option -> new DropdownOptionMetadata(option.label(), option.value()))
              .toList();
      return new PropertyMetadata(
          property.key(),
          property.displayName(),
          property.description(),
          property.required(),
          property.type(),
          property.defaultValue(),
          options);
    }

    @JsonProperty("key")
    public String key() {
      return key;
    }

    @JsonProperty("displayName")
    public String displayName() {
      return displayName;
    }

    @JsonProperty("description")
    public String description() {
      return description;
    }

    @JsonProperty("required")
    public boolean required() {
      return required;
    }

    @JsonProperty("type")
    public PropertyType type() {
      return type;
    }

    @JsonProperty("defaultValue")
    public @Nullable Object defaultValue() {
      return defaultValue;
    }

    @JsonProperty("options")
    public ImmutableList<DropdownOptionMetadata> options() {
      return options;
    }
  }

  /** Metadata for one {@link Property.DropdownOption}. */
  public record DropdownOptionMetadata(
      @JsonProperty("label") String label, @JsonProperty("value") Object value) {}
}
