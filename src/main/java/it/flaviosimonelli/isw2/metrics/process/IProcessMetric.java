package it.flaviosimonelli.isw2.metrics.process;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

import java.util.List;

// Definisce interfaccia di come una metrica deve reagire quando viene rilevata una modifica a un metodo
public interface IProcessMetric {

    // Aggiorna lo stato interno (questo resta uguale, calcoliamo sempre tutto internamente)
    void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted);

    /**
     * Restituisce gli header.
     * @param isGlobal true se stiamo chiedendo le colonne per lo storico globale (Global_...),
     * false per l'intervallo corrente.
     */
    List<String> getHeaderList(boolean isGlobal);

    /**
     * Restituisce i valori.
     * @param metrics Il contenitore dati.
     * @param isGlobal true se stiamo stampando dati globali (usa la config globale), false altrimenti.
     */
    List<Object> getValues(MethodProcessMetrics metrics, boolean isGlobal);

    /**
     * Fonde i dati di una release (current) nello storico globale (history).
     * Ogni metrica sa se deve sommare, fare il massimo o unire dei set.
     *
     * @param history L'accumulatore globale.
     * @param current I dati della release corrente.
     */
    void merge(MethodProcessMetrics history, MethodProcessMetrics current);

    /**
     * Restituisce il valore di default da usare se la metrica non è presente.
     * (Es. 0.0 per Churn, ma potrebbe essere 100.0 per HealthScore).
     */
    double getDefaultValue();
}