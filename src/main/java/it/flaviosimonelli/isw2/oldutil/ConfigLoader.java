package it.flaviosimonelli.isw2.oldutil;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;

public class ConfigLoader {
    public static Config loadConfig() throws ConfigException {

        LoaderOptions loaderOptions = new LoaderOptions();

        Constructor constructor = new Constructor(Config.class, loaderOptions);

        Yaml yaml = new Yaml(constructor);

        try (InputStream inputStream = ConfigLoader.class
                .getClassLoader()
                .getResourceAsStream("config.yaml")) {

            if (inputStream == null) {
                throw new RuntimeException("File di configurazione 'config.yaml' non trovato nel classpath.");
            }

            Config config = yaml.load(inputStream);

            config.validate();

            return config;

        }catch (ConfigException e) {
            // Se l'eccezione è già una ConfigurationException (es. validazione), rilanciala direttamente.
            throw e;

        } catch (Exception e) {
            // Intercetta qualsiasi altro errore di I/O o di parsing (es. YAML malformato)
            throw new ConfigException("Errore durante il caricamento o l'analisi del file di configurazione YAML.", e);
        }
    }
}