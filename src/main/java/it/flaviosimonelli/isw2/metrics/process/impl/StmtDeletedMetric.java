package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.metrics.process.IProcessMetric;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

public class StmtDeletedMetric implements IProcessMetric {

    @Override
    public String getName() {
        return "Stmt_Deleted";
    }

    @Override
    public void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted) {
        Double current = metrics.getMetric(getName());
        if (current == null) current = 0.0;

        // Sommiamo le righe rimosse
        metrics.addMetric(getName(), current + deleted);
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }
}