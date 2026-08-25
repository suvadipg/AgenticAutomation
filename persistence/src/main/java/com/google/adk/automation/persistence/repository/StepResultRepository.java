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

import com.google.adk.automation.persistence.entity.StepResultEntity;
import jakarta.persistence.EntityManager;
import java.util.List;

/** Thin data-access wrapper around {@link StepResultEntity}. See {@link FlowRepository}. */
public final class StepResultRepository {
  public void insert(EntityManager entityManager, StepResultEntity result) {
    entityManager.persist(result);
  }

  public List<StepResultEntity> findByFlowRunId(EntityManager entityManager, String flowRunId) {
    return entityManager
        .createQuery(
            "SELECT s FROM StepResultEntity s WHERE s.flowRunId = :flowRunId ORDER BY s.startedAt",
            StepResultEntity.class)
        .setParameter("flowRunId", flowRunId)
        .getResultList();
  }
}
