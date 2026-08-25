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

package com.google.adk.automation.sdk;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Describes why a flow run paused: either a delay ({@link #delay(Instant)}, resumed automatically
 * once {@code resumeAt} passes) or a human-in-the-loop approval gate ({@link
 * #humanApproval(String)}, resumed by an external call carrying an approval token).
 *
 * <p>M1 only defines this contract (an {@link ActionContext#pause} call short-circuits the executor
 * into a {@code PAUSED} {@code FlowRun} in memory); durable pause across process restarts is
 * layered on top once persistence exists (M2/M3).
 */
public final class PauseMetadata {
  /** Why a run is paused. */
  public enum Reason {
    DELAY,
    HUMAN_APPROVAL
  }

  private final Reason reason;
  private final @Nullable Instant resumeAt;
  private final @Nullable String note;

  private PauseMetadata(Reason reason, @Nullable Instant resumeAt, @Nullable String note) {
    this.reason = reason;
    this.resumeAt = resumeAt;
    this.note = note;
  }

  public static PauseMetadata delay(Instant resumeAt) {
    return new PauseMetadata(Reason.DELAY, resumeAt, null);
  }

  public static PauseMetadata humanApproval(String note) {
    return new PauseMetadata(Reason.HUMAN_APPROVAL, null, note);
  }

  public Reason reason() {
    return reason;
  }

  public @Nullable Instant resumeAt() {
    return resumeAt;
  }

  public @Nullable String note() {
    return note;
  }

  @Override
  public String toString() {
    return switch (reason) {
      case DELAY -> "PauseMetadata{DELAY, resumeAt=" + resumeAt + "}";
      case HUMAN_APPROVAL -> "PauseMetadata{HUMAN_APPROVAL, note=" + note + "}";
    };
  }
}
