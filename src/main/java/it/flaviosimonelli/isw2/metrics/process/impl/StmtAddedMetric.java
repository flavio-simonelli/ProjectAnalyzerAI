package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.metrics.process.IProcessMetric;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

public class StmtAddedMetric implements IProcessMetric {
    @Override
    public String getName() {
        return "Stmt_Added";
    }

    @Override
    public void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted) {
        Double current = metrics.getMetric(getName());
        if (current == null) current = 0.0;

        // Sommiamo SOLO le righe aggiunte
        metrics.addMetric(getName(), current + added);
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }
}