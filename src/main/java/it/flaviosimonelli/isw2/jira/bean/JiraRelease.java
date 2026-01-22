package it.flaviosimonelli.isw2.jira.bean;

import java.time.LocalDate;

public class JiraRelease {
    private String id;
    private String name;
    private boolean archived;
    private boolean released;
    private LocalDate releaseDate;

    public JiraRelease(String id, String name, LocalDate releaseDate) {
        this.id = id;
        this.name = name;
        this.releaseDate = releaseDate;
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isArchived() { return archived; }
    public boolean isReleased() { return released; }
    public LocalDate getReleaseDate() { return releaseDate; }

    // --- Setters ---
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public void setReleased(boolean released) { this.released = released; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    @Override
    public String toString() {
        return "JiraRelease{name='" + name + "', date=" + releaseDate + ", released=" + released + "}";
    }

    public boolean isBeforeOrEqual(JiraRelease other) {
        // Una data è "<= altra" se NON è dopo l'altra
        return !this.releaseDate.isAfter(other.getReleaseDate());
    }
}
