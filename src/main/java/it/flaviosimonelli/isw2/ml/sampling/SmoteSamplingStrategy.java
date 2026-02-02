package it.flaviosimonelli.isw2.ml.sampling;

import it.flaviosimonelli.isw2.ml.exceptions.SamplingException;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;

public class SmoteSamplingStrategy implements SamplingStrategy {

    private int randomSeed = 42;

    public void setRandomSeed(int seed) {
        this.randomSeed = seed;
    }

    @Override
    public Instances apply(Instances data) {
        int classIdx = data.classIndex();

        // 1. Analisi delle istanze per calcolare lo sbilanciamento
        int[] counts = data.attributeStats(classIdx).nominalCounts;
        int majorityCount = Math.max(counts[0], counts[1]);
        int minorityCount = Math.min(counts[0], counts[1]);

        // Se non ci sono istanze della classe minoritaria o il dataset è già bilanciato
        if (minorityCount == 0 || minorityCount == majorityCount) {
            return data;
        }

        // 2. Calcolo della percentuale per il bilanciamento 50/50
        double percentage = ((double) (majorityCount - minorityCount) / minorityCount) * 100;

        try {
            // 3. Setup e applicazione del filtro SMOTE
            SMOTE smote = new SMOTE();
            smote.setInputFormat(data);
            smote.setPercentage(percentage);
            smote.setRandomSeed(randomSeed);

            // Applichiamo il filtro e restituiamo il risultato
            return Filter.useFilter(data, smote);

        } catch (Exception e) {
            // FIX java:S112: Incapsuliamo l'eccezione generica di Weka nella nostra specifica
            throw new SamplingException(
                    "Fallimento SMOTE: impossibile bilanciare la classe minoritaria (" + minorityCount + " istanze)",
                    e
            );
        }
    }
}