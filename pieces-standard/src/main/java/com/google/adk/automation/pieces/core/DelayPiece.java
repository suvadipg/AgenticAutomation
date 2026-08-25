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
import com.google.adk.automation.sdk.PauseMetadata;
import com.google.adk.automation.sdk.Piece;
import com.google.adk.automation.sdk.Property;
import com.google.adk.automation.sdk.PropertyMap;
import com.google.adk.automation.sdk.TriggerDefinition;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A built-in piece whose {@code wait} action pauses the flow run via {@link ActionContext#pause},
 * demonstrating the M1 pause contract (see {@code FlowExecutor}/{@code PauseMetadata} in the engine
 * module): durable resumption across process restarts is a persistence-layer concern for M2/M3, but
 * the in-flight control-flow contract (this action never returns a normal result; it always
 * short-circuits the executor) is real and testable today.
 */
public final class DelayPiece implements Piece {
  @Override
  public String id() {
    return "delay";
  }

  @Override
  public String displayName() {
    return "Delay";
  }

  @Override
  public String description() {
    return "Pauses the flow run for a fixed duration.";
  }

  @Override
  public List<ActionDefinition> actions() {
    return List.of(new WaitAction());
  }

  @Override
  public List<TriggerDefinition> triggers() {
    return List.of();
  }

  private static final class WaitAction implements ActionDefinition {
    @Override
    public String name() {
      return "wait";
    }

    @Override
    public String displayName() {
      return "Wait";
    }

    @Override
    public String description() {
      return "Pauses the run until the given number of seconds have elapsed.";
    }

    @Override
    public PropertyMap props() {
      return PropertyMap.of(
          Property.number(
              "seconds", "Seconds", "How long to pause for, in seconds.", /* required= */ true));
    }

    @Override
    public Single<Map<String, Object>> execute(ActionContext context) {
      return Single.fromCallable(
          () -> {
            double seconds = context.require("seconds", Number.class).doubleValue();
            context.pause(PauseMetadata.delay(Instant.now().plusMillis((long) (seconds * 1000))));
            throw new AssertionError("unreachable: pause() always throws");
          });
    }
  }
}
