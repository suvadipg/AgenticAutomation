// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Types in this file mirror the backend's JSON shapes exactly (field names, not just meaning).
// Two different sources of truth, deliberately:
//  - FlowDefinition/StepConfig/TriggerConfig below mirror the *field* names of the engine's Java
//    classes, because FlowDefinitionJsonCodec serializes by field, not by getter (see its
//    javadoc in the engine module) — e.g. StepConfig's inherited `nextStep` field always appears,
//    even on RouterStepConfig where it's unused (always null there; use `defaultNextStep`).
//  - PieceMetadata/FlowSummary/FlowRunSummary/TestRunResult mirror backend DTOs that were built
//    to be plain, bean-style JSON on purpose (see the backend module's README) — nothing special
//    about those.

// ---- Piece catalog (GET /api/pieces) ----

export type PropertyType =
  | 'SHORT_TEXT'
  | 'LONG_TEXT'
  | 'NUMBER'
  | 'CHECKBOX'
  | 'DROPDOWN'
  | 'JSON'
  | 'ARRAY'
  | 'SECRET_TEXT'
  | 'OAUTH2'

export interface DropdownOptionMetadata {
  label: string
  value: unknown
}

export interface PropertyMetadata {
  key: string
  displayName: string
  description: string
  required: boolean
  type: PropertyType
  defaultValue: unknown
  options: DropdownOptionMetadata[]
}

export interface ActionMetadata {
  name: string
  displayName: string
  description: string
  properties: PropertyMetadata[]
}

export type TriggerType = 'WEBHOOK' | 'POLLING' | 'APP_WEBHOOK' | 'EMPTY'

export interface TriggerMetadata {
  name: string
  displayName: string
  description: string
  type: TriggerType
  properties: PropertyMetadata[]
}

export interface PieceMetadata {
  id: string
  displayName: string
  description: string
  authProperties: PropertyMetadata[]
  actions: ActionMetadata[]
  triggers: TriggerMetadata[]
}

// ---- Flow definition (engine model — see the field-vs-getter note above) ----

export type StepType = 'PIECE' | 'AI_AGENT' | 'ROUTER' | 'LOOP_ON_ITEMS' | 'PARALLEL'

interface StepConfigBase {
  id: string
  name: string
  nextStep: string | null
}

export interface PieceStepConfig extends StepConfigBase {
  type: 'PIECE'
  pieceId: string
  actionName: string
  input: Record<string, unknown>
  connectionId: string | null
}

export interface AiAgentStepConfig extends StepConfigBase {
  type: 'AI_AGENT'
  instruction: string
  model: string
  allowedTools: string[]
}

export interface RouterBranch {
  conditionExpression: string
  nextStep: string
}

export interface RouterStepConfig extends StepConfigBase {
  type: 'ROUTER'
  // nextStep is always null on a router (see StepConfigBase) — branches/defaultNextStep decide.
  branches: RouterBranch[]
  defaultNextStep: string | null
}

export interface LoopOnItemsStepConfig extends StepConfigBase {
  type: 'LOOP_ON_ITEMS'
  itemsExpression: string
  loopBody: StepConfig[]
}

/**
 * Runs each of `branches` concurrently, mapped conceptually to ADK's `ParallelAgent`. A branch
 * may only contain PIECE/ROUTER steps on the backend (see ParallelStepConfig's javadoc in the
 * engine module for the shared-state reasons AI_AGENT/LOOP_ON_ITEMS/nested PARALLEL are
 * rejected) — the branch editor in PropertyPanel only offers PIECE steps accordingly.
 */
export interface ParallelStepConfig extends StepConfigBase {
  type: 'PARALLEL'
  branches: StepConfig[][]
}

export type StepConfig =
  | PieceStepConfig
  | AiAgentStepConfig
  | RouterStepConfig
  | LoopOnItemsStepConfig
  | ParallelStepConfig

export type TriggerKind = 'MANUAL' | 'CRON' | 'WEBHOOK'

export interface TriggerConfig {
  kind: TriggerKind
  firstStepId: string
  cronExpression: string | null
}

export interface FlowDefinition {
  id: string
  name: string
  trigger: TriggerConfig
  steps: StepConfig[]
}

// ---- Backend REST DTOs ----

export interface FlowSummary {
  id: string
  name: string
  owner: string
  draftVersionId: string | null
  lockedVersionId: string | null
}

export interface PublishResult {
  versionId: string
  versionNumber: number
  webhookToken: string | null
}

export type RunStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'PAUSED'

export interface StepResultView {
  stepId: string
  stepType: StepType
  status: 'SUCCEEDED' | 'FAILED' | 'PAUSED'
  errorMessage: string | null
  iterationIndex: number | null
  input: Record<string, unknown>
  output: Record<string, unknown>
}

export interface TestRunResult {
  status: RunStatus
  steps: StepResultView[]
}

export type TriggerSource = 'MANUAL' | 'CRON' | 'WEBHOOK'

export interface FlowRunSummary {
  id: string
  flowId: string
  flowVersionId: string
  status: RunStatus
  triggerSource: TriggerSource
  steps: StepResultView[]
}
