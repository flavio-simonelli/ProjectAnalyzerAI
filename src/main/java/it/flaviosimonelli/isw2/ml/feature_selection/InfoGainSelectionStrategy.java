package it.flaviosimonelli.isw2.ml.feature_selection;

import it.flaviosimonelli.isw2.ml.exceptions.FeatureSelectionException;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;

/**
 * Strategia di selezione basata su Information Gain.
 * <p>
 * Calcola quanto ogni attributo aiuta a discriminare la classe target.
 * Usa un Ranker per ordinare le feature e tiene solo quelle che hanno
 * un guadagno informativo maggiore di 0.
 * </p>
 */
public class InfoGainSelectionStrategy implements FeatureSelectionStrategy {

    @Override
    public Instances[] apply(Instances train, Instances test) {
        try {
            // 1. Configura l'Evaluator tramite metodo helper
            AttributeSelection filter = getAttributeSelection();

            // 2. Addestra il filtro sul Training Set
            filter.setInputFormat(train);

            // 3. Applica il filtro a Train e Test
            Instances newTrain = Filter.useFilter(train, filter);
            Instances newTest = Filter.useFilter(test, filter);

            return new Instances[]{newTrain, newTest};

        } catch (Exception e) {
            throw new FeatureSelectionException("Errore nel calcolo del ranking tramite Information Gain", e);
        }
    }

    private static AttributeSelection getAttributeSelection() {
        InfoGainAttributeEval eval = new InfoGainAttributeEval();

        // 2. Configura il Search Method (Ranker)
        Ranker search = new Ranker();

        search.setThreshold(0.0);
        search.setNumToSelect(10); // Seleziona le top 10 feature

        // 3. Configura il Filtro
        AttributeSelection filter = new AttributeSelection();
        filter.setEvaluator(eval);
        filter.setSearch(search);
        return filter;
    }

    @Override
    public String getName() {
        return "InfoGain_Ranker";
    }
}