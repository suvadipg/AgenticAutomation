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

import type {
  AiAgentStepConfig,
  LoopOnItemsStepConfig,
  ParallelStepConfig,
  PieceMetadata,
  PieceStepConfig,
  RouterStepConfig,
  StepConfig,
} from '../api/types'
import { PropertyField } from './PropertyField'

interface PropertyPanelProps {
  step: StepConfig | null
  pieces: PieceMetadata[] | undefined
  onPatchStep: (patch: Record<string, unknown>) => void
}

/**
 * The selected step's editable fields. For a PIECE step, the fields themselves come from
 * PieceMetadata (see PropertyField) — nothing here is specific to any one piece. For the other
 * three step types, the fields are fixed (the engine's own config shape), not piece-driven.
 */
export function PropertyPanel({ step, pieces, onPatchStep }: PropertyPanelProps) {
  if (!step) {
    return <div className="property-panel property-panel--empty">Select a step to edit its properties.</div>
  }

  return (
    <div className="property-panel" key={step.id}>
      <h3 className="property-panel__heading">{STEP_TYPE_TITLES[step.type]}</h3>

      <label className="property-panel__field">
        <span>Name</span>
        <input value={step.name} onChange={(e) => onPatchStep({ name: e.target.value })} />
      </label>

      {step.type === 'PIECE' && <PieceStepFields step={step} pieces={pieces} onPatchStep={onPatchStep} />}
      {step.type === 'AI_AGENT' && <AiAgentStepFields step={step} onPatchStep={onPatchStep} />}
      {step.type === 'ROUTER' && <RouterStepFields step={step} onPatchStep={onPatchStep} />}
      {step.type === 'LOOP_ON_ITEMS' && <LoopStepFields step={step} onPatchStep={onPatchStep} />}
      {step.type === 'PARALLEL' && <ParallelStepFields step={step} onPatchStep={onPatchStep} />}
    </div>
  )
}

const STEP_TYPE_TITLES: Record<StepConfig['type'], string> = {
  PIECE: 'Piece Step',
  AI_AGENT: 'AI Agent Step',
  ROUTER: 'Router Step',
  LOOP_ON_ITEMS: 'Loop Step',
  PARALLEL: 'Parallel Step',
}

function PieceStepFields({
  step,
  pieces,
  onPatchStep,
}: {
  step: PieceStepConfig
  pieces: PieceMetadata[] | undefined
  onPatchStep: (patch: Record<string, unknown>) => void
}) {
  const piece = pieces?.find((p) => p.id === step.pieceId)
  const action = piece?.actions.find((a) => a.name === step.actionName)

  return (
    <>
      <div className="property-panel__readonly">
        {step.pieceId}.{step.actionName}
      </div>
      {!action && <p className="property-panel__status">Piece metadata not loaded yet.</p>}
      {action?.properties.map((property) => (
        <label key={property.key} className="property-panel__field">
          <span>
            {property.displayName}
            {property.required ? ' *' : ''}
          </span>
          <PropertyField
            property={property}
            value={step.input[property.key]}
            onChange={(value) => onPatchStep({ input: { ...step.input, [property.key]: value } })}
          />
          {property.description && <small>{property.description}</small>}
        </label>
      ))}
    </>
  )
}

function AiAgentStepFields({
  step,
  onPatchStep,
}: {
  step: AiAgentStepConfig
  onPatchStep: (patch: Record<string, unknown>) => void
}) {
  return (
    <>
      <label className="property-panel__field">
        <span>Model</span>
        <input value={step.model} onChange={(e) => onPatchStep({ model: e.target.value })} />
      </label>
      <label className="property-panel__field">
        <span>Instruction</span>
        <textarea
          rows={4}
          value={step.instruction}
          onChange={(e) => onPatchStep({ instruction: e.target.value })}
        />
        <small>May reference prior step output, e.g. {'{{steps.fetch.output.body}}'}.</small>
      </label>
      <label className="property-panel__field">
        <span>Allowed tools</span>
        <input
          value={step.allowedTools.join(', ')}
          onChange={(e) =>
            onPatchStep({
              allowedTools: e.target.value
                .split(',')
                .map((s) => s.trim())
                .filter(Boolean),
            })
          }
        />
        <small>Comma-separated qualified piece actions, e.g. http.request.</small>
      </label>
    </>
  )
}

