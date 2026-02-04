import sys
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os

# --- CONFIGURAZIONE ---
METRICS_TO_PLOT = ['Precision', 'Recall', 'F-Measure', 'AUC', 'Kappa', 'NPofB20']

# Palette colori personalizzata (IBk=Blu, NB=Rosso, RF=Verde)
CUSTOM_PALETTE = {
    "IBk": "#6495ED",           # CornflowerBlue
    "NaiveBayes": "#F08080",    # LightCoral
    "RandomForest": "#90EE90"   # LightGreen
}

def main():
    if len(sys.argv) < 3:
        print("Usage: python generate_graphs.py <input_csv> <output_dir>")
        sys.exit(1)

    input_csv = sys.argv[1]
    output_dir = sys.argv[2]

    print(f"[Python] Avvio generazione grafici a matrice...")

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    try:
        # 1. Caricamento Dati
        df = pd.read_csv(input_csv)
        if df.empty:
            print("[Python] Warning: Il CSV è vuoto.")
            sys.exit(0)

        # 2. Pulizia e Preparazione

        # --- MODIFICA: Rimossa la rinominazione forzata da 'Sampling' a 'SMOTE' ---
        # Ci assicuriamo che la colonna esista e la trattiamo come stringa
        sampling_col = 'Sampling'
        if sampling_col not in df.columns:
            # Fallback se la colonna non si chiama Sampling
            print(f"[Python] Warning: Colonna '{sampling_col}' non trovata. Cerco colonne simili...")
            # Qui potresti aggiungere logica per trovare colonne alternative se serve
            pass
        else:
            df[sampling_col] = df[sampling_col].astype(str)

        df['FeatureSelection'] = df['FeatureSelection'].astype(str)

        project_name = df['Project'].iloc[0] if 'Project' in df.columns else "Project"

        # Impostiamo lo stile
        sns.set_theme(style="whitegrid", font_scale=1.2)

        # 3. Loop Metriche
        for metric in METRICS_TO_PLOT:
            if metric not in df.columns:
                continue

            print(f"[Python] Generazione grafico per {metric}...")

            # --- Generazione Matrice ---
            # Nota: col="Sampling" invece di col="SMOTE"
            g = sns.catplot(
                data=df,
                kind="box",
                x="Classifier",
                y=metric,
                hue="Classifier",
                col=sampling_col,      # Usa la colonna corretta
                row="FeatureSelection",
                palette=CUSTOM_PALETTE,
                height=4,
                aspect=1.2,
                margin_titles=True,
                showfliers=False,
                linewidth=1.5,
                sharey=True,
                legend=True
            )

            # --- Styling Avanzato ---
            #g.figure.subplots_adjust(top=0.9)
            #g.figure.suptitle(f'{project_name} - {metric} Distribution', fontsize=20, fontweight='bold')

            g.set_axis_labels("", metric)

            # --- MODIFICA TITOLI ---
            # Ora userà "Sampling: Undersampling", "Sampling: SMOTE", ecc.
            g.set_titles(col_template="Sampling: {col_name}", row_template="Feat.Sel.: {row_name}")

            # Limiti asse Y
            if metric == 'Kappa':
                g.set(ylim=(-0.2, 1.05))
            else:
                g.set(ylim=(-0.05, 1.05))

            # --- FIX ERRORE LEGENDA ---
            try:
                sns.move_legend(g, "upper right", bbox_to_anchor=(1, 1))
            except ValueError:
                pass
            except Exception as e:
                print(f"[Python] Warning non bloccante sulla legenda: {e}")

            # Salvataggio
            filename = f"{project_name}_matrix_{metric}.png"
            out_path = os.path.join(output_dir, filename)
            plt.savefig(out_path, dpi=300)
            plt.close()

            print(f"[Python] Salvato: {filename}")

    except Exception as e:
        print(f"[Python] Errore critico: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()