package it.flaviosimonelli.isw2.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CsvUtils {
    private static final Logger logger = LoggerFactory.getLogger(CsvUtils.class);

    // Formato Standard: Virgola come separatore, gestisce quote, ignora spazi attorno
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build();


    /**
     * Crea e restituisce un CSVPrinter configurato secondo lo standard del progetto.
     * Utile per scrivere file riga per riga (streaming) in loop di grandi dimensioni.
     */
    public static CSVPrinter createPrinter(String outputPath, String... headers) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath));

        return new CSVPrinter(writer, CSVFormat.DEFAULT
                .builder()
                .setHeader(headers) // Scrive automaticamente l'header all'inizio
                .build());
    }

    /**
     * Legge un CSV in modo robusto usando Apache Commons CSV.
     * Restituisce Map<NomeColonna, ListaValori>.
     */
    public static Map<String, List<String>> readCsvByColumn(String filePath) {
        Map<String, List<String>> dataset = new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(Paths.get(filePath));
             CSVParser parser = CSV_FORMAT.parse(reader)) {

            // 1. Inizializza le liste per ogni header trovato
            List<String> headers = parser.getHeaderNames();
            for (String header : headers) {
                dataset.put(header, new ArrayList<>());
            }

            // 2. Itera sui record (righe) gestendo automaticamente quote e virgole interne
            for (CSVRecord record : parser) {
                for (String header : headers) {
                    // check difensivo se la riga ha meno colonne dell'header
                    String value = record.isMapped(header) ? record.get(header) : "";
                    dataset.get(header).add(value);
                }
            }

        } catch (IOException e) {
            logger.error("Errore lettura CSV con Commons-CSV: {}", filePath, e);
        }
        return dataset;
    }

    /**
     * Scrive un CSV completo usando Apache Commons CSV.
     * Gestisce automaticamente l'escaping delle virgolette se necessario.
     */
    public static void writeCsv(String outputPath, List<String> headers, List<List<String>> rows) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            // Scrivi Header
            printer.printRecord(headers);

            // Scrivi Righe
            for (List<String> row : rows) {
                printer.printRecord(row);
            }

            // Il flush è automatico alla chiusura, ma lo forziamo per sicurezza
            printer.flush();
            logger.info("CSV scritto con successo: {}", outputPath);

        } catch (IOException e) {
            logger.error("Errore scrittura CSV: {}", outputPath, e);
        }
    }
}
