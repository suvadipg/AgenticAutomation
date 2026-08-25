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

import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import type { PublishResult } from '../api/types'
import { toFlowDefinition, type FlowDraft } from '../state/flowDraft'

interface ToolbarProps {
  flowId: string
  flowName: string
  flowOwner: string
  draft: FlowDraft
  onFlowIdChange: (id: string) => void
  onFlowNameChange: (name: string) => void
  onFlowOwnerChange: (owner: string) => void
  onPublished: () => void
}

export function Toolbar({
  flowId,
  flowName,
  flowOwner,
  draft,
  onFlowIdChange,
  onFlowNameChange,
  onFlowOwnerChange,
  onPublished,
}: ToolbarProps) {
  const [publishResult, setPublishResult] = useState<PublishResult | null>(null)

  const saveDraft = useMutation({
    mutationFn: () => api.upsertDraft(flowId, flowName, flowOwner, toFlowDefinition(draft, flowId, flowName)),
  })
  const publish = useMutation({
    mutationFn: () => api.publish(flowId),
    onSuccess: (result) => {
      setPublishResult(result)
      onPublished()
    },
  })

  return (
    <div className="toolbar">
      <label className="toolbar__field">
        <span>Flow ID</span>
        <input value={flowId} onChange={(e) => onFlowIdChange(e.target.value)} placeholder="my-flow" />
      </label>
      <label className="toolbar__field">
        <span>Name</span>
        <input value={flowName} onChange={(e) => onFlowNameChange(e.target.value)} />
      </label>
      <label className="toolbar__field">
        <span>Owner</span>
        <input value={flowOwner} onChange={(e) => onFlowOwnerChange(e.target.value)} />
      </label>
      <button type="button" disabled={!flowId || saveDraft.isPending} onClick={() => saveDraft.mutate()}>
        {saveDraft.isPending ? 'Saving…' : 'Save Draft'}
      </button>
      <button type="button" disabled={!flowId || publish.isPending} onClick={() => publish.mutate()}>
        {publish.isPending ? 'Publishing…' : 'Publish'}
      </button>
      {saveDraft.isError && <span className="toolbar__error">Save failed: {(saveDraft.error as Error).message}</span>}
      {publish.isError && <span className="toolbar__error">Publish failed: {(publish.error as Error).message}</span>}
      {saveDraft.isSuccess && !saveDraft.isPending && <span className="toolbar__status">Draft saved ✓</span>}
      {publishResult && (
        <span className="toolbar__status">
          Published v{publishResult.versionNumber}
          {publishResult.webhookToken && (
            <>
              {' '}
              — webhook: <code>/api/webhooks/{flowId}/{publishResult.webhookToken}</code>
            </>
          )}
        </span>
      )}
    </div>
  )
}
