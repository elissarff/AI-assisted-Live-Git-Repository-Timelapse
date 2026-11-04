package com.timelapse.backend.service.git.metrics;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The only place where metric implementations are registered and constructed. */
public final class MetricRegistry {

    // To add a metric, add its class here. No metric class-name strings are needed.
    private static final List<Class<? extends Metric>> REGISTERED = List.of(
            ActivityMetric.class,
            ContributorMetric.class,
            FileHotspotMetric.class,
            DirectoryMetric.class,
            CoChangeMetric.class,
            ChurnMetric.class
    );

    private MetricRegistry() {}

    /** Create fresh, independent metric instances for each calculation. */
    public static Map<Class<? extends Metric>, Metric> createAll() {
        Map<Class<? extends Metric>, Metric> metrics = new LinkedHashMap<>();
        for (Class<? extends Metric> type : REGISTERED) {
            metrics.put(type, instantiate(type));
        }
        return metrics;
    }

    /** Serialize registered metric results without requiring service-specific fields. */
    public static Map<String, Object> results(Iterable<Metric> metrics) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Metric metric : metrics) {
            // Churn is already included as a legacy top-level summary field.
            if (metric instanceof ChurnMetric) continue;
            values.put(metric.name(), metric.result());
        }
        return values;
    }

    /** Find a metric by its public API name, as declared by Metric.name(). */
    public static Metric createByName(String name) {
        for (Class<? extends Metric> type : REGISTERED) {
            Metric metric = instantiate(type);
            if (metric.name().equals(name)) {
                return metric;
            }
        }
        throw new IllegalArgumentException("Unknown metric: " + name);
    }

    /** Reflectively call the no-argument constructor. */
    public static <T extends Metric> T create(Class<T> type) {
        if (!REGISTERED.contains(type)) {
            throw new IllegalArgumentException("Unregistered metric: " + type.getName());
        }
        return instantiate(type);
    }

    private static <T extends Metric> T instantiate(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException |
                 InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Cannot instantiate metric: " + type.getName(), e);
        }
    }

    public static <T extends Metric> T get(Map<Class<? extends Metric>, Metric> metrics, Class<T> type) {
        Metric metric = metrics.get(type);
        if (metric == null) {
            throw new IllegalArgumentException("Unregistered metric: " + type.getName());
        }
        return type.cast(metric);
    }
}
