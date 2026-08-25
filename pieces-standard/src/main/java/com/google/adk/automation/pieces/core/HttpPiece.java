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

package com.google.adk.automation.pieces.core;

import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import com.google.adk.automation.sdk.Piece;
import com.google.adk.automation.sdk.Property;
import com.google.adk.automation.sdk.PropertyMap;
import com.google.adk.automation.sdk.TriggerDefinition;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A built-in piece with a single {@code request} action that makes an HTTP call — the M1 stand-in
 * for the many "app" pieces (Slack, Gmail, ...) that would exist in a real deployment, and the
 * piece exercised by {@code PieceActionToolTest} to prove an AI_AGENT step can invoke a piece
 * action as a genuine ADK tool.
 */
public final class HttpPiece implements Piece {
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Override
  public String id() {
    return "http";
  }

  @Override
  public String displayName() {
    return "HTTP";
  }

  @Override
  public String description() {
    return "Make an HTTP request.";
  }

  @Override
  public List<ActionDefinition> actions() {
    return List.of(new RequestAction());
  }

  @Override
  public List<TriggerDefinition> triggers() {
    return List.of();
  }

  /** Makes a single HTTP request and returns its status, headers, and body. */
  private static final class RequestAction implements ActionDefinition {
    @Override
    public String name() {
      return "request";
    }

    @Override
    public String displayName() {
      return "Make Request";
    }

    @Override
    public String description() {
      return "Sends an HTTP request and returns the response.";
    }

    @Override
    public PropertyMap props() {
      return PropertyMap.of(
          Property.dropdown(
              "method",
              "Method",
              "HTTP method.",
              /* required= */ true,
              List.of(
                  new Property.DropdownOption<>("GET", "GET"),
                  new Property.DropdownOption<>("POST", "POST"),
                  new Property.DropdownOption<>("PUT", "PUT"),
                  new Property.DropdownOption<>("PATCH", "PATCH"),
                  new Property.DropdownOption<>("DELETE", "DELETE"))),
          Property.shortText("url", "URL", "The URL to call.", /* required= */ true),
          Property.json(
              "headers", "Headers", "Request headers, as a JSON object.", /* required= */ false),
          Property.longText("body", "Body", "Request body, sent as-is.", /* required= */ false));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Single<Map<String, Object>> execute(ActionContext context) {
      return Single.fromCallable(
              () -> {
                String method = context.require("method", String.class);
                String url = context.require("url", String.class);
                Map<String, Object> headers =
                    (Map<String, Object>) context.get("headers").orElse(Map.of());
                String body = (String) context.get("body").orElse("");

                HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder(URI.create(url))
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
                headers.forEach((key, value) -> requestBuilder.header(key, String.valueOf(value)));

                HttpResponse<String> response =
                    CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

                Map<String, Object> output = new LinkedHashMap<>();
                output.put("status", response.statusCode());
                output.put("body", response.body());
                output.put("headers", response.headers().map());
                return output;
              })
          .subscribeOn(Schedulers.io());
    }
  }
}
