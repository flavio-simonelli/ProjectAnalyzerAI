package it.flaviosimonelli.isw2.git.client;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.bean.GitDiffEntry;
import java.util.List;

public interface IGitClient {

    /**
     * Recupera tutti i commit del repository.
     */
    List<GitCommit> getAllCommits();

    /**
     * Recupera il contenuto testuale di un file in un preciso momento storico.
     * Fondamentale per calcolare metriche statiche (LOC, Complexity).
     */
    String getFileContent(String commitHash, String filePath);

    /**
     * Recupera la lista dei file modificati in un commit rispetto al suo genitore.
     * Fondamentale per capire quali classi sono coinvolte in un bug fix.
     */
    List<GitDiffEntry> getDiffEntries(String commitHash);

    List<String> listAllFiles(String commitHash);
}
