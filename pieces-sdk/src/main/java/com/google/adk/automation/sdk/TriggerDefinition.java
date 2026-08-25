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
import java.util.List;
import java.util.Map;

/**
 * A named event source exposed by a {@link Piece} that can start a flow run (activepieces calls
 * this {@code createTrigger(...)}).
 */
public interface TriggerDefinition {
  String name();

  String displayName();

  String description();

  TriggerType type();

  /** The typed inputs this trigger accepts (e.g. a polling interval, a webhook filter). */
  PropertyMap props();

  /** Called once when a flow using this trigger is published. No-op unless overridden. */
  default void onEnable(ActionContext context) {}

  /** Called once when a flow using this trigger is unpublished/deleted. No-op unless overridden. */
  default void onDisable(ActionContext context) {}

  /**
   * For {@link TriggerType#POLLING} triggers: called on a schedule, returns zero or more new
   * trigger payloads (each starts a separate flow run). Not called for other trigger types.
   */
  default Single<List<Map<String, Object>>> poll(ActionContext context) {
    return Single.just(List.of());
  }
}
