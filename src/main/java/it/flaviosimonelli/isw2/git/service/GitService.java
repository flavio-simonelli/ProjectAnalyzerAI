package it.flaviosimonelli.isw2.git.service;

import it.flaviosimonelli.isw2.git.client.IGitClient;
import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.bean.GitDiffEntry;
import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.jira.bean.JiraTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
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
            // Ordiniamo per data (dal più vecchio al più recente) per facilitare i filtri
            allCommitsCache.sort(Comparator.comparing(GitCommit::getDate));
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

        GitCommit lastMatch = null;
        for (GitCommit commit : commits) {
            // Poiché la lista è ordinata cronologicamente (dal vecchio al nuovo),
            // continuiamo ad aggiornare lastMatch finché siamo dentro la data.
            if (commit.getDate().isAfter(deadline)) {
                break; // Abbiamo superato il limite, fermiamoci.
            }
            lastMatch = commit;
        }

        if (lastMatch == null) {
            logger.debug("Nessun commit trovato prima del {}", date);
        }
        return lastMatch;
    }

    /**
     * Trova tutti i commit in un intervallo temporale specifico.
     * Ovvero: (Data Release Precedente) < Commit Date <= (Data Release Corrente)
     * @param startDate Data inizio (esclusa/inclusa a seconda della logica, qui gestiamo next second).
     * @param endDate Data fine (inclusa).
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
            startLimit = startDate.atTime(23, 59, 59);
        }

        LocalDateTime endLimit = endDate.atTime(23, 59, 59);

        for (GitCommit c : commits) {
            // Logica: startLimit < commitDate <= endLimit
            if (c.getDate().isAfter(startLimit) && !c.getDate().isAfter(endLimit)) {
                filteredCommits.add(c);
            }
        }
        return filteredCommits;
    }

    /**
     * Identifica i commit che hanno risolto un ticket specifico (Linking Jira-Git).
     * Cerca la KEY del ticket nel messaggio di commit.
     */
    public List<GitCommit> findFixCommits(JiraTicket ticket) {
        List<GitCommit> matches = new ArrayList<>();
        String ticketKey = ticket.getKey(); // es. BOOKKEEPER-1105

        // Validazione data: Il commit non può essere avvenuto dopo la data di risoluzione su Jira
        // (Aggiungiamo un buffer di qualche giorno per fusi orari o ritardi di sync)
        LocalDateTime resolutionDeadline = ticket.getResolution() != null
                ? ticket.getResolution().atTime(23, 59, 59).plusDays(1)
                : LocalDateTime.MAX;

        for (GitCommit c : getAllCommits()) {
            if (c.getMessage().contains(ticketKey)) {
                // Controllo Anti-Falso Positivo:
                // Se il commit è del 2020 e il ticket chiuso nel 2017, è solo una citazione, non il fix.
                if (c.getDate().isBefore(resolutionDeadline)) {
                    matches.add(c);
                } else {
                    logger.debug("Scartato commit {} per ticket {} (Data incongruente)", c.getHash(), ticketKey);
                }
            }
        }
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
                if (!path.toLowerCase().contains("/test/")) {
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
        List<String> allFiles = gitClient.listAllFiles(commit.getHash());
        return allFiles.stream()
                .filter(path -> path.endsWith(".java"))
                .filter(path -> !path.contains("/test/")) // Opzionale: escludi test
                .collect(Collectors.toList());
    }

    public String getRawFileContent(GitCommit commit, String path) {
        return gitClient.getFileContent(commit.getHash(), path);
    }
}
