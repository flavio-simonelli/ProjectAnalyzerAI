package it.flaviosimonelli.isw2.szz;

// ... imports (GitService, JiraTicket, MethodIdentity, etc.) ...
import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.jira.bean.JiraTicket;
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.szz.impl.IncrementalProportionStrategy;
import it.flaviosimonelli.isw2.util.AppConfig;
import it.flaviosimonelli.isw2.util.JavaParserUtils;
import org.eclipse.jgit.diff.Edit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class SZZService {
    private static final Logger logger = LoggerFactory.getLogger(SZZService.class);

    private static final String REPORT_SEP = "===============================================================";
    private static final String SECTION_SEP = "---------------------------------------------------------------";

    private final GitService gitService;
    private final List<JiraRelease> releases;

    private final String testPathMarker;

    // Campo per la strategia (Polimorfismo)
    private IVEstimationStrategy estimationStrategy;

    public SZZService(GitService gitService, List<JiraRelease> releases) {
        this.gitService = gitService;
        this.releases = releases;
        // Default Strategy: Incremental Proportion
        this.estimationStrategy = new IncrementalProportionStrategy(releases);
        this.testPathMarker = AppConfig.getProperty("git.test.path.marker", "/test/");
    }

    /**
     * Permette di cambiare strategia a runtime se necessario (Setter Injection).
     */
    public void setEstimationStrategy(IVEstimationStrategy strategy) {
        this.estimationStrategy = strategy;
    }

    public Map<String, Set<MethodIdentity>> getBuggyMethodsPerRelease(List<JiraTicket> tickets) {
        Map<String, Set<MethodIdentity>> buggyMap = initializeBuggyMap();
        SZZStats stats = new SZZStats();

        // Sorting per data resolution (importante per Proportion)
        tickets.sort(Comparator.comparing(JiraTicket::getResolution));

        for (JiraTicket ticket : tickets) {
            processTicket(ticket, buggyMap, stats);
        }

        printDetailedReport(tickets.size(), stats);
        return buggyMap;
    }

    private void processTicket(JiraTicket ticket, Map<String, Set<MethodIdentity>> buggyMap, SZZStats stats) {
        stats.updateInputStats(ticket);

        // 1. DETERMINAZIONE VERSIONI (FV, OV, IV)
        JiraRelease fv = determineFixVersion(ticket);
        JiraRelease ov = getReleaseByDate(ticket.getCreated());

        if (fv == null || ov == null) {
            logSkip(ticket, fv, ov);
            return;
        }

        JiraRelease iv = determineInjectedVersion(ticket, fv, ov);

        // 2. ANALISI GIT (Linkage + Heuristic)
        List<GitCommit> fixCommits = collectFixCommits(ticket);
        if (fixCommits.isEmpty()) return;

        Set<MethodIdentity> buggyMethods = extractMethodsFromCommits(fixCommits);
        if (buggyMethods.isEmpty()) return;

        // 3. LABELING
        markBuggyInReleases(buggyMap, buggyMethods, iv, fv);
        stats.updateProcessedStats(ticket, buggyMethods.size());
    }

    private JiraRelease determineFixVersion(JiraTicket ticket) {
        JiraRelease fv = getLatestReleaseFromList(ticket.getFixVersions());
        if (fv == null) {
            return getReleaseByDate(ticket.getResolution());
        }
        return fv;
    }

    private JiraRelease determineInjectedVersion(JiraTicket ticket, JiraRelease fv, JiraRelease ov) {
        JiraRelease iv = getEarliestReleaseFromList(ticket.getAffectedVersions());

        if (iv != null) {
            estimationStrategy.learn(iv, fv, ov);
        } else {
            iv = estimationStrategy.estimate(fv, ov);
        }

        // Sanity Check
        if (iv.getReleaseDate().isAfter(fv.getReleaseDate())) {
            return fv;
        }
        return iv;
    }

    private List<GitCommit> collectFixCommits(JiraTicket ticket) {
        List<GitCommit> fixCommits = new ArrayList<>(gitService.findFixCommits(ticket));

        if (!fixCommits.isEmpty() && ticket.getResolution() != null) {
            List<GitCommit> heuristic = recoverMissingCommits(ticket, fixCommits);
            fixCommits.addAll(heuristic);
        }

        return fixCommits;
    }

    private Set<MethodIdentity> extractMethodsFromCommits(List<GitCommit> commits) {
        Set<MethodIdentity> methods = new HashSet<>();
        for (GitCommit commit : commits) {
            methods.addAll(identifyModifiedMethods(commit));
        }
        return methods;
    }

    private Map<String, Set<MethodIdentity>> initializeBuggyMap() {
        Map<String, Set<MethodIdentity>> map = new HashMap<>();
        for (JiraRelease r : releases) {
            map.put(r.getName(), new HashSet<>());
        }
        return map;
    }

    // ==================================================================================
    //                           HELPERS DI RICERCA RELEASE
    // ==================================================================================

    /**
     * Data una lista di nomi versione (stringhe), trova la JiraRelease corrispondente più RECENTE.
     * Usato per trovare la Fix Version definitiva.
     */
    private JiraRelease getLatestReleaseFromList(List<String> versionNames) {
        if (versionNames == null || versionNames.isEmpty()) return null;

        return versionNames.stream()
                .map(this::getReleaseByName)
                .filter(Objects::nonNull)
                .max(Comparator.comparing(JiraRelease::getReleaseDate))
                .orElse(null);
    }

    /**
     * Data una lista di nomi versione (stringhe), trova la JiraRelease corrispondente più VECCHIA.
     * Usato per trovare la Injected Version originale tra le Affected Versions.
     */
    private JiraRelease getEarliestReleaseFromList(List<String> versionNames) {
        if (versionNames == null || versionNames.isEmpty()) return null;

        return versionNames.stream()
                .map(this::getReleaseByName)         // Trasforma i nomi in oggetti JiraRelease
                .filter(Objects::nonNull)            // Scarta i risultati nulli
                .min(Comparator.comparing(JiraRelease::getReleaseDate)) // Trova il minimo per data
                .orElse(null);                       // Se non trova nulla, ritorna null
    }

    private Set<MethodIdentity> identifyModifiedMethods(GitCommit commit) {
        Set<MethodIdentity> modifiedMethods = new HashSet<>();
        Map<String, List<Edit>> diffs = gitService.getDiffsWithEdits(commit);

        // 1. Iteriamo sulle entry della mappa
        for (Map.Entry<String, List<Edit>> entry : diffs.entrySet()) {
            String filePath = entry.getKey();
            List<Edit> edits = entry.getValue();

            // 2. Uniamo i filtri iniziali in un unico check positivo
            if (isJavaSourceFile(filePath)) {
                // 3. Estraiamo la logica di parsing in un metodo dedicato (opzionale ma consigliato)
                processFileEdits(commit, filePath, edits, modifiedMethods);
            }
        }
        return modifiedMethods;
    }

    private boolean isJavaSourceFile(String filePath) {
        return filePath.endsWith(".java") && !filePath.contains(this.testPathMarker);
    }

    private void processFileEdits(GitCommit commit, String filePath, List<Edit> edits, Set<MethodIdentity> result) {
        try {
            String sourceCode = gitService.getRawFileContent(commit, filePath);

            // Controllo validità contenuto senza 'continue'
            if (sourceCode != null && !sourceCode.isEmpty()) {
                CompilationUnit cu = StaticJavaParser.parse(sourceCode);

                // Estraiamo i metodi che intersecano le modifiche
                cu.findAll(MethodDeclaration.class).stream()
                        .filter(method -> isIntersection(method, edits))
                        .forEach(method -> {
                            result.add(new MethodIdentity(
                                    JavaParserUtils.getFullyQualifiedSignature(method, cu),
                                    JavaParserUtils.getParentClassName(method),
                                    method.getNameAsString()
                            ));
                        });
            }
        } catch (Exception e) {
            logger.debug("Impossibile analizzare il file {} nel commit {}: {}", filePath, commit.getHash(), e.getMessage());
        }
    }

    private boolean isIntersection(MethodDeclaration method, List<Edit> edits) {
        if (!method.getBegin().isPresent() || !method.getEnd().isPresent()) return false;

        // Range del metodo nel file corrente (Post-Fix)
        // JavaParser usa indici base-1
        int methodStart = method.getBegin().get().line;
        int methodEnd = method.getEnd().get().line;

        for (Edit edit : edits) {
            // --- LOGICA CORRETTA PER LE COORDINATE ---

            // JGit usa indici base-0.
            // beginB è inclusivo.
            // endB è esclusivo (tranne che per le DELETE dove beginB == endB).

            int editStart;
            int editEnd;

            if (edit.getType() == Edit.Type.DELETE) {
                // Caso DELETE: Le righe non esistono più nel file B.
                // L'edit avviene in un "punto" tra due righe attuali.
                // Consideriamo la riga successiva alla cancellazione come "toccata".
                editStart = edit.getBeginB() + 1;
                editEnd = editStart; // È un punto puntuale
            } else {
                // Caso INSERT o REPLACE: Ci sono nuove righe nel file B.
                // Convertiamo da 0-based (JGit) a 1-based (JavaParser).
                editStart = edit.getBeginB() + 1;

                // JGit endB è esclusivo, quindi per avere l'ultima riga inclusiva
                // normalmente faremmo (endB - 1) + 1 => endB.
                // Esempio: Modifica righe 0,1 (2 righe). begin=0, end=2.
                // Vogliamo righe 1,2.
                // start = 0+1 = 1.
                // end = 2.
                editEnd = edit.getEndB();
            }

            // Controllo intersezioni tra range [methodStart, methodEnd] e [editStart, editEnd]
            if (verifyOverlap(methodStart, methodEnd, editStart, editEnd)) {
                return true;
            }
        }
        return false;
    }

    // Helper per pulizia del codice
    private boolean verifyOverlap(int mStart, int mEnd, int eStart, int eEnd) {
        // Logica standard di overlap:
        // max(start1, start2) <= min(end1, end2)
        return Math.max(mStart, eStart) <= Math.min(mEnd, eEnd);
    }

    private void markBuggyInReleases(Map<String, Set<MethodIdentity>> map, Set<MethodIdentity> methods, JiraRelease iv, JiraRelease fv) {
        // Cerchiamo gli indici nella lista ordinata 'releases'
        int start = releases.indexOf(iv);
        int end = releases.indexOf(fv);
        // 1. CHECK VALIDITÀ
        if (start == -1 || end == -1) {
            logger.warn("SZZ MARKING ERROR: Impossibile mappare le release sugli indici. IV o FV non trovate nella lista releases. " +
                            "IV='{}' (idx={}), FV='{}' (idx={})",
                    (iv != null ? iv.getName() : "null"), start,
                    (fv != null ? fv.getName() : "null"), end);
            return;
        }
        // 2. CHECK COERENZA TEMPORALE
        if (start > end) {
            logger.warn("SZZ MARKING ERROR: Injected Version successiva alla Fixed Version! Skip. " +
                    "IV='{}' (idx={}), FV='{}' (idx={})", iv.getName(), start, fv.getName(), end);
            return;
        }
        // 3. LOOP DI LABELING
        // Da start (incluso) a end (escluso).
        // Esempio: IV=4.0.0 (idx 0), FV=4.2.0 (idx 2).
        // Loop: i=0 (4.0.0 Buggy), i=1 (4.1.0 Buggy). Stop. (4.2.0 Pulita).
        for (int i = start; i < end; i++) {
            String name = releases.get(i).getName();
            // Controllo difensivo sulla mappa (dovrebbe sempre esserci)
            if (map.containsKey(name)) {
                map.get(name).addAll(methods);
            } else {
                logger.error("SZZ CRITICAL: La mappa buggy non contiene la release '{}'", name);
            }
        }
    }

    private JiraRelease getReleaseByName(String name) {
        return releases.stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private JiraRelease getReleaseByDate(LocalDate date) {
        if (date == null) return null;
        for (JiraRelease r : releases) {
            if (!r.getReleaseDate().isBefore(date)) return r;
        }
        return null;
    }

    private void printDetailedReport(int total, SZZStats s) {
        double percProc = (total > 0) ? ((double) s.procTotal / total) * 100 : 0;
        double percAV = (s.procTotal > 0) ? ((double) s.procAV / s.procTotal) * 100 : 0;
        double percFV = (s.procTotal > 0) ? ((double) s.procFV / s.procTotal) * 100 : 0;
        double percBoth = (s.procTotal > 0) ? ((double) s.procBoth / s.procTotal) * 100 : 0;

        String report = """
            %1$s
                               SZZ DETAILED REPORT
            %1$s
            1. ANALISI INPUT (Ticket Jira scaricati: %2$d)
               - Con Fix Version dichiarata:      %3$d (Senza: %4$d)
               - Con Affected Version dichiarata: %5$d (Senza: %6$d)
            %7$s
            2. RISULTATO SZZ (Ticket Utilizzati/Processati: %8$d -> %9$.2f%%)
               Di questi %8$d ticket utilizzati:
               - Avevano Affected Version (Ground Truth): %10$d (%11$.2f%%)
               - Avevano Fix Version (Esplicita):         %12$d (%13$.2f%%)
               - Avevano ENTRAMBE (AV + FV):              %14$d (%15$.2f%%)
            %1$s
            """.formatted(
                REPORT_SEP, total, s.inFV, s.inNoFV, s.inAV, s.inNoAV,
                SECTION_SEP, s.procTotal, percProc,
                s.procAV, percAV, s.procFV, percFV, s.procBoth, percBoth
        );
        logger.info("\n{}", report);
    }

    private void logSkip(JiraTicket ticket, JiraRelease fv, JiraRelease ov) {
        logger.warn("SZZ SKIP Ticket {}: Impossibile determinare FV o OV (FV found? {}, OV found? {})",
                ticket.getKey(), (fv != null), (ov != null));
    }

    /**
     * --- MODIFICA SZZ: Metodo Helper per l'Euristica ---
     * Cerca commit "silenziosi" (senza ID ticket) fatti dallo stesso autore
     * sugli stessi file nel periodo di attività del ticket.
     */
    private List<GitCommit> recoverMissingCommits(JiraTicket ticket, List<GitCommit> strongCommits) {
        if (strongCommits.isEmpty() || ticket.getResolution() == null) {
            return Collections.emptyList();
        }

        // 1. Configurazione Intervallo Temporale
        LocalDateTime start = ticket.getCreated().atStartOfDay();
        LocalDateTime end = ticket.getResolution().atStartOfDay().plusDays(1);

        // 2. Costruzione della "Firma"
        Set<String> knownAuthors = new HashSet<>();
        Set<String> knownFiles = new HashSet<>();

        for (GitCommit c : strongCommits) {
            knownAuthors.add(c.getAuthorName());
            knownFiles.addAll(gitService.getTouchedJavaFilePaths(c));
        }

        // 3. Recupero Candidati
        List<GitCommit> candidates = gitService.findCommitsInDateRange(start, end);

        // 4. Filtraggio Euristico
        return candidates.stream()
                // Filtro A: Escludiamo i duplicati già noti
                .filter(candidate -> !strongCommits.contains(candidate))
                // Filtro B: Solo autori che hanno già lavorato su questo bug
                .filter(candidate -> knownAuthors.contains(candidate.getAuthorName()))
                // Filtro C: Solo commit che toccano almeno un file "sospetto"
                .filter(candidate -> {
                    Set<String> candidateFiles = gitService.getTouchedJavaFilePaths(candidate);
                    return !Collections.disjoint(knownFiles, candidateFiles);
                })
                .toList();
    }


    private static class SZZStats {
        int inFV = 0;
        int inNoFV = 0;
        int inAV = 0;
        int inNoAV = 0;
        int procTotal = 0;
        int procAV = 0;
        int procFV = 0;
        int procBoth = 0;

        void updateInputStats(JiraTicket t) {
            if (!t.getFixVersions().isEmpty()) inFV++; else inNoFV++;
            if (!t.getAffectedVersions().isEmpty()) inAV++; else inNoAV++;
        }

        void updateProcessedStats(JiraTicket t, int methodsCount) {
            if (methodsCount == 0) return;
            procTotal++;
            boolean hasAV = !t.getAffectedVersions().isEmpty();
            boolean hasFV = !t.getFixVersions().isEmpty();
            if (hasAV) procAV++;
            if (hasFV) procFV++;
            if (hasAV && hasFV) procBoth++;
        }
    }
}