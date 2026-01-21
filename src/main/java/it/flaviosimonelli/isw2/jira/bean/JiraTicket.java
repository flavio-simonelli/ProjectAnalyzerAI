package it.flaviosimonelli.isw2.jira.bean;

import java.time.LocalDate;
import java.util.List;

public class JiraTicket {
    private String key;
    private LocalDate created;
    private LocalDate resolution;
    private List<String> fixVersions;
    private List<String> affectedVersions;

    public JiraTicket(String key, LocalDate created, LocalDate resolution, List<String> fixVersions, List<String> affectedVersions) {
        this.key = key;
        this.created = created;
        this.resolution = resolution;
        this.fixVersions = fixVersions;
        this.affectedVersions = affectedVersions;
    }

    // --- Getters ---
    public String getKey() {
        return key;
    }
    public LocalDate getCreated() {
        return created;
    }
    public LocalDate getResolution() {
        return resolution;
    }
    public List<String> getFixVersions() {
        return fixVersions;
    }
    public List<String> getAffectedVersions() {
        return affectedVersions;
    }

    // --- Setters ---
    public void setKey(String key) {
        this.key = key;
    }
    public void setCreated(LocalDate created) {
        this.created = created;
    }
    public void setResolution(LocalDate resolution) {
        this.resolution = resolution;
    }
    public void setFixVersions(List<String> fixVersions) {
        this.fixVersions = fixVersions;
    }
    public void setAffectedVersions(List<String> affectedVersions) {
        this.affectedVersions = affectedVersions;
    }

    @Override
    public String toString() {
        return "JiraTicket{" +
                "key='" + key + '\'' +
                ", created=" + created +
                ", resolution=" + resolution +
                ", fixVersions=" + fixVersions +
                ", affectedVersions=" + affectedVersions +
                '}';
    }
}
