package it.flaviosimonelli.isw2.model;

import java.time.LocalDate;
import java.util.List;

public class JiraTicket {
    private String selfUrl;
    private String id;
    private LocalDate createdDate;
    private List<String> fixVersions;
    private List<String> affectedVersions;

    public JiraTicket() {}

    public JiraTicket(String selfUrl, String id, LocalDate createdDate,
                      List<String> fixVersions, List<String> affectedVersions) {
        this.selfUrl = selfUrl;
        this.id = id;
        this.createdDate = createdDate;
        this.fixVersions = fixVersions;
        this.affectedVersions = affectedVersions;
    }

    // Getters and Setters
    public String getSelfUrl() { return selfUrl; }
    public void setSelfUrl(String selfUrl) { this.selfUrl = selfUrl; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }

    public List<String> getFixVersions() { return fixVersions; }
    public void setFixVersions(List<String> fixVersions) { this.fixVersions = fixVersions; }

    public List<String> getAffectedVersions() { return affectedVersions; }
    public void setAffectedVersions(List<String> affectedVersions) { this.affectedVersions = affectedVersions; }

    @Override
    public String toString() {
        return "JiraTicket{" +
                "selfUrl='" + selfUrl + '\'' +
                ", id='" + id + '\'' +
                ", createdDate=" + createdDate +
                ", fixVersions=" + fixVersions +
                ", affectedVersions=" + affectedVersions +
                '}';
    }
}
