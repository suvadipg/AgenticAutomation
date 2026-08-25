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

package com.google.adk.automation.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Runs each of {@code branches} concurrently (see {@code ParallelStepExecutor}), then continues to
 * {@link #nextStep()} once every branch has finished. Each branch is a plain ordered list of steps,
 * executed sequentially *within* that branch — same "flat list, not a full nested chain"
 * simplification {@link LoopOnItemsStepConfig#loopBody()} uses, for the same reason (covers the
 * common case without building general recursive chain traversal inside a branch).
 *
 * <p>Branches may only contain {@code PIECE} and {@code ROUTER} steps — {@code AI_AGENT} and {@code
 * LOOP_ON_ITEMS} (and nested {@code PARALLEL}) are rejected by {@code ParallelStepExecutor} before
 * any branch runs. Both excluded types mutate state that {@link
 * com.google.adk.automation.engine.exec.ExecutionContext} shares across all branches but isn't safe
 * to mutate *concurrently*: {@code AI_AGENT} steps in different branches would share one ADK
 * session with no cross-branch write serialization (a real, verified hazard — see {@code
 * AiAgentStepExecutor}'s javadoc), and {@code LOOP_ON_ITEMS} mutates the shared {@code
 * loopItem}/{@code loopIndex} bindings as sequential scratch state that two concurrent loops would
 * stomp on each other.
 */
public final class ParallelStepConfig extends StepConfig {
  private final ImmutableList<ImmutableList<StepConfig>> branches;

  @JsonCreator
  public ParallelStepConfig(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("nextStep") @Nullable String nextStep,
      @JsonProperty("branches") List<List<StepConfig>> branches) {
    super(id, name, nextStep);
    this.branches =
        branches.stream().map(ImmutableList::copyOf).collect(ImmutableList.toImmutableList());
  }

  @Override
  public StepType type() {
    return StepType.PARALLEL;
  }

  public ImmutableList<ImmutableList<StepConfig>> branches() {
    return branches;
  }
}
