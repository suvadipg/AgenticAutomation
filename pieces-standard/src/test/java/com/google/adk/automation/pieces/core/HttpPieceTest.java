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

import static com.google.common.truth.Truth.assertThat;

import com.google.adk.automation.sdk.ActionContext;
import com.google.adk.automation.sdk.ActionDefinition;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HttpPieceTest {
  private HttpServer server;
  private ActionDefinition requestAction;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/hello",
        exchange -> {
          byte[] response = "hello".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.getResponseBody().close();
        });
    server.start();
    requestAction = new HttpPiece().actions().get(0);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void execute_returnsStatusAndBody() {
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hello";
    Map<String, Object> output =
        requestAction
            .execute(new ActionContext(Map.of("method", "GET", "url", url), null))
            .blockingGet();

    assertThat(output.get("status")).isEqualTo(200);
    assertThat(output.get("body")).isEqualTo("hello");
  }
}
