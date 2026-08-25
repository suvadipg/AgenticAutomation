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
import { api } from '../api/client'
import type { StepConfig } from '../api/types'
import { createStep, newStepId } from '../state/flowDraft'

interface PiecePaletteProps {
  onAddStep: (step: StepConfig) => void
}

/**
 * Adding a new piece never requires a code change here: the palette is driven entirely by
 * GET /api/pieces (PieceMetadata), so a brand-new Piece implementation on the backend shows up
 * automatically — see PieceMetadata's javadoc in pieces-sdk.
 */
export function PiecePalette({ onAddStep }: PiecePaletteProps) {
  const {
    data: pieces,
    isLoading,
    isError,
  } = useQuery({ queryKey: ['pieces'], queryFn: api.listPieces })

  return (
    <div className="palette">
      <h3 className="palette__heading">Add step</h3>
      <div className="palette__special">
        <button type="button" onClick={() => onAddStep(createStep('AI_AGENT', newStepId('ai')))}>
          + AI Agent
        </button>
        <button type="button" onClick={() => onAddStep(createStep('ROUTER', newStepId('router')))}>
          + Router
        </button>
        <button type="button" onClick={() => onAddStep(createStep('LOOP_ON_ITEMS', newStepId('loop')))}>
          + Loop
        </button>
        <button type="button" onClick={() => onAddStep(createStep('PARALLEL', newStepId('parallel')))}>
          + Parallel
        </button>
      </div>

      <h4 className="palette__heading">Pieces</h4>
      {isLoading && <p className="palette__status">Loading pieces…</p>}
      {isError && <p className="palette__status palette__status--error">Could not load pieces from the backend.</p>}
      {pieces?.map((piece) => (
        <div key={piece.id} className="palette__piece">
          <div className="palette__piece-name">{piece.displayName}</div>
          {piece.actions.map((action) => (
            <button
              key={action.name}
              type="button"
              className="palette__action"
              onClick={() =>
                onAddStep({
                  type: 'PIECE',
                  id: newStepId(piece.id),
                  name: action.displayName,
                  nextStep: null,
                  pieceId: piece.id,
                  actionName: action.name,
                  input: {},
                  connectionId: null,
                })
              }
            >
              {action.displayName}
            </button>
          ))}
        </div>
      ))}
    </div>
  )
}
