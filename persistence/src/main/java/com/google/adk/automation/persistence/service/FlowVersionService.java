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

package com.google.adk.automation.persistence.service;

import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowDefinitionJsonCodec;
import com.google.adk.automation.persistence.Transactions;
import com.google.adk.automation.persistence.entity.FlowEntity;
import com.google.adk.automation.persistence.entity.FlowVersionEntity;
import com.google.adk.automation.persistence.repository.FlowRepository;
import com.google.adk.automation.persistence.repository.FlowVersionRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.UUID;

/**
 * Owns the DRAFT/LOCKED lifecycle for a flow: editing always upserts the single DRAFT row; {@link
 * #publish} copies its current {@code definitionJson} into a brand new, immutable LOCKED row rather
 * than mutating the draft in place, so the draft stays independently editable afterward and
 * trigger-fired runs (which always read {@link #getLockedDefinition}) are unaffected by concurrent
 * edits. The DRAFT row itself is unnumbered ({@code versionNumber == 0} — see {@link
 * FlowVersionRepository#countLockedByFlowId}); only LOCKED rows get sequential version numbers, one
 * per publish.
 */
public final class FlowVersionService {
  private final EntityManagerFactory entityManagerFactory;
  private final FlowRepository flowRepository = new FlowRepository();
  private final FlowVersionRepository flowVersionRepository = new FlowVersionRepository();

  public FlowVersionService(EntityManagerFactory entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;
  }

  /**
   * Creates the flow (if this is the first call for {@code flowId}) or updates its existing DRAFT's
   * definition in place. Returns the DRAFT row either way.
   */
  public FlowVersionEntity upsertDraft(
      String flowId, String name, String owner, FlowDefinition definition) {
    return Transactions.run(
        entityManagerFactory,
        entityManager -> {
          Instant now = Instant.now();
          String definitionJson = FlowDefinitionJsonCodec.toJson(definition);

          FlowEntity flow = flowRepository.findById(entityManager, flowId).orElse(null);
          if (flow == null) {
            flow = new FlowEntity(flowId, name, owner, now);
            flowRepository.insert(entityManager, flow);
          }

          String existingDraftId = flow.getCurrentDraftVersionId();
          if (existingDraftId != null) {
            FlowVersionEntity draft =
                flowVersionRepository
                    .findById(entityManager, existingDraftId)
                    .orElseThrow(
                        () -> new IllegalStateException("Missing draft row: " + existingDraftId));
            draft.setDefinitionJson(definitionJson);
            flow.setUpdatedAt(now);
            return draft;
          }

          String draftId = UUID.randomUUID().toString();
          FlowVersionEntity draft =
              new FlowVersionEntity(draftId, flowId, /* versionNumber= */ 0, definitionJson, now);
          flowVersionRepository.insert(entityManager, draft);
          flow.setCurrentDraftVersionId(draftId);
          flow.setUpdatedAt(now);
          return draft;
        });
  }

  /** Locks the current draft into a new immutable version. Throws if there's no draft yet. */
  public FlowVersionEntity publish(String flowId) {
    return Transactions.run(
        entityManagerFactory,
        entityManager -> {
          FlowEntity flow =
              flowRepository
                  .findById(entityManager, flowId)
                  .orElseThrow(() -> new IllegalStateException("No such flow: " + flowId));
          String draftId = flow.getCurrentDraftVersionId();
          if (draftId == null) {
            throw new IllegalStateException("Flow has no draft to publish: " + flowId);
          }
          FlowVersionEntity draft =
              flowVersionRepository
                  .findById(entityManager, draftId)
                  .orElseThrow(() -> new IllegalStateException("Missing draft row: " + draftId));

          int nextVersionNumber =
              flowVersionRepository.countLockedByFlowId(entityManager, flowId) + 1;
          Instant now = Instant.now();
          FlowVersionEntity locked =
              FlowVersionEntity.lockedCopyOf(
                  UUID.randomUUID().toString(), draft, nextVersionNumber, now);
          flowVersionRepository.insert(entityManager, locked);

          flow.setCurrentLockedVersionId(locked.getId());
          flow.setUpdatedAt(now);
          return locked;
        });
  }

  /** The flow's editable draft — used for "test run" from the builder, not trigger-fired runs. */
  public FlowDefinition getDraftDefinition(String flowId) {
    return FlowDefinitionJsonCodec.fromJson(
        requireVersion(flowId, FlowEntity::getCurrentDraftVersionId, "draft"));
  }

  /** The flow's current published version — what every trigger-fired run always executes. */
  public FlowDefinition getLockedDefinition(String flowId) {
    return FlowDefinitionJsonCodec.fromJson(
        requireVersion(flowId, FlowEntity::getCurrentLockedVersionId, "locked"));
  }

  public String getLockedVersionId(String flowId) {
    return requireVersionId(flowId, FlowEntity::getCurrentLockedVersionId, "locked");
  }

  /**
   * The definition for one *specific* version id, regardless of whether it's still the flow's
   * current draft/locked pointer. {@code FlowRunService.resume} uses this rather than {@link
   * #getLockedDefinition} because a paused run must resume against the exact version it started on
   * — if the flow was republished while the run was paused, {@link #getLockedDefinition} would
   * return the *new* version instead, silently resuming against a different flow shape.
   */
  public FlowDefinition getDefinitionByVersionId(String flowVersionId) {
    return Transactions.run(
        entityManagerFactory,
        entityManager ->
            flowVersionRepository
                .findById(entityManager, flowVersionId)
                .map(v -> FlowDefinitionJsonCodec.fromJson(v.getDefinitionJson()))
                .orElseThrow(
                    () -> new IllegalStateException("No such flow version: " + flowVersionId)));
  }

  public java.util.Optional<FlowEntity> findFlow(String flowId) {
    return Transactions.run(
        entityManagerFactory, entityManager -> flowRepository.findById(entityManager, flowId));
  }

  private String requireVersion(
      String flowId, java.util.function.Function<FlowEntity, String> versionIdOf, String label) {
    String versionId = requireVersionId(flowId, versionIdOf, label);
    return Transactions.run(
        entityManagerFactory,
        entityManager ->
            flowVersionRepository
                .findById(entityManager, versionId)
                .orElseThrow(
                    () -> new IllegalStateException("Missing " + label + " row: " + versionId))
                .getDefinitionJson());
  }

  private String requireVersionId(
      String flowId, java.util.function.Function<FlowEntity, String> versionIdOf, String label) {
    return Transactions.run(
        entityManagerFactory,
        entityManager -> {
          FlowEntity flow =
              flowRepository
                  .findById(entityManager, flowId)
                  .orElseThrow(() -> new IllegalStateException("No such flow: " + flowId));
          String versionId = versionIdOf.apply(flow);
          if (versionId == null) {
            throw new IllegalStateException("Flow has no " + label + " version: " + flowId);
          }
          return versionId;
        });
  }
}
