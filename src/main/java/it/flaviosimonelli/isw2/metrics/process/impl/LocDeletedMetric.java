package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.metrics.process.AbstractConfigurableMetric;
import it.flaviosimonelli.isw2.metrics.process.MetricStat;

import java.util.EnumSet;
import java.util.Set;

/**
 * Calcola le Linee di Codice Cancellate (LOC Deleted).
 * <p>
 * Questa metrica misura quante righe sono state rimosse dal metodo.
 * Un alto numero di righe cancellate può indicare attività di Refactoring
 * o correzione di bug, ma rappresenta comunque una modifica strutturale.
 * </p>
 * <p>
 * Estendendo {@link AbstractConfigurableMetric}, questa classe fornisce automaticamente
 * le seguenti varianti statistiche (configurabili):
 * <ul>
 * <li><b>SUM (LOC_Deleted):</b> Il totale delle righe rimosse nell'intervallo.</li>
 * <li><b>MAX (MAX_LOC_Deleted):</b> Il massimo numero di righe rimosse in un singolo commit.</li>
 * <li><b>AVG (AVG_LOC_Deleted):</b> La media di righe rimosse per revisione.</li>
 * </ul>
 * </p>
 */
public class LocDeletedMetric extends AbstractConfigurableMetric {

    /**
     * Costruttore di default.
     * <p>
     * Inizializza la metrica abilitando TUTTE le statistiche (SUM, MAX, AVG)
     * sia per il contesto Locale (Release corrente) che per quello Globale (Storico).
     * </p>
     */
    public LocDeletedMetric() {
        super("LOC_Deleted",
                EnumSet.allOf(MetricStat.class), // Local: Abilita tutto
                EnumSet.allOf(MetricStat.class)  // Global: Abilita tutto
        );
    }

    /**
     * Costruttore configurabile.
     * <p>
     * Permette di specificare quali statistiche calcolare per ogni contesto.
     * Utile se si vuole alleggerire il CSV finale rimuovendo colonne ritenute superflue
     * (es. escludere il MAX globale).
     * </p>
     *
     * @param localConfig  Insieme delle statistiche da calcolare per l'intervallo corrente.
     * @param globalConfig Insieme delle statistiche da calcolare per lo storico accumulato.
     */
    public LocDeletedMetric(Set<MetricStat> localConfig, Set<MetricStat> globalConfig) {
        super("LOC_Deleted", localConfig, globalConfig);
    }

    /**
     * Estrae il valore rilevante per questa metrica dai dati grezzi del commit.
     *
     * @param added   Righe aggiunte nel commit (ignorato).
     * @param deleted Righe rimosse nel commit (valore restituito).
     * @return Il numero di righe cancellate ({@code deleted}).
     */
    @Override
    protected int calculateCommitValue(int added, int deleted) {
        return deleted;
    }
}