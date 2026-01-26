package it.flaviosimonelli.isw2.ml.sampling;

import weka.core.Instances;

/**
 * Interfaccia per gestire le strategie di bilanciamento delle classi.
 */
public interface SamplingStrategy {
    /**
     * Applica il bilanciamento al dataset di training.
     * @param trainingData Il dataset sbilanciato.
     * @return Il dataset bilanciato.
     */
    Instances apply(Instances trainingData) throws Exception;
}