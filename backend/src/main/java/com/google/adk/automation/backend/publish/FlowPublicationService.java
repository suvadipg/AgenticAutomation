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

package com.google.adk.automation.backend.publish;

import com.google.adk.automation.backend.scheduler.SchedulerService;
import com.google.adk.automation.engine.model.FlowDefinition;
import com.google.adk.automation.engine.model.FlowDefinitionJsonCodec;
import com.google.adk.automation.engine.model.TriggerConfig;
import com.google.adk.automation.persistence.entity.FlowVersionEntity;
import com.google.adk.automation.persistence.entity.WebhookRegistrationEntity;
import com.google.adk.automation.persistence.service.FlowVersionService;
import com.google.adk.automation.persistence.service.WebhookRegistrationService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Publishing a flow (locking its draft — see {@link FlowVersionService#publish}) also has to
 * provision whatever the locked version's {@link TriggerConfig.Kind} needs: a cron registration for
 * {@code CRON}, or a fresh webhook token for {@code WEBHOOK}. Neither {@code FlowVersionService}
 * nor {@code FlowRunService} (both in the framework-agnostic {@code persistence} module) know about
 * scheduling or HTTP, so this orchestration lives here in the backend instead.
 */
@Service
public class FlowPublicationService {
  private final FlowVersionService flowVersionService;
  private final SchedulerService schedulerService;
  private final WebhookRegistrationService webhookRegistrationService;

  public FlowPublicationService(
      FlowVersionService flowVersionService,
      SchedulerService schedulerService,
      WebhookRegistrationService webhookRegistrationService) {
    this.flowVersionService = flowVersionService;
    this.schedulerService = schedulerService;
    this.webhookRegistrationService = webhookRegistrationService;
  }

  public PublishResult publish(String flowId) {
    FlowVersionEntity locked = flowVersionService.publish(flowId);
    FlowDefinition definition = FlowDefinitionJsonCodec.fromJson(locked.getDefinitionJson());
    TriggerConfig trigger = definition.trigger();

    String webhookToken = null;
    switch (trigger.kind()) {
      case CRON -> schedulerService.register(flowId, trigger.cronExpression());
      case WEBHOOK -> {
        schedulerService.unregister(flowId); // in case an earlier version was CRON
        webhookToken =
            webhookRegistrationService
                .register(flowId, locked.getId(), WebhookRegistrationEntity.ResponseMode.SYNC)
                .getWebhookToken();
      }
      case MANUAL -> schedulerService.unregister(flowId); // in case an earlier version was CRON
    }

    return new PublishResult(locked.getId(), locked.getVersionNumber(), webhookToken);
  }

  /** {@code webhookToken} is non-null only when the published trigger is {@code WEBHOOK}. */
  public record PublishResult(String versionId, int versionNumber, @Nullable String webhookToken) {}
}
