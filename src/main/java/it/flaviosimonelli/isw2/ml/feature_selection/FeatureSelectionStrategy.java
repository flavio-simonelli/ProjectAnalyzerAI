package it.flaviosimonelli.isw2.ml.feature_selection;

import it.flaviosimonelli.isw2.ml.exceptions.FeatureSelectionException;
import weka.core.Instances;

/**
 * Strategia per la selezione delle feature.
 * Permette di filtrare gli attributi del training e del test set.
 */
public interface FeatureSelectionStrategy {

    /**
     * Applica la selezione delle feature.
     * * @param train Il training set su cui calcolare le feature migliori (senza sbirciare il test).
     * @param test Il test set da filtrare di conseguenza (deve avere le stesse colonne del train filtrato).
     * @return Un array di Instances: [0] = Train Filtrato, [1] = Test Filtrato.
     * @throws FeatureSelectionException Se la ricerca delle feature migliori fallisce.
     */
    Instances[] apply(Instances train, Instances test);

    /**
     * @return Il nome della strategia (es. "BestFirst_Forward").
     */
    String getName();
}