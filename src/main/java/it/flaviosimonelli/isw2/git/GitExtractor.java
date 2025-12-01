package it.flaviosimonelli.isw2.git;

import it.flaviosimonelli.isw2.model.GitRelease;
import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.model.Method;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GitExtractor {

    /*  Extract all Git releases (tags) along with their associated commit IDs. */
    public List<GitRelease> extractGitReleases(Path basePath, String owner, String repo) throws GitAPIException, IOException {
        GitClient client = new GitClient();
        List<GitRelease> allTags = client.getAllTagsWithCommits(basePath, owner, repo);

        System.out.printf("Estratti %d tag totali per %s/%s%n", allTags.size(), owner, repo);

        return allTags;
    }

    /**
     * Estrae i model Method con le sue metriche da uno specifico commitID.
     */
    public List<Method> extractMetricsMethodsAtCommit(Path basePath, String owner, String repo, String commitId) {
        GitClient client = new GitClient();
        RevCommit commit = client.getCommitById(basePath, owner, repo, commitId);
        if (commit == null) {
            System.out.printf("❌ Commit %s non trovato in %s/%s%n", commitId, owner, repo);
            return new ArrayList<>();
        }
        CodeAnalyzer analyzer = new CodeAnalyzer();
        List<Method> methods = analyzer.analyzeMethodsAtCommit(basePath, owner, repo

        System.out.printf("Estratti %d metodi per il commit %s di %s/%s%n",
                methods.size(), commitId, owner, repo);

        return methods;
    }

    /**
     * Filtra la lista dei tag in base a:
     *  - prefisso dei nomi dei tag
     *  - o una lista di nomi di versioni Jira (es. ["3.12.0", "3.11.0"])
     */
    public List<GitRelease> extractGitReleaseByTag(Path basePath, String owner, String repo, List<String> jiraVersions, String prefix) throws GitAPIException, IOException {
        GitClient client = new GitClient();
        List<GitRelease> allTags = client.getAllTagsWithCommits(basePath, owner, repo);
        List<GitRelease> filtered = new ArrayList<>();

        for (GitRelease tag : allTags) {
            for (String v : jiraVersions) {

                String expectedDash = prefix.toLowerCase() + v;

                if (tag.getId().equals(expectedDash)) {
                    filtered.add(tag);
                    break;
                }
            }
        }

        System.out.printf("Filtrati %d tag su %d per %s/%s%n",
                filtered.size(), allTags.size(), owner, repo);

        return filtered;
    }

    /**
     * Estrae i commit associati alle release Jira in base alla data di rilascio.
     * Per ogni release Jira, trova l’ultimo commit con data <= releaseDate.
     */
    public List<GitRelease> extractGitReleasesByDate(Path basePath, String owner, String repo, List<JiraRelease> jiraReleases) {

        GitClient client = new GitClient();
        List<GitRelease> result = new ArrayList<>();

        for (JiraRelease jr : jiraReleases) {
            if (jr.getReleaseDate() == null) {
                System.out.printf("⚠️ Nessuna data per la release %s → ignorata%n", jr.getName());
                continue;
            }

            String commitId = client.getLatestCommitIdBeforeDate(basePath, owner, repo, jr.getReleaseDate());

            if (commitId != null) {
                result.add(new GitRelease(jr.getName(), commitId));
            } else {
                System.out.printf("⚠️ Nessun commit trovato prima di %s (%s)%n",
                        jr.getReleaseDate(), jr.getName());
            }
        }

        System.out.printf("✅ Estratte %d release Git basate sulla data per %s/%s%n",
                result.size(), owner, repo);

        return result;
    }

}
