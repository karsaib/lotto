package karsaib.lotto;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/*
 * Lotto statistical generator
 * Author: Barna Karsai
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Use: java -jar lotto-1.jar [9|6|7] filenev.csv");
            return;
        }

        String mode = args[0];
        String fileName = args[1];

        try {
            if (mode.equals("9")) {
                // 5/90
                processFile(fileName, 90, 5);
            } else if (mode.equals("6")) {
                // 6-os lottó: 1..45, 6 szám soronként
                processFile(fileName, 45, 6);
            } else if (mode.equals("7")) {
                // 7-es (Skandi) mód: 1..35, 14 szám soronként
                processFile(fileName, 35, 14);
            } else {
                System.out.println("Érvénytelen mód.");
                System.out.println("Használj '9' (1..90, 5 szám soronként), "
                        + "'6' (1..45, 6 szám soronként), "
                        + "vagy '7' (1..35, 14 szám soronként).");
            }
        } catch (IOException e) {
            System.out.println("Hiba történt: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Fő feldolgozó
    // -------------------------------------------------------------------------
    private static void processFile(String fileName, int maxNumber, int numbersPerRow) throws IOException {
        int[][] numbers = readCsvTo2DArray(fileName, maxNumber, numbersPerRow);
        int totalRows = numbers.length;

        Map<Integer, Integer> occurrences = countOccurrences(numbers, maxNumber);
        GapStats gapStats = computeGapStats(numbers);

        // Legutóbbi (legfelső) előfordulás – első sor = legújabb húzás
        Map<Integer, Integer> latestRowMap = new HashMap<>();
        for (int row = 0; row < numbers.length; row++) {
            for (int col = 0; col < numbers[row].length; col++) {
                int value = numbers[row][col];
                if (!latestRowMap.containsKey(value)) {
                    latestRowMap.put(value, row); // 0 = legfrissebb húzás
                }
            }
        }

        // "Húzások az utolsó óta" (sinceLast)
        Map<Integer, Integer> sinceLastMap = new HashMap<>();
        for (int num : occurrences.keySet()) {
            Integer idx = latestRowMap.get(num);
            int sinceLast = (idx == null) ? totalRows : idx;
            sinceLastMap.put(num, sinceLast);
        }

        // Új score: occurrences + maxGap + sinceLast alapján
        Map<Integer, Double> scores = calculateWeightedScore(occurrences, gapStats.maxGap, sinceLastMap);

        System.out.println("Finished, report is created (result.html)..");

        writeToHtml(scores, occurrences, numbers, totalRows, gapStats.maxGap, gapStats.avgGap, sinceLastMap, "result.html");
    }

    // -------------------------------------------------------------------------
    // Átlagos és max kihagyás (gap) statok
    // -------------------------------------------------------------------------
    private static class GapStats {
        Map<Integer, Integer> maxGap = new HashMap<>();
        Map<Integer, Double> avgGap = new HashMap<>();
    }

    private static GapStats computeGapStats(int[][] numbers) {
        GapStats result = new GapStats();

        Map<Integer, Integer> lastSeen = new HashMap<>();
        Map<Integer, Integer> maxGap = new HashMap<>();
        Map<Integer, Integer> gapSum = new HashMap<>();
        Map<Integer, Integer> gapCount = new HashMap<>();

        for (int rowIndex = 0; rowIndex < numbers.length; rowIndex++) {
            int rowNumber = rowIndex + 1; // 1-alapú
            for (int num : numbers[rowIndex]) {
                if (lastSeen.containsKey(num)) {
                    int gap = rowNumber - lastSeen.get(num);

                    int currentMax = maxGap.getOrDefault(num, 0);
                    if (gap > currentMax) {
                        maxGap.put(num, gap);
                    }

                    gapSum.put(num, gapSum.getOrDefault(num, 0) + gap);
                    gapCount.put(num, gapCount.getOrDefault(num, 0) + 1);
                }
                lastSeen.put(num, rowNumber);
            }
        }

        Map<Integer, Double> avgGap = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : gapSum.entrySet()) {
            int num = e.getKey();
            int sum = e.getValue();
            int count = gapCount.getOrDefault(num, 1);
            avgGap.put(num, (double) sum / count);
        }

        result.maxGap = maxGap;
        result.avgGap = avgGap;
        return result;
    }

    // -------------------------------------------------------------------------
    // HTML riport színezéssel
    // -------------------------------------------------------------------------
    public static void writeToHtml(Map<Integer, Double> scores,
                                   Map<Integer, Integer> occurrences,
                                   int[][] numbers,
                                   int totalRows,
                                   Map<Integer, Integer> maxGaps,
                                   Map<Integer, Double> avgGaps,
                                   Map<Integer, Integer> sinceLastMap,
                                   String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("<html><head>");
            writer.write("<meta charset=\"UTF-8\">");
            writer.write("<title>Lottó statisztika</title>");

            writer.write("<style>");
            writer.write("body { font-family: Arial, sans-serif; }");
            writer.write("table { border-collapse: collapse; margin-top: 16px; }");
            writer.write("th, td { border: 1px solid #ccc; padding: 4px 8px; text-align: right; }");
            writer.write("th { background-color: #f0f0f0; }");
            writer.write("td:first-child, th:first-child { text-align: center; }");

            // Kék skála – sinceLast
            writer.write(".since1 { background-color: #e3f2fd; }");
            writer.write(".since2 { background-color: #bbdefb; }");
            writer.write(".since3 { background-color: #90caf9; }");
            writer.write(".since4 { background-color: #42a5f5; }");

            // Kék skála – maxGap
            writer.write(".gap1 { background-color: #e3f2fd; }");
            writer.write(".gap2 { background-color: #bbdefb; }");
            writer.write(".gap3 { background-color: #90caf9; }");
            writer.write(".gap4 { background-color: #42a5f5; }");

            // Kék skála – Darab (fordított: kevés darab = sötét)
            writer.write(".occ1 { background-color: #e3f2fd; }"); // legtöbb -> világos
            writer.write(".occ2 { background-color: #bbdefb; }");
            writer.write(".occ3 { background-color: #90caf9; }");
            writer.write(".occ4 { background-color: #42a5f5; }"); // legkevesebb -> sötét

            writer.write("</style>");
            writer.write("</head><body>");

            writer.write("<h1>Lottó statisztika</h1>");

            if (numbers.length > 0 && numbers[0].length >= 5) {
                writer.write("<h2>Első sor első 5 száma (legfrissebb húzás):</h2><p>");
                for (int i = 0; i < 5; i++) {
                    writer.write(numbers[0][i] + " ");
                }
                writer.write("</p>");
            }

            // dinamikus sinceLast határok
            int minSince = sinceLastMap.values().stream().min(Integer::compare).orElse(0);
            int maxSince = sinceLastMap.values().stream().max(Integer::compare).orElse(0);
            int sinceRange = maxSince - minSince;

            final int sinceRangeFinal = sinceRange;
            final double s1, s2, s3;
            if (sinceRangeFinal <= 0) {
                s1 = s2 = s3 = maxSince;
            } else {
                double step = sinceRangeFinal / 4.0;
                s1 = minSince + step;
                s2 = minSince + 2 * step;
                s3 = minSince + 3 * step;
            }

            // dinamikus maxGap határok
            int minGap = maxGaps.values().stream().min(Integer::compare).orElse(0);
            int maxGap = maxGaps.values().stream().max(Integer::compare).orElse(0);
            int gapRange = maxGap - minGap;

            final int gapRangeFinal = gapRange;
            final double t1, t2, t3;
            if (gapRangeFinal <= 0) {
                t1 = t2 = t3 = maxGap;
            } else {
                double step = gapRangeFinal / 4.0;
                t1 = minGap + step;
                t2 = minGap + 2 * step;
                t3 = minGap + 3 * step;
            }

            // dinamikus occurrences határok
            int minOcc = occurrences.values().stream().min(Integer::compare).orElse(0);
            int maxOcc = occurrences.values().stream().max(Integer::compare).orElse(0);
            int occRange = maxOcc - minOcc;

            final int occRangeFinal = occRange;
            final double o1, o2, o3;
            if (occRangeFinal <= 0) {
                o1 = o2 = o3 = maxOcc;
            } else {
                double step = occRangeFinal / 4.0;
                o1 = minOcc + step;
                o2 = minOcc + 2 * step;
                o3 = minOcc + 3 * step;
            }

            writer.write("<h2>Szám-statisztika</h2>");
            writer.write("<table>");
            writer.write("<thead><tr>"
                    + "<th>Szám</th>"
                    + "<th>Darab</th>"
                    + "<th>Húzások az utolsó óta</th>"
                    + "<th>Max kihagyás</th>"
                    + "<th>Súlyozott pontszám</th>"
                    + "</tr></thead>");
            writer.write("<tbody>");

            scores.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .forEach(entry -> {
                        int num = entry.getKey();
                        double score = entry.getValue();

                        int occ = occurrences.getOrDefault(num, 0);
                        int sinceLast = sinceLastMap.getOrDefault(num, 0);
                        int maxGapVal = maxGaps.getOrDefault(num, 0);

                        String sinceClass;
                        if (sinceRangeFinal <= 0) sinceClass = "since4";
                        else if (sinceLast <= s1) sinceClass = "since1";
                        else if (sinceLast <= s2) sinceClass = "since2";
                        else if (sinceLast <= s3) sinceClass = "since3";
                        else sinceClass = "since4";

                        String gapClass;
                        if (gapRangeFinal <= 0) gapClass = "gap4";
                        else if (maxGapVal <= t1) gapClass = "gap1";
                        else if (maxGapVal <= t2) gapClass = "gap2";
                        else if (maxGapVal <= t3) gapClass = "gap3";
                        else gapClass = "gap4";

                        // fordított: kevés darab -> occ4 (sötét)
                        String occClass;
                        if (occRangeFinal <= 0) occClass = "occ3";
                        else if (occ <= o1) occClass = "occ4";
                        else if (occ <= o2) occClass = "occ3";
                        else if (occ <= o3) occClass = "occ2";
                        else occClass = "occ1";

                        try {
                            writer.write(String.format(
                                    Locale.US,
                                    "<tr><td>%d</td><td class='%s'>%d</td><td class='%s'>%d</td><td class='%s'>%d</td><td>%.4f</td></tr>",
                                    num,
                                    occClass, occ,
                                    sinceClass, sinceLast,
                                    gapClass, maxGapVal,
                                    score
                            ));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

            writer.write("</tbody></table>");
            writer.write("</body></html>");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // CSV beolvasás
    // -------------------------------------------------------------------------
    private static int[][] readCsvTo2DArray(String fileName, int maxNumber, int numbersPerRow) throws IOException {
        List<int[]> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(fileName));

        for (String line : lines) {
            line = line.replace("\uFEFF", "").trim();
            if (line.isEmpty()) continue;

            if (line.endsWith(",")) line = line.substring(0, line.length() - 1).trim();

            // Elválasztó: ';' vagy ','
            String[] parts;
            if (line.contains(";")) {
                parts = line.split(";");
            } else if (line.contains(",")) {
                parts = line.split(",");
            } else {
                parts = new String[]{line};
            }

            // 7-es (Skandi): sor VÉGÉRŐL szedjük össze a 14 db 1..35 közti számot
            if (maxNumber == 35 && numbersPerRow == 14) {
                List<String> picked = new ArrayList<>();
                for (int i = parts.length - 1; i >= 0 && picked.size() < numbersPerRow; i--) {
                    String s = parts[i].trim();
                    if (s.isEmpty()) continue;
                    try {
                        int v = Integer.parseInt(s);
                        if (v >= 1 && v <= maxNumber) {
                            picked.add(s);
                        }
                    } catch (NumberFormatException ignore) { }
                }
                if (picked.size() != numbersPerRow) {
                    throw new IOException("7-es lottó: nem találtam " + numbersPerRow
                            + " db 1.." + maxNumber + " közti számot a sor végén: " + line);
                }
                Collections.reverse(picked);
                parts = picked.toArray(new String[0]);
            }

            // 6-os lottó: O..T (14..19) oszlopok, U-tól jobbra eldobjuk
            if (maxNumber == 45 && numbersPerRow == 6) {
                if (parts.length < 20) {
                    throw new IOException("6-os lottó: nincs elegendő oszlop (O..T): " + line);
                }
                parts = new String[] { parts[14], parts[15], parts[16], parts[17], parts[18], parts[19] };
            }

            // 5/90 export – csak a 11..15. oszlopot használjuk, ha hosszú a sor
            if (maxNumber == 90 && numbersPerRow == 5 && parts.length >= 16) {
                parts = new String[] { parts[11], parts[12], parts[13], parts[14], parts[15] };
            }

            // üres elemek kiszűrése
            List<String> tokens = new ArrayList<>();
            for (String s : parts) {
                s = s.trim();
                if (!s.isEmpty()) tokens.add(s);
            }

            if (tokens.size() != numbersPerRow) {
                throw new IOException("A sor nem pontosan " + numbersPerRow + " számot tartalmaz: " + line);
            }

            int[] row = new int[numbersPerRow];
            for (int i = 0; i < numbersPerRow; i++) {
                String token = tokens.get(i);
                try {
                    row[i] = Integer.parseInt(token);
                    if (row[i] < 1 || row[i] > maxNumber) {
                        throw new IOException("Érvénytelen szám található a sorban: '" + row[i] + "' a sor: " + line);
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Érvénytelen szám található a sorban: '" + token + "' a sor: " + line);
                }
            }

            rows.add(row);
        }

        return rows.toArray(new int[0][0]);
    }

    // -------------------------------------------------------------------------
    // Előfordulások számlálása
    // -------------------------------------------------------------------------
    private static Map<Integer, Integer> countOccurrences(int[][] numbers, int maxNumber) {
        Map<Integer, Integer> occurrences = new HashMap<>();
        for (int[] row : numbers) {
            for (int num : row) {
                occurrences.put(num, occurrences.getOrDefault(num, 0) + 1);
            }
        }
        return occurrences;
    }

    // -------------------------------------------------------------------------
    // Új score (occurrences + maxGap + sinceLast)
    // -------------------------------------------------------------------------
    private static Map<Integer, Double> calculateWeightedScore(
            Map<Integer, Integer> occurrences,
            Map<Integer, Integer> maxGaps,
            Map<Integer, Integer> sinceLastMap
    ) {
        Map<Integer, Double> scores = new HashMap<>();

        int maxOcc = occurrences.values().stream().max(Integer::compare).orElse(1);
        int maxGap = maxGaps.values().stream().max(Integer::compare).orElse(1);
        int maxSince = sinceLastMap.values().stream().max(Integer::compare).orElse(1);

        for (int num : occurrences.keySet()) {
            int occ = occurrences.getOrDefault(num, 0);
            int gap = maxGaps.getOrDefault(num, 0);
            int since = sinceLastMap.getOrDefault(num, 0);

            double normOcc = (double) occ / maxOcc;
            double normGap = (double) gap / maxGap;
            double normSince = (double) since / maxSince;

            // mindhárom tényező 1/3 súllyal
            double score = (normOcc + normGap + normSince) / 3.0;
            scores.put(num, score);
        }

        return scores;
    }
}
