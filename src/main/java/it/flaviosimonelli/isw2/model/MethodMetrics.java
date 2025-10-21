package it.flaviosimonelli.isw2.model;

public class MethodMetrics {
    private String className;
    private String methodName;
    int loc;

    public MethodMetrics(String className, String methodName, int loc) {
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
