package np.edu.origin.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableReadersTest {

    @TempDir Path dir;

    private Path write(String name, String text) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, text, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void csvHandlesQuotesBomAndBlankLines() throws IOException {
        Path f = write("a.csv", "﻿title,year,abstract\r\n\"Library, Lite\",2021,\"Issues \"\"books\"\"\"\r\n\r\nQuiz App,2022,Quizzes\r\n");
        List<List<String>> t = TableReader.forFile(f).read(f);
        assertEquals(3, t.size());
        assertEquals(List.of("title", "year", "abstract"), t.get(0));
        assertEquals(List.of("Library, Lite", "2021", "Issues \"books\""), t.get(1));
        assertEquals("Quiz App", t.get(2).get(0));
    }

    @Test
    void aPipeSeparatedCsvIsStillRead() throws IOException {
        Path pipe = write("p.csv", "title|year|abstract\nBlood Finder|2023|Finds donors\n");
        assertEquals(List.of("Blood Finder", "2023", "Finds donors"), TableReader.forFile(pipe).read(pipe).get(1));
    }

    @Test
    void nonCsvFormatsAreUnsupported() {
        assertNull(TableReader.forFile(Path.of("old-report.pdf")));
        assertNull(TableReader.forFile(Path.of("photo.png")));
        assertNull(TableReader.forFile(Path.of("notes.txt")));
        assertNull(TableReader.forFile(Path.of("old.xlsx")));
        assertNull(TableReader.forFile(Path.of("table.docx")));
        assertNotNull(TableReader.forFile(Path.of("DATA.CSV")));
    }

    // ---------- header names and row checks ----------

    @Test
    void headerNamesAreMatchedIgnoringCaseAndPunctuation() throws IOException {
        HeaderMap m = HeaderMap.of(List.of("Project Name", "SESSION", "Short_Description", "Tech Stack", "Keywords"));
        List<String> row = List.of("Quiz App", "2022", "Quizzes online", "Java", "quiz, student");
        assertEquals("Quiz App", m.get(row, HeaderMap.Field.TITLE));
        assertEquals("2022", m.get(row, HeaderMap.Field.YEAR));
        assertEquals("", m.get(row, HeaderMap.Field.ABSTRACT));   // "Short_Description" is not an accepted name
        assertEquals("Java", m.get(row, HeaderMap.Field.TECH));
        assertEquals("quiz, student", m.get(row, HeaderMap.Field.TAGS));
    }

    @Test
    void aHeaderWithoutTitleOrYearRejectsTheWholeFile() {
        IOException e = assertThrows(IOException.class, () -> HeaderMap.of(List.of("abstract", "year")));
        assertTrue(e.getMessage().startsWith("No title column"));
        assertThrows(IOException.class, () -> HeaderMap.of(List.of("title", "abstract")));
    }

    @Test
    void parserSkipsBadRowsAndNamesTheLine() throws IOException {
        List<List<String>> table = List.of(
                List.of("title", "year", "abstract"),
                List.of("Good Project", "2021", "Fine"),
                List.of("", "2021", "No title"),
                List.of("No Year Project", "soon", "x"),
                List.of("Batch Style", "Batch 2023-24", "The first year in the cell counts"),
                List.of("Too Old", "1850", "x"));
        ProjectParser.Result r = ProjectParser.parse(table);
        assertEquals(List.of("Good Project", "Batch Style"), r.records().stream().map(ProjectRecord::title).toList());
        assertEquals(2023, r.records().get(1).year());
        assertEquals(3, r.problems().size());
        assertTrue(r.problems().get(0).startsWith("line 3: no title"));
        assertTrue(r.problems().get(1).startsWith("line 4: no valid year"));
        assertTrue(r.problems().get(2).startsWith("line 6: no valid year"));
    }

    @Test
    void anEmptyFileIsRejected() {
        assertThrows(IOException.class, () -> ProjectParser.parse(List.of()));
    }
}
