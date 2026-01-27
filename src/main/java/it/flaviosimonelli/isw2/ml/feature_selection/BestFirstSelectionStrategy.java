package it.flaviosimonelli.isw2.ml.feature_selection;

import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.core.Instances;

/**
 * Implementa la Feature Selection tramite Forward Search.
 * <p>
 * Usa:
 * <ul>
 * <li><b>Evaluator:</b> CfsSubsetEval (Preferisce feature correlate alla classe ma non tra loro).</li>
 * <li><b>Search:</b> BestFirst (Forward Search con backtracking).</li>
 * </ul>
 * </p>
 */
public class BestFirstSelectionStrategy implements FeatureSelectionStrategy {

    @Override
    public Instances[] apply(Instances train, Instances test) throws Exception {
        // 1. Configura il Valutatore (Correlation-based)
        CfsSubsetEval eval = new CfsSubsetEval();

        // 2. Configura il Motore di Ricerca (BestFirst -> Forward Search)
        BestFirst search = new BestFirst();
        // search.setDirection(new SelectedTag(BestFirst.SELECTION_FORWARD, BestFirst.TAGS_SELECTION)); // Default è Forward

        // 3. Configura il Filtro Weka (Supervised)
        AttributeSelection filter = new AttributeSelection();
        filter.setEvaluator(eval);
        filter.setSearch(search);

        // 4. ADDESTRAMENTO DEL FILTRO (Cruciale: Solo su Train)
        filter.setInputFormat(train);

        // 5. APPLICAZIONE DEL FILTRO
        Instances newTrain = Filter.useFilter(train, filter);
        Instances newTest = Filter.useFilter(test, filter); // Applica la stessa trasformazione al test

        return new Instances[]{newTrain, newTest};
    }

    @Override
    public String getName() {
        return "BestFirst_CFS";
    }
}