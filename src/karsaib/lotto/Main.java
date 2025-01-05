package karsaib.lotto;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Használat: java -jar main.jar [9|6] filenev.csv");
            return;
        }

        String mode = args[0];
        String fileName = args[1];

        try {
            // Kapcsoló alapján működés
            if (mode.equals("9")) {
                processFile(fileName, 90, 5);
            } else if (mode.equals("6")) {
                processFile(fileName, 45, 6);
            } else {
                System.out.println("Érvénytelen mód. Használj '9' (1..90, 5 szám soronként) vagy '6' (1..45, 6 szám soronként).");
            }
        } catch (IOException e) {
            System.out.println("Hiba történt: " + e.getMessage());
        }
    }

    private static void processFile(String fileName, int maxNumber, int numbersPerRow) throws IOException {
        // A fájl tartalmának beolvasása és kétdimenziós tömbbe helyezése
        int[][] numbers = readCsvTo2DArray(fileName, maxNumber, numbersPerRow);

        // Az összes sor száma
        int totalRows = numbers.length;

        // Számosságok keresése
        Map<Integer, Integer> occurrences = countOccurrences(numbers, maxNumber);

        // Első előfordulások keresése és átalakítása
        Map<Integer, Integer> adjustedFirstOccurrences = findAdjustedFirstOccurrences(numbers, totalRows, maxNumber);

        // Súlyozott átlagok számítása
        Map<Integer, Double> weightedAverages = calculateWeightedAverage(occurrences, adjustedFirstOccurrences, totalRows);

        // Eredmények kiírása
        System.out.println("Számok súlyozott átlaga:");
        weightedAverages.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> System.out.printf("Szám: %d -> Súlyozott átlag: %.2f%n", entry.getKey(), entry.getValue()));
    }

    private static int[][] readCsvTo2DArray(String fileName, int maxNumber, int numbersPerRow) throws IOException {
        List<int[]> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(fileName));

        for (String line : lines) {
            // Tisztítás a BOM karakterektől és felesleges szóközöktől
            line = line.replace("\uFEFF", "").trim();

            // Üres sorok kihagyása
            if (line.isEmpty()) {
                continue;
            }

            // Ha van felesleges vessző a sor végén, eltávolítjuk
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1).trim();
            }

            String[] stringNumbers = line.split(",");
            if (stringNumbers.length != numbersPerRow) {
                throw new IOException("A sor nem pontosan " + numbersPerRow + " számot tartalmaz: " + line);
            }

            int[] row = new int[numbersPerRow];
            for (int i = 0; i < stringNumbers.length; i++) {
                try {
                    row[i] = Integer.parseInt(stringNumbers[i].trim());
                    if (row[i] < 1 || row[i] > maxNumber) {
                        throw new IOException("Érvénytelen szám található a sorban: '" + row[i] + "' a sor: " + line);
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Érvénytelen szám található a sorban: '" + stringNumbers[i] + "' a sor: " + line);
                }
            }

            rows.add(row);
        }

        return rows.toArray(new int[0][0]);
    }

    private static Map<Integer, Integer> countOccurrences(int[][] numbers, int maxNumber) {
        Map<Integer, Integer> occurrences = new HashMap<>();
        for (int[] row : numbers) {
            for (int num : row) {
                occurrences.put(num, occurrences.getOrDefault(num, 0) + 1);
            }
        }
        return occurrences;
    }

    private static Map<Integer, Integer> findAdjustedFirstOccurrences(int[][] numbers, int totalRows, int maxNumber) {
        Map<Integer, Integer> firstOccurrences = new HashMap<>();
        for (int rowIndex = 0; rowIndex < numbers.length; rowIndex++) {
            for (int num : numbers[rowIndex]) {
                if (!firstOccurrences.containsKey(num)) {
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

        int maxOccurrences = occurrences.values().stream().max(Integer::compare).orElse(1);
        int maxAdjustedFirst = totalRows;

        for (int num : occurrences.keySet()) {
            int occurrenceValue = occurrences.getOrDefault(num, 0);
            int firstOccurrenceValue = adjustedFirstOccurrences.getOrDefault(num, 0);

            double weightedAverage = ((double) occurrenceValue / maxOccurrences) * 0.5
                                    + ((double) firstOccurrenceValue / maxAdjustedFirst) * 0.5;

            weightedAverages.put(num, weightedAverage);
        }

        return weightedAverages;
    }
}

