package it.flaviosimonelli.isw2.metrics.process;

public enum MetricStat {
    SUM, // Il valore accumulato (es. Totale Churn)
    MAX, // Il picco massimo (es. Max Churn in un commit)
    AVG  // La media aritmetica (Sum / Revisions)
}
