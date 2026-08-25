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

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { StepConfig } from './api/types'
import { api } from './api/client'
import { FlowCanvas } from './components/FlowCanvas'
import { PiecePalette } from './components/PiecePalette'
import { PropertyPanel } from './components/PropertyPanel'
import { RunPanel } from './components/RunPanel'
import { Toolbar } from './components/Toolbar'
import type { RunStepStatus } from './components/nodes/StepNode'
import {
  addStep,
  connectSteps,
  disconnectEdge,
  emptyDraft,
  removeStep,
  updateStep,
  type FlowDraft,
  type Position,
} from './state/flowDraft'
import './App.css'

/**
 * Owns the single source of truth (`draft: FlowDraft`) and wires the palette/canvas/property
 * panel/toolbar/run panel around it — see flowDraft.ts's module doc for why nodes/edges are never
 * held as independent state.
 *
 * Deliberately session-local for M1 of this frontend: there's no "load an existing flow" action,
 * and `isPublished` only reflects a publish that happened in this session — see the module
 * README for what's still missing.
 */
export default function App() {
  const [flowId, setFlowId] = useState('my-flow')
  const [flowName, setFlowName] = useState('My Flow')
  const [flowOwner, setFlowOwner] = useState('me')
  const [draft, setDraft] = useState<FlowDraft>(emptyDraft())
  const [positions, setPositions] = useState<Record<string, Position>>({})
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null)
  const [isPublished, setIsPublished] = useState(false)
  const [stepStatuses, setStepStatuses] = useState<Record<string, RunStepStatus> | undefined>(undefined)

  const piecesQuery = useQuery({ queryKey: ['pieces'], queryFn: api.listPieces })

  function handleAddStep(step: StepConfig) {
    const index = draft.steps.length
    setPositions((prev) => ({
      ...prev,
      [step.id]: { x: 250 + (index % 4) * 260, y: 160 + Math.floor(index / 4) * 160 },
    }))
    setDraft((prev) => addStep(prev, step))
  }

  function handlePatchStep(patch: Record<string, unknown>) {
    if (!selectedStepId) return
    setDraft((prev) => updateStep(prev, selectedStepId, (s) => ({ ...s, ...patch }) as StepConfig))
  }

  function handleDeleteStep(stepId: string) {
    setDraft((prev) => removeStep(prev, stepId))
    if (selectedStepId === stepId) setSelectedStepId(null)
  }

  const selectedStep = draft.steps.find((s) => s.id === selectedStepId) ?? null

  return (
    <div className="app">
      <Toolbar
        flowId={flowId}
        flowName={flowName}
        flowOwner={flowOwner}
        draft={draft}
        onFlowIdChange={setFlowId}
        onFlowNameChange={setFlowName}
        onFlowOwnerChange={setFlowOwner}
        onPublished={() => setIsPublished(true)}
      />
      <div className="app__body">
        <PiecePalette onAddStep={handleAddStep} />
        <FlowCanvas
          draft={draft}
          positions={positions}
          selectedStepId={selectedStepId}
          onSelectStep={setSelectedStepId}
          onMoveNode={(id, position) => setPositions((prev) => ({ ...prev, [id]: position }))}
          onConnect={(source, handle, target) =>
            setDraft((prev) => connectSteps(prev, source, handle, target))
          }
          onDisconnectEdge={(edge) => setDraft((prev) => disconnectEdge(prev, edge))}
          onDeleteStep={handleDeleteStep}
          stepStatuses={stepStatuses}
        />
        <div className="app__sidebar">
          <PropertyPanel step={selectedStep} pieces={piecesQuery.data} onPatchStep={handlePatchStep} />
          <RunPanel flowId={flowId} isPublished={isPublished} onStepStatuses={setStepStatuses} />
        </div>
      </div>
    </div>
  )
}
