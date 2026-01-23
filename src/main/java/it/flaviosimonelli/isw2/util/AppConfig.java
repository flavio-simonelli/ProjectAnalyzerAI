package it.flaviosimonelli.isw2.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final String CONFIG_FILE = "config.properties";
    private static Properties properties;

    // Caricamento statico (Lazy load)
    private static void load() {
        properties = new Properties();
        // Prima prova a cercarlo nella cartella corrente (utile per l'utente finale)
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
        } catch (IOException ex) {
            // Se non c'è, prova a cercarlo nel classpath (utile durante lo sviluppo in IDE)
            try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (input == null) {
                    throw new RuntimeException("Impossibile trovare il file " + CONFIG_FILE);
                }
                properties.load(input);
            } catch (IOException e) {
                throw new RuntimeException("Errore critico caricando la configurazione", e);
            }
        }
    }

    public static String get(String key) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        if (val == null) throw new RuntimeException("Chiave mancante nel config: " + key);
        return val;
    }

    public static int getInt(String key, int defaultValue) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    public static boolean getBoolean(String key, boolean defaultValue) {
        if (properties == null) load();
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val.trim());
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

}
