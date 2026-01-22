package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.metrics.process.IProcessMetric;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

/**
 * Calcola il numero di autori DISTINTI che hanno modificato il metodo.
 * Usa un Set interno al DTO per garantire l'unicità.
 */
public class NumberOfAuthorsMetric implements IProcessMetric {

    @Override
    public String getName() {
        return "N-Authors";
    }

    @Override
    public void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted) {
        // Recuperiamo il nome dell'autore dal commit
        String authorName = commit.getAuthorName();

        // Aggiungiamo al Set (il DTO gestisce l'unicità e aggiorna il count numerico)
        metrics.addToSet(getName(), authorName);
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }
}