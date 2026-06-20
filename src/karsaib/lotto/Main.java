package karsaib.lotto;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/*
 * Lotto statistical generator
 * Author: Barna Karsai
 */
public class Main {

    // Source URL of history data
    private static final String URL_OTOS = "https://bet.szerencsejatek.hu/cmsfiles/otos.csv";
    private static final String URL_HATOS = "https://bet.szerencsejatek.hu/cmsfiles/hatos.csv";
    private static final String URL_SKANDI = "https://bet.szerencsejatek.hu/cmsfiles/skandi.csv";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Use: java -jar lotto-1.jar [5|6|7]");
            System.out.println("  - 5: 5/90 lottery (from URL)");
            System.out.println("  - 6: 6/45 lottery (from URL)");
            System.out.println("  - 7: Skandi lottery (from URL)");
            return;
        }

        String mode = args[0];

        try {
            String url;
            if (mode.equals("5")) {
                url = URL_OTOS;
                // 5/90
                processUrl(url, 90, 5);
            } else if (mode.equals("6")) {
                url = URL_HATOS;
                // 6 lottery 1..45, 6 szám soronként
                processUrl(url, 45, 6);
            } else if (mode.equals("7")) {
                url = URL_SKANDI;
                // 7 (Skandi) mode: 1..35, 14 szám numbers in row
                processUrl(url, 35, 14);
            } else {
                System.out.println("Wrong mode.");
                System.out.println("Use'5' (1..90, 5 numbers in row), "
                        + "'6' (1..45, 6 numbers in row), "
                        + "vagy '7' (1..35, 14 numbers in row).");
            }
        } catch (IOException e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Process data from URL
    // -------------------------------------------------------------------------
    private static void processUrl(String urlString, int maxNumber, int numbersPerRow) throws IOException {
        System.out.println("Downloading data from: " + urlString);
        String csvContent = downloadCsv(urlString);
        
        // Convert CSV content to 2D array
        int[][] numbers = parseCsvContent(csvContent, maxNumber, numbersPerRow);
        int totalRows = numbers.length;
        
        System.out.println("Total rows processed: " + totalRows);

        Map<Integer, Integer> occurrences = countOccurrences(numbers, maxNumber);
        GapStats gapStats = computeGapStats(numbers);

        // Last draw
        Map<Integer, Integer> latestRowMap = new HashMap<>();
        for (int row = 0; row < numbers.length; row++) {
            for (int col = 0; col < numbers[row].length; col++) {
                int value = numbers[row][col];
                if (!latestRowMap.containsKey(value)) {
                    latestRowMap.put(value, row);// 0 = legfrissebb húzás
                }
            }
        }

        // Since last draw
        Map<Integer, Integer> sinceLastMap = new HashMap<>();
        for (int num : occurrences.keySet()) {
            Integer idx = latestRowMap.get(num);
            int sinceLast = (idx == null) ? totalRows : idx;
            sinceLastMap.put(num, sinceLast);
        }
        
        // cycleFactor = sinceLast / avgGap
        Map<Integer, Double> cycleFactorMap = new HashMap<>();

        for (int num : occurrences.keySet()) {
            int sinceLast = sinceLastMap.getOrDefault(num, 0);
            double avgGap = gapStats.avgGap.getOrDefault(num, (double) totalRows);
            double cycleFactor = (avgGap <= 0.0) ? 0.0 : sinceLast / avgGap;
            cycleFactorMap.put(num, cycleFactor);
        }

        // New scope: occurrences + maxGap + sinceLast
        Map<Integer, Double> scores = calculateWeightedScore(
            occurrences,
            gapStats.maxGap,
            sinceLastMap,
            cycleFactorMap
        );

        System.out.println("Finished, report is created (result.html)..");

        writeToHtml(scores, occurrences, numbers, totalRows, gapStats.maxGap, gapStats.avgGap, sinceLastMap, "result.html");
    }

    // -------------------------------------------------------------------------
    // Download CSV from URL
    // -------------------------------------------------------------------------
    private static String downloadCsv(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download CSV: HTTP " + responseCode);
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        connection.disconnect();
        return content.toString();
    }

    // -------------------------------------------------------------------------
    // Parse CSV content (same logic as readCsvTo2DArray but from String)
    // -------------------------------------------------------------------------
    private static int[][] parseCsvContent(String csvContent, int maxNumber, int numbersPerRow) throws IOException {
        List<int[]> rows = new ArrayList<>();
        String[] lines = csvContent.split("\n");
        boolean isFirstLine = true;

        for (String line : lines) {
            line = line.replace("\uFEFF", "").trim();
            if (line.isEmpty()) continue;

            if (line.endsWith(",")) line = line.substring(0, line.length() - 1).trim();

            // Separator: ';' vagy ','
            String[] parts;
            if (line.contains(";")) {
                parts = line.split(";");
            } else if (line.contains(",")) {
                parts = line.split(",");
            } else {
                parts = new String[]{line};
            }

            // Skip header row
            if (isFirstLine && !isNumeric(parts)) {
                isFirstLine = false;
                continue;
            }
            isFirstLine = false;

            // Lottery 6: numbers in last coloums
            if (maxNumber == 45 && numbersPerRow == 6) {
                List<String> numbers = new ArrayList<>();
                // A számok a sor végén vannak (utolsó 6 oszlop)
                for (int i = parts.length - 6; i < parts.length && i >= 0; i++) {
                    String s = parts[i].trim();
                    if (!s.isEmpty()) {
                        try {
                            int v = Integer.parseInt(s);
                            if (v >= 1 && v <= maxNumber) {
                                numbers.add(s);
                            }
                        } catch (NumberFormatException ignore) { }
                    }
                }
                if (numbers.size() == numbersPerRow) {
                    parts = numbers.toArray(new String[0]);
                } else {
                    // Unhappy path: try collect in other way
                    numbers.clear();
                    for (String s : parts) {
                        s = s.trim();
                        if (!s.isEmpty()) {
                            try {
                                int v = Integer.parseInt(s);
                                if (v >= 1 && v <= maxNumber && numbers.size() < numbersPerRow) {
                                    numbers.add(s);
                                }
                            } catch (NumberFormatException ignore) { }
                        }
                    }
                   
                }
            }

            // 5/90 export – numbers are in last 5 coloums
            if (maxNumber == 90 && numbersPerRow == 5) {
                List<String> numbers = new ArrayList<>();
                for (int i = parts.length - 5; i < parts.length && i >= 0; i++) {
                    String s = parts[i].trim();
                    if (!s.isEmpty()) {
                        try {
                            int v = Integer.parseInt(s);
                            if (v >= 1 && v <= maxNumber) {
                                numbers.add(s);
                            }
                        } catch (NumberFormatException ignore) { }
                    }
                }
                if (numbers.size() == numbersPerRow) {
                    parts = numbers.toArray(new String[0]);
                } else {
                    // Unhapp path solution 
                    numbers.clear();
                    for (String s : parts) {
                        s = s.trim();
                        if (!s.isEmpty()) {
                            try {
                                int v = Integer.parseInt(s);
                                if (v >= 1 && v <= maxNumber && numbers.size() < numbersPerRow) {
                                    numbers.add(s);
                                }
                            } catch (NumberFormatException ignore) { }
                        }
                    }
                    if (numbers.size() == numbersPerRow) {
                        parts = numbers.toArray(new String[0]);
                    } else {
                        continue;
                    }
                }
            }

            // 7-es (Skandi): numbers are last 14 coloums
            if (maxNumber == 35 && numbersPerRow == 14) {
                List<String> numbers = new ArrayList<>();
                for (int i = parts.length - 14; i < parts.length && i >= 0; i++) {
                    String s = parts[i].trim();
                    if (!s.isEmpty()) {
                        try {
                            int v = Integer.parseInt(s);
                            if (v >= 1 && v <= maxNumber) {
                                numbers.add(s);
                            }
                        } catch (NumberFormatException ignore) { }
                    }
                }
                if (numbers.size() == numbersPerRow) {
                    parts = numbers.toArray(new String[0]);
                } else {
                    // Ha nem sikerült, próbáljuk a teljes sorból
                    numbers.clear();
                    for (String s : parts) {
                        s = s.trim();
                        if (!s.isEmpty()) {
                            try {
                                int v = Integer.parseInt(s);
                                if (v >= 1 && v <= maxNumber && numbers.size() < numbersPerRow) {
                                    numbers.add(s);
                                }
                            } catch (NumberFormatException ignore) { }
                        }
                    }
                    if (numbers.size() == numbersPerRow) {
                        parts = numbers.toArray(new String[0]);
                    } else {
                        continue;
                    }
                }
            }

            // Általános eset: csak számokat gyűjtünk
            List<String> tokens = new ArrayList<>();
            for (String s : parts) {
                s = s.trim();
                if (!s.isEmpty()) {
                    try {
                        int v = Integer.parseInt(s);
                        if (v >= 1 && v <= maxNumber && tokens.size() < numbersPerRow) {
                            tokens.add(s);
                        }
                    } catch (NumberFormatException ignore) { }
                }
            }

            if (tokens.size() != numbersPerRow) {
                continue;
            }

            int[] row = new int[numbersPerRow];
            for (int i = 0; i < numbersPerRow; i++) {
                row[i] = Integer.parseInt(tokens.get(i));
            }

            rows.add(row);
        }

        if (rows.isEmpty()) {
            throw new IOException("Error.CSV format issue");
        }

        return rows.toArray(new int[0][0]);
    }

    // -------------------------------------------------------------------------
    // Checking of numeric format
    // -------------------------------------------------------------------------
    private static boolean isNumeric(String[] parts) {
        int numberCount = 0;
        for (String s : parts) {
            s = s.trim();
            if (!s.isEmpty()) {
                try {
                    Integer.parseInt(s);
                    numberCount++;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
      
        return numberCount >= 5;
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
            int rowNumber = rowIndex + 1;
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
    // HTML riport
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
            writer.write("<title>Lottó statistics</title>");

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

            writer.write("<h1>Lottó statistics</h1>");
            writer.write("<p>Total draws: " + totalRows + "</p>");

            if (numbers.length > 0 && numbers[0].length >= 5) {
                writer.write("<h2>Last draw:</h2><p>");
                for (int i = 0; i < Math.min(6, numbers[0].length); i++) {
                    writer.write(numbers[0][i] + " ");
                }
                writer.write("</p>");
            }

            // sinceLast range
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

            // dinamikus maxGap range
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

            // occurrences range
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
                    + "<th>Átlag kihagyás</th>"
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
                        double avgGapVal = avgGaps.getOrDefault(num, 0.0);

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

                        
                        String occClass;
                        if (occRangeFinal <= 0) occClass = "occ3";
                        else if (occ <= o1) occClass = "occ4";
                        else if (occ <= o2) occClass = "occ3";
                        else if (occ <= o3) occClass = "occ2";
                        else occClass = "occ1";

                        try {
                            writer.write(String.format(
                                    Locale.US,
                                    "<tr><td>%d</td><td class='%s'>%d</td><td class='%s'>%d</td><td class='%s'>%d</td><td>%.2f</td><td>%.4f</td></tr>",
                                    num,
                                    occClass, occ,
                                    sinceClass, sinceLast,
                                    gapClass, maxGapVal,
                                    avgGapVal,
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
    // Occurance
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
    // New score (occurrences + maxGap + sinceLast)
    // -------------------------------------------------------------------------
    private static Map<Integer, Double> calculateWeightedScore(
        Map<Integer, Integer> occurrences,
        Map<Integer, Integer> maxGaps,
        Map<Integer, Integer> sinceLastMap,
        Map<Integer, Double> cycleFactorMap
    ) {
        Map<Integer, Double> scores = new HashMap<>();

        int maxOcc = occurrences.values().stream().max(Integer::compare).orElse(1);
        int maxGap = maxGaps.values().stream().max(Integer::compare).orElse(1);
        int maxSince = sinceLastMap.values().stream().max(Integer::compare).orElse(1);
        double maxCycle = cycleFactorMap.values()
            .stream()
            .max(Double::compare)
            .orElse(1.0);

        for (int num : occurrences.keySet()) {
            int occ = occurrences.getOrDefault(num, 0);
            int gap = maxGaps.getOrDefault(num, 0);
            int since = sinceLastMap.getOrDefault(num, 0);

            double normOcc = (double) occ / maxOcc;
            double normGap = (double) gap / maxGap;
            double normSince = (double) since / maxSince;
            double cycle = cycleFactorMap.getOrDefault(num, 0.0);
            double normCycle = cycle / maxCycle;

            double score = normOcc * 0.25 + normGap * 0.20 + normSince * 0.25 + normCycle * 0.30;
            scores.put(num, score);
        }

        return scores;
    }
}
