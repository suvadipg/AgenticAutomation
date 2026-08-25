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

-- Records which step id a run paused at, so it can be resumed later. Only set when a top-level
-- PIECE or AI_AGENT step pauses; a pause originating inside a LOOP_ON_ITEMS/PARALLEL child's
-- nested execution is not (yet) individually resumable, so this stays null for those runs — see
-- FlowRunService.resume's javadoc.
ALTER TABLE flow_runs ADD COLUMN paused_at_step_id VARCHAR(255);
