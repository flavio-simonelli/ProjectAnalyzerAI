package it.flaviosimonelli.isw2.ml.feature_selection;

import it.flaviosimonelli.isw2.ml.exceptions.FeatureSelectionException;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.core.Instances;

public class BestFirstSelectionStrategy implements FeatureSelectionStrategy {

    @Override
    public Instances[] apply(Instances train, Instances test) {
        try {
            // 1. Configura il Valutatore (Correlation-based)
            CfsSubsetEval eval = new CfsSubsetEval();

            // 2. Configura il Motore di Ricerca (BestFirst -> Forward Search)
            BestFirst search = new BestFirst();

            // 3. Configura il Filtro Weka
            AttributeSelection filter = new AttributeSelection();
            filter.setEvaluator(eval);
            filter.setSearch(search);

            // 4. Addestramento del filtro solo sul Train
            filter.setInputFormat(train);

            // 5. Applicazione del filtro
            Instances newTrain = Filter.useFilter(train, filter);
            Instances newTest = Filter.useFilter(test, filter);

            return new Instances[]{newTrain, newTest};

        } catch (Exception e) {
            throw new FeatureSelectionException("Errore durante la ricerca BestFirst (Forward Search)", e);
        }
    }

    @Override
    public String getName() {
        return "BestFirst_CFS";
    }
}