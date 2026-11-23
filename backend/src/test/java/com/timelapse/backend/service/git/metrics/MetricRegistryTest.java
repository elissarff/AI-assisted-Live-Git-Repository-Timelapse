package com.timelapse.backend.service.git.metrics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MetricRegistryTest {
    @Test void fileRegistrationPreservesLookupAndIndependentInstances() {
        var first=MetricRegistry.createAll();
        var second=MetricRegistry.createAll();
        assertEquals(6,first.size());
        assertEquals(first.keySet(),second.keySet());
        first.forEach((type,metric)->{
            assertNotSame(metric,second.get(type));
            assertEquals(type,MetricRegistry.createByName(metric.name()).getClass());
            assertEquals(type,MetricRegistry.create(type).getClass());
        });
        assertThrows(IllegalArgumentException.class,()->MetricRegistry.createByName("unknown"));
    }
}
