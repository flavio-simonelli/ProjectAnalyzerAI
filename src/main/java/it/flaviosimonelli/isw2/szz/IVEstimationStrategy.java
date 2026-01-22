package it.flaviosimonelli.isw2.szz;

import it.flaviosimonelli.isw2.jira.bean.JiraRelease;

/**
 * Interfaccia per le strategie di stima dell'Injected Version (IV).
 * Permette di implementare diverse varianti (Incremental Proportion, Standard Proportion, etc.)
 */
public interface IVEstimationStrategy {

    /**
     * Addestra il modello con un dato reale (Ground Truth).
     * Usato quando Jira fornisce esplicitamente l'Affected Version.
     *
     * @param iv Injected Version (Reale)
     * @param fv Fixed Version
     * @param ov Opening Version
     */
    void learn(JiraRelease iv, JiraRelease fv, JiraRelease ov);

    /**
     * Stima l'Injected Version basandosi sui dati storici appresi.
     * Usato quando Jira NON fornisce l'Affected Version.
     *
     * @param fv Fixed Version
     * @param ov Opening Version
     * @return La Injected Version stimata
     */
    JiraRelease estimate(JiraRelease fv, JiraRelease ov);
}
