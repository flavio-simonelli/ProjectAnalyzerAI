package it.flaviosimonelli.isw2.ml.validation;

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

    /**
     * Esegue la validazione Walk-Forward.
     *
     * @param data Il dataset completo.
     * @param classifier Il classificatore.
     * @param columnsToDrop Colonne amministrative da rimuovere.
     * @param samplingStrategy Strategia di bilanciamento (SMOTE, etc).
     * @param fsStrategy Strategia di selezione feature (BestFirst, etc).
     * @return Lista dei risultati.
     */
    public List<EvaluationResult> validate(Instances data, Classifier classifier, String[] columnsToDrop, SamplingStrategy samplingStrategy, FeatureSelectionStrategy fsStrategy) {
        List<EvaluationResult> results = new ArrayList<>();

        // 1. Recupera l'attributo ReleaseIndex (Numerico: 1, 2, 3...)
        Attribute indexAttr = data.attribute(ProjectConstants.RELEASE_INDEX_ATTRIBUTE);
        if (indexAttr == null) {
            throw new ModelEvaluationException("Colonna '" + ProjectConstants.RELEASE_INDEX_ATTRIBUTE + "' mancante. Impossibile ordinare temporalmente.");
        }

        try {
            // 2. Ordinamento (Fondamentale per il walkForward)
            data.sort(indexAttr);

            // 3. Calcolo numero release
            int numReleases = (int) data.attributeStats(indexAttr.index()).numericStats.max;
            logger.info("Avvio Walk-Forward su {} release.", numReleases);

            // 4. Ciclo Walk-Forward (Start from 2)
            for (int i = 2; i <= numReleases; i++) {

                // A. Split Temporale
                Instances rawTrain = filterByIndex(data, indexAttr, i, true);
                Instances rawTest = filterByIndex(data, indexAttr, i, false);

                // B. Check vacuità
                if (rawTest.numInstances() == 0) {
                    logger.warn("Fold Release {}: Test vuoti. Skipping.", i);
                    continue;
                }
                if (rawTrain.numInstances() == 0) {
                    logger.warn("Fold Release {}: Train vuoti. Skipping.", i);
                    continue;
                }

                // C. PULIZIA DATI (Rimuoviamo Version, Index, Date, ecc. ORA)
                // Se non lo facciamo, il modello impara che "Index=20" significa "Bug".
                Instances cleanTrain = removeColumns(rawTrain, columnsToDrop);
                Instances cleanTest = removeColumns(rawTest, columnsToDrop);

                // Sync header (sicurezza extra per Weka)
                if (!cleanTrain.equalHeaders(cleanTest)) {
                    throw new ModelEvaluationException("Mismatch headers tra Train e Test dopo la pulizia nel fold " + i);
                }

                logger.info("Fold Release {}: Train instances: {}, Test instances: {} (Attributi usati: {})",
                        i, cleanTrain.numInstances(), cleanTest.numInstances(), cleanTrain.numAttributes());

                // Salviamo un riferimento al test set "intero" (con LOC) PRIMA della Feature Selection.
                // Ci servirà solo alla fine per calcolare i costi, non per la predizione.
                Instances testSetForCostAnalysis = cleanTest;

                // --- 3. FEATURE SELECTION ---
                String selectedFeaturesString = "ALL"; // Default se non usiamo FS

                if (fsStrategy != null) {
                    // Applica la selezione: filtro train e test in base a ciò che è meglio per il train
                    Instances[] filtered = fsStrategy.apply(cleanTrain, cleanTest);
                    cleanTrain = filtered[0];
                    cleanTest = filtered[1];

                    // Salviamo i nomi delle feature rimaste per il report
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < cleanTrain.numAttributes(); k++) {
                        if (k != cleanTrain.classIndex()) {
                            if (!sb.isEmpty()) sb.append(";");
                            sb.append(cleanTrain.attribute(k).name());
                        }
                    }
                    selectedFeaturesString = sb.toString();
                    logger.debug("Fold {}: FS kept {} features.", i, cleanTrain.numAttributes() - 1);
                }

                // C. SAMPLING (Bilanciamento Classi)
                // APPLICATO SOLO AL TRAINING SET!
                // Il Test set deve rimanere sbilanciato come nella realtà.
                Instances trainToUse = cleanTrain;
                if (samplingStrategy != null) {
                    trainToUse = samplingStrategy.apply(cleanTrain);
                    logger.debug("Fold {}: SMOTE applied. Size: {} -> {}",
                            i, cleanTrain.numInstances(), trainToUse.numInstances());
                }

                // Conta quanti Buggy ci sono nel training set effettivo
                int[] stats = trainToUse.attributeStats(trainToUse.classIndex()).nominalCounts;
                // Assumendo indice 0=Clean, 1=Buggy (verifica l'ordine nel tuo ARFF se diverso)
                logger.info("Fold {}: Training Class Distribution -> Clean: {}, Buggy: {}",
                        i, stats[0], stats[1]);

                // D. Training & Evaluation
                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(classifier);
                clsCopy.buildClassifier(trainToUse);

                Evaluation eval = new Evaluation(cleanTrain); // L'eval si inizializza sulla struttura (va bene cleanTrain o trainToUse)
                eval.evaluateModel(clsCopy, cleanTest); // Il test DEVE essere cleanTest (non bilanciato)

                // E. Raccolta Metriche
                int posIdx = getPositiveClassIndex(cleanTrain);
                double npofb20 = calculateNPofB20(eval.predictions(), testSetForCostAnalysis, posIdx);

                results.add(new EvaluationResult(
                        i,
                        eval.precision(posIdx),
                        eval.recall(posIdx),
                        eval.fMeasure(posIdx),
                        eval.areaUnderROC(posIdx),
                        eval.kappa(),
                        npofb20,
                        selectedFeaturesString
                ));
            }

        } catch (ModelEvaluationException me) {
            throw me;
        } catch (Exception e) {
            // Cattura errori Weka generici e incapsula
            throw new ModelEvaluationException("Errore critico durante l'esecuzione del Walk-Forward: " + e.getMessage(), e);
        }

        return results;
    }

    // --- Helpers ---

    private Instances filterByIndex(Instances data, Attribute indexAttr, double targetIndex, boolean lessThan) {
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

    private Instances removeColumns(Instances data, String[] columnsNames) throws Exception {
        if (columnsNames == null || columnsNames.length == 0) return data;

        // Troviamo gli indici basati sui nomi
        List<Integer> indices = new ArrayList<>();
        for (String name : columnsNames) {
            Attribute a = data.attribute(name);
            if (a != null) indices.add(a.index());
        }

        if (indices.isEmpty()) return data;

        int[] indicesArray = indices.stream().mapToInt(i -> i).toArray();
        Remove remove = new Remove();
        remove.setAttributeIndicesArray(indicesArray);
        remove.setInputFormat(data);
        return Filter.useFilter(data, remove);
    }

    private int getPositiveClassIndex(Instances data) {
        int idx = data.classAttribute().indexOfValue(ProjectConstants.BUGGY_LABEL);
        return (idx == -1) ? 1 : idx;
    }

    private double calculateNPofB20(ArrayList<Prediction> predictions, Instances testData, int positiveClassIndex) {
        // 1. Verifica che LOC esista
        Attribute locAttr = testData.attribute(ProjectConstants.LOC_ATTRIBUTE);
        if (locAttr == null) {
            logger.warn("Colonna LOC '{}' non trovata. NPofB20 sarà 0.0", ProjectConstants.LOC_ATTRIBUTE);
            return 0.0;
        }

        // 2. Calcola LOC totali del test set e conta i Bug Reali
        double totalLoc = 0;
        int totalBugs = 0;

        // Struttura per associare predizione -> LOC
        List<PredictionLoc> rankedList = new ArrayList<>();

        for (int i = 0; i < predictions.size(); i++) {
            Prediction abstractPred = predictions.get(i);

            // --- CORREZIONE QUI ---
            // Verifichiamo e facciamo il cast a NominalPrediction
            if (abstractPred instanceof NominalPrediction pred) {

                // Accediamo alla distribuzione (array di probabilità per ogni classe)
                double[] dist = pred.distribution();

                // Prendiamo la probabilità della classe "Buggy" (Yes)
                // Se positiveClassIndex è corretto (es. 1), dist[1] è la probabilità di Bug.
                double probBuggy = dist[positiveClassIndex];

                // Recuperiamo LOC e Label reale
                double loc = testData.instance(i).value(locAttr);

                // Verifica se è realmente un bug (confrontando il valore reale con l'indice "Yes")
                // pred.actual() restituisce l'indice della classe reale (double)
                boolean isBuggy = ((int) pred.actual() == positiveClassIndex);

                totalLoc += loc;
                if (isBuggy) totalBugs++;

                rankedList.add(new PredictionLoc(probBuggy, loc, isBuggy));
            }
        }

        if (totalBugs == 0) return 0.0; // Nessun bug da trovare

        // 3. Ordina per probabilità decrescente (i più probabili bug in alto)
        rankedList.sort((a, b) -> Double.compare(b.prob, a.prob));

        // 4. Scorri la lista e conta i bug trovati nel primo 20% di LOC
        double locCutoff = totalLoc * 0.20;
        double currentLoc = 0;
        int foundBugs = 0;

        for (PredictionLoc item : rankedList) {
            currentLoc += item.loc;
            if (item.isBuggy) {
                foundBugs++;
            }

            // Ci fermiamo appena superiamo il 20% LOC
            if (currentLoc >= locCutoff) {
                break;
            }
        }

        // Restituiamo la proporzione di bug trovati (es. 0.85 = 85% dei bug presi)
        // Se il prof vuole il numero assoluto, restituisci (double) foundBugs
        return (double) foundBugs / totalBugs;
    }

    // Classe interna helper
    private static class PredictionLoc {
        double prob;
        double loc;
        boolean isBuggy;
        PredictionLoc(double p, double l, boolean b) { prob=p; loc=l; isBuggy=b; }
    }

}