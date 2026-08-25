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

package com.google.adk.automation.engine;

import static com.google.common.truth.Truth.assertThat;

import com.google.adk.automation.engine.exec.FlowExecutor;
import com.google.adk.automation.engine.model.AiAgentStepConfig;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowRun;
import com.google.adk.automation.engine.model.ParallelStepConfig;
import com.google.adk.automation.engine.model.PieceStepConfig;
import com.google.adk.automation.engine.model.StepConfig;
import com.google.adk.automation.engine.model.StepResult;
import com.google.adk.automation.engine.model.TriggerConfig;
import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import com.google.adk.automation.sdk.Piece;
import com.google.adk.automation.sdk.PieceRegistry;
import com.google.adk.automation.sdk.PropertyMap;
import com.google.adk.automation.sdk.TriggerDefinition;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the two things that matter for {@code ParallelStepExecutor}: branches genuinely overlap in
 * time (not just "technically async but effectively sequential"), and the shared-state hazards
 * described in {@link ParallelStepConfig}'s javadoc are caught before any branch runs, not left as
 * a silent race. Uses a test-only piece (a real sleep, recording start/end timestamps) rather than
 * pieces-standard's {@code delay}, which always pauses rather than actually sleeping.
 */
final class ParallelStepExecutorTest {
  @Test
  void execute_runsBranchesConcurrently_theirTimeWindowsOverlap() {
    List<Instant> starts = Collections.synchronizedList(new ArrayList<>());
    List<Instant> ends = Collections.synchronizedList(new ArrayList<>());
    PieceRegistry registry = new PieceRegistry().register(sleepPiece(starts, ends, 300));

    StepConfig branchA = new PieceStepConfig("a", "A", null, "test-sleep", "sleep", Map.of(), null);
    StepConfig branchB = new PieceStepConfig("b", "B", null, "test-sleep", "sleep", Map.of(), null);
    StepConfig parallel =
        new ParallelStepConfig("p", "P", null, List.of(List.of(branchA), List.of(branchB)));
    FlowDefinition flow =
        new FlowDefinition("f", "F", TriggerConfig.manual("p"), List.of(parallel));

    FlowRun run = new FlowExecutor(registry).execute(flow, Map.of()).blockingGet();

    assertThat(run.status()).isEqualTo(FlowRun.Status.SUCCEEDED);
    assertThat(starts).hasSize(2);
    assertThat(ends).hasSize(2);
    // Concurrency proof: whichever branch started second must have started before the branch
    // that started first had already finished. Purely sequential execution could never satisfy
    // this (the second branch wouldn't start until well after the first one's end timestamp).
    Instant laterStart = starts.get(0).isAfter(starts.get(1)) ? starts.get(0) : starts.get(1);
    Instant earlierEnd = ends.get(0).isBefore(ends.get(1)) ? ends.get(0) : ends.get(1);
    assertThat(laterStart.isBefore(earlierEnd)).isTrue();
  }

  @Test
  void execute_taggesEachBranchResultWithItsBranchIndex_andRecordsBothInHistory() {
    PieceRegistry registry = PieceRegistry.fromServiceLoader();
    StepConfig branchA =
        new PieceStepConfig(
            "a", "A", null, "code", "evaluate", Map.of("expression", "'from-a'"), null);
    StepConfig branchB =
        new PieceStepConfig(
            "b", "B", null, "code", "evaluate", Map.of("expression", "'from-b'"), null);
    StepConfig parallel =
        new ParallelStepConfig("p", "P", null, List.of(List.of(branchA), List.of(branchB)));
    FlowDefinition flow =
        new FlowDefinition("f", "F", TriggerConfig.manual("p"), List.of(parallel));

    FlowRun run = new FlowExecutor(registry).execute(flow, Map.of()).blockingGet();

    assertThat(run.status()).isEqualTo(FlowRun.Status.SUCCEEDED);
    List<StepResult> parallelResult =
        run.stepResults().stream().filter(r -> r.stepId().equals("p")).toList();
    assertThat(parallelResult).hasSize(1);
    assertThat(parallelResult.get(0).output()).isEqualTo(Map.of("branchCount", 2));

    List<StepResult> childResults =
        run.stepResults().stream().filter(r -> r.iterationIndex() != null).toList();
    assertThat(childResults).hasSize(2);
    assertThat(childResults.stream().map(StepResult::stepId).toList()).containsExactly("a", "b");
    assertThat(childResults.stream().map(StepResult::iterationIndex).toList())
        .containsExactly(0, 1);
  }

  @Test
  void execute_rejectsAnAiAgentStepInsideABranch_beforeRunningAnything() {
    StepConfig aiInBranch =
        new AiAgentStepConfig("ai", "AI", null, "hello", "gemini-3.6-flash", List.of());
    StepConfig parallel = new ParallelStepConfig("p", "P", null, List.of(List.of(aiInBranch)));
    FlowDefinition flow =
        new FlowDefinition("f", "F", TriggerConfig.manual("p"), List.of(parallel));

    FlowRun run = new FlowExecutor(new PieceRegistry()).execute(flow, Map.of()).blockingGet();

    assertThat(run.status()).isEqualTo(FlowRun.Status.FAILED);
    assertThat(run.stepResults()).hasSize(1);
    assertThat(run.stepResults().get(0).errorMessage()).contains("AI_AGENT");
  }

  private static Piece sleepPiece(List<Instant> starts, List<Instant> ends, long sleepMillis) {
    ActionDefinition sleepAction =
        new ActionDefinition() {
          @Override
          public String name() {
            return "sleep";
          }

          @Override
          public String displayName() {
            return "Sleep";
          }

          @Override
          public String description() {
            return "Test-only: records a start/end timestamp around a real Thread.sleep.";
          }

          @Override
          public PropertyMap props() {
            return PropertyMap.empty();
          }

          @Override
          public Single<Map<String, Object>> execute(ActionContext context) {
            return Single.fromCallable(
                    () -> {
                      starts.add(Instant.now());
                      Thread.sleep(sleepMillis);
                      ends.add(Instant.now());
                      return Map.<String, Object>of("done", true);
                    })
                .subscribeOn(Schedulers.io());
          }
        };

    return new Piece() {
      @Override
      public String id() {
        return "test-sleep";
      }

      @Override
      public String displayName() {
        return "Test Sleep";
      }

      @Override
      public String description() {
        return "Test-only piece for proving concurrent branch execution.";
      }

      @Override
      public List<ActionDefinition> actions() {
        return List.of(sleepAction);
      }

      @Override
      public List<TriggerDefinition> triggers() {
        return List.of();
      }
    };
  }
}
