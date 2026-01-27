package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.metrics.process.IProcessMetric;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

import java.util.List;

/**
 * Calcola il numero di revisioni (Number of Revisions - NR).
 * <p>
 * Questa metrica conta quante volte il metodo è stato modificato (toccato da un commit).
 * È una metrica fondamentale ("Denominatore") usata dalle altre metriche
 * per calcolare le medie (es. AVG_Churn = Total_Churn / NR).
 * </p>
 */
public class MethodHistoriesMetric implements IProcessMetric {

    /**
     * Chiave pubblica per accedere al valore NR.
     */
    public static final String NR_KEY = "NR";

    /**
     * Aggiorna il conteggio delle revisioni.
     * Ogni volta che questo metodo viene chiamato, significa che il metodo è stato
     * intercettato in un commit, quindi incrementiamo di 1.
     *
     * @param metrics Il contenitore dati.
     * @param commit  Il commit analizzato (non usato qui, serve solo il fatto che esista).
     * @param added   Righe aggiunte (non usato).
     * @param deleted Righe rimosse (non usato).
     */
    @Override
    public void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted) {
        // Usiamo l'helper increaseMetric per sommare 1.0 al valore esistente
        metrics.increaseMetric(NR_KEY, 1.0);
    }

    /**
     * Restituisce gli header per il CSV.
     * Ignora il parametro {@code isGlobal} perché la colonna si chiama sempre "NR".
     * L'analizzatore aggiungerà il prefisso "Global_" esternamente se necessario.
     *
     * @param isGlobal true se contesto globale, false se locale.
     * @return Lista contenente solo "NR".
     */
    @Override
    public List<String> getHeaderList(boolean isGlobal) {
        return List.of(NR_KEY);
    }

    /**
     * Restituisce il valore attuale del contatore NR.
     *
     * @param data     I dati delle metriche del metodo.
     * @param isGlobal Flag contesto (ignorato qui, il valore è sempre quello accumulato).
     * @return Lista contenente il valore intero delle revisioni (es. [5]).
     */
    @Override
    public List<Object> getValues(MethodProcessMetrics data, boolean isGlobal) {
        // Se non ci sono dati, significa 0 revisioni
        if (data == null) return List.of(0);

        // Restituiamo come intero per pulizia nel CSV (es. 5 invece di 5.0)
        return List.of(data.getMetric(NR_KEY).intValue());
    }

    @Override
    public void merge(MethodProcessMetrics history, MethodProcessMetrics current) {
        Double val = current.getMetric(NR_KEY);
        if (val != null && val > 0) {
            history.increaseMetric(NR_KEY, val);
        }
    }

    @Override
    public double getDefaultValue() {
        return 0.0;
    }
}