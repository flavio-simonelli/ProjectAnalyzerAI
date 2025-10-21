package it.flaviosimonelli.isw2.git;

import it.flaviosimonelli.isw2.model.GitRelease;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GitClient {

    /**
     * Clona o apre il repository richiesto in basePath.
     * @param basePath percorso base dove tenere le repo locali
     * @param owner proprietario del progetto GitHub (es. "apache")
     * @param repo nome del repository (es. "commons-lang")
     */
    private Git getRepository(Path basePath, String owner, String repo) throws IOException, GitAPIException {
        Path repoDir = basePath.resolve(repo);

        if (!repoDir.toFile().exists()) {
            System.out.println("⬇️ Clonazione repository " + repo + "...");
            String repoUrl = String.format("https://github.com/%s/%s.git", owner, repo);
            CloneCommand clone = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(repoDir.toFile())
                    .setCloneAllBranches(true);
            return clone.call();
        } else {
            return Git.open(repoDir.toFile());
        }
    }

    /**
     * Restituisce tutti i tag del repository specificato con i rispettivi commit.
     * Il metodo è completamente autonomo: controlla se il repo è già clonato e lo apre o lo clona se necessario.
     *
     * @param basePath directory locale dove salvare/clonare i repo
     * @param owner    proprietario del progetto su GitHub
     * @param repo     nome del repository GitHub
     * @return lista di GitRelease (tag + commitId)
     */
    public List<GitRelease> getAllTagsWithCommits(Path basePath, String owner, String repo) throws IOException, GitAPIException {
        List<GitRelease> tags = new ArrayList<>();

        try (Git git = getRepository(basePath, owner, repo);
             RevWalk revWalk = new RevWalk(git.getRepository())) {

            for (Ref tagRef : git.tagList().call()) {
                String tagName = tagRef.getName().replace("refs/tags/", "");

                ObjectId objectId = tagRef.getPeeledObjectId() != null
                        ? tagRef.getPeeledObjectId()
                        : tagRef.getObjectId();

                RevCommit commit = revWalk.parseCommit(objectId);
                tags.add(new GitRelease(tagName, commit.getName()));
            }

        } catch (Exception e) {
            System.err.println("❌ Errore lettura tag di " + repo + ": " + e.getMessage());
        }

        System.out.printf("✅ Trovati %d tag per %s/%s%n", tags.size(), owner, repo);
        return tags;
    }
}
