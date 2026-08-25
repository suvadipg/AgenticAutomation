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

package com.google.adk.automation.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.util.function.Function;

/**
 * Opens an {@link EntityManager}, runs one unit of work in a single {@code RESOURCE_LOCAL}
 * transaction, and commits or rolls back — the transaction-boundary helper every service method in
 * this module uses, so multi-repository-call operations (e.g. {@code FlowVersionService}'s publish,
 * which reads the draft, inserts a locked copy, and updates the flow's pointer) are atomic.
 */
public final class Transactions {
  private Transactions() {}

  public static <T> T run(EntityManagerFactory emf, Function<EntityManager, T> work) {
    EntityManager entityManager = emf.createEntityManager();
    EntityTransaction transaction = entityManager.getTransaction();
    try {
      transaction.begin();
      T result = work.apply(entityManager);
      transaction.commit();
      return result;
    } catch (RuntimeException e) {
      if (transaction.isActive()) {
        transaction.rollback();
      }
      throw e;
    } finally {
      entityManager.close();
    }
  }
}
