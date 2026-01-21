package it.flaviosimonelli.isw2.oldutil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;

/**
 * Generic CSV reader using reflection.
 * Reads a CSV file where the first line contains field names.
 * Works symmetrically with CsvWriter.
 */
public class CsvReader {

    /**
     * Reads a list of objects of the given class from a CSV file.
     *
     * @param clazz the class type (e.g. JiraRelease.class)
     * @param path  the path to the CSV file
     * @return a list of populated objects
     */
    public static <T> List<T> read(Class<T> clazz, String path) throws IOException {
        List<T> list = new ArrayList<>();
        File file = new File(path);

        if (!file.exists()) {
            System.err.println("❌ Il file CSV non esiste: " + file.getAbsolutePath());
            return list;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                System.out.println("⚠️ File CSV vuoto: " + path);
                return list;
            }

            String[] headers = headerLine.split(",", -1);
            Map<String, Field> fieldMap = mapFields(clazz, headers);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                T obj = populateObject(clazz, fieldMap, headers, parts);
                list.add(obj);
            }
        }

        System.out.println("✅ Letti " + list.size() + " elementi da " + path);
        return list;
    }

    // --- Helpers ---

    private static <T> Map<String, Field> mapFields(Class<T> clazz, String[] headers) {
        Map<String, Field> map = new HashMap<>();
        for (String header : headers) {
            try {
                Field f = clazz.getDeclaredField(header);
                f.setAccessible(true);
                map.put(header, f);
            } catch (NoSuchFieldException e) {
                System.err.println("⚠️ Campo non trovato nella classe: " + header);
            }
        }
        return map;
    }

    private static <T> T populateObject(Class<T> clazz, Map<String, Field> fieldMap, String[] headers, String[] parts) {
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            for (int i = 0; i < headers.length && i < parts.length; i++) {
                String header = headers[i];
                String raw = parts[i].trim();
                Field field = fieldMap.get(header);
                if (field == null) continue;

                Object value = parseValue(field.getType(), raw);
                field.set(obj, value);
            }
            return obj;
        } catch (Exception e) {
            System.err.println("⚠️ Errore durante la creazione di un oggetto " + clazz.getSimpleName());
            e.printStackTrace();
            return null;
        }
    }

    private static Object parseValue(Class<?> type, String raw) {
        if (raw == null || raw.isEmpty() || raw.equalsIgnoreCase("NULL")) return null;

        try {
            if (type == String.class) return raw;
            if (type == int.class || type == Integer.class) return Integer.parseInt(raw);
            if (type == long.class || type == Long.class) return Long.parseLong(raw);
            if (type == double.class || type == Double.class) return Double.parseDouble(raw);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(raw);
            if (type == LocalDate.class) return LocalDate.parse(raw);
        } catch (Exception e) {
            System.err.println("⚠️ Impossibile convertire valore '" + raw + "' in " + type.getSimpleName());
        }

        return null;
    }
}