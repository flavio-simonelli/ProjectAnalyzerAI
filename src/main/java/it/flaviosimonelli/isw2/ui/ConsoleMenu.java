package it.flaviosimonelli.isw2.ui;

import it.flaviosimonelli.isw2.controller.AppController;
import it.flaviosimonelli.isw2.github.GitHubClient;
import it.flaviosimonelli.isw2.github.GitHubExtractor;
import it.flaviosimonelli.isw2.jira.JiraClient;
import it.flaviosimonelli.isw2.jira.JiraExtractor;
import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.util.Config;
import it.flaviosimonelli.isw2.util.CsvReader;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class ConsoleMenu {

    private final Scanner scanner = new Scanner(System.in);

    public void start() {
        while (true) {
            System.out.println("\n===== ML Data Extractor =====");
            System.out.println("1. Estrai informazioni da GitHub");
            System.out.println("2. Estrai informazioni da Jira");
            System.out.println("3. Crea CSV finale");
            System.out.println("4. Usa Weka");
            System.out.println("0. Esci");
            System.out.print("Seleziona un'opzione: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // pulisci buffer

            switch (choice) {
                case 1 -> extractGitHub();
                case 2 -> extractJira();
                case 3 -> createCSV();
                case 4 -> useWeka();
                case 0 -> {
                    System.out.println("Uscita...");
                    return;
                }
                default -> System.out.println("Opzione non valida.");
            }
        }
    }

    private void extractGitHub() {
        //TODO: Placeholder per l'estrazione da GitHub
        System.out.println("Funzionalità di estrazione GitHub non ancora implementata.");
    }

    private void extractJira() {
        try {
            System.out.println("Estrazione informazioni da Apache Jira");
            System.out.println("Endpoint: " + Config.JIRA_BASE_URL);
            System.out.print("Inserisci chiave progetto (es. OPENJPA, BOOKKEEPER...): ");
            String projectKey = scanner.nextLine().trim();

            if (projectKey.isEmpty()) {
                System.out.println("Nessun progetto inserito. Operazione annullata.");
                return;
            }

            AppController controller = new AppController();
            controller.extractFromJira(projectKey);

            System.out.println("File generati:");
            System.out.println(" - " + Config.JIRA_RELEASES_CSV);
            System.out.println(" - " + Config.JIRA_ISSUES_CSV);

        } catch (Exception e) {
            System.err.println("Errore durante l'estrazione da Jira:");
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
