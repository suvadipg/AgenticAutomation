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

// Thin fetch wrapper over the backend module's REST API. Relative URLs ("/api/...") so this
// works both against the Vite dev server (proxied — see vite.config.ts) and a production build
// served from the same origin as the backend.

import type {
  FlowDefinition,
  FlowRunSummary,
  FlowSummary,
  PieceMetadata,
  PublishResult,
  TestRunResult,
} from './types'

class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!response.ok) {
    const body = await response.text()
    throw new ApiError(response.status, body || response.statusText)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const api = {
  listPieces: () => request<PieceMetadata[]>('/api/pieces'),
  getPiece: (pieceId: string) => request<PieceMetadata>(`/api/pieces/${encodeURIComponent(pieceId)}`),

  getFlow: (flowId: string) => request<FlowSummary>(`/api/flows/${encodeURIComponent(flowId)}`),

  upsertDraft: (flowId: string, name: string, owner: string, definition: FlowDefinition) =>
    request<FlowSummary>(`/api/flows/${encodeURIComponent(flowId)}/draft`, {
      method: 'PUT',
      body: JSON.stringify({ name, owner, definition }),
    }),

  publish: (flowId: string) =>
    request<PublishResult>(`/api/flows/${encodeURIComponent(flowId)}/publish`, {
      method: 'POST',
    }),

  testRun: (flowId: string, triggerPayload: Record<string, unknown> = {}) =>
    request<TestRunResult>(`/api/flows/${encodeURIComponent(flowId)}/test-run`, {
      method: 'POST',
      body: JSON.stringify(triggerPayload),
    }),

  getFlowRun: (runId: string) => request<FlowRunSummary>(`/api/flow-runs/${encodeURIComponent(runId)}`),

  listFlowRuns: (flowId: string) =>
    request<FlowRunSummary[]>(`/api/flow-runs?flowId=${encodeURIComponent(flowId)}`),

  resumeFlowRun: (runId: string, resumePayload: Record<string, unknown> = {}) =>
    request<FlowRunSummary>(`/api/flow-runs/${encodeURIComponent(runId)}/resume`, {
      method: 'POST',
      body: JSON.stringify(resumePayload),
    }),
}

export { ApiError }
