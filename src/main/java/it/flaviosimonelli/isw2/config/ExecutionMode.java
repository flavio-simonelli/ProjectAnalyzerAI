package it.flaviosimonelli.isw2.config;

public enum ExecutionMode {
    FULL,              // Dataset -> Correlation -> ML
    DATASET_ONLY,      // Solo estrazione dati (Mining + SZZ)
    CORRELATION_ONLY,  // Solo analisi feature (richiede dataset esistente)
    ML_ONLY,           // Solo Weka (richiede dataset esistente)
    GRAPH_ONLY;

    public static ExecutionMode fromString(String value) {
        try {
            return ExecutionMode.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return FULL; // Default safe
        }
    }
}