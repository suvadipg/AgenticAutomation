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

package com.google.adk.automation.engine.exec;

import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import com.google.adk.automation.sdk.Connection;
import com.google.adk.automation.sdk.Property;
import com.google.adk.automation.sdk.PropertyType;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Wraps an {@link ActionDefinition} as a real ADK {@link BaseTool}.
 *
 * <p>This is the concrete synergy point this design gets from being ADK-based rather than a
 * standalone engine: {@link ActionDefinition#execute}'s signature was chosen to match {@link
 * BaseTool#runAsync(Map, ToolContext)} almost exactly, so this class is a thin, near-zero-logic
 * adapter. The same {@link ActionDefinition} instance is called directly by {@code
 * PieceStepExecutor} for a deterministic {@code PIECE} step, and wrapped once here to be handed to
 * {@code LlmAgent.builder().tools(...)} for an {@code AI_AGENT} step — so an AI step can call "make
 * an HTTP request" as a genuine function-calling tool with no duplicated logic.
 */
public final class PieceActionTool extends BaseTool {
  private final ActionDefinition action;
  private final @Nullable Connection connection;

  public PieceActionTool(String pieceId, ActionDefinition action, @Nullable Connection connection) {
    super(toolName(pieceId, action.name()), action.description());
    this.action = action;
    this.connection = connection;
  }

  private static String toolName(String pieceId, String actionName) {
    return (pieceId + "_" + actionName).replaceAll("[^a-zA-Z0-9_]", "_");
  }

  @Override
  public Optional<FunctionDeclaration> declaration() {
    Map<String, Schema> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (Property<?> property : action.props().asMap().values()) {
      properties.put(property.key(), toSchema(property.type()));
      if (property.required()) {
        required.add(property.key());
      }
    }
    Schema parameters =
        Schema.builder().type("OBJECT").properties(properties).required(required).build();
    return Optional.of(
        FunctionDeclaration.builder()
            .name(name())
            .description(description())
            .parameters(parameters)
            .build());
  }

  @Override
  public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
    return action.execute(new ActionContext(args, connection));
  }

  private static Schema toSchema(PropertyType type) {
    String genaiType =
        switch (type) {
          case SHORT_TEXT, LONG_TEXT, SECRET_TEXT, OAUTH2, DROPDOWN -> "STRING";
          case NUMBER -> "NUMBER";
          case CHECKBOX -> "BOOLEAN";
          case JSON -> "OBJECT";
          case ARRAY -> "ARRAY";
        };
    return Schema.builder().type(genaiType).build();
  }
}
