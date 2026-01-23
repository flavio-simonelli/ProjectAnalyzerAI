package it.flaviosimonelli.isw2.szz.impl;

import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.szz.IVEstimationStrategy;
import it.flaviosimonelli.isw2.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class IncrementalProportionStrategy implements IVEstimationStrategy {
    private static final Logger logger = LoggerFactory.getLogger(IncrementalProportionStrategy.class);

    private final List<JiraRelease> releases;
    private final List<Double> pWindow;
    private final int windowSize;
    private final double defaultP;


    public IncrementalProportionStrategy(List<JiraRelease> releases) {
        this.releases = releases;
        this.pWindow = new ArrayList<>();

        // 1. Caricamento Configurazione dinamica
        // Se manca nel file properties, usa i default (3 e 0.01)
        this.windowSize = AppConfig.getInt("szz.proportion.window_size", 3);
        this.defaultP = AppConfig.getDouble("szz.proportion.default_p", 0.01);

        logger.debug("IncrementalProportionStrategy inizializzata. WindowSize={}, DefaultP={}", windowSize, defaultP);
    }

    @Override
    public void learn(JiraRelease iv, JiraRelease fv, JiraRelease ov) {
        if (iv == null || fv == null || ov == null) return;

        double p = calculateP(iv, fv, ov);

        // Sanity Check: P deve essere un valore logico.
        // Accettiamo valori leggermente > 1 per gestire casi limite di versioning,
        // ma scartiamo anomalie estreme.
        if (p >= 0.0 && p <= 10.0) {
            pWindow.add(p);

            // LOGICA IBRIDA (Accumulo -> Sliding):
            // Finché pWindow.size() < windowSize, stiamo accumulando "tutti i ticket noti".
            // Appena superiamo la soglia, rimuoviamo il più vecchio (FIFO) e diventa Sliding Window.
            if (pWindow.size() > windowSize) {
                pWindow.removeFirst();
            }
        }
    }

    @Override
    public JiraRelease estimate(JiraRelease fv, JiraRelease ov) {
        if (fv == null || ov == null) return null;

        // Se FV <= OV (anomalia dati), restituiamo FV come fallback sicuro
        if (fv.isBeforeOrEqual(ov)) return fv;

        // Calcolo della P media (Proportion)
        // Se la finestra è vuota (Cold Start assoluto), usiamo il default.
        // Se è parzialmente piena, usa la media di quelli che ha (Cumulative Average).
        // Se è piena, usa la media degli ultimi N (Moving Average).
        double pAvg = getAverageP();

        // Formula: IV = FV - (P * (FV - OV))
        int fvIdx = releases.indexOf(fv);
        int ovIdx = releases.indexOf(ov);

        // Distanza temporale (in numero di versioni) tra Apertura e Fix
        int distance = fvIdx - ovIdx;

        // Stima del salto indietro
        int estimatedJump = (int) Math.round(distance * pAvg);
        // Indice stimato IV
        int ivIdx = fvIdx - estimatedJump;

        // Bound Checks (non possiamo andare prima della prima release o dopo l'ultima)
        if (ivIdx < 0) ivIdx = 0;
        if (ivIdx >= releases.size()) ivIdx = releases.size() - 1;

        return releases.get(ivIdx);
    }

    // --- Helpers Interni ---

    private double calculateP(JiraRelease iv, JiraRelease fv, JiraRelease ov) {
        int fvIdx = releases.indexOf(fv);
        int ivIdx = releases.indexOf(iv);
        int ovIdx = releases.indexOf(ov);

        if (fvIdx <= ovIdx) return 0.0;
        return (double) (fvIdx - ivIdx) / (fvIdx - ovIdx);
    }

    private double getAverageP() {
        if (pWindow.isEmpty()) {
            return defaultP; // Fallback se non abbiamo ancora imparato nulla
        }
        // Calcola la media aritmetica degli elementi correnti nella finestra
        return pWindow.stream().mapToDouble(val -> val).average().orElse(defaultP);
    }
}