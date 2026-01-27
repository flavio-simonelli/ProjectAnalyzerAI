package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.metrics.process.IProcessMetric;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

import java.util.List;
import java.util.Set;

/**
 * Calcola il numero di autori DISTINTI (Number of Authors - NAuth).
 * <p>
 * Questa metrica conta quante persone diverse hanno contribuito al metodo
 * nel periodo di riferimento. Un alto numero di autori è spesso correlato
 * a una maggiore probabilità di bug (mancanza di ownership chiara).
 * </p>
 * <p>
 * Nota: Questa metrica gestisce un Set di stringhe internamente al {@link MethodProcessMetrics}
 * per garantire l'unicità dei nomi.
 * </p>
 */
public class NumberOfAuthorsMetric implements IProcessMetric {

    /**
     * Chiave pubblica per accedere alla metrica nel DTO.
     */
    public static final String NAUTH_KEY = "NAuth";

    /**
     * Aggiorna il set degli autori.
     * Estrae il nome dell'autore dal commit e lo aggiunge al Set gestito
     * da {@code MethodProcessMetrics}. L'unicità è gestita dal Set stesso.
     *
     * @param metrics Il contenitore dati.
     * @param commit  Il commit corrente contenente il nome dell'autore.
     * @param added   Righe aggiunte (ignorato).
     * @param deleted Righe rimosse (ignorato).
     */
    @Override
    public void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted) {
        String authorName = commit.getAuthorName();
        // addToSet aggiorna automaticamente anche il contatore numerico associato alla chiave
        metrics.addToSet(NAUTH_KEY, authorName);
    }

    /**
     * Restituisce la lista degli header per il CSV.
     * Restituisce sempre una singola colonna "NAuth".
     *
     * @param isGlobal Flag contesto (ignorato, il nome base è lo stesso).
     * @return Lista contenente solo "NAuth".
     */
    @Override
    public List<String> getHeaderList(boolean isGlobal) {
        return List.of(NAUTH_KEY);
    }

    /**
     * Restituisce il conteggio degli autori unici.
     *
     * @param data     I dati accumulati.
     * @param isGlobal Flag contesto (ignorato qui).
     * @return Lista contenente il numero intero di autori distinti.
     */
    @Override
    public List<Object> getValues(MethodProcessMetrics data, boolean isGlobal) {
        if (data == null) return List.of(0);

        // Recuperiamo il valore numerico (size del Set) calcolato dal DTO
        return List.of(data.getMetric(NAUTH_KEY).intValue());
    }

    @Override
    public void merge(MethodProcessMetrics history, MethodProcessMetrics current) {
        // Recuperiamo il Set degli autori della release corrente
        Set<String> newAuthors = current.getSet(NAUTH_KEY);

        // Lo uniamo al Set storico
        for (String author : newAuthors) {
            history.addToSet(NAUTH_KEY, author);
        }
        // Il DTO aggiornerà automaticamente il contatore numerico
    }

    @Override
    public double getDefaultValue() {
        return 0.0;
    }
}