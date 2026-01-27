package it.flaviosimonelli.isw2.metrics.process;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;

import java.util.*;

public abstract class AbstractConfigurableMetric implements IProcessMetric {

    protected final String baseName;
    protected final String maxName;
    private static final String NR_KEY = "NR"; // Necessario per AVG

    // Configurazioni separate per Local e Global
    private final Set<MetricStat> localConfig;
    private final Set<MetricStat> globalConfig;

    /**
     * Costruttore flessibile.
     * @param name Nome base della metrica (es. "Churn")
     * @param localConfig Quali statistiche mostrare per la release corrente
     * @param globalConfig Quali statistiche mostrare per lo storico globale
     */
    public AbstractConfigurableMetric(String name, Set<MetricStat> localConfig, Set<MetricStat> globalConfig) {
        this.baseName = name;
        this.maxName = "MAX_" + name;
        // Copia difensiva o EnumSet per efficienza
        this.localConfig = (localConfig != null) ? EnumSet.copyOf(localConfig) : EnumSet.noneOf(MetricStat.class);
        this.globalConfig = (globalConfig != null) ? EnumSet.copyOf(globalConfig) : EnumSet.noneOf(MetricStat.class);
    }

    protected abstract int calculateCommitValue(int added, int deleted);

    @Override
    public void update(MethodProcessMetrics metrics, GitCommit commit, int added, int deleted) {
        // Calcoliamo SEMPRE tutto internamente. Costa pochissimo (2 somme e 1 max)
        // e ci garantisce che i dati siano pronti se la config cambia o per il merge globale.
        int val = calculateCommitValue(added, deleted);
        metrics.increaseMetric(baseName, val);
        metrics.updateMax(maxName, val);
    }

    @Override
    public List<String> getHeaderList(boolean isGlobal) {
        Set<MetricStat> config = isGlobal ? globalConfig : localConfig;
        List<String> headers = new ArrayList<>();

        // L'ordine di inserimento qui decide l'ordine nel CSV
        if (config.contains(MetricStat.SUM)) headers.add(baseName);
        if (config.contains(MetricStat.MAX)) headers.add(maxName);
        if (config.contains(MetricStat.AVG)) headers.add("AVG_" + baseName);

        return headers;
    }

    @Override
    public List<Object> getValues(MethodProcessMetrics metrics, boolean isGlobal) {
        Set<MetricStat> config = isGlobal ? globalConfig : localConfig;
        List<Object> values = new ArrayList<>();

        if (metrics == null) {
            // Riempiamo di zeri in base a quante colonne sono attive
            for (int i = 0; i < config.size(); i++) values.add(getDefaultValue());
            return values;
        }

        // Recuperiamo il valore dalla mappa
        Double rawTotal = metrics.getMetric(baseName);

        // Se è null, usiamo il NOSTRO default definito nella classe
        double total = (rawTotal != null) ? rawTotal : getDefaultValue();

        // Stessa logica per il Max
        Double rawMax = metrics.getMetric(maxName);
        double max = (rawMax != null) ? rawMax : getDefaultValue();

        if (config.contains(MetricStat.SUM)) values.add(total);
        if (config.contains(MetricStat.MAX)) values.add(max);

        if (config.contains(MetricStat.AVG)) {
            Double rawNr = metrics.getMetric(NR_KEY);
            double nr = (rawNr != null) ? rawNr : 0.0; // NR ha sempre default 0 logicamente

            double avg = (nr > 0) ? total / nr : getDefaultValue();
            values.add(avg);
        }

        return values;
    }

    @Override
    public void merge(MethodProcessMetrics history, MethodProcessMetrics current) {
        // 1. Gestione SUM (Accumulo) - Corrisponde a MetricStat.SUM
        // Usiamo baseName (es. "Churn", "LOC_Added")
        Double currentSum = current.getMetric(baseName);
        if (currentSum != null && currentSum != 0.0) {
            history.increaseMetric(baseName, currentSum);
        }

        // 2. Gestione MAX (Picco) - Corrisponde a MetricStat.MAX
        // Usiamo maxName (es. "MAX_Churn")
        Double currentMax = current.getMetric(maxName);
        if (currentMax != null && currentMax != 0.0) {
            history.updateMax(maxName, currentMax);
        }

        // Nota: AVG non si mergia, si ricalcola al volo basandosi su SUM e NR.
    }

    /**
     * Implementazione base: per la maggior parte delle metriche numeriche
     * il default è 0.0. Le sottoclassi possono sovrascriverlo.
     */
    @Override
    public double getDefaultValue() {
        return 0.0;
    }
}