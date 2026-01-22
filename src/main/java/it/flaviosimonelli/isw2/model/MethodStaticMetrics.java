package it.flaviosimonelli.isw2.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MethodStaticMetrics {
    private final Map<String, Double> metrics = new HashMap<>();

    public void addMetric(String key, double value) {
        metrics.put(key, value);
    }

    public Double getMetric(String key) {
        return metrics.get(key);
    }

    public Map<String, Double> getMetrics() {
        return Collections.unmodifiableMap(metrics);
    }
}