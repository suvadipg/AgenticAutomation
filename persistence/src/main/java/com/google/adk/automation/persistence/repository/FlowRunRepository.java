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

import com.google.adk.automation.persistence.entity.FlowRunEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/** Thin data-access wrapper around {@link FlowRunEntity}. See {@link FlowRepository}. */
public final class FlowRunRepository {
  public void insert(EntityManager entityManager, FlowRunEntity run) {
    entityManager.persist(run);
  }

  public Optional<FlowRunEntity> findById(EntityManager entityManager, String id) {
    return Optional.ofNullable(entityManager.find(FlowRunEntity.class, id));
  }

  public List<FlowRunEntity> findByFlowId(EntityManager entityManager, String flowId) {
    return entityManager
        .createQuery(
            "SELECT r FROM FlowRunEntity r WHERE r.flowId = :flowId ORDER BY r.startedAt",
            FlowRunEntity.class)
        .setParameter("flowId", flowId)
        .getResultList();
  }
}
