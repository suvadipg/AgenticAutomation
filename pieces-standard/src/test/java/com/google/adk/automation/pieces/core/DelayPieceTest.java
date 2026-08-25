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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import com.google.adk.automation.sdk.FlowPausedException;
import com.google.adk.automation.sdk.PauseMetadata;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DelayPieceTest {
  @Test
  void execute_alwaysPauses() {
    ActionDefinition waitAction = new DelayPiece().actions().get(0);

    FlowPausedException thrown =
        assertThrows(
            FlowPausedException.class,
            () ->
                waitAction.execute(new ActionContext(Map.of("seconds", 5.0), null)).blockingGet());

    assertThat(thrown.metadata().reason()).isEqualTo(PauseMetadata.Reason.DELAY);
    assertThat(thrown.metadata().resumeAt()).isNotNull();
  }
}
