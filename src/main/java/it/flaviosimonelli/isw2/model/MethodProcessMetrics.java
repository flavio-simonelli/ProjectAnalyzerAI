package it.flaviosimonelli.isw2.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Contenitore puro per le metriche di processo (storiche).
 * Esempio: Numero di revisioni, Linee aggiunte, Autori distinti.
 */
public class MethodProcessMetrics {

    // Mappa: Nome Metrica -> Valore
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