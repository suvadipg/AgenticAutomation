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

import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import type { TriggerConfig } from '../../api/types'
import './StepNode.css'

export type TriggerNodeData = { trigger: TriggerConfig }
export type TriggerNodeType = Node<TriggerNodeData, 'trigger'>

export function TriggerNode({ data, selected }: NodeProps<TriggerNodeType>) {
  return (
    <div
      className="trigger-node"
      style={selected ? { boxShadow: '0 0 0 3px rgba(47, 111, 237, 0.35)' } : undefined}
    >
      {data.trigger.kind}
      <Handle type="source" position={Position.Right} />
    </div>
  )
}
