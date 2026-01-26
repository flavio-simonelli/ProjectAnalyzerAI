package it.flaviosimonelli.isw2.ml.data;

import it.flaviosimonelli.isw2.ml.exceptions.DatasetLoadingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.util.ArrayList;
import java.util.List;

public class WekaDataLoader {
    private static final Logger logger = LoggerFactory.getLogger(WekaDataLoader.class);

    /**
     * Carica il dataset, imposta la classe target e rimuove le colonne indesiderate.
     * * @param csvPath Percorso del file CSV.
     * @param className Il nome esatto della colonna target (es. "Buggy").
     * @param columnsToIgnore Array di stringhe con i nomi delle colonne da scartare (es. ID, Path).
     * @return Instances pronto per il training.
     */
    public Instances loadData(String csvPath, String className, String[] columnsToIgnore) {

        logger.info("Caricamento dataset da: {}", csvPath);

        Instances data;
        try {
            // 1. Caricamento Grezzo
            DataSource source = new DataSource(csvPath);
            data = source.getDataSet();
        } catch (Exception e) {
            logger.error("Errore critico durante la lettura del file CSV: {}", e.getMessage());
            throw new DatasetLoadingException("Impossibile leggere il file CSV al percorso: " + csvPath, e);
        }

        if (data == null) {
            throw new DatasetLoadingException("Il DataSource ha restituito un dataset nullo (file vuoto o corrotto?)");
        }

        // 2. Rimozione colonne inutili
        if (columnsToIgnore != null && columnsToIgnore.length > 0) {
            try {
                data = removeAttributesByName(data, columnsToIgnore);
            } catch (Exception e) {
                throw new DatasetLoadingException("Errore durante il filtraggio delle colonne", e);
            }
        }

        // 3. Impostazione della Classe (Target)
        Attribute classAttr = data.attribute(className);
        if (classAttr == null) {
            logger.error("Colonna target '{}' non trovata.", className);
            throw new DatasetLoadingException("La colonna target '" + className + "' non esiste nel dataset.");
        }

        data.setClass(classAttr);

        return data;
    }

    /**
     * Rimuove attributi specificandone il nome.
     */
    private Instances removeAttributesByName(Instances data, String[] attributeNames) throws Exception {
        List<Integer> indicesToRemove = new ArrayList<>();

        for (String name : attributeNames) {
            Attribute attr = data.attribute(name);
            if (attr != null) {
                indicesToRemove.add(attr.index());
                logger.trace("Marcato per rimozione attributo: {}", name);
            } else {
                logger.warn("Attributo da rimuovere '{}' non trovato nel dataset.", name);
            }
        }

        // Se non c'è nulla da rimuovere, restituisci i dati originali
        if (indicesToRemove.isEmpty()) {
            return data;
        }

        // Convertiamo la lista in un array di int per il filtro
        int[] indicesArray = indicesToRemove.stream().mapToInt(i -> i).toArray();

        // Configurazione del filtro Remove
        Remove removeFilter = new Remove();
        removeFilter.setAttributeIndicesArray(indicesArray);
        removeFilter.setInvertSelection(false); // False = rimuovi quelli selezionati
        removeFilter.setInputFormat(data);

        Instances filteredData = Filter.useFilter(data, removeFilter);
        logger.debug("Rimossi {} attributi. Nuovo totale attributi: {}", indicesArray.length, filteredData.numAttributes());

        return filteredData;
    }
}