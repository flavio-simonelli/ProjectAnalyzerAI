package it.flaviosimonelli.isw2.metrics;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.document.TextFile;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.flaviosimonelli.isw2.util.AppConfig;

import java.util.Collections;
import java.util.List;

public class PmdAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(PmdAnalysisService.class);
    private final PMDConfiguration config;
    private final LanguageVersion defaultJavaVersion;

    public PmdAnalysisService() {
        this.config = new PMDConfiguration();
        config.setIgnoreIncrementalAnalysis(true);

        // 1. CONFIGURAZIONE JAVA VERSION (Dinamica)
        String configuredVersion = AppConfig.getPmdJavaVersion();

        Language javaLang = LanguageRegistry.PMD.getLanguageById("java");
        if (javaLang != null) {
            // Cerchiamo di settare la versione richiesta, fallback a 1.8 se fallisce
            LanguageVersion ver = javaLang.getVersion(configuredVersion);
            if (ver != null) {
                this.defaultJavaVersion = ver;
            } else {
                logger.warn("Versione Java '{}' non supportata da PMD, uso 1.8", configuredVersion);
                this.defaultJavaVersion = javaLang.getVersion("1.8");
            }
            config.setDefaultLanguageVersion(defaultJavaVersion);
        } else {
            throw new RuntimeException("Modulo Java PMD non trovato!");
        }

        // 2. CONFIGURAZIONE RULESETS (Dinamica)
        String[] rulesets = AppConfig.getPmdRuleSets();

        for (String ruleset : rulesets) {
            try {
                config.addRuleSet(ruleset);
                logger.debug("Aggiunto ruleset PMD: {}", ruleset);
            } catch (Exception e) {
                // Logghiamo l'errore ma continuiamo con gli altri ruleset
                logger.error("Impossibile caricare ruleset PMD '{}': {}", ruleset, e.getMessage());
            }
        }
    }

    public List<RuleViolation> analyze(String fileContent, String fileName) {
        if (fileContent == null || fileContent.isEmpty()) {
            return Collections.emptyList();
        }

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            FileId fileId = FileId.fromPathLikeString(fileName);
            TextFile textFile = TextFile.forCharSeq(fileContent, fileId, defaultJavaVersion);
            pmd.files().addFile(textFile);
            Report report = pmd.performAnalysisAndCollectReport();
            return report.getViolations();

        } catch (Exception e) {
            logger.error("Errore PMD su {}: {}", fileName, e.getMessage());
            return Collections.emptyList();
        }
    }
}