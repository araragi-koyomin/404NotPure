package com.example.tomatomall.configure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisFailureProtectionConfigurationTest {

    @Test
    void applicationDefaultsBoundRedisWaitsAndProductDatabaseFallback() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application",
                new ClassPathResource("application.yml")
        );

        assertEquals("${REDIS_CONNECT_TIMEOUT:250ms}", property(sources, "spring.redis.connect-timeout"));
        assertEquals("${REDIS_COMMAND_TIMEOUT:500ms}", property(sources, "spring.redis.timeout"));
        assertEquals("${PRODUCT_CACHE_FAILURE_BYPASS_DURATION:5s}",
                property(sources, "tomatomall.cache.product-detail.resilience.bypass-duration"));
        assertEquals("${PRODUCT_CACHE_DB_FALLBACK_MAX_CONCURRENT:4}",
                property(sources, "tomatomall.cache.product-detail.resilience.max-concurrent-database-fallbacks"));
        assertEquals("${PRODUCT_CACHE_DB_FALLBACK_WAIT:50ms}",
                property(sources, "tomatomall.cache.product-detail.resilience.database-fallback-wait"));
        assertEquals("${PRODUCT_CACHE_SINGLE_FLIGHT_ENABLED:true}",
                property(sources, "tomatomall.cache.product-detail.single-flight.enabled"));
        assertEquals("${PRODUCT_CACHE_SINGLE_FLIGHT_WAIT_TIMEOUT:500ms}",
                property(sources, "tomatomall.cache.product-detail.single-flight.wait-timeout"));
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
