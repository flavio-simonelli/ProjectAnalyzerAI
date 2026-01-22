package it.flaviosimonelli.isw2.szz.impl;

import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.szz.IVEstimationStrategy;

import java.util.ArrayList;
import java.util.List;

public class IncrementalProportionStrategy implements IVEstimationStrategy {

    private final List<JiraRelease> releases;
    private final List<Double> pWindow;
    private final int windowSize;
    private final double defaultP;

    public IncrementalProportionStrategy(List<JiraRelease> releases) {
        this(releases, 3, 0.01); // Default: Window=3, P=1%
    }

    public IncrementalProportionStrategy(List<JiraRelease> releases, int windowSize, double defaultP) {
        this.releases = releases;
        this.pWindow = new ArrayList<>();
        this.windowSize = windowSize;
        this.defaultP = defaultP;
    }

    @Override
    public void learn(JiraRelease iv, JiraRelease fv, JiraRelease ov) {
        if (iv == null || fv == null || ov == null) return;

        double p = calculateP(iv, fv, ov);

        // Sanity check per valori P coerenti (0 <= P <= 1.5 circa)
        if (p >= 0.0 && p <= 2.0) {
            pWindow.add(p);
            // Gestione Sliding Window (FIFO)
            if (pWindow.size() > windowSize) {
                pWindow.remove(0);
            }
        }
    }

    @Override
    public JiraRelease estimate(JiraRelease fv, JiraRelease ov) {
        if (fv == null || ov == null) return null;

        // Se FV <= OV (anomalia dati), restituiamo FV come fallback sicuro
        if (fv.isBeforeOrEqual(ov)) return fv;

        double pAvg = getAverageP();

        // Formula: IV = FV - (P * (FV - OV))
        int fvIdx = releases.indexOf(fv);
        int ovIdx = releases.indexOf(ov);
        int distance = fvIdx - ovIdx;

        int estimatedDistance = (int) Math.round(distance * pAvg);
        int ivIdx = fvIdx - estimatedDistance;

        // Bound Checks
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
        if (pWindow.isEmpty()) return defaultP;
        return pWindow.stream().mapToDouble(val -> val).average().orElse(defaultP);
    }
}