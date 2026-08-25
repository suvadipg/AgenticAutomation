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

package com.google.adk.automation.backend.scheduler;

import com.google.adk.automation.persistence.entity.FlowRunEntity;
import com.google.adk.automation.persistence.service.FlowRunService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

/**
 * Registers/unregisters a cron job per published {@code CRON}-trigger flow, using Spring's {@link
 * ThreadPoolTaskScheduler} directly (dynamic per-flow cron expressions, so static
 * {@code @Scheduled} annotations don't fit) rather than Quartz — this is in-process,
 * single-instance scheduling; escalate to Quartz's JDBC {@code JobStore} only if/when running
 * multiple backend instances needs coordinated (not duplicated) firing.
 *
 * <p>Cron expressions are Spring's 6-field format (seconds included), e.g. {@code "0 0 * * * *"}
 * for hourly, not standard 5-field Unix cron.
 */
@Service
public class SchedulerService {
  private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

  private final FlowRunService flowRunService;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final Map<String, ScheduledFuture<?>> scheduledTasksByFlowId = new ConcurrentHashMap<>();

  public SchedulerService(FlowRunService flowRunService) {
    this.flowRunService = flowRunService;
    this.taskScheduler = new ThreadPoolTaskScheduler();
    taskScheduler.setPoolSize(4);
    taskScheduler.setThreadNamePrefix("agentic-automation-scheduler-");
    taskScheduler.initialize();
  }

  /** Replaces any existing schedule for {@code flowId} (e.g. from a prior published version). */
  public void register(String flowId, String cronExpression) {
    unregister(flowId);
    ScheduledFuture<?> future =
        taskScheduler.schedule(() -> runQuietly(flowId), new CronTrigger(cronExpression));
    scheduledTasksByFlowId.put(flowId, future);
  }

  public void unregister(String flowId) {
    ScheduledFuture<?> existing = scheduledTasksByFlowId.remove(flowId);
    if (existing != null) {
      existing.cancel(false);
    }
  }

  private void runQuietly(String flowId) {
    try {
      flowRunService.run(flowId, Map.of(), FlowRunEntity.TriggerSource.CRON);
    } catch (RuntimeException e) {
      // A single misfire shouldn't cancel future scheduled firings.
      logger.warn("Scheduled run of flow {} failed", flowId, e);
    }
  }

  @PreDestroy
  void shutdown() {
    taskScheduler.shutdown();
  }
}
