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

package com.google.adk.automation.persistence.repository;

import com.google.adk.automation.persistence.entity.FlowVersionEntity;
import jakarta.persistence.EntityManager;
import java.util.Optional;

/** Thin data-access wrapper around {@link FlowVersionEntity}. See {@link FlowRepository}. */
public final class FlowVersionRepository {
  public void insert(EntityManager entityManager, FlowVersionEntity version) {
    entityManager.persist(version);
  }

  public Optional<FlowVersionEntity> findById(EntityManager entityManager, String id) {
    return Optional.ofNullable(entityManager.find(FlowVersionEntity.class, id));
  }

  /** Used to number a newly published LOCKED row {@code count + 1}; the DRAFT row is unnumbered. */
  public int countLockedByFlowId(EntityManager entityManager, String flowId) {
    return entityManager
        .createQuery(
            "SELECT COUNT(v) FROM FlowVersionEntity v WHERE v.flowId = :flowId AND v.status ="
                + " :status",
            Long.class)
        .setParameter("flowId", flowId)
        .setParameter("status", FlowVersionEntity.Status.LOCKED)
        .getSingleResult()
        .intValue();
  }
}
