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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;

/**
 * Resolves {@code {{expression}}} placeholders against an {@link ExecutionContext}, backed by
 * Apache Commons JEXL. Also used to evaluate {@code ROUTER} branch conditions (see {@link
 * #evaluateCondition}).
 *
 * <p>A value that is *entirely* one {@code {{...}}} expression (e.g. {@code "{{steps.a.output}}"}
 * for a step input that should be a whole map, not a string) evaluates to the raw typed result. A
 * value with a {@code {{...}}} expression mixed into surrounding text (e.g. {@code "Hello
 * {{trigger.output.name}}!"}) is evaluated as a string template: each expression's result is
 * stringified and substituted in place.
 */
public final class ExpressionResolver {
  private static final Pattern EXPRESSION = Pattern.compile("\\{\\{(.+?)}}");

  private final JexlEngine jexl = new JexlBuilder().create();

  /**
   * Recursively resolves {@code {{...}}} expressions in a value, a {@link Map}, or a {@link List}.
   */
  public Object resolve(Object rawValue, ExecutionContext context) {
    if (rawValue instanceof String string) {
      return resolveString(string, context);
    }
    if (rawValue instanceof Map<?, ?> map) {
      Map<Object, Object> resolved = new LinkedHashMap<>();
      map.forEach((key, value) -> resolved.put(key, resolve(value, context)));
      return resolved;
    }
    if (rawValue instanceof List<?> list) {
      return list.stream().map(item -> resolve(item, context)).collect(Collectors.toList());
    }
    return rawValue;
  }

  public Map<String, Object> resolveMap(Map<String, Object> rawValues, ExecutionContext context) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    rawValues.forEach((key, value) -> resolved.put(key, resolve(value, context)));
    return resolved;
  }

  public Object evaluate(String expression, ExecutionContext context) {
    return jexl.createExpression(expression.strip()).evaluate(context.toJexlContext());
  }

  public boolean evaluateCondition(String expression, ExecutionContext context) {
    return Boolean.TRUE.equals(evaluate(expression, context));
  }

  private Object resolveString(String value, ExecutionContext context) {
    Matcher fullMatch = EXPRESSION.matcher(value.strip());
    if (fullMatch.matches()) {
      return evaluate(fullMatch.group(1), context);
    }

    Matcher matcher = EXPRESSION.matcher(value);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;
    while (matcher.find()) {
      result.append(value, lastEnd, matcher.start());
      Object evaluated = evaluate(matcher.group(1), context);
      result.append(evaluated == null ? "" : String.valueOf(evaluated));
      lastEnd = matcher.end();
    }
    result.append(value.substring(lastEnd));
    return result.toString();
  }
}
