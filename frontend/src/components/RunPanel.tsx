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

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { api } from '../api/client'
import type { StepResultView } from '../api/types'
import type { RunStepStatus } from './nodes/StepNode'

interface RunPanelProps {
  flowId: string
  isPublished: boolean
  onStepStatuses: (statuses: Record<string, RunStepStatus> | undefined) => void
}

/**
 * Two ways to see a run's progress on the canvas: "Test Run" executes the DRAFT synchronously
 * (POST /api/flows/{id}/test-run — see FlowController) and shows the result immediately; the
 * "Latest published run" section polls GET /api/flow-runs?flowId=... every 2s (no SSE in this
 * project yet — see the backend module's README) to pick up runs triggered externally (a curl'd
 * webhook, or a fired cron schedule), including ones still {@code RUNNING} with only some steps
 * completed so far.
 */
export function RunPanel({ flowId, isPublished, onStepStatuses }: RunPanelProps) {
  const queryClient = useQueryClient()

  const testRun = useMutation({
    mutationFn: () => api.testRun(flowId),
    onSuccess: (result) => onStepStatuses(toStatusMap(result.steps)),
  })

  const latestRuns = useQuery({
    queryKey: ['flow-runs', flowId],
    queryFn: () => api.listFlowRuns(flowId),
    enabled: Boolean(flowId) && isPublished,
    refetchInterval: 2000,
  })
  const latestRun = latestRuns.data && latestRuns.data.length > 0 ? latestRuns.data[latestRuns.data.length - 1] : undefined

  // Resuming isn't always possible — a run paused inside a LOOP_ON_ITEMS/PARALLEL child is
  // rejected by the backend (409) since that child step's identity isn't individually resumable
  // (see FlowRunController.resume's javadoc); the button is still offered so that rejection is
  // surfaced clearly rather than the run silently sitting there with no obvious next action.
  const resumeRun = useMutation({
    mutationFn: (runId: string) => api.resumeFlowRun(runId),
    onSuccess: (result) => {
      onStepStatuses(toStatusMap(result.steps))
      queryClient.setQueryData(['flow-runs', flowId], (runs: typeof latestRuns.data) =>
        runs?.map((run) => (run.id === result.id ? result : run)),
      )
    },
  })

  useEffect(() => {
    if (latestRun) onStepStatuses(toStatusMap(latestRun.steps))
  }, [latestRun, onStepStatuses])

  return (
    <div className="run-panel">
      <h3 className="run-panel__heading">Run</h3>
      <button type="button" disabled={!flowId || testRun.isPending} onClick={() => testRun.mutate()}>
        {testRun.isPending ? 'Running…' : 'Test Run (draft)'}
      </button>
      {testRun.isError && <p className="run-panel__error">{(testRun.error as Error).message}</p>}
      {testRun.data && <RunResultSummary status={testRun.data.status} steps={testRun.data.steps} />}

      <h4 className="run-panel__heading">Latest published run</h4>
      {!isPublished && <p className="run-panel__status">Publish the flow to see triggered runs here.</p>}
      {isPublished && !latestRun && (
        <p className="run-panel__status">No runs yet — trigger the webhook, or wait for the next scheduled run.</p>
      )}
      {latestRun && (
        <>
          {latestRun.status === 'PAUSED' && (
            <button
              type="button"
              disabled={resumeRun.isPending}
              onClick={() => resumeRun.mutate(latestRun.id)}
            >
              {resumeRun.isPending ? 'Resuming…' : 'Resume'}
            </button>
          )}
          {resumeRun.isError && <p className="run-panel__error">{(resumeRun.error as Error).message}</p>}
          <RunResultSummary status={latestRun.status} steps={latestRun.steps} />
        </>
      )}
    </div>
  )
}

function toStatusMap(steps: StepResultView[]): Record<string, RunStepStatus> {
  const map: Record<string, RunStepStatus> = {}
  for (const step of steps) {
    map[step.stepId] = step.status
  }
  return map
}

function RunResultSummary({ status, steps }: { status: string; steps: StepResultView[] }) {
  return (
    <ul className="run-panel__steps">
      <li>
        Overall: <strong>{status}</strong>
      </li>
      {steps.map((step) => (
        <li key={`${step.stepId}-${step.iterationIndex ?? ''}`}>
          {step.stepId}
          {step.iterationIndex != null ? ` [${step.iterationIndex}]` : ''}: {step.status}
          {step.errorMessage && <span className="run-panel__error"> — {step.errorMessage}</span>}
        </li>
      ))}
    </ul>
  )
}
