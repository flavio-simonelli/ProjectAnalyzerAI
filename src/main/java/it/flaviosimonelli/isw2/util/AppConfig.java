package it.flaviosimonelli.isw2.util;

import it.flaviosimonelli.isw2.exception.ConfigException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class AppConfig {
    private static final String CONFIG_FILE = "config.properties";
    private static Properties properties;

    private AppConfig() {
        throw new IllegalStateException("Utility class");
    }

    // Caricamento statico (Lazy load)
    private static void load() {
        properties = new Properties();
        // Prima prova a cercarlo nella cartella corrente (utile per l'utente finale)
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
        } catch (IOException _) {
            // Se non c'è, prova a cercarlo nel classpath (utile durante lo sviluppo in IDE)
            try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (input == null) {
                    throw new ConfigException("Impossibile trovare il file " + CONFIG_FILE + " nel filesystem o nel classpath.");
                }
                properties.load(input);
            } catch (IOException e) {
                throw new ConfigException("Errore critico durante il caricamento della configurazione", e);
            }
        }
    }

    public static String get(String key) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        if (val == null) throw new ConfigException("Chiave obbligatoria mancante nel config: " + key);
        return val;
    }

    public static int getInt(String key, int defaultValue) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException _) {
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException _) {
            return defaultValue;
        }
    }
    public static boolean getBoolean(String key, boolean defaultValue) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val.trim());
    }

    public static List<String> getList(String key, String defaultValue) {
        String raw = getProperty(key, defaultValue);

        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Split sicuro su virgola e trim tramite Stream
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Recupera una stringa con un valore di default se la chiave non esiste.
     * Utile per parametri opzionali (es. path di output).
     */
    public static String getProperty(String key, String defaultValue) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        return (val == null) ? defaultValue : val;
    }

    /**
     * Ritorna la versione Java configurata per PMD (default: "1.8")
     */
    public static String getPmdJavaVersion() {
        // Usiamo il tuo metodo getProperty che gestisce già il load()
        return getProperty("pmd.java.version", "1.8");
    }

    /**
     * Ritorna l'array di ruleset configurati.
     * Legge una stringa separata da virgole e restituisce l'array.
     */
    public static String[] getPmdRuleSets() {
        // Fallback default
        String defaultRules = "category/java/design.xml, category/java/bestpractices.xml, category/java/errorprone.xml";

        // Ora riutilizziamo il metodo sicuro getList
        List<String> rules = getList("pmd.rulesets", defaultRules);

        return rules.toArray(new String[0]);
    }

    public static String getRefactoringOriginalFile() {
        return getProperty("refactoring.original.file", "refactoring_experiments/Original.java");
    }

    public static String getRefactoringRefactoredFile() {
        return getProperty("refactoring.refactored.file", "refactoring_experiments/Refactored.java");
    }

    public static String getRefactoringTargetSignature() {
        return getProperty("refactoring.target.signature", "");
    }

    public static String getRefactoringOutputDir() {
        String base = getProperty("output.base.path", "./results");
        return base + java.io.File.separator + "refactoring_experiments";
    }

    public static String getRefactoringOutputFullCsv() {
        return getRefactoringOutputDir() + java.io.File.separator + "refactoring_experiment_results.csv";
    }

}
