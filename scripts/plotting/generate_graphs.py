import sys
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os

# --- CONFIGURAZIONE ---
# Lista delle metriche per cui vogliamo generare i grafici
METRICS_TO_PLOT = ['Precision', 'Recall', 'F-Measure', 'AUC', 'Kappa', 'NPofB20']

def main():
    # 1. Parsing Argomenti
    if len(sys.argv) < 3:
        print("Usage: python generate_graphs.py <input_csv> <output_dir>")
        sys.exit(1)

    input_csv = sys.argv[1]
    output_dir = sys.argv[2]

    print(f"[Python] Avvio generazione grafici...")
    print(f"[Python] Input: {input_csv}")

    # 2. Controllo Cartella Output
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    try:
        # 3. Caricamento Dati
        df = pd.read_csv(input_csv)

        if df.empty:
            print("[Python] Warning: Il CSV è vuoto. Nessun grafico generato.")
            sys.exit(0)

        # Recuperiamo il nome del progetto dalla prima riga (es. BOOKKEEPER)
        project_name = df['Project'].iloc[0]

        # 4. Preprocessing
        # Creiamo una colonna "Configuration" che unisce le 3 variabili indipendenti.
        # Usiamo \n per andare a capo e rendere l'etichetta più compatta sull'asse X.
        df['Configuration'] = (
                df['Classifier'] + " + " +
                df['Sampling'] + "\n(" +
                df['FeatureSelection'] + ")"
        )

        # Ordiniamo il DataFrame per avere i boxplot raggruppati logicamente
        df.sort_values(by=['Classifier', 'Sampling', 'FeatureSelection'], inplace=True)

        # Impostiamo lo stile grafico
        sns.set_theme(style="whitegrid")

        # 5. Generazione Grafici (Loop sulle metriche)
        for metric in METRICS_TO_PLOT:
            # Verifica che la metrica esista nel CSV (es. per evitare errori se manca NPofB20)
            if metric not in df.columns:
                print(f"[Python] Warning: Metrica '{metric}' non trovata nel CSV. Salto.")
                continue

            # Creazione Figura (Molto larga per far stare tutte le combinazioni)
            plt.figure(figsize=(16, 8))

            # Creazione Boxplot
            # x = Configurazione (Combinazione di 3 fattori)
            # y = Valore della metrica (es. AUC)
            plot = sns.boxplot(
                x='Configuration',
                y=metric,
                data=df,
                palette="Set3", # Una palette colori pastello per distinguere
                showfliers=False # Opzionale: Nasconde gli outlier estremi per pulizia
            )

            # --- Styling ---
            plt.title(f'Project: {project_name} - Distribuzione {metric}', fontsize=16)
            plt.ylabel(metric, fontsize=14)
            plt.xlabel('Configurazione (Classifier + Sampling + Feat.Sel.)', fontsize=14)

            # Rotazione etichette asse X per leggerle bene
            plt.xticks(rotation=45, ha='right', fontsize=10)

            # Imposta limite Y da 0 a 1 (poiché sono percentuali/probabilità)
            # tranne per metriche che possono essere negative o >1 se ce ne fossero
            if metric != 'Kappa': # Kappa può essere negativo, gli altri sono di solito 0-1
                plt.ylim(-0.05, 1.05)

            # Layout ottimizzato per evitare tagli delle scritte
            plt.tight_layout()

            # Salvataggio
            filename = f"{project_name}_boxplot_{metric}.png"
            out_path = os.path.join(output_dir, filename)
            plt.savefig(out_path)
            plt.close() # Chiude la figura per liberare memoria

            print(f"[Python] Generato: {filename}")

        print("[Python] Processo completato con successo.")

    except Exception as e:
        print(f"[Python] Errore critico: {e}")
        import traceback
        traceback.print_exc() # Stampa lo stack trace completo per debug
        sys.exit(1)

if __name__ == "__main__":
    main()