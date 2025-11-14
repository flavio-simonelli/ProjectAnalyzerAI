package it.flaviosimonelli.isw2.model;

public class Release {
    private String name;
    private String commitId;

    public Release() {}

    public Release(String name, String commitId) {
        this.name = name;
        this.commitId = commitId;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getCommitId() {
        return commitId;
    }
    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }
}
