package it.flaviosimonelli.isw2.jira.client;

import it.flaviosimonelli.isw2.jira.exceptions.JiraClientException;
import it.flaviosimonelli.isw2.util.AppConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class RestJiraClient implements IJiraClient {
    private static final Logger logger = LoggerFactory.getLogger(RestJiraClient.class);

    @Override
    public JSONArray getProjectVersions(String projectKey) throws JiraClientException {
        String url = AppConfig.get("jira.url") + "rest/api/2/project/" + projectKey + "/versions";
        try {
            return readJsonArrayFromUrl(url);
        } catch (IOException | URISyntaxException e) {
            throw new JiraClientException("Impossibile scaricare le versioni per il progetto: " + projectKey, e);
        }
    }

    @Override
    public JSONArray getProjectIssues(String projectKey) throws JiraClientException {
        JSONArray allIssues = new JSONArray();

        int startAt = 0;
        int maxResults = 100;
        int total = Integer.MAX_VALUE;

        while (startAt < total) {
            String url = AppConfig.get("jira.url")
                    + "rest/api/2/search?jql=project%20%3D%20"
                    + projectKey
                    + "%20AND%20issuetype%20%3D%20Bug%20AND%20status%20in%20(Resolved%2C%20Closed)%20AND%20resolution%20%3D%20Fixed"
                    + "&startAt=" + startAt + "&maxResults=" + maxResults;

            JSONObject json;
            try {
                json = readJsonObjectFromUrl(url);
            } catch (IOException | URISyntaxException e) {
                throw new JiraClientException("Errore durante il recupero dei ticket per: " + projectKey, e);
            }

            // read the results
            total = json.getInt("total");
            int currentMax = json.getInt("maxResults");
            JSONArray issues = json.getJSONArray("issues");
            for (int i = 0; i < issues.length(); i++) {
                allIssues.put(issues.getJSONObject(i));
            }
            logger.debug("Scaricati {} issue (startAt={}, total={})", issues.length(), startAt, total);
            // next page
            startAt += currentMax;
        }
        return  allIssues;
    }

    private JSONArray readJsonArrayFromUrl(String urlStr) throws IOException, JSONException, URISyntaxException {
        URI uri = new URI(urlStr);
        URL url = uri.toURL();
        URLConnection conn = url.openConnection();
        try (InputStream is = conn.getInputStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            return new JSONArray(sb.toString());
        }
    }

    private JSONObject readJsonObjectFromUrl(String urlStr) throws IOException, JSONException, URISyntaxException {
        URI uri = new URI(urlStr);
        URL url = uri.toURL();
        URLConnection conn = url.openConnection();
        try (InputStream is = conn.getInputStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            return new JSONObject(sb.toString());
        }
    }
}
