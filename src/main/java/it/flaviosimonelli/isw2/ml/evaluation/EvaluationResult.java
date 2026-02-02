package it.flaviosimonelli.isw2.ml.evaluation;

/**
 * Contenitore dei risultati della validazione.
 * Implementato come Record per immutabilità e concisione.
 */
public record EvaluationResult(
        int releaseIndex,
        ClassificationMetrics metrics,
        double npofb20,
        String selectedFeatures
) {
    @Override
    public String toString() {
        return String.format("Release: %d, %s, NPofB20: %.3f",
                releaseIndex,
                metrics.toString(), // Il record ClassificationMetrics ha già un toString automatico
                npofb20);
    }
}