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

package com.google.adk.automation.engine;

import static com.google.common.truth.Truth.assertThat;

import com.google.adk.automation.engine.exec.ExecutionContext;
import com.google.adk.automation.engine.exec.ExpressionResolver;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import com.google.adk.automation.sdk.PieceRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ExpressionResolverTest {
  private final ExpressionResolver resolver = new ExpressionResolver();
  private ExecutionContext context;

  @BeforeEach
  void setUp() {
    context =
        new ExecutionContext("test-run", new PieceRegistry(), Map.of("name", "World"), Map.of());
    context.recordStepResult(
        StepResult.succeeded(
            "stepA", StepType.PIECE, Map.of(), Map.of("value", 42), Instant.now()));
  }

  @Test
  void resolve_stringWithNoExpression_isUnchanged() {
    assertThat(resolver.resolve("plain text", context)).isEqualTo("plain text");
  }

  @Test
  void resolve_wholeStringExpression_returnsRawTypedValue() {
    Object resolved = resolver.resolve("{{steps.stepA.output}}", context);

    assertThat(resolved).isEqualTo(Map.of("value", 42));
  }

  @Test
  void resolve_mixedTextAndExpression_returnsStringTemplate() {
    Object resolved = resolver.resolve("Hello {{trigger.output.name}}!", context);

    assertThat(resolved).isEqualTo("Hello World!");
  }

  @Test
  void resolve_nestedMapAndList_resolvesEachLeaf() {
    // A leaf string that is *entirely* one expression (like "{{steps.stepA.output.value}}")
    // resolves to the raw typed value (an Integer here), not a stringified template.
    Object resolved =
        resolver.resolve(
            Map.of(
                "greeting",
                "Hi {{trigger.output.name}}",
                "items",
                List.of("{{steps.stepA.output.value}}")),
            context);

    assertThat(resolved).isEqualTo(Map.of("greeting", "Hi World", "items", List.of(42)));
  }

  @Test
  void evaluateCondition_trueAndFalseCases() {
    assertThat(resolver.evaluateCondition("steps.stepA.output.value == 42", context)).isTrue();
    assertThat(resolver.evaluateCondition("steps.stepA.output.value == 1", context)).isFalse();
  }
}
