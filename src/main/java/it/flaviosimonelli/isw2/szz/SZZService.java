package it.flaviosimonelli.isw2.szz;

// ... imports (GitService, JiraTicket, MethodIdentity, etc.) ...
import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.jira.bean.JiraTicket;
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.szz.impl.IncrementalProportionStrategy;
import it.flaviosimonelli.isw2.util.JavaParserUtils;
import org.eclipse.jgit.diff.Edit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.time.LocalDate;
import java.util.*;

public class SZZService {
    private static final Logger logger = LoggerFactory.getLogger(SZZService.class);

    private final GitService gitService;
    private final List<JiraRelease> releases;

    // Campo per la strategia (Polimorfismo)
    private IVEstimationStrategy estimationStrategy;

    public SZZService(GitService gitService, List<JiraRelease> releases) {
        this.gitService = gitService;
        this.releases = releases;
        // Default Strategy: Incremental Proportion
        this.estimationStrategy = new IncrementalProportionStrategy(releases);
    }

    /**
     * Permette di cambiare strategia a runtime se necessario (Setter Injection).
     */
    public void setEstimationStrategy(IVEstimationStrategy strategy) {
        this.estimationStrategy = strategy;
    }

    public Map<String, Set<MethodIdentity>> getBuggyMethodsPerRelease(List<JiraTicket> tickets) {
        Map<String, Set<MethodIdentity>> buggyMap = new HashMap<>();
        for (JiraRelease r : releases) buggyMap.put(r.getName(), new HashSet<>());

        // Sorting necessario per Incremental Proportion (ma male non fa alle altre strategie)
        tickets.sort(Comparator.comparing(JiraTicket::getResolution));

        int processed = 0;
        logger.info("Avvio SZZ su {} ticket. Strategia: {}", tickets.size(), estimationStrategy.getClass().getSimpleName());

        for (JiraTicket ticket : tickets) {

            // --- FASE 1: STIMA DATE (Delegata alla Strategia) ---
            JiraRelease fv = getReleaseByDate(ticket.getResolution());
            JiraRelease ov = getReleaseByDate(ticket.getCreated());
            JiraRelease iv = null;

            if (fv == null || ov == null) continue;

            // 1. Dati noti? Addestriamo la strategia
            if (!ticket.getAffectedVersions().isEmpty()) {
                iv = getReleaseByName(ticket.getAffectedVersions().get(0));
                if (iv != null) {
                    estimationStrategy.learn(iv, fv, ov); // <--- DELEGA
                }
            }

            // 2. Dato mancante? Chiediamo stima alla strategia
            if (iv == null) {
                iv = estimationStrategy.estimate(fv, ov); // <--- DELEGA
            }

            // Sanity Check
            if (iv == null || iv.getReleaseDate().isAfter(fv.getReleaseDate())) iv = fv;


            // --- FASE 2: ANALISI GIT ---
            List<GitCommit> fixCommits = gitService.findFixCommits(ticket);
            if (fixCommits.isEmpty()) continue;

            Set<MethodIdentity> buggyMethods = new HashSet<>();
            for (GitCommit commit : fixCommits) {
                buggyMethods.addAll(identifyModifiedMethods(commit));
            }

            // --- FASE 3: LABELING ---
            markBuggyInReleases(buggyMap, buggyMethods, iv, fv);
            processed++;
        }

        logger.info("SZZ Terminato. Ticket processati: {}", processed);
        return buggyMap;
    }

    private Set<MethodIdentity> identifyModifiedMethods(GitCommit commit) {
        // ... (Logica identica a prima: Diff -> JavaParser -> Intersezione) ...
        Set<MethodIdentity> modifiedMethods = new HashSet<>();
        Map<String, List<Edit>> diffs = gitService.getDiffsWithEdits(commit);

        for (Map.Entry<String, List<Edit>> entry : diffs.entrySet()) {
            String filePath = entry.getKey();
            List<Edit> edits = entry.getValue();

            if (!filePath.endsWith(".java") || filePath.contains("/test/")) continue;
            try {
                String sourceCode = gitService.getRawFileContent(commit, filePath);
                if (sourceCode == null || sourceCode.isEmpty()) continue;
                CompilationUnit cu = StaticJavaParser.parse(sourceCode);
                for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                    if (isIntersection(method, edits)) {
                        String fullSig = JavaParserUtils.getFullyQualifiedSignature(method, cu);
                        String className = JavaParserUtils.getParentClassName(method);
                        String pureName = method.getNameAsString();
                        modifiedMethods.add(new MethodIdentity(fullSig, className, pureName));
                    }
                }
            } catch (Exception e) {}
        }
        return modifiedMethods;
    }

    private boolean isIntersection(MethodDeclaration method, List<Edit> edits) {
        if (!method.getBegin().isPresent() || !method.getEnd().isPresent()) return false;
        int start = method.getBegin().get().line;
        int end = method.getEnd().get().line;
        for (Edit edit : edits) {
            if (edit.getType() == Edit.Type.DELETE) continue;
            int eStart = edit.getBeginB() + 1;
            int eEnd = edit.getEndB() + 1;
            if (Math.max(start, eStart) <= Math.min(end, eEnd)) return true;
        }
        return false;
    }

    private void markBuggyInReleases(Map<String, Set<MethodIdentity>> map, Set<MethodIdentity> methods, JiraRelease iv, JiraRelease fv) {
        int start = releases.indexOf(iv);
        int end = releases.indexOf(fv);
        if (start == -1 || end == -1) return;
        for (int i = start; i < end; i++) {
            String name = releases.get(i).getName();
            if (map.containsKey(name)) map.get(name).addAll(methods);
        }
    }

    private JiraRelease getReleaseByDate(LocalDate date) {
        if (date == null) return null;
        for (JiraRelease r : releases) { if (!r.getReleaseDate().isBefore(date)) return r; }
        return null;
    }

    private JiraRelease getReleaseByName(String name) {
        return releases.stream().filter(r -> r.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}