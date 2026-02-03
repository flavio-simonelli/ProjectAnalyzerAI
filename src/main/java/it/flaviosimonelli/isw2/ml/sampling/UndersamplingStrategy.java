package it.flaviosimonelli.isw2.ml.sampling;

import it.flaviosimonelli.isw2.ml.exceptions.SamplingException;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SpreadSubsample;

/**
 * Implementazione dell'Undersampling utilizzando SpreadSubsample.
 * Ideale per dataset di grandi dimensioni come OpenJPA per ridurre i tempi di training.
 */
public class UndersamplingStrategy implements SamplingStrategy {

    private int seed = 42;

    public void setSeed(int seed) {
        this.seed = seed;
    }

    @Override
    public Instances apply(Instances trainingData) {
        try {
            SpreadSubsample filter = new SpreadSubsample();

            // Impostiamo il formato in base ai dati di training
            filter.setInputFormat(trainingData);

            /* * setDistributionSpread(1.0) indica un rapporto 1:1.
             * Se abbiamo 1000 istanze Buggy, il filtro terrà solo 1000 istanze Clean,
             * eliminando casualmente le restanti.
             */
            filter.setDistributionSpread(1.0);
            filter.setRandomSeed(this.seed);

            return Filter.useFilter(trainingData, filter);

        } catch (Exception e) {
            // Incapsuliamo l'eccezione di Weka nella tua eccezione di dominio
            throw new SamplingException("Errore durante l'applicazione dell'Undersampling", e);
        }
    }
}