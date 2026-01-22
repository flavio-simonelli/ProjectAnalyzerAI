package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.metrics.process.IProcessMetric;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

/**
 * Calcola il numero di revisioni (Method Histories).
 * Incrementa il contatore ogni volta che il metodo viene toccato da un commit.
 */
public class MethodHistoriesMetric implements IProcessMetric {

    @Override
    public String getName() {
        return "MethodHistories";
    }

    @Override
    public void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted) {
        // Recupera il valore attuale (o 0.0 se è la prima volta che lo tocchiamo)
        Double current = metrics.getMetric(getName());
        if (current == null) current = 0.0;

        metrics.addMetric(getName(), current + 1);
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }
}