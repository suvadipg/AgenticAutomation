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

// The canonical, in-editor representation of a flow being built, and pure functions over it.
//
// Deliberately a single source of truth: a step's `nextStep`/`branches`/`defaultNextStep` fields
// *are* the wiring — there's no separate "edges" state to keep in sync. The canvas (FlowCanvas)
// only ever *derives* React Flow nodes/edges from a FlowDraft (toReactFlow) and translates user
// interactions (drag a connection, delete an edge) back into FlowDraft updates (connectSteps,
// disconnectStep) — it never mutates edges directly.
//
// Node positions are cosmetic only and are not part of FlowDefinition (the backend doesn't store
// layout) — see autoLayout, used whenever a draft is freshly loaded from the backend.

import type { Edge, Node } from '@xyflow/react'
import type { FlowDefinition, RouterStepConfig, StepConfig, TriggerConfig } from '../api/types'

export const TRIGGER_NODE_ID = '__trigger__'

export interface FlowDraft {
  trigger: TriggerConfig
  steps: StepConfig[]
}

export function emptyDraft(): FlowDraft {
  return { trigger: { kind: 'MANUAL', firstStepId: '', cronExpression: null }, steps: [] }
}

export function toFlowDefinition(draft: FlowDraft, id: string, name: string): FlowDefinition {
  return { id, name, trigger: draft.trigger, steps: draft.steps }
}

export function fromFlowDefinition(definition: FlowDefinition): FlowDraft {
  return { trigger: definition.trigger, steps: definition.steps }
}

let stepCounter = 0
export function newStepId(prefix: string): string {
  stepCounter += 1
  return `${prefix}-${Date.now().toString(36)}-${stepCounter}`
}

export function createStep(type: StepConfig['type'], id: string): StepConfig {
  const base = { id, name: id, nextStep: null }
  switch (type) {
    case 'PIECE':
      return { ...base, type: 'PIECE', pieceId: '', actionName: '', input: {}, connectionId: null }
    case 'AI_AGENT':
      return { ...base, type: 'AI_AGENT', instruction: '', model: 'gemini-3.6-flash', allowedTools: [] }
    case 'ROUTER':
      return { ...base, type: 'ROUTER', branches: [], defaultNextStep: null }
    case 'LOOP_ON_ITEMS':
      return { ...base, type: 'LOOP_ON_ITEMS', itemsExpression: '[]', loopBody: [] }
    case 'PARALLEL':
      return { ...base, type: 'PARALLEL', branches: [] }
  }
}

export function addStep(draft: FlowDraft, step: StepConfig): FlowDraft {
  return { ...draft, steps: [...draft.steps, step] }
}

export function removeStep(draft: FlowDraft, stepId: string): FlowDraft {
  const steps = draft.steps
    .filter((s) => s.id !== stepId)
    .map((s) => unlinkReferencesTo(s, stepId))
  const trigger =
    draft.trigger.firstStepId === stepId ? { ...draft.trigger, firstStepId: '' } : draft.trigger
  return { trigger, steps }
}

function unlinkReferencesTo(step: StepConfig, removedId: string): StepConfig {
  if (step.type === 'ROUTER') {
    return {
      ...step,
      branches: step.branches.filter((b) => b.nextStep !== removedId),
      defaultNextStep: step.defaultNextStep === removedId ? null : step.defaultNextStep,
    }
  }
  return step.nextStep === removedId ? { ...step, nextStep: null } : step
}

export function updateStep(
  draft: FlowDraft,
  stepId: string,
  updater: (step: StepConfig) => StepConfig,
): FlowDraft {
  return { ...draft, steps: draft.steps.map((s) => (s.id === stepId ? updater(s) : s)) }
}

/** Router branch source handles are `branch-<index>` or `default`; other step types have one. */
export function connectSteps(
  draft: FlowDraft,
  sourceId: string,
  sourceHandle: string | null,
  targetId: string,
): FlowDraft {
  if (sourceId === TRIGGER_NODE_ID) {
    return { ...draft, trigger: { ...draft.trigger, firstStepId: targetId } }
  }
  return updateStep(draft, sourceId, (step) => {
    if (step.type !== 'ROUTER') {
      return { ...step, nextStep: targetId }
    }
    if (sourceHandle === 'default') {
      return { ...step, defaultNextStep: targetId }
    }
    const branchIndex = sourceHandle ? Number.parseInt(sourceHandle.replace('branch-', ''), 10) : NaN
    if (!Number.isNaN(branchIndex) && step.branches[branchIndex]) {
      const branches = [...step.branches]
      branches[branchIndex] = { ...branches[branchIndex], nextStep: targetId }
      return { ...step, branches }
    }
    return { ...step, branches: [...step.branches, { conditionExpression: 'true', nextStep: targetId }] }
  })
}

