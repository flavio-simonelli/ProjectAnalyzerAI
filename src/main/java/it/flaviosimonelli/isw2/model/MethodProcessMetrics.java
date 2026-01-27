package it.flaviosimonelli.isw2.model;

import java.util.*;

/**
 * Contenitore puro per le metriche di processo (storiche).
 * Esempio: Numero di revisioni, Linee aggiunte, Autori distinti.
 */
public class MethodProcessMetrics {

    // Mappa per valori numerici semplici (CSV ready)
    private final Map<String, Double> metrics = new HashMap<>();

    // Mappa per dati complessi (es. Set di Autori) necessari per il calcolo
    private final Map<String, Set<String>> complexData = new HashMap<>();

    // --- Gestione Metriche Numeriche ---
    public void addMetric(String key, double value) {
        metrics.put(key, value);
    }

    public Double getMetric(String key) {
        return metrics.get(key);
    }

    public Map<String, Double> getMetrics() {
        return Collections.unmodifiableMap(metrics);
    }

    /**
     * Somma il valore a quello esistente (es. Totale Churn).
     */
    public void increaseMetric(String key, double valueToAdd) {
        metrics.merge(key, valueToAdd, Double::sum);
    }

    /**
     * Aggiorna il valore solo se il nuovo è maggiore (es. Max Churn).
     */
    public void updateMax(String key, double potentialNewMax) {
        metrics.merge(key, potentialNewMax, Math::max);
    }

    // --- Gestione Dati Complessi (Per Autori Distinti) ---
    public void addToSet(String metricName, String value) {
        complexData.computeIfAbsent(metricName, _ -> new HashSet<>()).add(value);
        // Aggiorniamo subito il contatore numerico
        metrics.put(metricName, (double) complexData.get(metricName).size());
    }

    public Set<String> getSet(String metricName) {
        return complexData.getOrDefault(metricName, Collections.emptySet());
    }
}