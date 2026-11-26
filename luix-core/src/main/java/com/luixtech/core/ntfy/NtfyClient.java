package com.luixtech.core.ntfy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Zero-dependency ntfy JSON API publisher backed by {@link HttpClient}.
 *
 * <p>Uses ntfy's JSON publish endpoint ({@code POST <serverUrl>}) for full UTF-8 support
 * (including CJK titles/messages and emoji), Bearer token authentication, priority, tags,
 * icons, and interactive view actions.
 */
public final class NtfyClient {

    public static final String DEFAULT_SERVER_URL = "https://ntfy.sh";
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final String defaultServerUrl;
    private final String defaultToken;
    private final Duration requestTimeout;

    public NtfyClient() {
        this(DEFAULT_SERVER_URL, null, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public NtfyClient(String serverUrl, String token) {
        this(serverUrl, token, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public NtfyClient(String serverUrl, String token, Duration connectTimeout, Duration requestTimeout) {
        this(HttpClient.newBuilder()
                        .connectTimeout(connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                serverUrl,
                token,
                requestTimeout);
    }

    NtfyClient(HttpClient httpClient, String serverUrl, String token, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.defaultServerUrl = normalizeServerUrl(serverUrl);
        this.defaultToken = trimToNull(token);
        this.requestTimeout = requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT;
    }

    /**
     * Publishes a message using this client's configured server URL and Bearer token.
     */
    public void publish(NtfyMessage message) {
        publish(defaultServerUrl, defaultToken, message);
    }

    /**
     * Publishes a message to an explicit ntfy server URL and optional Bearer token,
     * allowing a single shared {@link NtfyClient} to target multiple dynamic channels.
     */
    public void publish(String serverUrl, String bearerToken, NtfyMessage message) {
        Objects.requireNonNull(message, "message must not be null");

        String targetUrl = normalizeServerUrl(serverUrl);
        String json = toJson(message);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

        String token = trimToNull(bearerToken);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }

        try {
            HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new NtfyException(response.statusCode(),
                        "ntfy returned HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NtfyException("ntfy publish interrupted", e);
        } catch (IOException e) {
            throw new NtfyException("ntfy network request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Derives an absolute icon URL on the same origin as {@code actionUrl}, skipping non-public localhost URLs.
     *
     * @param actionUrl base or deep-link URL (e.g. {@code https://app.example.com/jobs/1})
     * @param iconPath  absolute path to the icon asset (e.g. {@code /images/logo.png})
     * @return derived icon URL, or {@code null} if {@code actionUrl} is blank, invalid, or points to localhost
     */
    public static String deriveIconUrl(String actionUrl, String iconPath) {
        if (actionUrl == null || actionUrl.isBlank() || iconPath == null || iconPath.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(actionUrl.trim());
            String host = uri.getHost();
            if (uri.getScheme() == null || host == null
                    || "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)) {
                return null;
            }
            String portPart = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            String normalizedPath = iconPath.startsWith("/") ? iconPath : "/" + iconPath;
            return uri.getScheme() + "://" + host + portPart + normalizedPath;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String toJson(NtfyMessage msg) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        boolean first = true;

        first = appendStringField(sb, "topic", msg.topic(), first);
        if (msg.title() != null && !msg.title().isBlank()) {
            first = appendStringField(sb, "title", msg.title(), first);
        }
        if (msg.message() != null) {
            first = appendStringField(sb, "message", msg.message(), first);
        }
        if (msg.priority() != null) {
            first = appendFieldName(sb, "priority", first);
            sb.append(msg.priority());
        }
        if (!msg.tags().isEmpty()) {
            first = appendFieldName(sb, "tags", first);
            appendStringArray(sb, msg.tags());
        }
        if (msg.icon() != null && !msg.icon().isBlank()) {
            first = appendStringField(sb, "icon", msg.icon(), first);
        }
        if (msg.click() != null && !msg.click().isBlank()) {
            first = appendStringField(sb, "click", msg.click(), first);
        }
        if (!msg.actions().isEmpty()) {
            appendFieldName(sb, "actions", first);
            sb.append('[');
            for (int i = 0; i < msg.actions().size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                NtfyAction action = msg.actions().get(i);
                sb.append('{');
                appendStringField(sb, "action", action.action(), true);
                appendStringField(sb, "label", action.label(), false);
                appendStringField(sb, "url", action.url(), false);
                if (action.clear()) {
                    sb.append(",\"clear\":true");
                }
                sb.append('}');
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private static boolean appendStringField(StringBuilder sb, String name, String value, boolean first) {
        appendFieldName(sb, name, first);
        appendJsonString(sb, value);
        return false;
    }

    private static boolean appendFieldName(StringBuilder sb, String name, boolean first) {
        if (!first) {
            sb.append(',');
        }
        appendJsonString(sb, name);
        sb.append(':');
        return false;
    }

    private static void appendStringArray(StringBuilder sb, List<String> values) {
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendJsonString(sb, values.get(i));
        }
        sb.append(']');
    }

    private static void appendJsonString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static String normalizeServerUrl(String serverUrl) {
        String trimmed = trimToNull(serverUrl);
        if (trimmed == null) {
            return DEFAULT_SERVER_URL;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? DEFAULT_SERVER_URL : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
