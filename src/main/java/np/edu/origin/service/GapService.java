package np.edu.origin.service;

import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.GapDao;
import np.edu.origin.dao.IdeaDao;
import np.edu.origin.dao.ProposalDao;
import np.edu.origin.model.GapCell;
import np.edu.origin.model.ProjectRow;
import np.edu.origin.model.ProposalView;
import np.edu.origin.model.Suggestion;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The gap map: which domain and project-type combinations are crowded and which are untouched. */
public class GapService {

    /** One row of the map: a domain and its cells, one per project type. */
    public static class Row {
        private final String domainName;
        private final String shortName;
        private final int domainId;
        private final List<GapCell> cells = new ArrayList<>();

        Row(int domainId, String domainName, String shortName) {
            this.domainId = domainId;
            this.domainName = domainName;
            this.shortName = shortName;
        }

        public int getDomainId()       { return domainId; }
        public String getDomainName()  { return domainName; }
        public String getShortName()   { return shortName; }
        public List<GapCell> getCells() { return cells; }

        public int getTotal() {
            return cells.stream().mapToInt(c -> c.getProjectCount() + c.getLiveCount()).sum();
        }
    }

    /** Everything the page needs for one clicked cell. */
    public record CellDetail(GapCell cell, List<ProjectRow> projects, List<ProposalView> live, List<Suggestion> ideas) {
        public GapCell getCell()             { return cell; }
        public List<ProjectRow> getProjects() { return projects; }
        public List<ProposalView> getLive()   { return live; }
        public List<Suggestion> getIdeas()    { return ideas; }
    }

    private final GapDao gaps;
    private final CorpusDao corpus;
    private final ProposalDao proposals;
    private final IdeaDao ideas;

    public GapService(GapDao gaps, CorpusDao corpus, ProposalDao proposals, IdeaDao ideas) {
        this.gaps = gaps;
        this.corpus = corpus;
        this.proposals = proposals;
        this.ideas = ideas;
    }

    /** Rows sorted so the most crowded domains come first and the open ground collects at the bottom. */
    public List<Row> rows() throws SQLException {
        Map<Integer, Row> byDomain = new LinkedHashMap<>();
        for (GapCell c : gaps.matrix()) {
            byDomain.computeIfAbsent(c.getDomainId(), id -> new Row(id, c.getDomainName(), c.getShortName()))
                    .getCells().add(c);
        }
        List<Row> rows = new ArrayList<>(byDomain.values());
        rows.sort(Comparator.comparingInt(Row::getTotal).reversed().thenComparing(Row::getDomainName));
        return rows;
    }

    public List<GapCell> cells() throws SQLException {
        return gaps.matrix();
    }

    public CellDetail cell(int domainId, int typeId) throws NotFoundException, SQLException {
        GapCell cell = gaps.matrix().stream()
                .filter(c -> c.getDomainId() == domainId && c.getTypeId() == typeId)
                .findFirst().orElseThrow(() -> new NotFoundException("No such cell"));
        List<ProposalView> live = new ArrayList<>();
        for (ProposalView p : proposals.radar(null)) {
            if (p.getDomainName().equals(cell.getDomainName()) && p.getTypeName().equals(cell.getTypeName())) live.add(p);
        }
        return new CellDetail(cell, corpus.inCell(domainId, typeId), live,
                ideas.list(domainId, typeId, null, false, 10));
    }

    /** Headline numbers for the map page. */
    public Map<String, Object> summary() throws SQLException {
        List<GapCell> all = gaps.matrix();
        long open = all.stream().filter(GapCell::isOpen).count();
        GapCell crowded = all.stream().max(Comparator.comparingInt(c -> c.getProjectCount() + c.getLiveCount())).orElse(null);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("cells", all.size());
        s.put("open", open);
        s.put("projects", corpus.count());
        s.put("ideas", ideas.countOpen());
        s.put("crowdedName", crowded == null ? "" : crowded.getDomainName() + " / " + crowded.getTypeName());
        s.put("crowdedCount", crowded == null ? 0 : crowded.getProjectCount() + crowded.getLiveCount());
        return s;
    }
}
