package it.flaviosimonelli.isw2.model;

public class MetricCorrelation {
    private final String metricName;
    private final double pearson;
    private final double spearman;

    public MetricCorrelation(String metricName, double pearson, double spearman) {
        this.metricName = metricName;
        this.pearson = pearson;
        this.spearman = spearman;
    }

    public String getMetricName() { return metricName; }
    public double getPearson() { return pearson; }
    public double getSpearman() { return spearman; }
}