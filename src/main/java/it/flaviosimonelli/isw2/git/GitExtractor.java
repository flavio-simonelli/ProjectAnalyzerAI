package it.flaviosimonelli.isw2.git;

import it.flaviosimonelli.isw2.model.GitRelease;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GitExtractor {
    /**
     * Filtra la lista dei tag in base a:
     *  - prefisso dei nomi dei tag
     *  - o una lista di nomi di versioni Jira (es. ["3.12.0", "3.11.0"])
     */
    public List<GitRelease> extractGitReleaseTag(Path basePath, String owner, String repo, List<String> jiraVersions, String prefix) throws GitAPIException, IOException {
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

        System.out.printf("✅ Filtrati %d tag su %d per %s/%s%n",
                filtered.size(), allTags.size(), owner, repo);

        return filtered;
    }
}
