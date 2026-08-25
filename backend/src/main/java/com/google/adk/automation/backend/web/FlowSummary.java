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

package com.google.adk.automation.backend.web;

import com.google.adk.automation.persistence.entity.FlowEntity;
import org.jspecify.annotations.Nullable;

/**
 * A plain, bean-friendly response DTO for {@code FlowEntity} — deliberately never returning {@code
 * FlowDefinition}/{@code StepConfig} objects directly over HTTP. Those types are serialized via a
 * special field-visibility {@code ObjectMapper} ({@code FlowDefinitionJsonCodec}), not Spring's
 * default one, so mixing them into normal {@code @RestController} responses (which use Spring's
 * default Jackson configuration) would silently serialize to {@code {}}. See {@link FlowController}
 * for how a flow's actual definition JSON is returned instead (as a raw string, bypassing Spring's
 * converter).
 */
public record FlowSummary(
    String id,
    String name,
    String owner,
    @Nullable String draftVersionId,
    @Nullable String lockedVersionId) {
  public static FlowSummary of(FlowEntity flow) {
    return new FlowSummary(
        flow.getId(),
        flow.getName(),
        flow.getOwner(),
        flow.getCurrentDraftVersionId(),
        flow.getCurrentLockedVersionId());
  }
}
