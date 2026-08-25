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

package com.google.adk.automation.pieces.core;

import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import com.google.adk.automation.sdk.Piece;
import com.google.adk.automation.sdk.Property;
import com.google.adk.automation.sdk.PropertyMap;
import com.google.adk.automation.sdk.TriggerDefinition;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

/**
 * A built-in piece with a single {@code evaluate} action: runs a JEXL expression against a
 * caller-supplied variable map. This is the M1 stand-in for activepieces' {@code CODE} action type
 * (rather than a full embedded JS/TS sandbox); the same {@link JexlEngine} is reused by {@code
 * ExpressionResolver} and {@code RouterStepExecutor} in the engine module.
 */
public final class CodePiece implements Piece {
  private static final JexlEngine JEXL = new JexlBuilder().create();

  @Override
  public String id() {
    return "code";
  }

  @Override
  public String displayName() {
    return "Code";
  }

  @Override
  public String description() {
    return "Evaluates a JEXL expression against a variable map.";
  }

  @Override
  public List<ActionDefinition> actions() {
    return List.of(new EvaluateAction());
  }

  @Override
  public List<TriggerDefinition> triggers() {
    return List.of();
  }

  private static final class EvaluateAction implements ActionDefinition {
    @Override
    public String name() {
      return "evaluate";
    }

    @Override
    public String displayName() {
      return "Evaluate Expression";
    }

    @Override
    public String description() {
      return "Evaluates a JEXL expression and returns its result.";
    }

    @Override
    public PropertyMap props() {
      return PropertyMap.of(
          Property.longText(
              "expression", "Expression", "A JEXL expression, e.g. `input.a + input.b`.", true),
          Property.json(
              "variables", "Variables", "Variables the expression can reference.", false));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Single<Map<String, Object>> execute(ActionContext context) {
      return Single.fromCallable(
          () -> {
            String expression = context.require("expression", String.class);
            Map<String, Object> variables =
                (Map<String, Object>) context.get("variables").orElse(Map.of());
            JexlContext jexlContext = new MapContext();
            variables.forEach(jexlContext::set);
            Object result = JEXL.createExpression(expression).evaluate(jexlContext);
            return Map.of("result", result == null ? "" : result);
          });
    }
  }
}
