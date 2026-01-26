package it.flaviosimonelli.isw2.config;

public final class ProjectConstants {

    // Costruttore privato per evitare istanziazione
    private ProjectConstants() {}

    // Nome della colonna Target nel CSV
    public static final String TARGET_CLASS = "isBuggy";
    public static final String VERSION_ATTRIBUTE = "Version";
    public static final String RELEASE_INDEX_ATTRIBUTE = "ReleaseIndex";
    public static final String DATA_ATTRIBUTE = "ReleaseData";
    public static final String LOC_ATTRIBUTE = "LOC";

    // Valori della classe target (utile per controlli futuri)
    public static final String BUGGY_LABEL = "True";
    public static final String CLEAN_LABEL = "False";

    // Altre costanti globali se serviranno (es. nome file default)
    public static final String DATASET_SUFFIX = "_dataset.csv";
    public static final String CORRELATION_SUFFIX = "_correlation.csv";
}