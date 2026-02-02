package it.flaviosimonelli.isw2.ml.reporting;

import it.flaviosimonelli.isw2.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GraphGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(GraphGenerationService.class);

    public void generateGraphs(String inputCsvPath, String outputImagesDir) {
        String pythonExec = AppConfig.getProperty("python.exec", "python");
        String scriptPath = AppConfig.getProperty("python.script.path", "scripts/plotting/generate_graphs.py");

        // Verifica esistenza script
        if (!new File(scriptPath).exists()) {
            logger.error("Script Python non trovato in: {}", scriptPath);
            return;
        }

        try {
            logger.info("Avvio generazione grafici Python...");

            // Costruzione comando: python script.py <input> <output>
            List<String> command = new ArrayList<>();
            command.add(pythonExec);
            command.add(scriptPath);
            command.add(inputCsvPath);    // Argomento 1
            command.add(outputImagesDir); // Argomento 2

            ProcessBuilder pb = new ProcessBuilder(command);

            // Reindirizza l'output di Python sulla console di Java (utile per debug)
            // Oppure leggilo manualmente come sotto:
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Leggiamo l'output del processo Python
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[PYTHON] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Grafici generati correttamente in: {}", outputImagesDir);
            } else {
                logger.error("Lo script Python è terminato con errore. Exit code: {}", exitCode);
            }

        } catch (InterruptedException e) {
            // 1. Ripristina lo stato di interruzione
            Thread.currentThread().interrupt();
            // 2. Log dell'errore
            logger.error("Generazione grafici interrotta forzatamente", e);
        } catch (IOException e) {
            // Gestione specifica per IO (ProcessBuilder, etc)
            logger.error("Errore di I/O durante l'esecuzione dello script Python", e);
        } catch (Exception e) {
            // Catch-all per altre eccezioni non previste
            logger.error("Errore generico durante l'esecuzione dello script Python", e);
        }
    }
}