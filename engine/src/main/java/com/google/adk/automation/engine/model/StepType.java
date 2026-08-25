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

package com.google.adk.automation.engine.model;

/**
 * The kind of a {@link StepConfig}, mirroring activepieces' action-type discriminated union with
 * two additions: {@code AI_AGENT}, a step backed by a real ADK {@code LlmAgent} rather than a fixed
 * deterministic operation; and {@code PARALLEL} (M5), mapped conceptually to ADK's {@code
 * ParallelAgent} — runs its branches concurrently rather than sequentially. {@code CODE} folds into
 * {@code PIECE} for M1 via the built-in {@code code} piece rather than being its own type.
 */
public enum StepType {
  PIECE,
  AI_AGENT,
  ROUTER,
  LOOP_ON_ITEMS,
  PARALLEL
}
