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

import { describe, expect, it } from 'vitest'
import type { FlowDefinition, RouterStepConfig } from '../api/types'
import {
  addStep,
  connectSteps,
  createStep,
  disconnectEdge,
  emptyDraft,
  fromFlowDefinition,
  removeStep,
  toFlowDefinition,
  toReactFlow,
  TRIGGER_NODE_ID,
} from './flowDraft'

describe('fromFlowDefinition / toFlowDefinition round trip', () => {
  it('reproduces an equivalent FlowDefinition, covering all four step types', () => {
    const original: FlowDefinition = {
      id: 'flow-1',
      name: 'My Flow',
      trigger: { kind: 'CRON', firstStepId: 'fetch', cronExpression: '0 * * * * *' },
      steps: [
        {
          type: 'PIECE',
          id: 'fetch',
          name: 'Fetch',
          nextStep: 'ai',
          pieceId: 'http',
          actionName: 'request',
          input: { method: 'GET', url: 'http://example.invalid' },
          connectionId: null,
        },
        {
          type: 'AI_AGENT',
          id: 'ai',
          name: 'Summarize',
          nextStep: 'route',
          instruction: 'Summarize {{steps.fetch.output.body}}',
          model: 'gemini-3.6-flash',
          allowedTools: ['http.request'],
        },
        {
          type: 'ROUTER',
          id: 'route',
          name: 'Route',
          nextStep: null,
          branches: [{ conditionExpression: 'true', nextStep: 'loop' }],
          defaultNextStep: 'loop',
        },
        {
          type: 'LOOP_ON_ITEMS',
          id: 'loop',
          name: 'Loop',
          nextStep: 'fanout',
          itemsExpression: "['a', 'b']",
          loopBody: [
            {
              type: 'PIECE',
              id: 'loop-body',
              name: 'Body',
              nextStep: null,
              pieceId: 'code',
              actionName: 'evaluate',
              input: { expression: 'loopItem' },
              connectionId: null,
            },
          ],
        },
        {
          type: 'PARALLEL',
          id: 'fanout',
          name: 'Fan Out',
          nextStep: null,
          branches: [
            [
              {
                type: 'PIECE',
                id: 'branch-a',
                name: 'Branch A',
                nextStep: null,
                pieceId: 'code',
                actionName: 'evaluate',
                input: { expression: "'a'" },
                connectionId: null,
              },
            ],
            [],
          ],
        },
      ],
    }

    const draft = fromFlowDefinition(original)
    const restored = toFlowDefinition(draft, original.id, original.name)

    expect(restored).toEqual(original)
  })
})

describe('toReactFlow', () => {
  it('derives one node per step plus a trigger node, and edges matching the wiring', () => {
    const draft = fromFlowDefinition({
      id: 'f',
      name: 'F',
      trigger: { kind: 'MANUAL', firstStepId: 'a', cronExpression: null },
      steps: [
        { type: 'PIECE', id: 'a', name: 'A', nextStep: 'b', pieceId: 'code', actionName: 'evaluate', input: {}, connectionId: null },
        { type: 'PIECE', id: 'b', name: 'B', nextStep: null, pieceId: 'code', actionName: 'evaluate', input: {}, connectionId: null },
      ],
    })

    const { nodes, edges } = toReactFlow(draft, {})

    expect(nodes.map((n) => n.id)).toEqual([TRIGGER_NODE_ID, 'a', 'b'])
    expect(edges).toContainEqual(expect.objectContaining({ source: TRIGGER_NODE_ID, target: 'a' }))
    expect(edges).toContainEqual(expect.objectContaining({ source: 'a', target: 'b' }))
  })

  it('emits one edge per router branch plus a default edge, each on its own handle', () => {
    const router: RouterStepConfig = {
      type: 'ROUTER',
      id: 'r',
      name: 'R',
      nextStep: null,
      branches: [
        { conditionExpression: 'x == 1', nextStep: 'a' },
        { conditionExpression: 'x == 2', nextStep: 'b' },
      ],
      defaultNextStep: 'c',
    }
    const draft = { trigger: { kind: 'MANUAL' as const, firstStepId: 'r', cronExpression: null }, steps: [router] }

    const { edges } = toReactFlow(draft, {})

    expect(edges).toContainEqual(expect.objectContaining({ source: 'r', sourceHandle: 'branch-0', target: 'a' }))
    expect(edges).toContainEqual(expect.objectContaining({ source: 'r', sourceHandle: 'branch-1', target: 'b' }))
    expect(edges).toContainEqual(expect.objectContaining({ source: 'r', sourceHandle: 'default', target: 'c' }))
  })
})

describe('connectSteps / disconnectEdge', () => {
  it('wires the trigger to the first step', () => {
    const draft = addStep(emptyDraft(), createStep('PIECE', 'a'))
    const connected = connectSteps(draft, TRIGGER_NODE_ID, null, 'a')
    expect(connected.trigger.firstStepId).toBe('a')
  })

  it('sets nextStep on a non-router step', () => {
    let draft = addStep(emptyDraft(), createStep('PIECE', 'a'))
    draft = addStep(draft, createStep('PIECE', 'b'))
    draft = connectSteps(draft, 'a', null, 'b')
    expect(draft.steps.find((s) => s.id === 'a')?.nextStep).toBe('b')
  })

  it('adds a new branch when connecting from a router with no matching handle', () => {
    let draft = addStep(emptyDraft(), createStep('ROUTER', 'r'))
    draft = addStep(draft, createStep('PIECE', 'a'))
    draft = connectSteps(draft, 'r', null, 'a')
    const router = draft.steps.find((s) => s.id === 'r') as RouterStepConfig
    expect(router.branches).toEqual([{ conditionExpression: 'true', nextStep: 'a' }])
  })

  it('sets defaultNextStep when connecting from the router default handle', () => {
    let draft = addStep(emptyDraft(), createStep('ROUTER', 'r'))
    draft = addStep(draft, createStep('PIECE', 'a'))
    draft = connectSteps(draft, 'r', 'default', 'a')
    const router = draft.steps.find((s) => s.id === 'r') as RouterStepConfig
    expect(router.defaultNextStep).toBe('a')
  })

  it('disconnectEdge undoes a plain nextStep connection', () => {
    let draft = addStep(emptyDraft(), createStep('PIECE', 'a'));
    draft = addStep(draft, createStep('PIECE', 'b'))
    draft = connectSteps(draft, 'a', null, 'b')
    draft = disconnectEdge(draft, { id: 'a->b', source: 'a', target: 'b' })
    expect(draft.steps.find((s) => s.id === 'a')?.nextStep).toBeNull()
  })
})

describe('removeStep', () => {
  it('deletes the step and unlinks any nextStep/branch pointers to it', () => {
    let draft = addStep(emptyDraft(), createStep('PIECE', 'a'))
    draft = addStep(draft, createStep('PIECE', 'b'))
    draft = connectSteps(draft, 'a', null, 'b')
    draft = connectSteps(draft, TRIGGER_NODE_ID, null, 'a')

    draft = removeStep(draft, 'b')

    expect(draft.steps.map((s) => s.id)).toEqual(['a'])
    expect(draft.steps[0].nextStep).toBeNull()
  })

  it('clears trigger.firstStepId if the removed step was the entry point', () => {
    let draft = addStep(emptyDraft(), createStep('PIECE', 'a'))
    draft = connectSteps(draft, TRIGGER_NODE_ID, null, 'a')

    draft = removeStep(draft, 'a')

    expect(draft.trigger.firstStepId).toBe('')
  })
})
