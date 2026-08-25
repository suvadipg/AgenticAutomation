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

import com.google.adk.automation.persistence.entity.FlowEntity;
import jakarta.persistence.EntityManager;
import java.util.Optional;

/**
 * Thin data-access wrapper around {@link FlowEntity}. Methods take the caller's {@link
 * EntityManager} rather than owning one, so a service method can compose several repository calls
 * into a single transaction (see {@code Transactions}).
 */
public final class FlowRepository {
  public void insert(EntityManager entityManager, FlowEntity flow) {
    entityManager.persist(flow);
  }

  public Optional<FlowEntity> findById(EntityManager entityManager, String id) {
    return Optional.ofNullable(entityManager.find(FlowEntity.class, id));
  }
}
