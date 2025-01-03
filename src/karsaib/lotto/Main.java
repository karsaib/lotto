package karsaib.lotto;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Kérlek, add meg a CSV fájl nevét argumentumként.");
            return;
        }

        String fileName = args[0];

        try {
            // A fájl tartalmának beolvasása és kétdimenziós tömbbe helyezése
            int[][] numbers = readCsvTo2DArray(fileName);

            // Az összes sor száma
            int totalRows = numbers.length;

            // Számosságok keresése
            Map<Integer, Integer> occurrences = countOccurrences(numbers);

            // Első előfordulások keresése és átalakítása
            Map<Integer, Integer> adjustedFirstOccurrences = findAdjustedFirstOccurrences(numbers, totalRows);

            // Súlyozott átlagok számítása
            Map<Integer, Double> weightedAverages = calculateWeightedAverage(occurrences, adjustedFirstOccurrences, totalRows);

            // Map rendezése érték szerint növekvő sorrendbe
            Map<Integer, Double> sortedWeightedAverages = weightedAverages.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));

            // Eredmények kiírása
            System.out.println("Számok súlyozott átlaga (növekvő sorrendben):");
            for (Map.Entry<Integer, Double> entry : sortedWeightedAverages.entrySet()) {
                System.out.printf("Szám: %d -> Súlyozott átlag: %.2f%n", entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            System.out.println("Hiba történt: " + e.getMessage());
        }
    }

    private static int[][] readCsvTo2DArray(String fileName) throws IOException {
        List<int[]> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(fileName));
        for (String line : lines) {
            String[] stringNumbers = line.split(",");
            if (stringNumbers.length != 5) {
                throw new IOException("A sor nem pontosan 5 számot tartalmaz: " + line);
            }
            int[] row = new int[5];
            for (int i = 0; i < stringNumbers.length; i++) {
                try {
                    row[i] = Integer.parseInt(stringNumbers[i].trim());
                } catch (NumberFormatException e) {
                    throw new IOException("Érvénytelen szám található a sorban: '" + stringNumbers[i] + "' a sor: " + line);
                }
            }
            rows.add(row);
        }
        return rows.toArray(new int[0][0]);
    }

    private static Map<Integer, Integer> countOccurrences(int[][] numbers) {
        Map<Integer, Integer> occurrences = new HashMap<>();
        for (int[] row : numbers) {
            for (int num : row) {
                if (num >= 1 && num <= 90) {
                    occurrences.put(num, occurrences.getOrDefault(num, 0) + 1);
                }
            }
        }
        return occurrences;
    }

    private static Map<Integer, Integer> findAdjustedFirstOccurrences(int[][] numbers, int totalRows) {
        Map<Integer, Integer> firstOccurrences = new HashMap<>();
        for (int rowIndex = 0; rowIndex < numbers.length; rowIndex++) {
            for (int num : numbers[rowIndex]) {
                if (num >= 1 && num <= 90 && !firstOccurrences.containsKey(num)) {
                    firstOccurrences.put(num, totalRows - (rowIndex + 1));
                }
            }
        }
        return firstOccurrences;
    }

    private static Map<Integer, Double> calculateWeightedAverage(
        Map<Integer, Integer> occurrences,
        Map<Integer, Integer> adjustedFirstOccurrences,
        int totalRows
    ) {
        Map<Integer, Double> weightedAverages = new HashMap<>();

        // A maximális értékek
        int maxOccurrences = occurrences.values().stream().max(Integer::compare).orElse(1);
        int maxAdjustedFirst = totalRows;

        for (int num = 1; num <= 90; num++) {
            int occurrenceValue = occurrences.getOrDefault(num, 0);
            int firstOccurrenceValue = adjustedFirstOccurrences.getOrDefault(num, 0);

            // Súlyozott átlag számítása
            double weightedAverage = ((double) occurrenceValue / maxOccurrences) * 0.5
                                    + ((double) firstOccurrenceValue / maxAdjustedFirst) * 0.5;

            weightedAverages.put(num, weightedAverage);
        }

        return weightedAverages;
    }
}

