package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.metrics.process.IProcessMetric;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

/**
 * Calcola il Churn Totale (turbolenza) del metodo.
 * Formula: Churn = Righe Aggiunte + Righe Cancellate.
 * Si accumula aritmeticamente nel tempo.
 */
public class ChurnMetric implements IProcessMetric {

    @Override
    public String getName() {
        return "Churn";
    }

    @Override
    public void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted) {
        // 1. Recupera il valore attuale (o 0.0 se è nuovo)
        Double current = metrics.getMetric(getName());
        if (current == null) current = 0.0;

        // 2. Calcola il churn di questo singolo commit
        int commitChurn = added + deleted;

        // 3. Somma al totale
        metrics.addMetric(getName(), current + commitChurn);
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }
}