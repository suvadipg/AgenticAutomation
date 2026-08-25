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
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A single typed, form-renderable input for an {@link ActionDefinition} or {@link
 * TriggerDefinition}.
 *
 * <p>This is the Java analogue of activepieces' {@code Property} system: a piece author declares
 * {@code Property} instances instead of hand-writing UI, and both the deterministic engine (input
 * validation/coercion) and the eventual visual builder (auto-generated property forms) are driven
 * by the same metadata.
 */
public final class Property<T> {
  private final PropertyType type;
  private final String key;
  private final String displayName;
  private final String description;
  private final boolean required;
  private final @Nullable T defaultValue;
  private final ImmutableList<DropdownOption<T>> options;

  private Property(
      PropertyType type,
      String key,
      String displayName,
      String description,
      boolean required,
      @Nullable T defaultValue,
      List<DropdownOption<T>> options) {
    this.type = type;
    this.key = key;
    this.displayName = displayName;
    this.description = description;
    this.required = required;
    this.defaultValue = defaultValue;
    this.options = ImmutableList.copyOf(options);
  }

  public static Property<String> shortText(
      String key, String displayName, String description, boolean required) {
    return new Property<>(
        PropertyType.SHORT_TEXT, key, displayName, description, required, null, List.of());
  }

  public static Property<String> longText(
      String key, String displayName, String description, boolean required) {
    return new Property<>(
        PropertyType.LONG_TEXT, key, displayName, description, required, null, List.of());
  }

  public static Property<Double> number(
      String key, String displayName, String description, boolean required) {
    return new Property<>(
        PropertyType.NUMBER, key, displayName, description, required, null, List.of());
  }

  public static Property<Double> number(
      String key, String displayName, String description, boolean required, double defaultValue) {
    return new Property<>(
        PropertyType.NUMBER, key, displayName, description, required, defaultValue, List.of());
  }

  public static Property<Boolean> checkbox(
      String key, String displayName, String description, boolean required, boolean defaultValue) {
    return new Property<>(
        PropertyType.CHECKBOX, key, displayName, description, required, defaultValue, List.of());
  }

  public static <T> Property<T> dropdown(
      String key,
      String displayName,
      String description,
      boolean required,
      List<DropdownOption<T>> options) {
    return new Property<>(
        PropertyType.DROPDOWN, key, displayName, description, required, null, options);
  }

  public static Property<Object> json(
      String key, String displayName, String description, boolean required) {
    return new Property<>(
        PropertyType.JSON, key, displayName, description, required, null, List.of());
  }

  public static Property<List<Object>> array(
      String key, String displayName, String description, boolean required) {
    return new Property<>(
        PropertyType.ARRAY, key, displayName, description, required, null, List.of());
  }

  public static Property<String> secretText(
      String key, String displayName, String description, boolean required) {
    return new Property<>(
        PropertyType.SECRET_TEXT, key, displayName, description, required, null, List.of());
  }

  public static Property<String> oauth2(
      String key, String displayName, String description, boolean required) {
    return new Property<>(
        PropertyType.OAUTH2, key, displayName, description, required, null, List.of());
  }

  public PropertyType type() {
    return type;
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }

  public String description() {
    return description;
  }

  public boolean required() {
    return required;
  }

  public @Nullable T defaultValue() {
    return defaultValue;
  }

  public ImmutableList<DropdownOption<T>> options() {
    return options;
  }

  /** A single choice in a {@link PropertyType#DROPDOWN} property. */
  public record DropdownOption<T>(String label, T value) {}
}
