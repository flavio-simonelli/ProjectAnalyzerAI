package it.flaviosimonelli.isw2.model;

import java.time.LocalDate;

public class Release {
    private String id; // identificativo univoco intenrno al database di Jira
    private String name; // nome della versione visibile all'utente
    private boolean released; // I ticket associati a questa versione come "Fix Version" sono considerati parte di un ciclo chiuso
    private LocalDate releaseDate; // La data di rilascio

    public Release(String id, String name, boolean released, LocalDate releaseDate) {
        this.id = id;
        this.name = name;
        this.released = released;
        this.releaseDate = releaseDate;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public boolean isReleased() {
        return released;
    }
    public void setReleased(boolean released) {
        this.released = released;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }
    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }
}
