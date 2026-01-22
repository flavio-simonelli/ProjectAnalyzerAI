package it.flaviosimonelli.isw2.metrics.process;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

// Definisce interfaccia di come una metrica deve reagire quando viene rilevata una modifica a un metodo
public interface IProcessMetric {
    /**
     * Il nome della metrica (usato come chiave nella mappa e intestazione CSV).
     */
    String getName();

    /**
     * Aggiorna i dati accumulati in base alla nuova modifica rilevata.
     *
     * @param metrics          Il contenitore dei dati per il metodo corrente.
     * @param commit        Il commit in cui è avvenuta la modifica.
     * @param added    Righe aggiunte in questa modifica.
     * @param deleted  Righe rimosse in questa modifica.
     */
    void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted);

    // Valore da usare nel CSV se il metodo non è MAI stato toccato (es. "0")
    String getDefaultValue();
}