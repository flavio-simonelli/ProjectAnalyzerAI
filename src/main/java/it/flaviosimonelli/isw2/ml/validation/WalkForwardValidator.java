package it.flaviosimonelli.isw2.ml.validation;

import it.flaviosimonelli.isw2.ml.evaluation.ClassificationMetrics;
import it.flaviosimonelli.isw2.ml.evaluation.EvaluationResult;
import it.flaviosimonelli.isw2.ml.exceptions.ModelEvaluationException;
import it.flaviosimonelli.isw2.ml.feature_selection.FeatureSelectionStrategy;
import it.flaviosimonelli.isw2.ml.sampling.SamplingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.evaluation.Prediction;
import weka.core.Attribute;
import weka.core.Instances;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;

import java.util.ArrayList;
import java.util.List;

import it.flaviosimonelli.isw2.config.ProjectConstants;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

public class WalkForwardValidator {

    private static final Logger logger = LoggerFactory.getLogger(WalkForwardValidator.class);
    private static final double NPOFB20_EFFORT_LIMIT = 0.20;


    /**
     * Raggruppa i parametri della validazione per evitare lo smell S107 (Too many parameters).
     */
    private record ValidationContext(
            Classifier classifier,
            List<String> columnsToDrop,
            SamplingStrategy samplingStrategy,
            FeatureSelectionStrategy fsStrategy,
            List<EvaluationResult> results
    ) {}


    /**
     * Helper record per mappare le predizioni di Weka alle righe di codice (LOC).
     * Utilizzato esclusivamente per il calcolo della metrica NPofB20.
     */
    private record PredictionEntry(double probBuggy, double loc, boolean isActuallyBuggy) {}

    public List<EvaluationResult> validate(Instances data, Classifier classifier, List<String> columnsToDrop,
                                           SamplingStrategy samplingStrategy, FeatureSelectionStrategy fsStrategy) {
        List<EvaluationResult> results = new ArrayList<>();
        Attribute releaseIndex = data.attribute(ProjectConstants.RELEASE_INDEX_ATTRIBUTE);

        if (releaseIndex == null) {
            throw new ModelEvaluationException("Attributo '" + ProjectConstants.RELEASE_INDEX_ATTRIBUTE + "' non trovato.");
        }

        ValidationContext context = new ValidationContext(classifier, columnsToDrop, samplingStrategy, fsStrategy, results);

        try {
            data.sort(releaseIndex);
            int numReleases = (int) data.attributeStats(releaseIndex.index()).numericStats.max;

            // Inizio dalla release 2: la release 1 funge da training iniziale
            for (int i = 2; i <= numReleases; i++) {
                Instances rawTrain = filterByReleaseIndex(data, releaseIndex, i, true);
                Instances rawTest = filterByReleaseIndex(data, releaseIndex, i, false);

                // Evitiamo fold con dati mancanti (java:S135 - rimosso continue)
                if (rawTrain.numInstances() > 0 && rawTest.numInstances() > 0) {
                    processFold(i, rawTrain, rawTest, context);
                }
            }
        } catch (Exception e) {
            throw new ModelEvaluationException("Errore durante la validazione Walk-Forward", e);
        }
        return results;
    }

    /**
     * Esegue il training e il test per una singola iterazione temporale (fold).
     */
    private void processFold(int releaseId, Instances rawTrain, Instances rawTest, ValidationContext ctx) throws Exception {

        // 1. Rimozione attributi non predittivi (ID, Date, etc.)
        Instances cleanTrain = removeColumns(rawTrain, ctx.columnsToDrop());
        Instances cleanTest = removeColumns(rawTest, ctx.columnsToDrop());

        // 2. Feature Selection (opzionale)
        String selectedFeatures = "ALL";
        if (ctx.fsStrategy() != null) {
            Instances[] filtered = ctx.fsStrategy.apply(cleanTrain, cleanTest);
            cleanTrain = filtered[0];
            cleanTest = filtered[1];
            selectedFeatures = extractFeatureNames(cleanTrain);
        }

        // 3. Bilanciamento Training Set (Sampling)
        Instances finalTrain = (ctx.samplingStrategy() != null)
                ? ctx.samplingStrategy().apply(cleanTrain)
                : cleanTrain;

        // 4. Training e Testing
        Classifier model = weka.classifiers.AbstractClassifier.makeCopy(ctx.classifier());
        model.buildClassifier(finalTrain);

        Evaluation eval = new Evaluation(cleanTrain);
        eval.evaluateModel(model, cleanTest);

        // Calcolo indici e metriche finali
        int posIdx = getPositiveClassIndex(cleanTrain);
        double npofb20Value = calculateNPofB20(eval.predictions(), cleanTest, posIdx);

        ClassificationMetrics metrics = new ClassificationMetrics(
                eval.precision(posIdx), eval.recall(posIdx), eval.fMeasure(posIdx),
                eval.areaUnderROC(posIdx), eval.kappa()
        );

        ctx.results().add(new EvaluationResult(releaseId, metrics, npofb20Value, selectedFeatures));
    }

