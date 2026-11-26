package com.luixtech.core.ntfy;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NtfyClientTest {

    @Test
    void serializesUtf8AndSpecialCharactersToValidJson() {
        NtfyMessage msg = NtfyMessage.builder("sky-trading-alerts-1")
                .title("股价提醒 \"AAPL\" ⚡️")
                .message("Line 1\nLine 2\\path\t\"quoted\"")
                .priority(NtfyMessage.PRIORITY_HIGH)
                .tags(List.of("rotating_light", "chart_with_upwards_trend"))
                .icon("https://app.luixtech.com/logo.png")
                .click("https://app.luixtech.com/stock/AAPL")
                .addAction(NtfyAction.view("View AAPL", "https://app.luixtech.com/stock/AAPL", true))
                .build();

        String json = NtfyClient.toJson(msg);
        assertTrue(json.contains("\"topic\":\"sky-trading-alerts-1\""));
        assertTrue(json.contains("\"title\":\"股价提醒 \\\"AAPL\\\" ⚡️\""));
        assertTrue(json.contains("\"message\":\"Line 1\\nLine 2\\\\path\\t\\\"quoted\\\"\""));
        assertTrue(json.contains("\"priority\":4"));
        assertTrue(json.contains("\"tags\":[\"rotating_light\",\"chart_with_upwards_trend\"]"));
        assertTrue(json.contains("\"clear\":true"));
    }

    @Test
    void publishesToLocalHttpServerWithBearerToken() throws Exception {
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"id\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            NtfyClient client = new NtfyClient(baseUrl, "tk_secret");
            client.publish(NtfyMessage.builder("test-topic")
                    .title("Hello 中文")
                    .message("Body")
                    .priority(NtfyMessage.PRIORITY_DEFAULT)
                    .build());

            assertEquals("Bearer tk_secret", receivedAuth.get());
            assertEquals("application/json; charset=utf-8", receivedContentType.get());
            assertTrue(receivedBody.get().contains("\"topic\":\"test-topic\""));
            assertTrue(receivedBody.get().contains("\"title\":\"Hello 中文\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsNtfyExceptionWithHttpStatusOnServerError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] resp = "forbidden".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            NtfyClient client = new NtfyClient(baseUrl, null);
            NtfyException ex = assertThrows(NtfyException.class,
                    () -> client.publish(NtfyMessage.builder("t").message("m").build()));
            assertEquals(403, ex.statusCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deriveIconUrlSkipsLocalhostAndResolvesPublicOrigin() {
        assertEquals("https://scheduler.luixtech.com/images/logo.png",
                NtfyClient.deriveIconUrl("https://scheduler.luixtech.com/jobs/123", "/images/logo.png"));
        assertEquals("https://app.luixtech.com:8443/assets/logo.png",
                NtfyClient.deriveIconUrl("https://app.luixtech.com:8443/stock/NVDA", "assets/logo.png"));
        assertNull(NtfyClient.deriveIconUrl("http://localhost:4001/jobs/1", "/images/logo.png"));
        assertNull(NtfyClient.deriveIconUrl("http://127.0.0.1:4001/jobs/1", "/images/logo.png"));
        assertNull(NtfyClient.deriveIconUrl(null, "/images/logo.png"));
    }
}
