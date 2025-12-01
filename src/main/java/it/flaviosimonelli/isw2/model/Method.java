package it.flaviosimonelli.isw2.model;

public class Method {
    private String className;
    private String methodName;
    int loc;

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
