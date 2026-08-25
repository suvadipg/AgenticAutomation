-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Flow definitions are stored as a JSON blob (definition_json, plain TEXT rather than the
-- Postgres `jsonb` type — see FlowVersionEntity's javadoc for why) rather than modeled
-- relationally, so new step/piece types never require a migration here.

CREATE TABLE flows (
  id                         VARCHAR(64)  PRIMARY KEY,
  name                       VARCHAR(255) NOT NULL,
  owner                      VARCHAR(255) NOT NULL,
  created_at                 TIMESTAMPTZ  NOT NULL,
  updated_at                 TIMESTAMPTZ  NOT NULL,
  current_draft_version_id   VARCHAR(64),
  current_locked_version_id  VARCHAR(64)
);

CREATE TABLE flow_versions (
  id               VARCHAR(64)  PRIMARY KEY,
  flow_id          VARCHAR(64)  NOT NULL REFERENCES flows (id),
  version_number   INT          NOT NULL,
  status           VARCHAR(16)  NOT NULL, -- DRAFT | LOCKED
  definition_json  TEXT         NOT NULL,
  created_at       TIMESTAMPTZ  NOT NULL,
  published_at     TIMESTAMPTZ
);
CREATE INDEX idx_flow_versions_flow_id ON flow_versions (flow_id);

CREATE TABLE flow_runs (
  id                    VARCHAR(64)  PRIMARY KEY,
  flow_id               VARCHAR(64)  NOT NULL REFERENCES flows (id),
  flow_version_id       VARCHAR(64)  NOT NULL REFERENCES flow_versions (id),
  status                VARCHAR(16)  NOT NULL, -- RUNNING | SUCCEEDED | FAILED | PAUSED
  trigger_source        VARCHAR(16)  NOT NULL, -- MANUAL | CRON | WEBHOOK
  trigger_payload_json  TEXT         NOT NULL,
  pause_metadata_json   TEXT,
  started_at            TIMESTAMPTZ  NOT NULL,
  finished_at           TIMESTAMPTZ
);
CREATE INDEX idx_flow_runs_flow_id ON flow_runs (flow_id);

CREATE TABLE step_results (
  id               VARCHAR(64)  PRIMARY KEY,
  flow_run_id      VARCHAR(64)  NOT NULL REFERENCES flow_runs (id),
  step_id          VARCHAR(255) NOT NULL,
  step_type        VARCHAR(16)  NOT NULL,
  status           VARCHAR(16)  NOT NULL,
  input_json       TEXT         NOT NULL,
  output_json      TEXT         NOT NULL,
  error_message    TEXT,
  iteration_index  INT,
  started_at       TIMESTAMPTZ  NOT NULL,
  finished_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_step_results_flow_run_id ON step_results (flow_run_id);

-- encrypted_credential holds AES-GCM ciphertext (see CredentialCipher) of the connection's
-- credential map, serialized as JSON before encryption.
CREATE TABLE connections (
  id                     VARCHAR(64)  PRIMARY KEY,
  owner                  VARCHAR(255) NOT NULL,
  piece_id               VARCHAR(255) NOT NULL,
  display_name           VARCHAR(255) NOT NULL,
  encrypted_credential   BYTEA        NOT NULL,
  created_at             TIMESTAMPTZ  NOT NULL
);

-- Lifecycle (create/delete) is tied to a flow version's publish/unpublish; no separate CRUD API
-- until the webhook listener itself is built (M3). Included in the schema now so adding it later
-- doesn't require a migration.
CREATE TABLE webhook_registrations (
  id               VARCHAR(64)  PRIMARY KEY,
  flow_id          VARCHAR(64)  NOT NULL REFERENCES flows (id),
  flow_version_id  VARCHAR(64)  NOT NULL REFERENCES flow_versions (id),
  webhook_token    VARCHAR(128) NOT NULL UNIQUE,
  response_mode    VARCHAR(16)  NOT NULL, -- IMMEDIATE | SYNC
  created_at       TIMESTAMPTZ  NOT NULL
);
