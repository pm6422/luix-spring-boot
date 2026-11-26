package com.luixtech.boot.banner;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

/**
 * Logs a compact runtime summary box once Spring Boot finishes startup.
 */
public class StartupBannerLogger {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(StartupBannerLogger.class);

    private static final String DEFAULT_TEMPLATE = """
            Application is running:
            ----------------------------------------------------------------------------------
                Application:     {}
                Local URL:       {}://localhost:{}{}
                External URL:    {}://{}:{}{}
                Profiles:        {}
                PID:             {}
                Charset:         {}
                Timezone:        {}
                Log level:       {}
            ----------------------------------------------------------------------------------""";

    private final Environment env;
    private final LuixBannerProperties properties;

    public StartupBannerLogger(Environment env, LuixBannerProperties properties) {
        this.env = env;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String protocol = (env.getProperty("server.ssl.key-store") != null) ? "https" : "http";
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        String contextPath = env.getProperty("server.servlet.context-path", "");
        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        String hostAddress = resolveHostAddress();
        String[] activeProfiles = env.getActiveProfiles();
        String profiles = activeProfiles.length > 0
                ? String.join(", ", activeProfiles)
                : String.join(", ", env.getDefaultProfiles());
        String pid = String.valueOf(ProcessHandle.current().pid());
        String charset = Charset.defaultCharset().displayName();
        String timezone = ZoneId.systemDefault().getId();
        String logLevel = resolveRootLogLevel();

        String template = loadTemplate();
        log.info("\n" + template,
                env.getProperty("spring.application.name", "application"),
                protocol, port, contextPath,
                protocol, hostAddress, port, contextPath,
                profiles,
                pid,
                charset,
                timezone,
                logLevel);
    }

    String loadTemplate() {
        String location = properties.getTemplateLocation();
        if (location != null && !location.isBlank()) {
            ClassPathResource resource = new ClassPathResource(location);
            if (resource.exists()) {
                try {
                    return resource.getContentAsString(StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    // Fall back to built-in template
                }
            }
        }
        return DEFAULT_TEMPLATE;
    }

    private String resolveHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private String resolveRootLogLevel() {
        try {
            return LogbackLevelResolver.resolve();
        } catch (NoClassDefFoundError e) {
            return "INFO";
        }
    }

    private static final class LogbackLevelResolver {

        private static String resolve() {
            org.slf4j.Logger root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            if (root instanceof Logger logbackLogger && logbackLogger.getEffectiveLevel() != null) {
                return logbackLogger.getEffectiveLevel().toString();
            }
            return "INFO";
        }
    }
}
