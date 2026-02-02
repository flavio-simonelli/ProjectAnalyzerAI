package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola un indice aggregato di <b>Implementation Smells</b> (Cattive pratiche).
 * <p>
 * Questa metrica si concentra su errori di utilizzo del linguaggio che
 * aumentano la probabilità di bug, ESCLUDENDO metriche già calcolate
 * a parte (come LOC, Complessità, Parametri).
 * <br>
 * Regole PMD implementate:
 * <ul>
 * <li><b>EmptyCatchBlock:</b> Blocchi catch vuoti che silenziano errori.</li>
 * <li><b>AvoidCatchingGenericException:</b> Catch di 'Exception', 'RuntimeException' o 'Throwable'.</li>
 * <li><b>AvoidPrintStackTrace / SystemPrintln:</b> Uso di console invece di logger.</li>
 * <li><b>SwitchStmtsShouldHaveDefault:</b> Switch privi del caso default.</li>
 * <li><b>AvoidReassigningLoopVariables:</b> Modifica dell'indice del for all'interno del loop.</li>
 * </ul>
 * </p>
 */
public class CodeSmellsMetric implements IMetric {

    @Override
    public String getName() {
        return "CodeSmells";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        int smellCount = 0;

        // 1. SMELL: Empty Catch Block
        // "Ingoiare" le eccezioni nasconde i bug.
        smellCount += (int) method.findAll(CatchClause.class).stream()
                .filter(c -> c.getBody().getStatements().isEmpty())
                // Ignoriamo se c'è un commento (es. //ignore)
                .filter(c -> c.getBody().getOrphanComments().isEmpty())
                .count();

        // 2. SMELL: Generic Exception Catching
        // Catturare tutto impedisce di gestire errori specifici correttamente.
        smellCount += (int) method.findAll(CatchClause.class).stream()
                .map(CatchClause::getParameter)
                .map(Parameter::getType)
                .map(t -> t.asString())
                .filter(type -> type.equals("Exception") ||
                        type.equals("Throwable") ||
                        type.equals("RuntimeException") ||
                        type.equals("Error"))
                .count();

        // 3. SMELL: Print Stack Trace / System.out / System.err
        // Uso improprio della console.
        smellCount += (int) method.findAll(MethodCallExpr.class).stream()
                .filter(mc -> mc.getNameAsString().equals("printStackTrace"))
                .count();

        smellCount += (int) method.findAll(FieldAccessExpr.class).stream()
                .filter(fa -> fa.getScope().toString().equals("System") &&
                        (fa.getNameAsString().equals("out") || fa.getNameAsString().equals("err")))
                .count();

        // 4. SMELL: Missing Default in Switch
        // Uno switch senza default può causare comportamenti imprevisti se il valore non è gestito.
        smellCount += (int) method.findAll(SwitchStmt.class).stream()
                .filter(sw -> sw.getEntries().stream()
                        .noneMatch(entry -> entry.getLabels().isEmpty())) // Default non ha label
                .count();

        // 5. SMELL: Reassigning Loop Variables (Molto pericoloso)
        for (ForStmt forStmt : method.findAll(ForStmt.class)) {
            // Cerchiamo le variabili di inizializzazione (es. 'i' in 'int i=0')
            forStmt.getInitialization().stream()
                    .filter(init -> init.isVariableDeclarationExpr())
                    .flatMap(init -> init.asVariableDeclarationExpr().getVariables().stream())
                    .forEach(declarator -> {
                        String varName = declarator.getNameAsString();
                        // Cerchiamo se viene riassegnata nel corpo
                        boolean isReassigned = forStmt.getBody().findAll(AssignExpr.class).stream()
                                .anyMatch(assign -> assign.getTarget().isNameExpr() &&
                                        assign.getTarget().asNameExpr().getNameAsString().equals(varName));
                        if (isReassigned) {
                            // Non possiamo incrementare una variabile locale in una lambda stream, quindi usiamo un trucco o un loop esterno.
                            // Ma per semplicità qui, assumiamo che questo for-each esterno funzioni.
                            // (Nota: in un contesto reale servirebbe un AtomicInteger o un array sporco,
                            // ma qui JavaParser Visitor sarebbe meglio. Lasciamo perdere la complessità
                            // e consideriamola "Trovata" se la logica sopra è vera).
                        }
                    });
        }

        // Versione semplificata per il punto 5 (senza lambda complesse):
        smellCount += countLoopReassignments(method);

        return smellCount;
    }

    private int countLoopReassignments(MethodDeclaration method) {
        int count = 0;
        for (ForStmt forStmt : method.findAll(ForStmt.class)) {
            // Troviamo i nomi delle variabili del loop
            java.util.List<String> loopVars = forStmt.getInitialization().stream()
                    .filter(init -> init.isVariableDeclarationExpr())
                    .flatMap(init -> init.asVariableDeclarationExpr().getVariables().stream())
                    .map(v -> v.getNameAsString())
                    .toList();

            if (loopVars.isEmpty()) continue;

            // Cerchiamo assegnazioni nel corpo
            boolean reassigned = forStmt.getBody().findAll(AssignExpr.class).stream()
                    .map(AssignExpr::getTarget)
                    .filter(t -> t.isNameExpr())
                    .map(t -> t.asNameExpr().getNameAsString())
                    .anyMatch(loopVars::contains);

            if (reassigned) count++;
        }
        return count;
    }
}