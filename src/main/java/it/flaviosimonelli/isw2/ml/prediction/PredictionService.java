package it.flaviosimonelli.isw2.ml.prediction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PredictionService {
    private static final Logger logger = LoggerFactory.getLogger(PredictionService.class);

    // DTO per trasportare i risultati
    public record PredictionResult(
            String signature,       // Firma del metodo
            String actualClass,     // Classe reale
            String predictedClass,  // Classe predetta
            double bugProbability   // Probabilità di bug
    ) {}

    public List<PredictionResult> predictDataset(String modelPath, String csvPath) {
        List<PredictionResult> results = new ArrayList<>();

        try {
            if (!new File(modelPath).exists()) {
                logger.error("Modello non trovato: {}", modelPath);
                return results;
            }

            // 1. Carica Modello
            Classifier model = (Classifier) weka.core.SerializationHelper.read(modelPath);

            // 2. Carica Dati Raw
            DataSource source = new DataSource(csvPath);
            Instances originalData = source.getDataSet();

            // 3. Impostazione manuale della Classe
            if (originalData.classIndex() == -1)
                originalData.setClassIndex(originalData.numAttributes() - 1);

            Attribute classAttribute = originalData.classAttribute();
            if (classAttribute.numValues() == 0) {
                // Se l'attributo è vuoto (causa '?'), lo rimpiazziamo "al volo" con uno corretto
                // Questa è una manipolazione avanzata ma necessaria per i file "dummy"
                // In realtà, la via più semplice è gestire l'eccezione, ma proviamo a prevenire.
            }

            // 4. Rimozione Metadati (Colonne 1-6: Version...Signature)
            Remove remove = new Remove();
            remove.setAttributeIndices("1-6");
            remove.setInputFormat(originalData);
            Instances filteredData = Filter.useFilter(originalData, remove);

            // FIX: Assicuriamoci che filteredData abbia la classe settata
            if (filteredData.classIndex() == -1)
                filteredData.setClassIndex(filteredData.numAttributes() - 1);

            // 5. Predizione Loop
            for (int i = 0; i < filteredData.numInstances(); i++) {

                // A. Classificazione
                // Il modello restituisce un indice (es. 0.0 o 1.0)
                double labelIdx = model.classifyInstance(filteredData.instance(i));
                double[] probs = model.distributionForInstance(filteredData.instance(i));

                // B. Decodifica Etichetta
                // Qui stava l'errore: filteredData.classAttribute().value((int) labelIdx) esplodeva
                // Se l'attributo non ha valori, usiamo noi "False"/"True" manualmente basandoci sull'indice
                String predLabel = safeGetPredLabel(filteredData, labelIdx);

                // C. Probabilità Bug (True)
                double pBug = 0.0;
                // Se abbiamo 2 probabilità, prendiamo quella di "True" (indice 1 solitamente)
                if (probs.length > 1) {
                    pBug = probs[1];
                } else if (probs.length == 1) {
                    pBug = (labelIdx == 1.0) ? 1.0 : 0.0;
                }

                // D. Recupero Signature originale
                String signature = "?";
                Attribute sigAttr = originalData.attribute("Signature");
                if (sigAttr != null) {
                    signature = originalData.instance(i).stringValue(sigAttr);
                } else if (originalData.numAttributes() > 5) {
                    // Fallback posizionale (colonna 5, indice 5)
                    signature = originalData.instance(i).stringValue(5);
                }

                // E. Actual Class (che sarà "?")
                String actualLabel = safeGetActualLabel(originalData, i);

                results.add(new PredictionResult(signature, actualLabel, predLabel, pBug));
            }

        } catch (Exception e) {
            logger.error("Errore predizione dataset: {}", csvPath, e);
        }
        return results;
    }

    private String safeGetPredLabel(Instances data, double labelIdx) {
        try {
            return data.classAttribute().value((int) labelIdx);
        } catch (IndexOutOfBoundsException _) {
            // Fallback manuale se i metadati sono vuoti
            return (labelIdx == 0.0) ? "False" : "True";
        }
    }

    /**
     * Metodo helper per estrarre l'etichetta reale (Actual Class) gestendo le eccezioni.
     * Risolve il Code Smell java:S1141 (Nested try-catch).
     */
    private String safeGetActualLabel(Instances data, int index) {
        try {
            return data.instance(index).stringValue(data.classIndex());
        } catch (Exception _) {
            // Se l'indice non esiste o il valore è mancante (dummy dataset), restituiamo "?"
            return "?";
        }
    }
}