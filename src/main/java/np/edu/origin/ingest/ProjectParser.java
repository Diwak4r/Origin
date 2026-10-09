package np.edu.origin.ingest;

import np.edu.origin.ingest.HeaderMap.Field;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a table into project records. A bad row is skipped and reported with its line number. */
public final class ProjectParser {

    /** @param problems one readable message per skipped row, such as "line 7: no year" */
    public record Result(List<ProjectRecord> records, List<String> problems) { }

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    private static final int MIN_YEAR = 1990;
    private static final int MAX_YEAR = 2100;
    private static final int MAX_TITLE = 160;               // corpus_projects.title is VARCHAR(160)

    private ProjectParser() { }

    public static Result parse(List<List<String>> table) throws IOException {
        if (table.isEmpty()) throw new IOException("The file is empty.");
        HeaderMap map = HeaderMap.of(table.get(0));
        List<ProjectRecord> records = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            int line = i + 1;
            String title = map.get(row, Field.TITLE).strip();
            if (title.length() < 3) {
                problems.add("line " + line + ": no title");
                continue;
            }
            if (title.length() > MAX_TITLE) {
                problems.add("line " + line + ": title is longer than " + MAX_TITLE + " characters");
                continue;
            }
            Matcher m = YEAR.matcher(map.get(row, Field.YEAR));
            int year = m.find() ? Integer.parseInt(m.group()) : 0;
            if (year < MIN_YEAR || year > MAX_YEAR) {
                problems.add("line " + line + ": no valid year for \"" + shorten(title) + "\"");
                continue;
            }
            records.add(new ProjectRecord(line, title, year, map.get(row, Field.ABSTRACT), map.get(row, Field.TECH),
                    map.get(row, Field.TYPE), map.get(row, Field.DOMAIN), map.get(row, Field.TAGS)));
        }
        return new Result(records, problems);
    }

    private static String shorten(String s) {
        return s.length() <= 40 ? s : s.substring(0, 37) + "...";
    }
}
