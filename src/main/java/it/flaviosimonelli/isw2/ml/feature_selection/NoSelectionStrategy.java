package it.flaviosimonelli.isw2.ml.feature_selection;

import weka.core.Instances;

public class NoSelectionStrategy implements FeatureSelectionStrategy {
    @Override
    public Instances[] apply(Instances train, Instances test) {
        // Restituisce i dataset originali senza toccarli
        return new Instances[]{train, test};
    }
    @Override
    public String getName() {
        return "NoSelection";
    }
}