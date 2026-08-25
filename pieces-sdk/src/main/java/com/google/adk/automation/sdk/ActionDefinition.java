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

import io.reactivex.rxjava3.core.Single;
import java.util.Map;

/**
 * A single callable operation exposed by a {@link Piece} (activepieces calls this {@code
 * createAction(...)}).
 *
 * <p>{@link #execute}'s signature is deliberately identical to {@code
 * com.google.adk.tools.BaseTool#runAsync(Map, ToolContext)} (modulo the {@code ToolContext}
 * parameter): this is what lets {@code PieceActionTool} in the engine module wrap any {@code
 * ActionDefinition} as a real ADK tool with a one-line delegating body, so the same action can be
 * called directly by the deterministic engine for a {@code PIECE} step *and* handed to an {@code
 * LlmAgent} as a function-calling tool for an {@code AI_AGENT} step.
 */
public interface ActionDefinition {
  /** Stable identifier, unique within the owning {@link Piece}. Used in flow definitions. */
  String name();

  String displayName();

  String description();

  /** The typed inputs this action accepts, in declaration order. */
  PropertyMap props();

  /**
   * Executes the action against already-resolved inputs ({@code context.input()}).
   *
   * @return a map of named outputs, addressable by later steps as {@code
   *     {{steps.stepId.output.someKey}}}.
   */
  Single<Map<String, Object>> execute(ActionContext context);
}
