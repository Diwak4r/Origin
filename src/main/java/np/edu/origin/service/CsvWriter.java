package np.edu.origin.service;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Writes rows as RFC 4180 CSV: fields with commas, quotes or line breaks are quoted. */
public final class CsvWriter {

    private CsvWriter() { }

    public static void write(Writer out, List<String> header, List<Map<String, Object>> rows) throws IOException {
        line(out, header);
        for (Map<String, Object> row : rows) line(out, row.values());
        out.flush();
    }

    private static void line(Writer out, Collection<?> values) throws IOException {
        boolean first = true;
        for (Object v : values) {
            if (!first) out.write(',');
            out.write(escape(v == null ? "" : v.toString()));
            first = false;
        }
        out.write("\r\n");
    }

    static String escape(String s) {
        // A leading = + - @ would run as a formula in Excel; prefix it so it stays plain text.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
