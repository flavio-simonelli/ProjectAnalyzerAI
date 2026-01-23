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
import java.time.LocalDateTime;
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
        // creiamo una mappa di <nomirelease, metodi Buggy in quella Release>
        Map<String, Set<MethodIdentity>> buggyMap = new HashMap<>();
        for (JiraRelease r : releases) buggyMap.put(r.getName(), new HashSet<>());

        // Sorting per data resolution (serve per la strategia Proportion)
        tickets.sort(Comparator.comparing(JiraTicket::getResolution)); // visto che la strategia di propotion si basa sulla fixversion e non sull data, qui dobbiamo ordinare rispetto alla fix verison e non la data

        // --- CONTATORI STATISTICI ---
        int totalTickets = 0;

        // Statistiche Input (Jira)
        int inputWithFV = 0;   // Ticket che hanno il campo FixVersion compilato
        int inputWithoutFV = 0;
        int inputWithAV = 0;   // Ticket che hanno il campo AffectedVersion compilato
        int inputWithoutAV = 0;

        // Statistiche Output (Processati / Utilizzati)
        int processedTotal = 0;
        int processedWithAV = 0;      // Utilizzati che avevano AV
        int processedWithFV = 0;      // Utilizzati che avevano FV
        int processedWithBoth = 0;    // Utilizzati che avevano SIA AV CHE FV
        logger.info("Avvio SZZ su {} ticket. Strategia: {}", tickets.size(), estimationStrategy.getClass().getSimpleName());

        for (JiraTicket ticket : tickets) {
            totalTickets ++;
            boolean hasJiraFixVersion = !ticket.getFixVersions().isEmpty();
            boolean hasJiraAffectedVersion = !ticket.getAffectedVersions().isEmpty();

            if (hasJiraFixVersion) inputWithFV++; else inputWithoutFV++;
            if (hasJiraAffectedVersion) inputWithAV++; else inputWithoutAV++;

            // --- FASE 1: DETERMINAZIONE VERSIONI (FV, OV, IV) ---
            JiraRelease fv = getLatestReleaseFromList(ticket.getFixVersions());
            // FALLBACK: Se Jira non ha fixVersions, usiamo la Resolution Date
            if (fv == null) {
                logger.warn("Nessuna fix version trovata per il ticket {}. fallback utilizzando la data di risoluzione", ticket.getKey());
                fv = getReleaseByDate(ticket.getResolution());
            }
            JiraRelease ov = getReleaseByDate(ticket.getCreated());
            if (fv == null || ov == null) {
                logger.warn("SZZ SKIP Ticket {}: Impossibile determinare FV o OV (FV found? {}, OV found? {})",
                        ticket.getKey(), (fv != null), (ov != null));
                continue;
            }
            JiraRelease iv = null;

            // Injected Version (IV) - GROUND TRUTH
            // Cerchiamo la Affected Version più vecchia tra quelle dichiarate
            if (hasJiraAffectedVersion) {
                iv = getEarliestReleaseFromList(ticket.getAffectedVersions());
                // Se l'abbiamo trovata, addestriamo Proportion
                if (iv != null) {
                    estimationStrategy.learn(iv, fv, ov);
                }
            }

            // Injected Version (IV) - STIMA
            if (iv == null) {
                iv = estimationStrategy.estimate(fv, ov);
            }

            // SANITY CHECK FINALE
            // Se per errore di dati o stima IV è dopo FV, tronchiamo a FV
            if (iv.getReleaseDate().isAfter(fv.getReleaseDate())) {
                logger.warn("SZZ ADJUST Ticket {}: IV stimata/trovata ({}) successiva a FV ({}). Imposto IV=FV (il ticket diventa essenzialmente inutile).",
                        ticket.getKey(), iv.getName(), fv.getName());
                iv = fv;
            }

            // --- FASE 2: ANALISI GIT ---
            List<GitCommit> fixCommits = gitService.findFixCommits(ticket); // trova tutti i commit che sono precedenti alla data di risoluzione del ticket e possiedono come descrizione il nome del ticket
            if (fixCommits.isEmpty()) {
                logger.warn("SZZ SKIP Ticket {}: Nessun commit di fix trovato in Git.", ticket.getKey());
                continue;
            }
            // LOGICA CORRETTA (Aggregazione):
            // Un bug può essere risolto in più step. Consideriamo TUTTI i commit validi
            // che fanno riferimento al ticket. Se un metodo è stato toccato in uno
            // qualsiasi di questi commit, era parte del problema.
            Set<MethodIdentity> buggyMethods = new HashSet<>();
            // Lower Bound: Data Creazione Ticket (con 60 giorni di buffer per sicurezza)
            LocalDateTime creationLowerBound = ticket.getCreated().atStartOfDay().minusDays(60);
            boolean validCommitsFound = false;
            for (GitCommit commit : fixCommits) {
                // identifyModifiedMethods restituisce i metodi toccati in QUEL commit.
                // addAll fa l'unione dei set, gestendo i duplicati automaticamente.
                // CHECK TEMPORALE INFERIORE (Anti-Noise)
                if (commit.getDate().isBefore(creationLowerBound)) {
                    // Calcolo giorni di anticipo per il log
                    long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(
                            commit.getDate(),
                            ticket.getCreated().atStartOfDay()
                    );

                    // Logghiamo se scartiamo qualcosa (evitiamo log inutili per date di anni prima)
                    if (daysDiff < 365) {
                        logger.debug("SZZ SKIP Commit {} per ticket {}: Commit del {} (Created: {}). Anticipo sospetto: {} giorni.",
                                commit.getHash().substring(0, 7),
                                ticket.getKey(),
                                commit.getDate().toLocalDate(),
                                ticket.getCreated(),
                                daysDiff);
                    }
                    continue;
                }
                // Se passa il filtro, uniamo i metodi
                Set<MethodIdentity> methods = identifyModifiedMethods(commit);
                if (!methods.isEmpty()) {
                    buggyMethods.addAll(methods);
                    validCommitsFound = true;
                }
            }

            // Se alla fine del filtro temporale non abbiamo metodi validi, il ticket è scartato
            if (buggyMethods.isEmpty()) {
                logger.debug("SZZ SKIP Ticket {}: I commit trovati non modificavano file .java o erano fuori tempo.", ticket.getKey());
                continue;
            }

            // DEBUG: Vediamo quanti metodi abbiamo trovato per questo ticket
            logger.debug("Ticket {} -> {} metodi buggati identificati da {} commit.", ticket.getKey(), buggyMethods.size(), fixCommits.size());

            // --- FASE 3: LABELING ---
            markBuggyInReleases(buggyMap, buggyMethods, iv, fv);
            processedTotal++;
            if (hasJiraAffectedVersion) processedWithAV++;
            if (hasJiraFixVersion) processedWithFV++;
            if (hasJiraAffectedVersion && hasJiraFixVersion) processedWithBoth++;
        }

        printDetailedReport(totalTickets, inputWithFV, inputWithoutFV, inputWithAV, inputWithoutAV,
                processedTotal, processedWithAV, processedWithFV, processedWithBoth);
        return buggyMap;
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

        JiraRelease latest = null;
        for (String name : versionNames) {
            JiraRelease candidate = getReleaseByName(name);
            if (candidate != null) {
                if (latest == null || candidate.getReleaseDate().isAfter(latest.getReleaseDate())) {
                    latest = candidate;
                }
            }
        }
        return latest;
    }

    /**
     * Data una lista di nomi versione (stringhe), trova la JiraRelease corrispondente più VECCHIA.
     * Usato per trovare la Injected Version originale tra le Affected Versions.
     */
    private JiraRelease getEarliestReleaseFromList(List<String> versionNames) {
        if (versionNames == null || versionNames.isEmpty()) return null;

        JiraRelease earliest = null;
        for (String name : versionNames) {
            JiraRelease candidate = getReleaseByName(name);
            if (candidate != null) {
                if (earliest == null || candidate.getReleaseDate().isBefore(earliest.getReleaseDate())) {
                    earliest = candidate;
                }
            }
        }
        return earliest;
    }

    private Set<MethodIdentity> identifyModifiedMethods(GitCommit commit) {
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
        if (method.getBegin().isEmpty() || method.getEnd().isEmpty()) return false;
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

    private void printDetailedReport(int total, int inFV, int inNoFV, int inAV, int inNoAV,
                                     int proc, int procAV, int procFV, int procBoth) {

        // Calcolo percentuale totale processati
        double percProc = (total > 0) ? ((double)proc / total) * 100 : 0;

        logger.info("===============================================================");
        logger.info("                   SZZ DETAILED REPORT                         ");
        logger.info("===============================================================");
        logger.info("1. ANALISI INPUT (Ticket Jira scaricati: {})", total);
        logger.info("   - Con Fix Version dichiarata:      {} (Senza: {})", inFV, inNoFV);
        logger.info("   - Con Affected Version dichiarata: {} (Senza: {})", inAV, inNoAV);
        logger.info("---------------------------------------------------------------");
        // CORREZIONE QUI: Uso String.format per la percentuale
        logger.info("2. RISULTATO SZZ (Ticket Utilizzati/Processati: {} -> {}%)",
                proc, String.format("%.2f", percProc));

        logger.info("   Di questi {} ticket utilizzati:", proc);

        // Calcoli e formattazione per i dettagli
        double percAV = (proc > 0) ? ((double)procAV / proc) * 100 : 0;
        double percFV = (proc > 0) ? ((double)procFV / proc) * 100 : 0;
        double percBoth = (proc > 0) ? ((double)procBoth / proc) * 100 : 0;

        // CORREZIONE QUI: String.format per ogni percentuale
        logger.info("   - Avevano Affected Version (Ground Truth): {} ({}%)",
                procAV, String.format("%.2f", percAV));

        logger.info("   - Avevano Fix Version (Esplicita):         {} ({}%)",
                procFV, String.format("%.2f", percFV));

        logger.info("   - Avevano ENTRAMBE (AV + FV):              {} ({}%)",
                procBoth, String.format("%.2f", percBoth));

        logger.info("===============================================================");
    }
}