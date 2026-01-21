package it.flaviosimonelli.isw2.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Method {
    private String className;
    private String signature;
    // LinkedHashMap mantiene l'ordine di inserimento (essenziale per l'allineamento colonne CSV)
    private Map<String, Double> metrics = new LinkedHashMap<>();

    public Method(String className, String signature) {
        this.className = className;
        this.signature = signature;
    }

    public void addMetric(String key, double value) {
        metrics.put(key, value);
    }

    /**
     * Genera la riga CSV dinamica.
     */
    public String toCSV() {
        String metricsCsv = metrics.values().stream()
                .map(val -> {
                    // Stampa pulita: se è 5.0 stampa "5", altrimenti "5.12"
                    if (val % 1 == 0) return String.valueOf(val.intValue());
                    return String.valueOf(val);
                })
                .collect(Collectors.joining(","));

        // PROTEZIONE CSV:
        // La signature contiene virgole (es. "sum(int, int)").
        // Le virgolette "..." dicono a Excel/CSV parser di trattarla come un unico valore.
        return String.format("%s,\"%s\",%s", className, signature, metricsCsv);
    }

    // --- Getters ---

    public String getClassName() {
        return className;
    }

    public String getSignature() {
        return signature;
    }

    // Metodo helper opzionale per ottenere le chiavi metriche (utile per debug)
    public String getMetricNames() {
        return String.join(",", metrics.keySet());
    }
}