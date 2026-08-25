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

import { Handle, Position, type NodeProps, type Node } from '@xyflow/react'
import type { StepConfig } from '../../api/types'
import './StepNode.css'

export type RunStepStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'PAUSED'

export type StepNodeData = { step: StepConfig; runStatus?: RunStepStatus }
export type StepNodeType = Node<StepNodeData, 'step'>

const RUN_STATUS_COLORS: Record<RunStepStatus, string> = {
  RUNNING: '#f59e0b',
  SUCCEEDED: '#10b981',
  FAILED: '#ef4444',
  PAUSED: '#6b7280',
}

const TYPE_COLORS: Record<StepConfig['type'], string> = {
  PIECE: '#2f6fed',
  AI_AGENT: '#8b5cf6',
  ROUTER: '#f59e0b',
  LOOP_ON_ITEMS: '#10b981',
  PARALLEL: '#0ea5e9',
}

const TYPE_LABELS: Record<StepConfig['type'], string> = {
  PIECE: 'Piece',
  AI_AGENT: 'AI Agent',
  ROUTER: 'Router',
  LOOP_ON_ITEMS: 'Loop',
  PARALLEL: 'Parallel',
}

function stepSubtitle(step: StepConfig): string {
  switch (step.type) {
    case 'PIECE':
      return step.pieceId && step.actionName ? `${step.pieceId}.${step.actionName}` : '(unconfigured)'
    case 'AI_AGENT':
      return step.model
    case 'ROUTER':
      return `${step.branches.length} branch${step.branches.length === 1 ? '' : 'es'}`
    case 'LOOP_ON_ITEMS':
      return step.itemsExpression
    case 'PARALLEL':
      return `${step.branches.length} branch${step.branches.length === 1 ? '' : 'es'} (concurrent)`
  }
}

export function StepNode({ data, selected }: NodeProps<StepNodeType>) {
  const { step, runStatus } = data
  const color = TYPE_COLORS[step.type]

  return (
    <div className={`step-node${selected ? ' step-node--selected' : ''}`} style={{ borderColor: color }}>
      {runStatus && (
        <span
          className="step-node__run-badge"
          style={{ background: RUN_STATUS_COLORS[runStatus] }}
          title={`Last run: ${runStatus}`}
        />
      )}
      <Handle type="target" position={Position.Left} />
      <div className="step-node__header" style={{ background: color }}>
        {TYPE_LABELS[step.type]}
      </div>
      <div className="step-node__body">
        <div className="step-node__name">{step.name || step.id}</div>
        <div className="step-node__subtitle">{stepSubtitle(step)}</div>
      </div>

      {step.type === 'ROUTER' ? (
        <div className="step-node__handles">
          {step.branches.map((branch, index) => (
            <div key={index} className="step-node__handle-row">
              <span className="step-node__handle-label">{branch.conditionExpression || `branch ${index}`}</span>
              <Handle
                type="source"
                position={Position.Right}
                id={`branch-${index}`}
                style={{ position: 'relative', transform: 'none', right: 0 }}
              />
            </div>
          ))}
          <div className="step-node__handle-row">
            <span className="step-node__handle-label">default</span>
            <Handle
              type="source"
              position={Position.Right}
              id="default"
              style={{ position: 'relative', transform: 'none', right: 0 }}
            />
          </div>
          <div className="step-node__handle-row">
            <span className="step-node__handle-label step-node__handle-label--new">+ new branch</span>
            <Handle
              type="source"
              position={Position.Right}
              id="new-branch"
              style={{ position: 'relative', transform: 'none', right: 0 }}
            />
          </div>
        </div>
      ) : (
        <Handle type="source" position={Position.Right} />
      )}
    </div>
  )
}
