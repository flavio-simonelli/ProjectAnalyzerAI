package it.flaviosimonelli.isw2.git.client;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.bean.GitDiffEntry;
import it.flaviosimonelli.isw2.git.exceptions.GitClientException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JGitClient implements IGitClient {
    private static final Logger logger = LoggerFactory.getLogger(JGitClient.class);
    private final Repository repository;

    // Costruttore: Apre il repository locale
    public JGitClient(String repoPath) {
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            this.repository = builder.setGitDir(new File(repoPath, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            logger.info("Repository Git aperto correttamente: {}", repoPath);
        } catch (IOException e) {
            throw new GitClientException("Impossibile aprire il repository Git in: " + repoPath, e);
        }
    }

    @Override
    public List<GitCommit> getAllCommits() {
        List<GitCommit> commits = new ArrayList<>();
        // RevWalk serve per camminare nel grafo dei commit
        try (Git git = new Git(repository)) {
            Iterable<RevCommit> log = git.log().call();
            for (RevCommit rev : log) {
                commits.add(convert(rev));
            }
        } catch (Exception e) {
            throw new GitClientException("Errore durante il recupero dei log Git", e);
        }
        return commits;
    }

    @Override
    public String getFileContent(String commitHash, String filePath) {
        try (RevWalk revWalk = new RevWalk(repository)) {
            // 1. Trova l'oggetto Commit
            ObjectId commitId = ObjectId.fromString(commitHash);
            RevCommit commit = revWalk.parseCommit(commitId);

            // 2. Trova l'albero dei file (Tree) associato al commit
            RevTree tree = commit.getTree();

            // 3. Cerca il file specifico nell'albero
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath));

                if (!treeWalk.next()) {
                    // File non trovato in questo commit (magari è stato cancellato o non esisteva ancora)
                    return null;
                }

                // 4. Carica il contenuto (Blob)
                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);

                // Convertiamo i byte in stringa
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                loader.copyTo(stream);
                return stream.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new GitClientException("Errore leggendo il file " + filePath + " al commit " + commitHash, e);
        }
    }

    @Override
    public List<GitDiffEntry> getDiffEntries(String commitHash) {
        List<GitDiffEntry> diffs = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repository);
             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            // 1. Parsa il commit corrente
            ObjectId commitId = ObjectId.fromString(commitHash);
            RevCommit commit = revWalk.parseCommit(commitId);

            // 2. Gestione del commit iniziale (che non ha parenti)
            if (commit.getParentCount() == 0) {
                return diffs; // Nessun diff per il primo commit
            }

            // 3. Prendi il genitore (Parent) per fare il confronto
            RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());

            // 4. Configura il formatter per confrontare i due alberi
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);

            // 5. Esegui il diff (Parent vs Current)
            List<DiffEntry> entries = diffFormatter.scan(parent.getTree(), commit.getTree());

            for (DiffEntry entry : entries) {
                // Filtriamo solo file Java se necessario, o prendiamo tutto
                // Qui mappiamo l'oggetto JGit nel nostro oggetto di dominio
                diffs.add(new GitDiffEntry(
                        entry.getChangeType().name(), // ADD, MODIFY, DELETE
                        entry.getOldPath(),
                        entry.getNewPath()
                ));
            }

        } catch (Exception e) {
            throw new GitClientException("Errore calcolando i diff per il commit " + commitHash, e);
        }
        return diffs;
    }

    @Override
    public Map<String, String> getJavaFilesContent(String commitHash) {
        Map<String, String> contents = new HashMap<>();

        try (RevWalk revWalk = new RevWalk(repository);
             TreeWalk treeWalk = new TreeWalk(repository)) {

            ObjectId commitId = ObjectId.fromString(commitHash);
            RevCommit commit = revWalk.parseCommit(commitId);

            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            // Filtriamo subito per .java
            treeWalk.setFilter(PathSuffixFilter.create(".java"));

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();

                // FILTRO EXTRA: Se vuoi escludere i test direttamente qui
                if (path.contains("/test/")) continue;

                // Leggiamo il contenuto "al volo"
                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);

                // JGit limita la lettura in memoria per sicurezza, ma i sorgenti sono piccoli
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                loader.copyTo(stream);

                String content = stream.toString(StandardCharsets.UTF_8);
                contents.put(path, content);
            }

        } catch (Exception e) {
            throw new GitClientException("Errore lettura bulk file Java per commit " + commitHash, e);
        }

        return contents;
    }

    @Override
    public List<String> getAllJavaFiles(String commitHash) {
        List<String> filePaths = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repository);
             TreeWalk treeWalk = new TreeWalk(repository)) {

            ObjectId commitId = ObjectId.fromString(commitHash);
            RevCommit commit = revWalk.parseCommit(commitId);

            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            // FILTRO CRUCIALE: Prende solo i file che finiscono con .java
            treeWalk.setFilter(PathSuffixFilter.create(".java"));

            while (treeWalk.next()) {
                filePaths.add(treeWalk.getPathString());
            }
        } catch (Exception e) {
            throw new GitClientException("Errore durante il recupero dei file Java al commit " + commitHash, e);
        }
        return filePaths;
    }

    @Override
    public Map<String, List<Edit>> getDiffsWithEdits(String commitHash) {
        Map<String, List<Edit>> diffMap = new HashMap<>();

        // Usiamo il try-with-resources per chiudere automaticamente formatter e walk
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             RevWalk revWalk = new RevWalk(repository)) {

            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);

            // 1. Risolviamo il commit corrente dall'hash
            ObjectId commitId = ObjectId.fromString(commitHash);
            RevCommit currentCommit = revWalk.parseCommit(commitId);

            // 2. Risolviamo il commit genitore
            RevCommit parentCommit = null;
            if (currentCommit.getParentCount() > 0) {
                parentCommit = revWalk.parseCommit(currentCommit.getParent(0).getId());
            }

            // 3. Calcoliamo i Diff (Parent vs Current)
            // Se parent è null, scan gestisce automaticamente il confronto con "albero vuoto"
            List<DiffEntry> entries = diffFormatter.scan(parentCommit, currentCommit);

            for (DiffEntry entry : entries) {
                // Ignoriamo le cancellazioni pure (non possiamo analizzare metodi su file che non esistono più)
                if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    continue;
                }

                // Estraiamo la lista degli Edit (Range di righe)
                FileHeader fileHeader = diffFormatter.toFileHeader(entry);
                diffMap.put(entry.getNewPath(), fileHeader.toEditList());
            }

        } catch (Exception e) {
            throw new GitClientException("Errore estrazione edits per " + commitHash, e);
        }

        return diffMap;
    }

    @Override
    public List<String> listAllFiles(String commitHash) {
        List<String> filePaths = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repository);
             TreeWalk treeWalk = new TreeWalk(repository)) {

            ObjectId commitId = ObjectId.fromString(commitHash);
            RevCommit commit = revWalk.parseCommit(commitId);

            // Impostiamo l'albero del commit corrente
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true); // Visita tutte le sottocartelle

            while (treeWalk.next()) {
                filePaths.add(treeWalk.getPathString());
            }
        } catch (Exception e) {
            throw new GitClientException("Errore durante il listing dei file al commit " + commitHash, e);
        }
        return filePaths;
    }

    // --- Helper per convertire RevCommit in GitCommit ---
    private GitCommit convert(RevCommit rev) {
        // JGit usa i secondi dall'epoca, Java Time usa Instant/LocalDateTime
        LocalDateTime date = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(rev.getCommitTime()),
                ZoneId.systemDefault());

        return new GitCommit(
                rev.getName(), // Hash completo
                rev.getFullMessage(),
                rev.getAuthorIdent().getName(),
                date
        );
    }
}
