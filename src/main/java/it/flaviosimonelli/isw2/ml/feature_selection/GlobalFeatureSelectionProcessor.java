package it.flaviosimonelli.isw2.ml.feature_selection;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class GlobalFeatureSelectionProcessor {

    public Instances apply(Instances data, String strategyName, List<String> metadataCols) throws Exception {
        if (strategyName == null || "NoSelection".equalsIgnoreCase(strategyName)) return data;

        // 1. Prepariamo i dati per la selezione (Weka non accetta stringhe/date come input dei filtri)
        Instances forSelection = new Instances(data);
        forSelection = removeColumns(forSelection, metadataCols);
        forSelection.setClassIndex(forSelection.numAttributes() - 1);

        // 2. Eseguiamo la strategia (BestFirst/CFS o InfoGain)
        FeatureSelectionStrategy strategy = FeatureSelectionFactory.getStrategy(strategyName);
        Instances[] results = strategy.apply(forSelection, forSelection);

        // 3. Identifichiamo i nomi delle feature sopravvissute
        List<String> toKeep = new ArrayList<>(metadataCols);
        IntStream.range(0, results[0].numAttributes())
                .mapToObj(i -> results[0].attribute(i).name())
                .forEach(toKeep::add);

        // 4. Applichiamo il filtro invertito al dataset ORIGINALE per mantenere i metadati
        return keepColumns(data, toKeep);
    }

    private Instances removeColumns(Instances data, List<String> names) throws Exception {
        Remove remove = new Remove();
        remove.setAttributeIndicesArray(getIndices(data, names));
        remove.setInputFormat(data);
        return Filter.useFilter(data, remove);
    }

    private Instances keepColumns(Instances data, List<String> names) throws Exception {
        Remove remove = new Remove();
        remove.setAttributeIndicesArray(getIndices(data, names));
        remove.setInvertSelection(true); // "Tieni solo questi"
        remove.setInputFormat(data);
        return Filter.useFilter(data, remove);
    }

    private int[] getIndices(Instances data, List<String> names) {
        return names.stream()
                .map(data::attribute)
                .filter(java.util.Objects::nonNull)
                .mapToInt(weka.core.Attribute::index)
                .toArray();
    }
}