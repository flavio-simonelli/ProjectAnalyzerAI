import sys
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os

# Leggiamo gli argomenti passati da Java
# sys.argv[0] è il nome dello script
# sys.argv[1] sarà il path del CSV di input
# sys.argv[2] sarà la cartella di output per le immagini

if len(sys.argv) < 3:
    print("Usage: python generate_graphs.py <input_csv> <output_dir>")
    sys.exit(1)

input_csv = sys.argv[1]
output_dir = sys.argv[2]

print(f"[Python] Generazione grafici da: {input_csv}")
print(f"[Python] Output directory: {output_dir}")

# Creazione cartella se non esiste
if not os.path.exists(output_dir):
    os.makedirs(output_dir)

try:
    df = pd.read_csv(input_csv)

    # Filtra eventuali NaN sull'AUC
    df_clean = df.dropna(subset=['AUC'])

    # Configurazione stile
    sns.set_theme(style="whitegrid")

    # --- PLOT 1: Boxplot AUC ---
    plt.figure(figsize=(10, 6))
    sns.boxplot(x='Classifier', y='AUC', hue='Sampling', data=df_clean)
    plt.title('Distribuzione AUC per Classificatore')
    plt.savefig(os.path.join(output_dir, 'boxplot_auc.png'))
    plt.close()

    # --- PLOT 2: Trend Temporale ---
    plt.figure(figsize=(12, 6))
    # Calcola la media se ci sono più run per la stessa release
    df_grouped = df_clean.groupby(['ReleaseIndex', 'Classifier', 'Sampling'])['AUC'].mean().reset_index()
    sns.lineplot(x='ReleaseIndex', y='AUC', hue='Classifier', style='Sampling', markers=True, data=df_grouped)
    plt.title('Andamento AUC per Release (Walk-Forward)')
    plt.savefig(os.path.join(output_dir, 'trend_auc.png'))
    plt.close()

    print("[Python] Grafici generati con successo.")

except Exception as e:
    print(f"[Python] Errore critico: {e}")
    sys.exit(1)