function RouterStepFields({
  step,
  onPatchStep,
}: {
  step: RouterStepConfig
  onPatchStep: (patch: Record<string, unknown>) => void
}) {
  function updateBranchCondition(index: number, conditionExpression: string) {
    const branches = step.branches.map((b, i) => (i === index ? { ...b, conditionExpression } : b))
    onPatchStep({ branches })
  }

  return (
    <>
      <p className="property-panel__status">
        Draw connections on the canvas to wire branches/default to their target steps.
      </p>
      {step.branches.map((branch, index) => (
        <label key={index} className="property-panel__field">
          <span>
            Branch {index} condition (JEXL, → {branch.nextStep || '(unconnected)'})
          </span>
          <input value={branch.conditionExpression} onChange={(e) => updateBranchCondition(index, e.target.value)} />
        </label>
      ))}
      <div className="property-panel__readonly">Default → {step.defaultNextStep || '(unconnected)'}</div>
    </>
  )
}

function LoopStepFields({
  step,
  onPatchStep,
}: {
  step: LoopOnItemsStepConfig
  onPatchStep: (patch: Record<string, unknown>) => void
}) {
  return (
    <>
      <label className="property-panel__field">
        <span>Items expression (JEXL, must evaluate to a list)</span>
        <input value={step.itemsExpression} onChange={(e) => onPatchStep({ itemsExpression: e.target.value })} />
      </label>
      <LoopBodyEditor step={step} onPatchStep={onPatchStep} />
    </>
  )
}

/**
 * Loop bodies aren't rendered as canvas nodes (see flowDraft.ts's module doc) — this compact list
 * editor is the v1 substitute. Deliberately limited to PIECE sub-steps with a raw-JSON input
 * field, not the full property-driven form PieceStepFields gets, to keep this scoped.
 */
function LoopBodyEditor({
  step,
  onPatchStep,
}: {
  step: LoopOnItemsStepConfig
  onPatchStep: (patch: Record<string, unknown>) => void
}) {
  function addBodyStep() {
    const bodyStep: PieceStepConfig = {
      type: 'PIECE',
      id: `${step.id}-body-${step.loopBody.length}`,
      name: `Body step ${step.loopBody.length + 1}`,
      nextStep: null,
      pieceId: '',
      actionName: '',
      input: {},
      connectionId: null,
    }
    onPatchStep({ loopBody: [...step.loopBody, bodyStep] })
  }

  function updateBodyStep(index: number, patch: Partial<PieceStepConfig>) {
    const loopBody = step.loopBody.map((s, i) => (i === index ? { ...(s as PieceStepConfig), ...patch } : s))
    onPatchStep({ loopBody })
  }

  function removeBodyStep(index: number) {
    onPatchStep({ loopBody: step.loopBody.filter((_, i) => i !== index) })
  }

  return (
    <div className="property-panel__loop-body">
      <span>Loop body (runs in order, each iteration)</span>
      {step.loopBody.map((bodyStep, index) => {
        const pieceStep = bodyStep as PieceStepConfig
        return (
          <div key={bodyStep.id} className="property-panel__loop-body-item">
            <input
              placeholder="pieceId"
              value={pieceStep.pieceId}
              onChange={(e) => updateBodyStep(index, { pieceId: e.target.value })}
            />
            <input
              placeholder="actionName"
              value={pieceStep.actionName}
              onChange={(e) => updateBodyStep(index, { actionName: e.target.value })}
            />
            <textarea
              placeholder="input (JSON)"
              rows={2}
              defaultValue={JSON.stringify(pieceStep.input, null, 2)}
              onBlur={(e) => {
                try {
                  updateBodyStep(index, { input: JSON.parse(e.target.value || '{}') })
                } catch {
                  // leave last-valid input in place until fixed
                }
              }}
            />
            <button type="button" onClick={() => removeBodyStep(index)}>
              Remove
            </button>
          </div>
        )
      })}
      <button type="button" onClick={addBodyStep}>
        + Add body step
      </button>
    </div>
  )
}

