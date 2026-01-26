package it.flaviosimonelli.isw2.ml.evaluation;

/**
 * Contenitore dei risultati. output della validazione
 */
public class EvaluationResult {
    private int releaseIndex; // A quale release si riferisce questo test
    private double precision;
    private double recall;
    private double fMeasure;
    private double auc;      // Area Under ROC
    private double kappa;    // Cohen's Kappa
    private double npofb20;  // Number of Predicted Bugs in top 20% LOC

    public EvaluationResult() {}

    public EvaluationResult(int releaseIndex, double precision, double recall, double fMeasure,
                            double auc, double kappa, double npofb20) {
        this.releaseIndex = releaseIndex;
        this.precision = precision;
        this.recall = recall;
        this.fMeasure = fMeasure;
        this.auc = auc;
        this.kappa = kappa;
        this.npofb20 = npofb20;
    }

    // getter e setter
    public int getReleaseIndex() { return releaseIndex; }

    public double getPrecision() {
        return precision;
    }
    public void setPrecision(double precision) {
        this.precision = precision;
    }

    public double getRecall() {
        return recall;
    }
    public void setRecall(double recall) {
        this.recall = recall;
    }

    public double getFMeasure() {
        return fMeasure;
    }
    public void setFMeasure(double fMeasure) {
        this.fMeasure = fMeasure;
    }

    public double getAuc() {
        return auc;
    }
    public void setAuc(double auc) {
        this.auc = auc;
    }

    public double getKappa() {
        return kappa;
    }
    public void setKappa(double kappa) {
        this.kappa = kappa;
    }

    public double getNpofb20() {
        return npofb20;
    }
    public void setNpofb20(double npofb20) {
        this.npofb20 = npofb20;
    }

    @Override
    public String toString() {
        return String.format("Prec: %.3f, Rec: %.3f, AUC: %.3f, Kappa: %.3f, NPofB20: %.3f",
                precision, recall, auc, kappa, npofb20);
    }
}
