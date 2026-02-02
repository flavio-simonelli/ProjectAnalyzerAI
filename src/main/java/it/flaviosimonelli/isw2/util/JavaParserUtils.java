package it.flaviosimonelli.isw2.util;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;

public class JavaParserUtils {

    private JavaParserUtils() {
        throw new IllegalStateException("Utility class - non istanziabile");
    }

    /**
     * Genera la firma univoca completa (Package + Classi + Metodo + Parametri).
     * Esempio: "it.pkg.Outer.Inner.myMethod(int, String)"
     */
    public static String getFullyQualifiedSignature(MethodDeclaration method, CompilationUnit cu) {
        String packageName = cu.getPackageDeclaration()
                .map(NodeWithName::getNameAsString)
                .orElse("");

        String classPrefix = getParentClassPrefix(method);
        String methodName = method.getSignature().asString();

        return (packageName.isEmpty() ? "" : packageName + ".") + classPrefix + methodName;
    }

    /**
     * Recupera solo il nome della classe (o catena di classi) parent.
     * Esempio: "Outer.Inner"
     */
    public static String getParentClassName(MethodDeclaration method) {
        String prefix = getParentClassPrefix(method);
        // Rimuove l'ultimo punto se presente (es "Class." -> "Class")
        if (prefix.endsWith(".")) {
            return prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    private static String getParentClassPrefix(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder();
        Node current = method.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration classDecl) {
                sb.insert(0, classDecl.getNameAsString() + ".");
            } else if (current instanceof EnumDeclaration enumDecl) {
                sb.insert(0, enumDecl.getNameAsString() + ".");
            }
            current = current.getParentNode().orElse(null);
        }
        return sb.toString();
    }
}