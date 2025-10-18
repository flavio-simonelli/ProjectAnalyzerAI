package it.flaviosimonelli.isw2.model;

import java.time.LocalDate;

public class JiraRelease {
    private String selfUrl;
    private String id;
    private String description;
    private String name;
    private boolean archived;
    private boolean released;
    private LocalDate startDate;
    private LocalDate releaseDate;
    private boolean overdue;

    public JiraRelease() {}

    public JiraRelease(String selfUrl, String id, String description, String name,
                       boolean archived, boolean released,
                       LocalDate startDate, LocalDate releaseDate, boolean overdue) {
        this.selfUrl = selfUrl;
        this.id = id;
        this.description = description;
        this.name = name;
        this.archived = archived;
        this.released = released;
        this.startDate = startDate;
        this.releaseDate = releaseDate;
        this.overdue = overdue;
    }

    // Getters and Setters
    public String getSelfUrl() { return selfUrl; }
    public void setSelfUrl(String selfUrl) { this.selfUrl = selfUrl; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public boolean isOverdue() { return overdue; }
    public void setOverdue(boolean overdue) { this.overdue = overdue; }

    @Override
    public String toString() {
        return String.format("%s [%s] (%s → %s, released=%s, overdue=%s)",
                name, id,
                startDate != null ? startDate : "?",
                releaseDate != null ? releaseDate : "?",
                released, overdue);
    }
}
