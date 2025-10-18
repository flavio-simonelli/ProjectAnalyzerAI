package it.flaviosimonelli.isw2.util;

public class Config {

    private Config() {
        // Prevent instantiation
    }

    // -- Jira Configuration --
    public static final String JIRA_BASE_URL = "https://issues.apache.org/jira/";
    public static final String JIRA_RELEASES_CSV = "data/jira_releases.csv";
    public static final String JIRA_ISSUES_CSV   = "data/jira_issues.csv";
}
