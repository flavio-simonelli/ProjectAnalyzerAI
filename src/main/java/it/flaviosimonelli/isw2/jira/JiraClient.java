package it.flaviosimonelli.isw2.jira;

import it.flaviosimonelli.isw2.util.Config;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class JiraClient {

    public JSONArray getProjectVersions(String projectKey) throws IOException, URISyntaxException {
        String url = Config.JIRA_BASE_URL + "rest/api/2/project/" + projectKey + "/versions";
        return readJsonArrayFromUrl(url);
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
}
