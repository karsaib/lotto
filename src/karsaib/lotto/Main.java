package karsaib.lotto;

import java.io.*;
import java.nio.file.*;
import java.util.*;
/*Lotto statistical generator
 * Author Barna Karsai
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Használat: java -jar main.jar [9|6|7] filenev.csv");
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
            } else if (mode.equals("7")) {
                processFile(fileName, 35, 14);
            } else {
                System.out.println("Érvénytelen mód. Használj '9' (1..90, 5 szám soronként), '6' (1..45, 6 szám soronként), vagy '7' (1..45, 14 szám soronként).");
            }
        } catch (IOException e) {
            System.out.println("Hiba történt: " + e.getMessage());
        }
    }
//Read lotto number export from CSV
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
        System.out.println("Finished, report is created..");
        writeToHtml(weightedAverages,numbers,"result.html");
    }
    //Write statistics to Html file
    public static void writeToHtml(Map<Integer, Double> weightedAverages, int[][] numbers, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("<html><head><title>Súlyozott Átlagok</title></head><body>");
            
            // Első sor első 5 számának kiírása
            if (numbers.length > 0 && numbers[0].length >= 5) {
                writer.write("<h2>Első sor első 5 száma:</h2><p>");
                for (int i = 0; i < 5; i++) {
                    writer.write(numbers[0][i] + " ");
                }
                writer.write("</p>");
            }
            
            writer.write("<h2>Súlyozott Átlagok</h2><ul>");
            
            weightedAverages.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> {
                    try {
                        writer.write(String.format("<li>Szám: %d -> Súlyozott átlag: %.2f</li>", 
                                    entry.getKey(), entry.getValue()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            
            writer.write("</ul></body></html>");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
//File content reader
    private static int[][] readCsvTo2DArray(String fileName, int maxNumber, int numbersPerRow) throws IOException {
        List<int[]> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(fileName));

        for (String line : lines) {
            line = line.replace("\uFEFF", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1).trim();
            }

            String[] stringNumbers = line.split(",");
            if (maxNumber == 90 && stringNumbers.length >= 12) {
                stringNumbers = new String[]{stringNumbers[11], stringNumbers[12], stringNumbers[13], stringNumbers[14], stringNumbers[15]};
               
            }
            
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
//Summ of occurence, small value is higher priority
    private static Map<Integer, Integer> countOccurrences(int[][] numbers, int maxNumber) {
        Map<Integer, Integer> occurrences = new HashMap<>();
        for (int[] row : numbers) {
            for (int num : row) {
                occurrences.put(num, occurrences.getOrDefault(num, 0) + 1);
            }
        }
        return occurrences;
    }
//Find the earliest draw, large(older) value is higher priority
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
//Aggregate statistics
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
                                    + ((double) totalRows-firstOccurrenceValue / maxAdjustedFirst) * 0.5;

            weightedAverages.put(num, weightedAverage);
        }

        return weightedAverages;
    }
}
