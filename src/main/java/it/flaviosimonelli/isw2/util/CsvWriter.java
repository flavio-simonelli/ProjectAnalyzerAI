package it.flaviosimonelli.isw2.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Simple generic CSV writer that uses reflection
 * to write lists of POJOs to CSV files.
 */
public class CsvWriter {

    /**
     * Writes a list of Java objects (POJOs) to a CSV file.
     *
     * @param list   list of objects to export
     * @param path   output file path (e.g. "data/jira_releases.csv")
     */
    public static <T> void write(List<T> list, String path) throws IOException, IllegalAccessException {
        if (list == null || list.isEmpty()) {
            System.out.println("⚠️ Nessun dato da scrivere in " + path);
            return;
        }

        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
            System.out.println("📁 Creata cartella: " + parent.getAbsolutePath());
        }

        try (FileWriter fw = new FileWriter(path)) {
            Class<?> clazz = list.getFirst().getClass();
            Field[] fields = clazz.getDeclaredFields();

            // Header
            for (int i = 0; i < fields.length; i++) {
                fw.append(fields[i].getName());
                if (i < fields.length - 1) fw.append(",");
            }
            fw.append("\n");

            // Rows
            for (T item : list) {
                for (int i = 0; i < fields.length; i++) {
                    Field f = fields[i];
                    f.setAccessible(true);
                    Object value = f.get(item);
                    fw.append(value != null ? escapeCsv(value.toString()) : "NULL");
                    if (i < fields.length - 1) fw.append(",");
                }
                fw.append("\n");
            }
        }

        System.out.println("✅ File CSV generato: " + path);
    }

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }
}