package it.flaviosimonelli.isw2.git.bean;

import java.time.LocalDateTime;

public class GitCommit {
    private String hash;
    private String message;
    private String authorName;
    private LocalDateTime date;

    public GitCommit(String hash, String message, String authorName, LocalDateTime date) {
        this.hash = hash;
        this.message = message;
        this.authorName = authorName;
        this.date = date;
    }

    // Getters
    public String getHash() { return hash; }
    public String getMessage() { return message; }
    public String getAuthorName() { return authorName; }
    public LocalDateTime getDate() { return date; }

    @Override
    public String toString() {
        return "GitCommit{hash='" + hash + "', date=" + date + "}";
    }
}