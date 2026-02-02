package it.flaviosimonelli.isw2.snoring;

import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SnoringControlService {
    private static final Logger logger = LoggerFactory.getLogger(SnoringControlService.class);

    // Configurazione
    private final double discardRatio;
    private final boolean keepBuggyInSnoring;
    private final int totalReleases;
    private final int stopIndex;

    // Statistiche Interne
    private int statsReleasesSkipped = 0;
    private int statsRowsKept = 0;
    private int statsRowsDroppedSnoring = 0;
    private int statsBuggyKeptSnoring = 0;
    private int statsCleanDroppedSnoring = 0;

    public SnoringControlService(List<JiraRelease> releases) {
        this.totalReleases = releases.size();

        // 1. Carica Configurazione
        this.discardRatio = AppConfig.getDouble("dataset.generation.snoring_discard_ratio", 0.66);
        this.keepBuggyInSnoring = AppConfig.getBoolean("dataset.generation.snoring.keep_only_buggy", false);

        // 2. Calcola Indice di Taglio
        int calculatedStopIndex = (int) Math.round(totalReleases * (1.0 - discardRatio));
        // Sanity check: processiamo almeno 1 release se possibile
        if (calculatedStopIndex < 1 && totalReleases > 0) calculatedStopIndex = 1;
        this.stopIndex = calculatedStopIndex;

        printInitLog(releases);
    }

    private void printInitLog(List<JiraRelease> releases) {
        String stopReleaseName = (stopIndex > 0 && stopIndex <= releases.size())
                ? releases.get(stopIndex - 1).getName()
                : "N/A";

        logger.info("=== SNORING CONTROL INITIALIZED ===");
        logger.info("Total Releases: {}", totalReleases);
        logger.info("Ratio: {} (Zona Snoring inizia dopo indice {})", discardRatio, stopIndex);
        logger.info("Strategy: {}", keepBuggyInSnoring ? "FILTER (Keep Buggy Only)" : "CUTOFF (Truncate Dataset)");
        logger.info("Dataset Limit: Processerò fino alla release #{} ({})", stopIndex, stopReleaseName);
    }

    /**
     * Controlla se l'intera generazione deve fermarsi (CUTOFF strategy).
     * @param releaseIndex Indice corrente (0-based)
     * @return true se dobbiamo fare BREAK nel loop principale.
     */
    public boolean shouldStopProcessingReleases(int releaseIndex) {
        // Se siamo nella zona snoring e la strategia è CUTOFF (non keepBuggy)
        if (releaseIndex >= stopIndex && !keepBuggyInSnoring) {
            statsReleasesSkipped = totalReleases - releaseIndex;
            return true;
        }
        return false;
    }

    /**
     * Controlla se siamo nella zona snoring (informativo per log).
     */
    public boolean isSnoringZone(int releaseIndex) {
        return releaseIndex >= stopIndex;
    }

    /**
     * Decide se una specifica riga (metodo) deve essere mantenuta o scartata.
     * Gestisce anche l'aggiornamento delle statistiche interne.
     * * @param releaseIndex Indice della release corrente
     * @param isBuggy Se il metodo è buggato (Yes) o no (No)
     * @return true se la riga va scritta nel CSV, false se va scartata.
     */
    public boolean shouldKeepRow(int releaseIndex, boolean isBuggy) {
        // Se siamo nella SAFE ZONE (prima dello stopIndex), teniamo tutto.
        if (releaseIndex < stopIndex) {
            statsRowsKept++;
            return true;
        }

        // --- CHECK DIFENSIVO: STRATEGIA CUTOFF ---
        // Se la configurazione dice "SCARTA TUTTO" (!keepBuggyInSnoring),
        // dobbiamo restituire FALSE a prescindere.
        // Questo ci protegge nel caso in cui il Controller dimentichi di fare il "break".
        if (!keepBuggyInSnoring) {
            // Non aggiorniamo statistiche specifiche di riga qui perché tecnicamente
            // l'intera release doveva essere saltata, ma blocchiamo la scrittura.
            return false;
        }

        // --- CHECK STRATEGIA FILTER (Keep Buggy) ---
        // Se siamo qui, keepBuggyInSnoring è TRUE.
        if (isBuggy) {
            // È un Bug nella zona snoring -> TIENI
            statsBuggyKeptSnoring++;
            statsRowsKept++;
            return true;
        } else {
            // È Pulito nella zona snoring -> SCARTA (Probabile Falso Negativo)
            statsCleanDroppedSnoring++;
            statsRowsDroppedSnoring++;
            return false;
        }
    }

    public void printFinalReport(String outputCsvPath) {
        logger.info("===============================================================");
        logger.info("                DATASET GENERATION REPORT                      ");
        logger.info("===============================================================");
        logger.info("File Output: {}", outputCsvPath);
        logger.info("Strategy Used:  {}", keepBuggyInSnoring ? "FILTER (Keep Buggy)" : "CUTOFF (Truncate)");
        logger.info("---------------------------------------------------------------");

        if (!keepBuggyInSnoring) {
            // Report per CUTOFF
            logger.info("Releases Processed: {}", (totalReleases - statsReleasesSkipped));
            logger.info("Releases Skipped:   {}", statsReleasesSkipped);
            logger.info("Total Rows Written: {}", statsRowsKept);
            logger.info("(Note: Rows in skipped releases were not calculated)");
        } else {
            // Report per FILTER
            logger.info("Releases Processed: {}", totalReleases);
            logger.info("Total Rows Written: {}", statsRowsKept);
            logger.info("Snoring Zone Stats:");
            logger.info("   - Rows Dropped (Clean/No): {}", statsCleanDroppedSnoring);
            logger.info("   - Rows Kept (Buggy/Yes):   {}", statsBuggyKeptSnoring);
            logger.info("   - Total Filtered Out:      {}", statsRowsDroppedSnoring);
        }
        logger.info("===============================================================");
    }
}