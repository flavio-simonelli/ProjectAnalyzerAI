package it.flaviosimonelli.isw2.jira;

import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.model.JiraTicket;
import it.flaviosimonelli.isw2.util.VersionUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JiraExtractor {

    // Methods to extract Jira version of a project
    public List<JiraRelease> extractJiraReleases(String projectKey) throws IOException, URISyntaxException {
        // get json array from JiraClient
        JiraClient jiraClient = new JiraClient();
        JSONArray jsonVersions = jiraClient.getProjectVersions(projectKey);
        // parse json array to list of JiraRelease
        List<JiraRelease> releases = new ArrayList<>();

        for (int i = 0; i < jsonVersions.length(); i++) {
            JSONObject v = jsonVersions.getJSONObject(i);

            String selfUrl = v.optString("self", null);
            String id = v.optString("id", null);
            String description = v.optString("description", null);
            String name = v.optString("name", null);
            boolean archived = false;
            boolean released = false;
            boolean overdue = false;
            LocalDate startDate = null;
            LocalDate releaseDate = null;

            if (v.has("archived") && !v.isNull("archived")) {
                archived = v.getBoolean("archived");
            }
            if (v.has("released") && !v.isNull("released")) {
                released = v.getBoolean("released");
            }
            if (v.has("overdue") && !v.isNull("overdue")) {
                overdue = v.getBoolean("overdue");
            }
            if (v.has("startDate") && !v.isNull("startDate")) {
                try {
                    startDate = LocalDate.parse(v.getString("startDate"));
                } catch (Exception e) {
                    System.err.println("⚠️ Errore parsing startDate per versione " + name + ": " + v.getString("startDate"));
                }
            }
            if (v.has("releaseDate") && !v.isNull("releaseDate")) {
                try {
                    releaseDate = LocalDate.parse(v.getString("releaseDate"));
                } catch (Exception e) {
                    System.err.println("⚠️ Errore parsing releaseDate per versione " + name + ": " + v.getString("releaseDate"));
                }
            }

            JiraRelease jiraRelease = new JiraRelease(
                    selfUrl,
                    id,
                    description,
                    name,
                    archived,
                    released,
                    startDate,
                    releaseDate,
                    overdue
            );

            releases.add(jiraRelease);
        }

        releases.sort(Comparator.comparing(JiraRelease::getName, VersionUtils.comparator()));

        return releases;
    }

    private static final DateTimeFormatter JIRA_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"); // es. 2016-03-31T20:28:05.679+0000


    public List<JiraTicket> extractJiraTickets(String projectKey) throws IOException, URISyntaxException {
        // get json array from JiraClient
        JiraClient jiraClient = new JiraClient();
        JSONArray jsontickets = jiraClient.getprojectIssues(projectKey);
        // parse json array to list of JiraTickets
        List<JiraTicket> tickets = new ArrayList<>();
        for (int i = 0; i < jsontickets.length(); i++) {
            JSONObject ticket = jsontickets.getJSONObject(i);
            JSONObject fields = ticket.getJSONObject("fields");

            // extract information from object json
            String selfUrl = ticket.optString("self", null);
            String id = ticket.optString("id", null);
            LocalDate createdDate = null;
            if (fields.has("created") && !fields.isNull("created")) {
                try {
                    createdDate = LocalDate.parse(fields.getString("created"), JIRA_DATE_FORMAT);
                } catch (Exception e) {
                    // fallback se Jira restituisce formato semplificato
                    createdDate = LocalDate.parse(fields.getString("created").substring(0, 10));
                }
            }

            List<String> fixVersions = new ArrayList<>();
            if (fields.has("fixVersions")) {
                JSONArray fixArray = fields.getJSONArray("fixVersions");
                for (int j = 0; j < fixArray.length(); j++) {
                    JSONObject fix = fixArray.getJSONObject(j);
                    fixVersions.add(fix.optString("name"));
                }
            }

            List<String> affectedVersions = new ArrayList<>();
            if (fields.has("versions")) {
                JSONArray affectedArray = fields.getJSONArray("versions");
                for (int j = 0; j < affectedArray.length(); j++) {
                    JSONObject ver = affectedArray.getJSONObject(j);
                    affectedVersions.add(ver.optString("name"));
                }
            }

            JiraTicket jiraTicket = new JiraTicket(
                    selfUrl,
                    id,
                    createdDate,
                    fixVersions,
                    affectedVersions
            );

            tickets.add(jiraTicket);

        }
        return tickets;
    }
}