function ParallelStepFields({
  step,
  onPatchStep,
}: {
  step: ParallelStepConfig
  onPatchStep: (patch: Record<string, unknown>) => void
}) {
  function addBranch() {
    onPatchStep({ branches: [...step.branches, []] })
  }

  function removeBranch(branchIndex: number) {
    onPatchStep({ branches: step.branches.filter((_, i) => i !== branchIndex) })
  }

  function setBranch(branchIndex: number, branchSteps: StepConfig[]) {
    onPatchStep({
      branches: step.branches.map((b, i) => (i === branchIndex ? branchSteps : b)),
    })
  }

  return (
    <div className="property-panel__loop-body">
      <span>Branches (run concurrently — PIECE/ROUTER steps only)</span>
      {step.branches.map((branch, branchIndex) => (
        <div key={branchIndex} className="property-panel__loop-body-item">
          <strong>Branch {branchIndex}</strong>
          <ParallelBranchEditor
            stepId={step.id}
            branchIndex={branchIndex}
            branch={branch}
            onChange={(next) => setBranch(branchIndex, next)}
          />
          <button type="button" onClick={() => removeBranch(branchIndex)}>
            Remove branch
          </button>
        </div>
      ))}
      <button type="button" onClick={addBranch}>
        + Add branch
      </button>
    </div>
  )
}

/** One PARALLEL branch's flat step list — same PIECE-only, raw-JSON-input editor as LoopBodyEditor. */
function ParallelBranchEditor({
  stepId,
  branchIndex,
  branch,
  onChange,
}: {
  stepId: string
  branchIndex: number
  branch: StepConfig[]
  onChange: (branch: StepConfig[]) => void
}) {
  function addStep() {
    const newStep: PieceStepConfig = {
      type: 'PIECE',
      id: `${stepId}-b${branchIndex}-${branch.length}`,
      name: `Step ${branch.length + 1}`,
      nextStep: null,
      pieceId: '',
      actionName: '',
      input: {},
      connectionId: null,
    }
    onChange([...branch, newStep])
  }

  function updateStep(index: number, patch: Partial<PieceStepConfig>) {
    onChange(branch.map((s, i) => (i === index ? { ...(s as PieceStepConfig), ...patch } : s)))
  }

  function removeStep(index: number) {
    onChange(branch.filter((_, i) => i !== index))
  }

  return (
    <div className="property-panel__loop-body">
      {branch.map((branchStep, index) => {
        const pieceStep = branchStep as PieceStepConfig
        return (
          <div key={branchStep.id} className="property-panel__loop-body-item">
            <input
              placeholder="pieceId"
              value={pieceStep.pieceId}
              onChange={(e) => updateStep(index, { pieceId: e.target.value })}
            />
            <input
              placeholder="actionName"
              value={pieceStep.actionName}
              onChange={(e) => updateStep(index, { actionName: e.target.value })}
            />
            <textarea
              placeholder="input (JSON)"
              rows={2}
              defaultValue={JSON.stringify(pieceStep.input, null, 2)}
              onBlur={(e) => {
                try {
                  updateStep(index, { input: JSON.parse(e.target.value || '{}') })
                } catch {
                  // leave last-valid input in place until fixed
                }
              }}
            />
            <button type="button" onClick={() => removeStep(index)}>
              Remove
            </button>
          </div>
        )
      })}
      <button type="button" onClick={addStep}>
        + Add step to branch
      </button>
    </div>
  )
}
