package com.example.tomatomall.configure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceObservabilityConfigurationTest {

    @Test
    void normalApplicationDisablesActuatorEndpointsAndKeepsProductCacheEnabledByDefault() {
        Properties properties = load("application.yml");

        assertEquals("false", properties.getProperty("management.endpoints.enabled-by-default"));
        assertEquals("${PRODUCT_DETAIL_CACHE_ENABLED:true}",
                properties.getProperty("tomatomall.cache.product-detail.enabled"));
    }

    @Test
    void performanceProfileExposesOnlyInternalHealthAndMetricsOnDedicatedPort() {
        Properties properties = load("application-perf.yml");

        assertEquals("9090", properties.getProperty("management.server.port"));
        assertEquals("health,metrics", properties.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("true", properties.getProperty("management.endpoint.health.enabled"));
        assertEquals("true", properties.getProperty("management.endpoint.metrics.enabled"));
        assertFalse(properties.containsKey("management.endpoint.env.enabled"));
        assertFalse(properties.containsKey("management.endpoint.configprops.enabled"));
        assertTrue(Boolean.parseBoolean(properties.getProperty("spring.jpa.properties.hibernate.generate_statistics")));
        assertEquals("OFF", properties.getProperty(
                "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener"));
    }

    private Properties load(String resourceName) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resourceName));
        Properties properties = factory.getObject();
        if (properties == null) {
            throw new IllegalStateException("Could not load " + resourceName);
        }
        return properties;
    }
}
