package it.flaviosimonelli.isw2.model;

import java.util.Date;

public class Ticket {
    private String id; // identificativo del ticket Jira
    private Release openingVersion; // OV
    private Release fixVersion;     // FV
    private Release injectedVersion; // IV
}
