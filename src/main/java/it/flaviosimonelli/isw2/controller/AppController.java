package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.jira.JiraExtractor;
import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.util.CsvWriter;

import java.util.List;

public class AppController {

    public void extractFromJira(String projectKey) {
        try {
            JiraExtractor jiraExtractor = new JiraExtractor();
            List<JiraRelease> releases = jiraExtractor.extractJiraReleases(projectKey);
            System.out.println("📦 Numero release trovate: " + releases.size());
            CsvWriter.write(releases, "data/jira_releases_" + projectKey + ".csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
