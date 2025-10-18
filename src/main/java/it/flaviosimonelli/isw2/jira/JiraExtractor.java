package it.flaviosimonelli.isw2.jira;

import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.util.VersionUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
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
}
