package it.flaviosimonelli.isw2.ml.evaluation;

/**
 * Raggruppa le metriche di performance standard della classificazione.
 */
public record ClassificationMetrics(
        double precision,
        double recall,
        double fMeasure,
        double auc,
        double kappa
) {}
