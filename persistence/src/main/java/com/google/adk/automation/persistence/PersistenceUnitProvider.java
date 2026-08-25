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

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Map;
import org.flywaydb.core.Flyway;

/**
 * Runs Flyway migrations against a Postgres database, then builds the {@code agentic-automation}
 * JPA {@link EntityManagerFactory} bound to it.
 *
 * <p>Deliberately framework-agnostic (no Spring): this module is meant to be usable standalone (as
 * in this M2, via a CLI/tests) and later wrapped by a Spring Boot backend (M3) without rewriting
 * it.
 */
public final class PersistenceUnitProvider {
  private PersistenceUnitProvider() {}

  /** Migrates the schema (idempotent — safe to call every startup) and opens an EMF. */
  public static EntityManagerFactory create(PostgresConfig config) {
    Flyway.configure()
        .dataSource(config.jdbcUrl(), config.username(), config.password())
        .load()
        .migrate();

    Map<String, Object> overrides =
        Map.of(
            "jakarta.persistence.jdbc.url", config.jdbcUrl(),
            "jakarta.persistence.jdbc.user", config.username(),
            "jakarta.persistence.jdbc.password", config.password(),
            "jakarta.persistence.jdbc.driver", "org.postgresql.Driver");

    return Persistence.createEntityManagerFactory("agentic-automation", overrides);
  }
}
