package com.example.tomatomall.service.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCacheSingleFlightBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void negativeWaitTimeoutRejectsApplicationStartup() {
        contextRunner.withPropertyValues(
                        "tomatomall.cache.product-detail.single-flight.wait-timeout=-1ms"
                )
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(rootMessage(context.getStartupFailure()).contains("waitTimeout must be positive"));
                });
    }

    @Test
    void invalidWaitTimeoutTextRejectsApplicationStartup() {
        contextRunner.withPropertyValues(
                        "tomatomall.cache.product-detail.single-flight.wait-timeout=not-a-duration"
                )
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        ProductCacheSingleFlightProperties properties() {
            return new ProductCacheSingleFlightProperties();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ProductCacheSingleFlight singleFlight(
                ProductCacheSingleFlightProperties properties,
                MeterRegistry registry
        ) {
            return new ProductCacheSingleFlight(properties, registry);
        }
    }
}
