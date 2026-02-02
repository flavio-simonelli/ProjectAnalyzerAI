package it.flaviosimonelli.isw2.ml.sampling;

import it.flaviosimonelli.isw2.ml.exceptions.SamplingException;
import weka.core.Instances;

/**
 * Interfaccia per gestire le strategie di bilanciamento delle classi.
 */
public interface SamplingStrategy {
    /**
     * Applica il bilanciamento al dataset di training.
     * @param trainingData Il dataset sbilanciato.
     * @return Il dataset bilanciato.
     * @throws SamplingException se il processo di campionamento (es. SMOTE) fallisce.
     */
    Instances apply(Instances trainingData);
}