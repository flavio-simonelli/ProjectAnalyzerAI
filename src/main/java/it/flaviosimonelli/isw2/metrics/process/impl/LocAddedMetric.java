package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.metrics.process.AbstractConfigurableMetric;
import it.flaviosimonelli.isw2.metrics.process.MetricStat;

import java.util.EnumSet;
import java.util.Set;

/**
 * Calcola le Linee di Codice Aggiunte (LOC Added).
 * <p>
 * Misura quante righe nuove sono state introdotte nel metodo.
 * </p>
 * <p>
 * Sfrutta {@link AbstractConfigurableMetric} per fornire automaticamente:
 * <ul>
 * <li><b>SUM (LOC_Added):</b> Totale righe aggiunte nell'intervallo.</li>
 * <li><b>MAX (MAX_LOC_Added):</b> Il massimo numero di righe aggiunte in un singolo commit.</li>
 * <li><b>AVG (AVG_LOC_Added):</b> La media di righe aggiunte per revisione.</li>
 * </ul>
 * </p>
 */
public class LocAddedMetric extends AbstractConfigurableMetric {

    /**
     * Costruttore di default.
     * Abilita SUM, MAX e AVG sia per Locale che per Globale.
     */
    public LocAddedMetric() {
        super("LOC_Added",
                EnumSet.allOf(MetricStat.class), // Local
                EnumSet.allOf(MetricStat.class)  // Global
        );
    }

    /**
     * Costruttore configurabile.
     * @param localConfig Configurazioni per la release corrente.
     * @param globalConfig Configurazioni per lo storico globale.
     */
    public LocAddedMetric(Set<MetricStat> localConfig, Set<MetricStat> globalConfig) {
        super("LOC_Added", localConfig, globalConfig);
    }

    /**
     * Logica specifica: restituisce solo il parametro 'added'.
     *
     * @param added   Righe aggiunte nel commit.
     * @param deleted Righe rimosse nel commit.
     * @return Il valore da accumulare (solo added).
     */
    @Override
    protected int calculateCommitValue(int added, int deleted) {
        return added;
    }
}