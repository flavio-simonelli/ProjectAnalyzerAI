package it.flaviosimonelli.isw2.git.service;

import it.flaviosimonelli.isw2.git.client.IGitClient;
import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.bean.GitDiffEntry;
import it.flaviosimonelli.isw2.jira.bean.JiraTicket;
import org.eclipse.jgit.diff.Edit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
    private static final String TEST_PATH_MARKER = "/test/";

    private final IGitClient gitClient;

    // Cache locale per non ricaricare la lista commit 1000 volte
    private List<GitCommit> allCommitsCache;

    public GitService(IGitClient gitClient) {
        this.gitClient = gitClient;
    }

    /**
     * Recupera tutti i commit (lazy loading con cache).
     */
    public List<GitCommit> getAllCommits() {
        if (allCommitsCache == null) {
            allCommitsCache = gitClient.getAllCommits();
            // Il Controller si aspetta: Indice 0 = Più Recente, Indice N = Più Vecchio.
            allCommitsCache.sort(Comparator.comparing(GitCommit::getDate).reversed());
        }
        return allCommitsCache;
    }

    /**
     * Trova l'ultimo commit effettuato ENTRO la fine della data specificata.
     * Questo commit rappresenta lo "Snapshot" del software in quella data (per le versioni).
     * @param date La data limite (considera fino alle 23:59:59).
     * @return L'ultimo commit valido o null se non ne esistono prima di quella data.
     */
    public GitCommit getLastCommitOnOrBeforeDate(LocalDate date) {
        if (date == null) {
            return null;
        }

        List<GitCommit> commits = getAllCommits();

        // Impostiamo la deadline alla fine della giornata indicata
        LocalDateTime deadline = date.atTime(23, 59, 59);

        // Poiché la lista va dal PIÙ RECENTE al PIÙ VECCHIO:
        // Il primo che incontriamo che è <= deadline è quello che cerchiamo (lo Snapshot).
        for (GitCommit commit : commits) {
            if (!commit.getDate().isAfter(deadline)) {
                return commit; // Trovato! È il più recente entro la data.
            }
        }
        return null;
    }

    /**
     * Trova tutti i commit in un intervallo temporale specifico.
     * Ovvero: (Data Release Precedente) < Commit Date <= (Data Release Corrente)
     * @param startDate Data di rilascio della precedente release (o null se vogliamo l'intera storia).
     * @param endDate Data fine (inclusa).
     * @return List<GitCommit> lista dei commit dal più recente al meno recedente
     */
    public List<GitCommit> getCommitsBetweenDates(LocalDate startDate, LocalDate endDate) {
        if (endDate == null) {
            return Collections.emptyList();
        }

        List<GitCommit> commits = getAllCommits();
        List<GitCommit> filteredCommits = new ArrayList<>();

        LocalDateTime startLimit;
        if (startDate == null) {
            // Se è la prima release in assoluto, prendiamo tutto dall'inizio dei tempi
            startLimit = LocalDateTime.MIN;
        } else {
            // Prendiamo i commit fatti DOPO la fine della release precedente
            startLimit = startDate.plusDays(1).atStartOfDay();
        }

        LocalDateTime endLimit = endDate.atTime(23, 59, 59);

        for (GitCommit c : commits) {
            // Logica: startLimit < commitDate <= endLimit
            if (!c.getDate().isBefore(startLimit) && !c.getDate().isAfter(endLimit)) {
                filteredCommits.add(c);
            }
        }
        return filteredCommits; // Mantiene ordine Newest -> Oldest
    }

    /**
     * Identifica i commit che hanno risolto un ticket specifico (Linking Jira-Git).
     * Cerca la KEY del ticket nel messaggio di commit.
     */
    public List<GitCommit> findFixCommits(JiraTicket ticket) {
        List<GitCommit> matches = new ArrayList<>();
        String ticketKey = ticket.getKey(); // es. BOOKKEEPER-1105

        // Costruiamo la regex una volta sola per efficienza
        // \b garantisce che BOOKKEEPER-1 non matchi BOOKKEEPER-12
        String regex = ".*\\b" + Pattern.quote(ticketKey) + "\\b.*";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);

        for (GitCommit c : getAllCommits()) {
            // 1. Nessun filtro data (come nel riferimento)
            // 2. Uso della Regex (come nel riferimento)
            // 3. Controllo su tutto il messaggio (meglio del riferimento, ma sicuro)
            if (pattern.matcher(c.getMessage()).matches()) {
                matches.add(c);
            }
        }

        // Ordiniamo per data per pulizia, ma li ritorniamo TUTTI
        matches.sort(Comparator.comparing(GitCommit::getDate));

        return matches;
    }

    /**
     * Recupera solo i file Java modificati in un commit (Utility per le metriche).
     * Filtra via file di testo, xml, documentazione, test, etc.
     */
    public Map<String, String> getChangedJavaFiles(GitCommit commit) {
        List<GitDiffEntry> diffs = gitClient.getDiffEntries(commit.getHash());
        Map<String, String> javaChanges = new HashMap<>();

        for (GitDiffEntry entry : diffs) {
            // Prendiamo il path nuovo (se aggiunto/modificato) o vecchio (se cancellato)
            String path = entry.getNewPath().equals("/dev/null") ? entry.getOldPath() : entry.getNewPath();

            // 1. Filtro: Solo file .java
            if (path.endsWith(".java")) {
                // 2. Filtro opzionale: Escludere i Test (spesso in ISW2 si analizza solo production code)
                if (!path.toLowerCase().contains(TEST_PATH_MARKER)) {
                    javaChanges.put(path, entry.getChangeType());
                }
            }
        }
        return javaChanges;
    }

    /**
     * Recupera la lista di tutti i path dei file Java esistenti in quel commit.
     */
    public List<String> getAllJavaFiles(GitCommit commit) {
        // 1. Chiamiamo il metodo ottimizzato (JGit filtra .java nativamente)
        List<String> javaFiles = gitClient.getAllJavaFiles(commit.getHash());

        // 2. Applichiamo solo i filtri di business (es. no test)
        return javaFiles.stream()
                .filter(path -> !path.contains(TEST_PATH_MARKER)) // Filtro business
                .collect(Collectors.toList());
    }

    public Map<String, String> getJavaFilesContent(GitCommit commit) {
        return gitClient.getJavaFilesContent(commit.getHash());
    }

    /**
     * Recupera le modifiche riga per riga (Edit List) delegando al client.
     */
    public Map<String, List<Edit>> getDiffsWithEdits(GitCommit commit) {
        // Delega semplice: passa l'hash al client
        return gitClient.getDiffsWithEdits(commit.getHash());
    }

    public String getRawFileContent(GitCommit commit, String path) {
        return gitClient.getFileContent(commit.getHash(), path);
    }


    /**
     * --- NUOVO PER SZZ ---
     * Trova i commit in un range esatto (LocalDateTime), necessario per l'euristica
     * ticket Created -> Resolution.
     */
    public List<GitCommit> findCommitsInDateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return Collections.emptyList();

        // Nota: getAllCommits è già ordinata per data decrescente.
        // Possiamo ottimizzare uscendo dal loop quando superiamo la start date,
        // ma per ora il filtro semplice va bene.
        return getAllCommits().stream()
                .filter(c -> !c.getDate().isBefore(start) && !c.getDate().isAfter(end))
                .collect(Collectors.toList());
    }

    /**
     * --- NUOVO PER SZZ ---
     * Restituisce un Set di path dei file Java toccati.
     * Serve per fare l'intersezione veloce: SetA.retainAll(SetB).
     */
    public Set<String> getTouchedJavaFilePaths(GitCommit commit) {
        List<GitDiffEntry> diffs = gitClient.getDiffEntries(commit.getHash());
        Set<String> touchedFiles = new HashSet<>();

        for (GitDiffEntry entry : diffs) {
            String path = entry.getNewPath().equals("/dev/null") ? entry.getOldPath() : entry.getNewPath();

            // Filtriamo solo i file Java (e opzionalmente rimuoviamo i test)
            if (path.endsWith(".java") && !path.contains(TEST_PATH_MARKER)) {
                touchedFiles.add(path);
            }
        }
        return touchedFiles;
    }
}
