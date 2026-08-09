package com.example.tomatomall.configure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OssSensitiveLoggingTest {

    @Test
    void apacheHttpSensitiveWireLoggersAreDisabled() {
        SpringApplication application = new SpringApplication(LoggingOnlyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);

        try (ConfigurableApplicationContext ignored = application.run("--spring.main.banner-mode=off")) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            assertEquals(Level.OFF, context.getLogger("org.apache.http.headers").getEffectiveLevel());
            assertEquals(Level.OFF, context.getLogger("org.apache.http.wire").getEffectiveLevel());
        }
    }

    @Test
    void productionLogbackConfigurationLoadsAndDisablesDetailedOssLogs() throws Exception {
        URL configuration = getClass().getClassLoader().getResource("logback-spring.xml");
        assertNotNull(configuration);

        LoggerContext isolatedContext = new LoggerContext();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(isolatedContext);
            configurator.doConfigure(configuration);

            assertFalse(isolatedContext.getStatusManager().getCopyOfStatusList().stream()
                    .anyMatch(status -> status.getLevel() == Status.ERROR));
            assertEquals(
                    Level.OFF,
                    isolatedContext.getLogger("org.apache.http.headers").getEffectiveLevel()
            );
            assertEquals(
                    Level.OFF,
                    isolatedContext.getLogger("org.apache.http.wire").getEffectiveLevel()
            );
            assertEquals(
                    Level.OFF,
                    isolatedContext.getLogger("com.aliyun.oss").getEffectiveLevel()
            );
        } finally {
            isolatedContext.stop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LoggingOnlyConfiguration {
    }
}
