colonne del CSV finale:
1. Name of the project: AVRO
2. Name of the methodStaticMetrics: /ddd/sss/dddd/ccc/caption.java/aaa()
3. Release ID
4. About 10 features with at least one actionable feature
5. Bugginess (yes/no)

Metriche che voglio estrarre da github per metodo:
queste sono le metriche actionable
1. Lines of Code (LOC): linee di codice di ogni metodo
2. Statements Count
3. Cyclomatic Complexity
4. Cognitive Complexity
5. Halstead Complexity Measures
6. Nesting Depth
7. Number of Branches/Decision Points
8. Number of Code Smells
9. Parameter Count
10. Duplication

queste sono quelle non actionable
1. methodHistories: number of times a methodStaticMetrics was changed.
2. authors: number of distinct authors that changed a methodStaticMetrics.
3. stmtAdded: sum of all source code statements added to a methodStaticMetrics body over all methodStaticMetrics histories.
4. maxStmtAdded: maximum number of source code statements added to a methodStaticMetrics body throughout the methodStaticMetrics’s change history.
5. avgStmtAdded: average number of source code statements added to a methodStaticMetrics body per change to the methodStaticMetrics.
6. stmtDeleted: sum of all source code statements deleted from a methodStaticMetrics body over all methodStaticMetrics histories.
7. maxStmtDeleted: maximum number of source code statements deleted from a methodStaticMetrics body for all methodStaticMetrics histories.
8. avgStmtDeleted: Average number of source code statements deleted from a methodStaticMetrics body per methodStaticMetrics history
9. churn: sum of stmtAdded plus stmtDeleted over all methodStaticMetrics histories.
10. maxChurn: maximum churn for all methodStaticMetrics histories.
11. avgChurn: average churn per methodStaticMetrics history
12. cond: number of condition expression changes in a methodStaticMetrics body over all revisions.
13. elseAdded: number of added else-parts in a methodStaticMetrics body over all revisions.
14. elseDeleted: number of deleted else-parts from a methodStaticMetrics body over all revisions


🔹 Coupling (Accoppiamento)

CBO (Coupling Between Objects) – numero di classi esterne referenziate da una classe.

CBO Modified – variante che considera sia le dipendenze usate sia quelle ricevute da altre classi.

FAN-IN – numero di classi che dipendono da una classe (input dependencies).

FAN-OUT – numero di classi referenziate da una classe (output dependencies).

🔹 Inheritance (Ereditarietà)

DIT (Depth of Inheritance Tree) – profondità della gerarchia di ereditarietà.

NOC (Number of Children) – numero di sottoclassi dirette di una classe.

🔹 Structural Size (Dimensione strutturale)

Number of Fields – numero di variabili d’istanza (totali e per modificatore: static, public, private, ecc.).

Number of Methods – numero totale di metodi dichiarati, inclusi costruttori.

Number of Visible Methods – numero di metodi non privati (accessibili dall’esterno).

🔹 Complexity and Responsibility (Complessità e responsabilità della classe)

NOSI (Number of Static Invocations) – numero di chiamate a metodi statici risolte staticamente.

RFC (Response For a Class) – numero totale di metodi che possono essere invocati in risposta a un messaggio inviato alla classe (metodi propri + chiamati).

WMC (Weighted Methods per Class) – somma delle complessità McCabe di tutti i metodi della classe.

🔹 Cohesion (Coesione)

LCOM / LCOM* – misura di mancanza di coesione tra metodi; valori alti indicano bassa coesione.

TCC (Tight Class Cohesion) – percentuale di metodi che condividono accesso diretto agli stessi attributi.

LCC (Loose Class Cohesion) – come TCC ma include anche connessioni indirette; LCC ≥ TCC.

🔹 Structural Counts (Conteggi sintattici)

Quantity of Returns – numero di istruzioni return.

Quantity of Loops – numero di cicli (for, while, do-while, for-each).

Quantity of Comparisons – numero di confronti (==, !=).

Quantity of Try/Catches – numero di blocchi try/catch.

Quantity of Parenthesized Expressions – numero di espressioni racchiuse tra parentesi tonde.

Quantity of Variables – numero di variabili locali dichiarate.

🔹 Literals and Operations (Letterali e operazioni)

String Literals – numero di stringhe letterali nel codice.

Quantity of Numbers – numero di costanti numeriche (int, double, float, ecc.).

Quantity of Math Operations – numero di operazioni matematiche (+, -, *, /, %, shift).

🔹 Structures and Constructs (Strutture e costrutti)

Max Nested Blocks – profondità massima di blocchi annidati.

Anonymous / Inner Classes / Lambdas – numero di classi anonime, interne e di espressioni lambda.

Has Javadoc – vero/falso: indica se il metodo ha un commento Javadoc.

🔹 Lexical Analysis (Analisi lessicale)

Number of Unique Words – numero di parole distinte nel corpo del metodo (split camelCase e underscore, esclusi keyword).

Number of Log Statements – numero di chiamate a log (compatibili con SLF4J, Log4J, ecc.).

🔹 Usage and Invocations (Analisi d’uso e invocazioni)

Usage of Each Variable – numero di volte che ogni variabile locale è usata nel metodo.

Usage of Each Field – frequenza d’uso degli attributi della classe (inclusi usi indiretti).

Method Invocations – numero o elenco di metodi invocati direttamente o indirettamente.