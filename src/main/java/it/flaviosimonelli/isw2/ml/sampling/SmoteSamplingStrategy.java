package it.flaviosimonelli.isw2.ml.sampling;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;

public class SmoteSamplingStrategy implements SamplingStrategy {

    private int seed = 1;

    public void setRandomSeed(int seed) {
        this.seed = seed;
    }

    @Override
    public Instances apply(Instances trainingData) throws Exception {
        // Configurazione SMOTE
        SMOTE smote = new SMOTE();
        smote.setInputFormat(trainingData);
        smote.setRandomSeed(this.seed);

        // "Percentage" in Weka SMOTE indica quanto aumentare la minoranza.
        // Se i bug sono pochissimi, vogliamo raddoppiarli o triplicarli?
        // Lasciare i default spesso funziona bene, ma puoi settare:
        // smote.setPercentage(100.0); // Raddoppia la classe minoritaria

        return Filter.useFilter(trainingData, smote);
    }
}