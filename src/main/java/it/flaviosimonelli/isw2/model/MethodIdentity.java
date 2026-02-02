package it.flaviosimonelli.isw2.model;

import java.util.Objects;

/**
 * Rappresenta l'identità univoca di un metodo.
 * Implementata come Record per garantire immutabilità e concisione (SonarCloud java:S6206).
 *
 * @param fullSignature L'identificativo univoco (es. "it.pkg.MyClass.myMethod(int)")
 * @param className     Nome della classe
 * @param methodName    Nome del metodo
 */
public record MethodIdentity(String fullSignature, String className, String methodName) {

    /**
     * Costruttore compatto per la validazione.
     * Le assegnazioni ai campi avvengono automaticamente alla fine del blocco.
     */
    public MethodIdentity {
        Objects.requireNonNull(fullSignature, "La signature non può essere null");
    }

    /**
     * Redefiniamo equals per mantenere la tua logica originale:
     * l'uguaglianza dipende solo dalla firma completa.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodIdentity that)) return false;
        return Objects.equals(fullSignature, that.fullSignature);
    }

    /**
     * L'hashcode deve essere consistente con la logica di equals basata solo sulla firma.
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