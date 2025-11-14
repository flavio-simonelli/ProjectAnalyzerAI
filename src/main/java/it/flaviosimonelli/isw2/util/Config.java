package it.flaviosimonelli.isw2.util;

import it.flaviosimonelli.isw2.exception.ConfigException;

public class Config {
    public static final String JIRA_BASE_URL = "https://issues.apache.org/jira/";
    public static final String JIRA_RELEASES_CSV = "data/jira_releases.csv";
    public static final String JIRA_ISSUES_CSV   = "data/jira_issues.csv";

    private JiraConfig jira;
    private GithubConfig github;
    private OutputConfig output;

    public Config() {
    }

    public Config(JiraConfig jira, GithubConfig github, OutputConfig output) {
        this.jira = jira;
        this.github = github;
        this.output = output;
    }

    public static class JiraConfig {
        private String projectKey;

        public JiraConfig() {
        }

        public JiraConfig(String projectKey) {
            this.projectKey = projectKey;
        }

        public String getProjectKey() {
            return projectKey;
        }

        public void setProjectKey(String projectKey) {
            this.projectKey = projectKey;
        }

        public void validate() throws ConfigException {
            if (this.projectKey == null || this.projectKey.trim().isEmpty()) {
                throw new ConfigException("Il campo 'jira.projectKey' è obbligatorio e non può essere vuoto.");
            }
        }

        @Override
        public String toString() {
            return "JiraConfig {" +
                    "projectKey='" + projectKey + '\'' +
                    '}';
        }
    }

    public static class GithubConfig {
        private String repository;
        private String owner;
        private String branch;
        private String localClonePath = System.getProperty("java.io.tmpdir");

        public GithubConfig() {
        }

        public GithubConfig(String repository, String owner, String branch) {
            this.repository = repository;
            this.owner = owner;
            this.branch = branch;
        }

        public String getRepository() {
            return repository;
        }
        public void setRepository(String repository) {
            this.repository = repository;
        }

        public String getOwner() {
            return owner;
        }
        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getBranch() {
            return branch;
        }
        public void setBranch(String branch) {
            this.branch = branch;
        }

        public String getLocalClonePath() {
            return localClonePath;
        }
        public void setLocalClonePath(String localClonePath) {
            this.localClonePath = localClonePath;
        }

        public void validate() throws ConfigException {
            if (this.repository == null || this.repository.trim().isEmpty()) {
                throw new ConfigException("Il campo 'github.repository' è obbligatorio e non può essere vuoto.");
            }
            if (this.owner == null || this.owner.trim().isEmpty()) {
                throw new ConfigException("Il campo 'github.owner' è obbligatorio e non può essere vuoto.");
            }
            if (this.branch == null || this.branch.trim().isEmpty()) {
                throw new ConfigException("Il campo 'github.branch' è obbligatorio e non può essere vuoto.");
            }
            if (this.localClonePath == null || this.localClonePath.trim().isEmpty()) {
                throw new ConfigException("Il campo 'github.localClonePath' è obbligatorio e non può essere vuoto.");
            }
        }

        @Override
        public String toString() {
            return "GithubConfig {" +
                    "repository='" + repository + '\'' +
                    ", owner='" + owner + '\'' +
                    ", branch='" + branch + '\'' +
                    ", localClonePath='" + localClonePath + '\'' +
                    '}';
        }
    }

    public static class OutputConfig {
        private String directory = "./reports";

        public OutputConfig() {
        }

        public OutputConfig(String directory) {
            this.directory = directory;
        }

        public String getDirectory() {
            return directory;
        }
        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public void validate() throws ConfigException {
            if (this.directory == null || this.directory.trim().isEmpty()) {
                throw new ConfigException("Il campo 'output.directory' è obbligatorio e non può essere vuoto.");
            }
        }

        @Override
        public String toString() {
            return "OutputConfig {" +
                    "directory='" + directory + '\'' +
                    '}';
        }
    }


    public JiraConfig getJira() {
        return this.jira;
    }
    public void setJira(JiraConfig jiraConfig) {
        this.jira = jiraConfig;
    }

    public GithubConfig getGithub() {
        return this.github;
    }
    public void setGithub(GithubConfig githubConfig) {
        this.github = githubConfig;
    }

    public OutputConfig getOutput() {
        return this.output;
    }
    public void setOutput(OutputConfig outputConfig) {
        this.output = outputConfig;
    }

    /**
     * Valida che tutte le sezioni di configurazione obbligatorie siano presenti e complete.
     */
    public void validate() throws ConfigException {
        // Valida la sezione JIRA (obbligatoria)
        if (this.jira == null) {
            throw new ConfigException("La sezione di configurazione 'jira' è mancante nel file YAML.");
        }
        this.jira.validate();

        // Valida la sezione GITHUB (obbligatoria)
        if (this.github == null) {
            throw new ConfigException("La sezione di configurazione 'github' è mancante nel file YAML.");
        }
        this.github.validate();

        // Valida la sezione OUTPUT (obbligatoria)
        if (this.output == null) {
            throw new ConfigException("La sezione di configurazione 'output' è mancante nel file YAML.");
        }
        this.output.validate();
    }

    @Override
    public String toString() {
        return "Config {" +
                "\n  jira=" + jira +
                ",\n  github=" + github +
                ",\n  output=" + output +
                "\n}";
    }

}
