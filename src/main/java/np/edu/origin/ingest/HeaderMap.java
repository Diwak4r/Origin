package np.edu.origin.ingest;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds which column holds which field by looking at the header names, ignoring case, spaces and
 * punctuation. "Project Name", "project_name" and "PROJECT-NAME" are all the title.
 */
public final class HeaderMap {

    public enum Field {
        TITLE(true, Set.of("title", "projecttitle", "projectname", "name", "project")),
        YEAR(true, Set.of("year", "projectyear", "batch", "session", "submittedyear")),
        ABSTRACT(false, Set.of("abstract", "description", "summary", "problemstatement", "about", "details", "objective")),
        TECH(false, Set.of("tech", "technology", "technologies", "techstack", "tools", "language", "languages")),
        TYPE(false, Set.of("type", "projecttype", "category", "platform", "kind")),
        DOMAIN(false, Set.of("domain", "field", "area", "sector")),
        TAGS(false, Set.of("tags", "tag", "keywords", "keyword", "topics"));

        final boolean required;
        final Set<String> names;

        Field(boolean required, Set<String> names) {
            this.required = required;
            this.names = names;
        }
    }

    private final Map<Field, Integer> columns;

    private HeaderMap(Map<Field, Integer> columns) {
        this.columns = columns;
    }

    /** @throws IOException when a required column (title, year) cannot be found, naming what is accepted */
    public static HeaderMap of(List<String> header) throws IOException {
        Map<Field, Integer> found = new EnumMap<>(Field.class);
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            for (Field f : Field.values()) {
                if (!found.containsKey(f) && f.names.contains(key)) found.put(f, i);
            }
        }
        for (Field f : Field.values()) {
            if (f.required && !found.containsKey(f)) {
                throw new IOException("No " + f.name().toLowerCase(Locale.ROOT) + " column in the header. Accepted names: "
                        + String.join(", ", new java.util.TreeSet<>(f.names)) + ".");
            }
        }
        return new HeaderMap(found);
    }

    /** The cell for this field in the row, or an empty string when the column or the cell is missing. */
    public String get(List<String> row, Field f) {
        Integer i = columns.get(f);
        return (i == null || i >= row.size()) ? "" : row.get(i);
    }
}
