package it.flaviosimonelli.isw2.git.client;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.bean.GitDiffEntry;
import org.eclipse.jgit.diff.Edit;

import java.util.List;
import java.util.Map;

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

    List<String> getAllJavaFiles(String commitHash);

    /**
     * Recupera la lista dettagliata delle modifiche (Edit) per ogni file modificato in un commit.
     * * @param commitHash il commit da analizzare.
     * @return Una Mappa: FilePath -> Lista di Edit (Range di righe modificate).
     */
    Map<String, List<Edit>> getDiffsWithEdits(String commitHash);

    List<String> listAllFiles(String commitHash);

    Map<String, String> getJavaFilesContent(String commitHash);
}
