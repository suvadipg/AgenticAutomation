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

import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { StepNode, type RunStepStatus } from './nodes/StepNode'
import { TriggerNode } from './nodes/TriggerNode'
import { toReactFlow, TRIGGER_NODE_ID, type FlowDraft, type Position as DraftPosition } from '../state/flowDraft'

const nodeTypes = { step: StepNode, trigger: TriggerNode }

interface FlowCanvasProps {
  draft: FlowDraft
  positions: Record<string, DraftPosition>
  selectedStepId: string | null
  onSelectStep: (stepId: string | null) => void
  onMoveNode: (nodeId: string, position: DraftPosition) => void
  onConnect: (sourceId: string, sourceHandle: string | null, targetId: string) => void
  onDisconnectEdge: (edge: Edge) => void
  onDeleteStep: (stepId: string) => void
  stepStatuses?: Record<string, RunStepStatus>
}

/**
 * A thin, mostly-presentational wrapper around React Flow: nodes/edges are *derived* fresh from
 * `draft`/`positions` every render (see `toReactFlow`), never held as independent React Flow
 * internal state — this component's job is just to translate user gestures (drag, connect,
 * delete) into calls back to the owner, which updates the canonical `FlowDraft`.
 */
export function FlowCanvas({
  draft,
  positions,
  selectedStepId,
  onSelectStep,
  onMoveNode,
  onConnect,
  onDisconnectEdge,
  onDeleteStep,
  stepStatuses,
}: FlowCanvasProps) {
  const { nodes: derivedNodes, edges } = toReactFlow(draft, positions)
  const nodes: Node[] = derivedNodes.map((node) => ({
    ...node,
    selected: node.id === selectedStepId,
    data:
      node.type === 'step' && stepStatuses?.[node.id]
        ? { ...node.data, runStatus: stepStatuses[node.id] }
        : node.data,
  }))

  function handleNodesChange(changes: NodeChange[]) {
    for (const change of changes) {
      if (change.type === 'position' && change.position) {
        onMoveNode(change.id, change.position)
      } else if (change.type === 'select') {
        if (change.selected) {
          onSelectStep(change.id === TRIGGER_NODE_ID ? null : change.id)
        }
      } else if (change.type === 'remove' && change.id !== TRIGGER_NODE_ID) {
        onDeleteStep(change.id)
      }
    }
  }

  function handleEdgesChange(changes: EdgeChange[]) {
    for (const change of changes) {
      if (change.type === 'remove') {
        const edge = edges.find((e) => e.id === change.id)
        if (edge) onDisconnectEdge(edge)
      }
    }
  }

  function handleConnect(connection: Connection) {
    if (!connection.target) return
    onConnect(connection.source, connection.sourceHandle ?? null, connection.target)
  }

  return (
    <div style={{ flex: 1, height: '100%' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodesChange={handleNodesChange}
        onEdgesChange={handleEdgesChange}
        onConnect={handleConnect}
        onPaneClick={() => onSelectStep(null)}
        fitView
      >
        <Background />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  )
}
