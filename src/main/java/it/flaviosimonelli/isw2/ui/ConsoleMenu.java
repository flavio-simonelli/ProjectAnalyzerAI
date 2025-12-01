package it.flaviosimonelli.isw2.ui;

import it.flaviosimonelli.isw2.controller.CsvController;
import it.flaviosimonelli.isw2.exception.ConfigException;
import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.util.Config;
import it.flaviosimonelli.isw2.util.ConfigLoader;
import it.flaviosimonelli.isw2.util.CsvReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class ConsoleMenu {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleMenu.class);

    private final Scanner scanner = new Scanner(System.in);

    public void start() {
        Config appConfig = null;
        try {
            appConfig = ConfigLoader.loadConfig();
            logger.info("Configurazione caricata con successo: {}", appConfig);
        } catch (ConfigException e) {
            logger.error("Errore critico durante il caricamento della configurazione. Uscita.", e);

            System.err.println("--- ERRORE NEL CARICAMENTO DELLA CONFIGURAZIONE ---");
            System.err.println(e.getMessage());

            System.exit(1);
        }
        while (true) {
            System.out.println("\n===== ISW2 Project: ML Analyzer =====");
            System.out.println("1. Estrai Jira Releases");
            System.out.println("2. Estrai Jira Tickets");
            System.out.println("3. Estrai Github Releases");
            System.out.println("4. Combina Release da Jira e GitHub");
            System.out.println("0. Esci");
            System.out.print("Seleziona un'opzione: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // pulisci buffer

            logger.debug("Opzione selezionata dall'utente: {}", choice);

            switch (choice) {
                case 1 -> extractJiraReleases(appConfig);
                case 2 -> extractJiraTickets(appConfig);
                case 3 -> extractGitHubReleases(appConfig);
                case 4 -> combineReleases(appConfig);
                case 0 -> {
                    logger.info("Programma terminato su richiesta utente.");
                    System.out.println("Uscita...");
                    return;
                }
                default -> {
                    logger.warn("Opzione non gestita selezionata: {}", choice);
                    System.out.println("Opzione non valida.");
                }
            }
        }
    }

    /* Placeholder per future funzionalità */
    private void notImplemented(Config appConfig) {
        System.out.println("Funzionalità non ancora implementata.");
    }

    /* Funzione per estrarre le versioni del progetto da Jira */
    private void extractJiraReleases(Config appConfig) {
        System.out.println("\nestrazione Jira Releases...");
        CsvController controller = new CsvController();
        controller.extractReleasesFromJira(appConfig);
    }

    /* Funzione per estrarre i Tickets da Jira */
    private void extractJiraTickets(Config appConfig) {
        System.out.println("\nestrazione Jira Tickets...");
        CsvController controller = new CsvController();
        controller.extractTicketsFromJira(appConfig);
    }

    /* Funzione per estrarre le versioni del progetto da GitHub */
    private void extractGitHubReleases(Config appConfig) {
        System.out.println("\nestrazione GitHub Releases...");
        CsvController controller = new CsvController();
        controller.extractTagsFromGit(appConfig);
    }

    /* Funzione per combinare le release da Jira e GitHub */
    private void combineReleases(Config appConfig) {
        System.out.println("\ncombinazione release da Jira e GitHub...");
        CsvController controller = new CsvController();
        controller.filterReleases(appConfig);
    }

    private void extractGitHub() {
        try {
            System.out.println("=== Estrazione informazioni da GitHub ===");
            System.out.print("Inserisci il nome dell'owner (default: apache): ");
            String owner = scanner.nextLine().trim();
            if (owner.isEmpty()) {
                owner = "apache";
            }

            System.out.print("Inserisci il nome del repository (es: openjpa): ");
            String repo = scanner.nextLine().trim();
            if (repo.isEmpty()) {
                System.err.println("Inserisci il nome del repository (es: openjpa)");
                return;
            }

            System.out.print("Inserisci la chiave del progetto Jira associato (es. LANG): ");
            String projectKey = scanner.nextLine().trim();
            if (projectKey.isEmpty()) {
                System.err.println("Inserisci il nome del progetto Jira associato");
                return;
            }

            System.out.print("Inserisci il prefisso nei tag associati alle release: ");
            String prefix = scanner.nextLine().trim();

            CsvController controller = new CsvController();

            // percorso dove salvare/clonare le repo locali
            Path basePath = Path.of(
                    System.getProperty("user.home"), "isw2_repos");

            // esegue l’estrazione Git (incluso matching con le release Jira)
            controller.extractFromGitByDates(basePath, owner, repo, projectKey);

            System.out.println("✅ Estrazione GitHub completata con successo!");
            System.out.println("File generato: data/git_releases_" + projectKey + ".csv");

        } catch (Exception e) {
            System.err.println("❌ Errore durante l'estrazione da GitHub:");
            e.printStackTrace();
        }
    }


    private void createCSV() {
        /*
        //TODO: Placeholder per la creazione del CSV finale
        System.out.println("Funzionalità di creazione CSV non ancora implementata.");
        */
        String path = "data/jira_releases_BOOKKEEPER.csv";

        System.out.println("📖 Lettura CSV: " + path);
        List<JiraRelease> releases = null;
        try {
            releases = CsvReader.read(JiraRelease.class, path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (releases.isEmpty()) {
            System.out.println("⚠️ Nessuna release trovata nel file.");
            return;
        }

        System.out.println("📦 Elenco release:");
        System.out.println("────────────────────────────────────────────────────────────");

        int index = 1;
        for (JiraRelease r : releases) {
            System.out.printf(
                    "%2d. %-20s | ID: %-10s | Released: %-5s | Overdue: %-5s | Start: %-10s | Release: %-10s%n",
                    index++,
                    r.getName(),
                    r.getId(),
                    r.isReleased(),
                    r.isOverdue(),
                    r.getStartDate(),
                    r.getReleaseDate()
            );
        }

        System.out.println("────────────────────────────────────────────────────────────");
        System.out.println("🏁 Fine elenco.");
    }


    private void useWeka() {
        //TODO: Placeholder per l'integrazione con Weka
        System.out.println("Funzionalità Weka non ancora implementata.");
    }
}
