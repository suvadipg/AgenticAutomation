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

package com.google.adk.automation.engine.exec;

import com.google.adk.automation.engine.model.ParallelStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.StepType;
import com.google.common.collect.ImmutableList;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a {@link ParallelStepConfig}'s branches concurrently — each branch's own sub-chain is
 * subscribed on {@link Schedulers#io()} (a real, separate worker thread per branch, not just an
 * async-looking but effectively sequential chain), then {@link Single#zip} waits for all of them.
 * See {@link ParallelStepConfig}'s javadoc for why branches are restricted to {@code PIECE}/{@code
 * ROUTER} steps — validated up front, before any branch runs.
 */
public final class ParallelStepExecutor implements StepExecutor {
  private final StepExecutorRegistry registry;

  public ParallelStepExecutor(StepExecutorRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Single<StepResult> execute(StepConfig config, ExecutionContext context) {
    ParallelStepConfig step = (ParallelStepConfig) config;
    Instant startedAt = Instant.now();

    Optional<String> violation = findDisallowedStepType(step);
    if (violation.isPresent()) {
      return Single.just(
          StepResult.failed(
              step.id(),
              StepType.PARALLEL,
              Map.of(),
              new IllegalArgumentException(violation.get()),
              startedAt));
    }

    List<Single<List<StepResult>>> branchSingles = new ArrayList<>();
    for (ImmutableList<StepConfig> branch : step.branches()) {
      branchSingles.add(runBranch(branch, 0, context).subscribeOn(Schedulers.io()));
    }

    return Single.zip(branchSingles, ParallelStepExecutor::flattenBranchResults)
        .flatMap(allChildResults -> finish(step, context, allChildResults, startedAt));
  }

  @SuppressWarnings("unchecked")
  private static List<StepResult> flattenBranchResults(Object[] perBranchResults) {
    List<StepResult> tagged = new ArrayList<>();
    for (int branchIndex = 0; branchIndex < perBranchResults.length; branchIndex++) {
      for (StepResult result : (List<StepResult>) perBranchResults[branchIndex]) {
        tagged.add(result.withIterationIndex(branchIndex));
      }
    }
    return tagged;
  }

  private Single<StepResult> finish(
      ParallelStepConfig step,
      ExecutionContext context,
      List<StepResult> taggedResults,
      Instant startedAt) {
    for (StepResult tagged : taggedResults) {
      context.recordStepResult(tagged);
      context.appendToHistory(tagged);
    }

    Optional<StepResult> notSucceeded =
        taggedResults.stream().filter(r -> r.status() != StepResult.Status.SUCCEEDED).findFirst();
    if (notSucceeded.isPresent()) {
      StepResult bad = notSucceeded.get();
      if (bad.status() == StepResult.Status.PAUSED) {
        return Single.just(
            StepResult.paused(
                step.id(), StepType.PARALLEL, Map.of(), bad.pauseMetadata(), startedAt));
      }
      return Single.just(
          StepResult.failed(
              step.id(),
              StepType.PARALLEL,
              Map.of(),
              new RuntimeException(
                  "Branch "
                      + bad.iterationIndex()
                      + " step '"
                      + bad.stepId()
                      + "' failed: "
                      + bad.errorMessage()),
              startedAt));
    }

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("branchCount", step.branches().size());
    return Single.just(
        StepResult.succeeded(step.id(), StepType.PARALLEL, Map.of(), output, startedAt));
  }

  /**
   * Runs one branch's steps in order, recording each so later steps in the *same* branch see it.
   */
  private Single<List<StepResult>> runBranch(
      ImmutableList<StepConfig> branch, int index, ExecutionContext context) {
    if (index >= branch.size()) {
      return Single.just(new ArrayList<>());
    }
    StepConfig branchStep = branch.get(index);
    StepExecutor executor = registry.executorFor(branchStep.type());
    return executor
        .execute(branchStep, context)
        .flatMap(
            result -> {
              if (result.status() != StepResult.Status.SUCCEEDED) {
                List<StepResult> onlyThis = new ArrayList<>();
                onlyThis.add(result);
                return Single.just(onlyThis);
              }
              context.recordStepResult(result);
              return runBranch(branch, index + 1, context)
                  .map(
                      rest -> {
                        List<StepResult> all = new ArrayList<>();
                        all.add(result);
                        all.addAll(rest);
                        return all;
                      });
            });
  }

  private static Optional<String> findDisallowedStepType(ParallelStepConfig step) {
    ImmutableList<ImmutableList<StepConfig>> branches = step.branches();
    for (int branchIndex = 0; branchIndex < branches.size(); branchIndex++) {
      for (StepConfig branchStep : branches.get(branchIndex)) {
        if (branchStep.type() == StepType.AI_AGENT
            || branchStep.type() == StepType.LOOP_ON_ITEMS
            || branchStep.type() == StepType.PARALLEL) {
          return Optional.of(
              "PARALLEL branch "
                  + branchIndex
                  + " contains a disallowed step type "
                  + branchStep.type()
                  + " (step '"
                  + branchStep.id()
                  + "'); only PIECE and ROUTER steps are allowed inside PARALLEL branches.");
        }
      }
    }
    return Optional.empty();
  }
}
