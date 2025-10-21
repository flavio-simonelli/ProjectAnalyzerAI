package it.flaviosimonelli.isw2.model;

public class GitRelease {
    private String id;
    private String commitId;

    public GitRelease() {}

    public GitRelease(String id, String commitId) {
        this.id = id;
        this.commitId = commitId;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCommitId() { return commitId; }
    public void setCommitId(String commitId) { this.commitId = commitId; }

    @Override
    public String toString() {
        return "GitRelease{" +
                "id='" + id + '\'' +
                ", commitId='" + commitId + '\'' +
                '}';
    }
}
