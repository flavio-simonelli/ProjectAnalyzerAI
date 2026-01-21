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
}
