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

import static com.google.common.truth.Truth.assertThat;

import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CodePieceTest {
  @Test
  void execute_evaluatesExpressionAgainstVariables() {
    ActionDefinition evaluateAction = new CodePiece().actions().get(0);

    Map<String, Object> output =
        evaluateAction
            .execute(
                new ActionContext(
                    Map.of("expression", "'sum=' + (a + b)", "variables", Map.of("a", 2, "b", 3)),
                    null))
            .blockingGet();

    assertThat(output.get("result")).isEqualTo("sum=5");
  }
}
