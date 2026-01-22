package it.flaviosimonelli.isw2.model;

import java.util.Objects;

/**
 * Rappresenta l'identità univoca di un metodo.
 * È progettata per essere usata come CHIAVE nelle HashMap.
 * È IMMUTABILE.
 */
public final class MethodIdentity {

    // L'identificativo univoco (es. "it.pkg.MyClass.myMethod(int)")
    private final String fullSignature;

    // Metadati utili già separati (per comodità di scrittura CSV)
    private final String className;
    private final String methodName;

    public MethodIdentity(String fullSignature, String className, String methodName) {
        // Fail-fast: Non ha senso avere una identità null
        this.fullSignature = Objects.requireNonNull(fullSignature, "La signature non può essere null");
        this.className = className;
        this.methodName = methodName;
    }

    // --- GETTERS ---
    public String getFullSignature() {
        return fullSignature;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    // --- CORE LOGIC PER HASHMAP ---

    /**
     * Due identità sono uguali se hanno la stessa firma completa.
     * ClassName e MethodName sono derivati, quindi non servono per l'uguaglianza.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodIdentity that = (MethodIdentity) o;
        return fullSignature.equals(that.fullSignature);
    }

    /**
     * L'hashcode deve essere consistente con equals.
     */
    @Override
    public int hashCode() {
        return Objects.hash(fullSignature);
    }

    @Override
    public String toString() {
        return fullSignature;
    }
}