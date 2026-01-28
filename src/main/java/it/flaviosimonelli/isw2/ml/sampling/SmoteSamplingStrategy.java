package it.flaviosimonelli.isw2.ml.sampling;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;

public class SmoteSamplingStrategy implements SamplingStrategy {

    private int randomSeed = 42;

    public void setRandomSeed(int seed) {
        this.randomSeed = seed;
    }

    @Override
    public Instances apply(Instances data) throws Exception {
        int classIdx = data.classIndex();

        // Conta le istanze
        int[] counts = data.attributeStats(classIdx).nominalCounts;
        // Assumiamo che Clean sia la maggioritaria e Buggy la minoritaria
        // Ma per sicurezza calcoliamo chi è chi
        int majorityCount = Math.max(counts[0], counts[1]);
        int minorityCount = Math.min(counts[0], counts[1]);

        if (minorityCount == 0) return data; // Niente da bilanciare

        // Calcoliamo la percentuale per arrivare circa al 50/50
        // Formula SMOTE Weka: (Percentage / 100) * minority = new_instances
        // Vogliamo: minority + new_instances = majority
        // Quindi: new_instances = majority - minority
        // (Perc / 100) * minority = majority - minority
        // Perc = ((majority - minority) / minority) * 100

        double percentage = ((double)(majorityCount - minorityCount) / minorityCount) * 100;

        // Setup SMOTE
        SMOTE smote = new SMOTE();
        smote.setInputFormat(data);
        smote.setPercentage(percentage);
        smote.setRandomSeed(randomSeed);

        return weka.filters.Filter.useFilter(data, smote);
    }
}