    /**
     * Calcola la metrica NPofB20: percentuale di bug trovati ispezionando il top 20% delle LOC.
     * Implementata tramite Stream e takeWhile per evitare istruzioni break (java:S135).
     */
    private double calculateNPofB20(ArrayList<Prediction> predictions, Instances testData, int posIdx) {
        Attribute locAttr = testData.attribute(ProjectConstants.LOC_ATTRIBUTE);
        if (locAttr == null) return 0.0;

        List<PredictionEntry> entries = new ArrayList<>();
        int totalBugs = 0;
        double totalLoc = 0;

        // Raccolta dati predetti e reali
        for (int i = 0; i < predictions.size(); i++) {
            if (predictions.get(i) instanceof NominalPrediction pred) {
                double prob = pred.distribution()[posIdx];
                double loc = testData.instance(i).value(locAttr);
                boolean isBug = ((int) pred.actual() == posIdx);

                totalLoc += loc;
                if (isBug) totalBugs++;
                entries.add(new PredictionEntry(prob, loc, isBug));
            }
        }

        if (totalBugs == 0) return 0.0;

        // Sorting decrescente per probabilità (Ranking)
        entries.sort((a, b) -> Double.compare(b.probBuggy(), a.probBuggy()));

        double effortLimit = totalLoc * NPOFB20_EFFORT_LIMIT;
        final double[] currentEffort = {0};

        // takeWhile arresta lo stream appena superiamo la soglia del 20% LOC
        long bugsFound = entries.stream()
                .takeWhile(e -> {
                    boolean canInspect = currentEffort[0] < effortLimit;
                    currentEffort[0] += e.loc();
                    return canInspect;
                })
                .filter(PredictionEntry::isActuallyBuggy)
                .count();

        return (double) bugsFound / totalBugs;
    }

    // --- Metodi Helper ---

    private Instances filterByReleaseIndex(Instances data, Attribute indexAttr, double targetIndex, boolean lessThan) {
        Instances subset = new Instances(data, 0);
        int attrIdx = indexAttr.index();
        for (int k = 0; k < data.numInstances(); k++) {
            double val = data.instance(k).value(attrIdx);
            if (lessThan ? (val < targetIndex) : (Math.abs(val - targetIndex) < 0.0001)) {
                subset.add(data.instance(k));
            }
        }
        return subset;
    }

    private String extractFeatureNames(Instances data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.numAttributes(); i++) {
            if (i != data.classIndex()) {
                if (!sb.isEmpty()) sb.append(";");
                sb.append(data.attribute(i).name());
            }
        }
        return sb.toString();
    }

    private Instances removeColumns(Instances data, List<String> columnNames) throws Exception {
        if (columnNames == null || columnNames.isEmpty()) {
            return data;
        }

        List<Integer> indices = new ArrayList<>();
        for (String name : columnNames) {
            Attribute a = data.attribute(name);
            if (a != null) {
                indices.add(a.index());
            }
        }

        if (indices.isEmpty()) {
            return data;
        }

        Remove remove = new Remove();
        remove.setAttributeIndicesArray(indices.stream().mapToInt(i -> i).toArray());
        remove.setInputFormat(data);
        return Filter.useFilter(data, remove);
    }

    private int getPositiveClassIndex(Instances data) {
        int idx = data.classAttribute().indexOfValue(ProjectConstants.BUGGY_LABEL);
        return (idx == -1) ? 1 : idx;
    }

}