export function disconnectEdge(draft: FlowDraft, edge: Edge): FlowDraft {
  if (edge.source === TRIGGER_NODE_ID) {
    return { ...draft, trigger: { ...draft.trigger, firstStepId: '' } }
  }
  return updateStep(draft, edge.source, (step) => {
    if (step.type !== 'ROUTER') {
      return { ...step, nextStep: null }
    }
    if (edge.sourceHandle === 'default') {
      return { ...step, defaultNextStep: null }
    }
    return { ...step, branches: step.branches.filter((b) => b.nextStep !== edge.target) }
  })
}

export function addRouterBranch(step: RouterStepConfig): RouterStepConfig {
  return { ...step, branches: [...step.branches, { conditionExpression: 'true', nextStep: '' }] }
}

export type Position = { x: number; y: number }

/** BFS layout from the trigger; steps unreachable from it (broken/new wiring) go off to the side. */
export function autoLayout(draft: FlowDraft): Record<string, Position> {
  const positions: Record<string, Position> = { [TRIGGER_NODE_ID]: { x: 250, y: 0 } }
  const stepsById = new Map(draft.steps.map((s) => [s.id, s]))
  const visited = new Set<string>()
  const depthCounts: Record<number, number> = {}
  const queue: Array<{ id: string; depth: number }> = draft.trigger.firstStepId
    ? [{ id: draft.trigger.firstStepId, depth: 1 }]
    : []

  while (queue.length > 0) {
    const { id, depth } = queue.shift()!
    if (visited.has(id) || !stepsById.has(id)) continue
    visited.add(id)
    const column = depthCounts[depth] ?? 0
    depthCounts[depth] = column + 1
    positions[id] = { x: 250 + column * 260, y: depth * 160 }

    const step = stepsById.get(id)!
    if (step.type === 'ROUTER') {
      for (const branch of step.branches) {
        if (branch.nextStep) queue.push({ id: branch.nextStep, depth: depth + 1 })
      }
      if (step.defaultNextStep) queue.push({ id: step.defaultNextStep, depth: depth + 1 })
    } else if (step.nextStep) {
      queue.push({ id: step.nextStep, depth: depth + 1 })
    }
  }

  let orphanIndex = 0
  for (const step of draft.steps) {
    if (!positions[step.id]) {
      positions[step.id] = { x: 700 + orphanIndex * 260, y: 0 }
      orphanIndex += 1
    }
  }
  return positions
}

export function toReactFlow(
  draft: FlowDraft,
  positions: Record<string, Position>,
): { nodes: Node[]; edges: Edge[] } {
  const nodes: Node[] = [
    {
      id: TRIGGER_NODE_ID,
      type: 'trigger',
      position: positions[TRIGGER_NODE_ID] ?? { x: 0, y: 0 },
      data: { trigger: draft.trigger },
      deletable: false,
    },
    ...draft.steps.map((step) => ({
      id: step.id,
      type: 'step',
      position: positions[step.id] ?? { x: 0, y: 0 },
      data: { step },
    })),
  ]

  const edges: Edge[] = []
  if (draft.trigger.firstStepId) {
    edges.push({
      id: `${TRIGGER_NODE_ID}->${draft.trigger.firstStepId}`,
      source: TRIGGER_NODE_ID,
      target: draft.trigger.firstStepId,
    })
  }
  for (const step of draft.steps) {
    if (step.type === 'ROUTER') {
      step.branches.forEach((branch, index) => {
        if (!branch.nextStep) return
        edges.push({
          id: `${step.id}-branch-${index}->${branch.nextStep}`,
          source: step.id,
          sourceHandle: `branch-${index}`,
          target: branch.nextStep,
          label: branch.conditionExpression,
        })
      })
      if (step.defaultNextStep) {
        edges.push({
          id: `${step.id}-default->${step.defaultNextStep}`,
          source: step.id,
          sourceHandle: 'default',
          target: step.defaultNextStep,
          label: 'default',
        })
      }
    } else if (step.nextStep) {
      edges.push({ id: `${step.id}->${step.nextStep}`, source: step.id, target: step.nextStep })
    }
  }
  return { nodes, edges }
}
