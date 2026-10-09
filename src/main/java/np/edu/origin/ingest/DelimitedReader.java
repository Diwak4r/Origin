package np.edu.origin.ingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV files. The separator (comma, pipe, tab or semicolon) is read from the header line,
 * quoted cells may hold separators and line breaks, and a UTF-8 byte order mark is ignored.
 */
public class DelimitedReader implements TableReader {

    private static final char[] SEPARATORS = {',', '|', '\t', ';'};

    @Override
    public List<List<String>> read(Path file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) > 0) text.append(buf, 0, n);
        }
        if (text.length() > 0 && text.charAt(0) == '﻿') text.deleteCharAt(0);
        return parse(text.toString());
    }

    static List<List<String>> parse(String text) {
        char sep = detectSeparator(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                row.add(cell.toString().strip());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                endRow(rows, row, cell);
            } else {
                cell.append(c);
            }
        }
        endRow(rows, row, cell);
        return rows;
    }

    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder cell) {
        row.add(cell.toString().strip());
        cell.setLength(0);
        boolean blank = row.stream().allMatch(String::isEmpty);
        if (!blank) rows.add(new ArrayList<>(row));
        row.clear();
    }

    /** The separator that appears most often in the first line, outside quotes. */
    static char detectSeparator(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        char best = ',';
        int bestCount = 0;
        for (char s : SEPARATORS) {
            int count = 0;
            boolean quoted = false;
            for (char c : first.toCharArray()) {
                if (c == '"') quoted = !quoted;
                else if (c == s && !quoted) count++;
            }
            if (count > bestCount) {
                best = s;
                bestCount = count;
            }
        }
        return best;
    }
}
