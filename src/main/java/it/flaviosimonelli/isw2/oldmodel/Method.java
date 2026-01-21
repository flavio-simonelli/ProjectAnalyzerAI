package it.flaviosimonelli.isw2.model;

import java.util.ArrayList;

public class Method {
    // Informazioni base del metodo
    private String className;
    private String methodName;
    private String packageName;

    // informazioni riguardanti la sua presenza nel progetto
    private final List<Version> Presentversions = new ArrayList<>();

    // metriche del metodo
    private Map<Version, Integer> locPerCommit = new HashMap<>();
    private Map<Commit, Integer> statementPerCommit = new HashMap<>();
    private Map<Commit, Integer> cyclomaticComplexityPerCommit = new HashMap<>();
    private Map<Commit, Integer> cognitiveComplexityPerCommit = new HashMap<>();
    private Map<Version, Integer> methodHistoriesPerVersion = new HashMap<>();
    private Map<Commit, Integer> churnPerCommit = new HashMap<>();
    private Map<Commit, Integer> addedLinesPerCommit = new HashMap<>();
    private Map<Commit, Integer> deletedLinesPerCommit = new HashMap<>();
    private Map<Version, Boolean> buggyPerVersion = new HashMap<>();
    private Map<Commit, MethodInfo> methodInfoPerCommit = new HashMap<>();

    public Method(String className, String methodName, int loc) {
        this.className = className;
        this.methodName = methodName;
        this.loc = loc;
    }

    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public int getLoc() { return loc; }

    @Override
    public String toString() {
        return "MethodMetrics{" +
                "className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", loc=" + loc +
                '}';
    }
}
