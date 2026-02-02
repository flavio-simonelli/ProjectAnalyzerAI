package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.config.ProjectConstants;
import it.flaviosimonelli.isw2.ml.data.WekaDataLoader;
import it.flaviosimonelli.isw2.util.AppConfig;
import it.flaviosimonelli.isw2.util.CsvUtils; // <--- Importiamo la tua utility
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DatasetVariantController {

    private static final Logger logger = LoggerFactory.getLogger(DatasetVariantController.class);

    private final String datasetPath;
    private final String projectKey;

    public DatasetVariantController(String datasetPath, String projectKey) {
        this.datasetPath = datasetPath;
        this.projectKey = projectKey;
    }

    public void createVariants() {
        logger.info(">>> CREAZIONE VARIANTI DATASET (A, B+, B, C) per {} <<<", projectKey);

        WekaDataLoader loader = new WekaDataLoader();
        // Cartella di output
        String outputDir = AppConfig.getProperty("output.base.path", "./results") + "/dataset/variants";
        new File(outputDir).mkdirs();

        try {
            // 1. Carica Dataset A (Originale Completo)
            Instances dataA = loader.loadData(datasetPath, ProjectConstants.TARGET_CLASS, null);
            logger.info("Dataset A caricato: {} istanze.", dataA.numInstances());

            // 2. Trova l'indice dell'attributo CodeSmells
            Attribute smellAttr = dataA.attribute("CodeSmells");
            if (smellAttr == null) {
                throw new RuntimeException("Colonna 'CodeSmells' non trovata nel dataset!");
            }
            int smellIdx = smellAttr.index();

            // 3. Prepara i contenitori vuoti (copia solo header)
            Instances dataBPlus = new Instances(dataA, 0); // B+: Smells > 0
            Instances dataB      = new Instances(dataA, 0); // B : Smells > 0 ma forzati a 0
            Instances dataC      = new Instances(dataA, 0); // C : Smells == 0 (naturali)

            // 4. Itera e smista
            for (int i = 0; i < dataA.numInstances(); i++) {
                Instance originalInst = dataA.instance(i);
                double smellValue = originalInst.value(smellIdx);

                if (smellValue > 0) {
                    // --- Caso B+ (Originale con smell) ---
                    dataBPlus.add((Instance) originalInst.copy());

                    // --- Caso B (Artificiale senza smell) ---
                    Instance artificialInst = (Instance) originalInst.copy();
                    // Modifichiamo il valore della cella a 0.0
                    artificialInst.setValue(smellIdx, 0.0);
                    dataB.add(artificialInst);
                } else {
                    // --- Caso C (Originale senza smell) ---
                    dataC.add((Instance) originalInst.copy());
                }
            }

            logger.info("Split completato. Scrittura CSV...");
            logger.info("Dataset B+ (Smelly): {} istanze", dataBPlus.numInstances());
            logger.info("Dataset B  (Fixed):  {} istanze", dataB.numInstances());
            logger.info("Dataset C  (Clean):  {} istanze", dataC.numInstances());

            // 5. Salva i file usando CsvUtils
            saveViaCsvUtils(dataBPlus, outputDir, projectKey + "_B_PLUS.csv");
            saveViaCsvUtils(dataB,      outputDir, projectKey + "_B_FIXED.csv");
            saveViaCsvUtils(dataC,      outputDir, projectKey + "_C_CLEAN.csv");

        } catch (Exception e) {
            logger.error("Errore durante la creazione delle varianti", e);
        }
    }

    /**
     * Converte le Instances Weka in liste di stringhe e le salva usando CsvUtils.
     */
    private void saveViaCsvUtils(Instances data, String dir, String fileName) {
        String fullPath = dir + File.separator + fileName;

        // A. Estrai gli Header
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < data.numAttributes(); i++) {
            headers.add(data.attribute(i).name());
        }

        // B. Estrai le Righe
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            List<String> row = new ArrayList<>();
            for (int attrIdx = 0; attrIdx < data.numAttributes(); attrIdx++) {
                // inst.toString(idx) restituisce la rappresentazione stringa del valore
                // (gestisce correttamente nominali, stringhe e numeri)
                row.add(inst.toString(attrIdx));
            }
            rows.add(row);
        }

        // C. Scrivi usando la tua utility
        CsvUtils.writeCsv(fullPath, headers, rows);
    }
}