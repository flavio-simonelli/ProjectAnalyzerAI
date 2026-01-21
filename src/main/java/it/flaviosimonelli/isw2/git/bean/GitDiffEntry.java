package it.flaviosimonelli.isw2.git.bean;

/**
 * Rappresenta un file modificato in un determinato commit
 */
public class GitDiffEntry {
    // Tipo di modifica: ADD, MODIFY, DELETE, RENAME
    private String changeType;
    private String oldPath;
    private String newPath;

    public GitDiffEntry(String changeType, String oldPath, String newPath) {
        this.changeType = changeType;
        this.oldPath = oldPath;
        this.newPath = newPath;
    }

    // Getters
    public String getChangeType() { return changeType; }
    public String getOldPath() { return oldPath; }
    public String getNewPath() { return newPath; }
}