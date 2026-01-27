package it.flaviosimonelli.isw2.metrics.process.impl;

import it.flaviosimonelli.isw2.metrics.process.AbstractConfigurableMetric;
import it.flaviosimonelli.isw2.metrics.process.MetricStat;

import java.util.EnumSet;
import java.util.Set;

/**
 * Calcola il Churn (Turbolenza del codice).
 * <p>
 * <b>Formula:</b> {@code Churn = LinesAdded + LinesDeleted}
 * </p>
 * <p>
 * Il Churn misura la quantità di attività o "movimento" su un file.
 * Un alto valore di Churn indica che il metodo ha subito pesanti modifiche,
 * il che aumenta statisticamente la probabilità di introdurre difetti.
 * </p>
 * <p>
 * Questa classe sfrutta {@link AbstractConfigurableMetric} per fornire automaticamente:
 * <ul>
 * <li><b>SUM (Churn):</b> Il churn totale accumulato nell'intervallo.</li>
 * <li><b>MAX (MAX_Churn):</b> Il churn massimo registrato in un singolo commit (picco di attività).</li>
 * <li><b>AVG (AVG_Churn):</b> Il churn medio per revisione (intensità media delle modifiche).</li>
 * </ul>
 * </p>
 */
public class ChurnMetric extends AbstractConfigurableMetric {

    /**
     * Costruttore di default.
     * Abilita TUTTE le statistiche (SUM, MAX, AVG) sia per l'analisi
     * locale (Release corrente) che per quella globale (Storico).
     */
    public ChurnMetric() {
        super("Churn",
                EnumSet.allOf(MetricStat.class), // Local: SUM, MAX, AVG
                EnumSet.allOf(MetricStat.class)  // Global: SUM, MAX, AVG
        );
    }

    /**
     * Costruttore per configurazione personalizzata.
     * Utile se si vogliono escludere alcune colonne (es. disabilitare MAX globale).
     *
     * @param localConfig  Set di statistiche da calcolare per l'intervallo corrente.
     * @param globalConfig Set di statistiche da calcolare per lo storico globale.
     */
    public ChurnMetric(Set<MetricStat> localConfig, Set<MetricStat> globalConfig) {
        super("Churn", localConfig, globalConfig);
    }

    /**
     * Definisce la logica di calcolo del valore grezzo per un singolo commit.
     * Per il Churn, sommiamo le righe aggiunte e quelle rimosse.
     *
     * @param added   Numero di righe aggiunte nel commit.
     * @param deleted Numero di righe rimosse nel commit.
     * @return Il valore di Churn per questo commit (added + deleted).
     */
    @Override
    protected int calculateCommitValue(int added, int deleted) {
        return added + deleted;
    }
}