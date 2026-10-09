package np.edu.origin.service;

import np.edu.origin.dao.CheckDao;
import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.LookupDao;
import np.edu.origin.dao.ProposalDao;
import np.edu.origin.db.Database;
import np.edu.origin.engine.Band;
import np.edu.origin.engine.Breakdown;
import np.edu.origin.engine.Candidate;
import np.edu.origin.engine.SimilarityEngine;
import np.edu.origin.engine.Vocabulary;
import np.edu.origin.model.CheckView;
import np.edu.origin.model.ProposalView;
import np.edu.origin.model.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashSet;
import java.util.List;

/**
 * Class Radar. Each group locks one idea per semester. Locking re-scores the new idea
 * against every other locked idea, and each clash is stored for both groups to see.
 */
public class RadarService {

    private final LookupDao lookups;
    private final CorpusDao corpus;
    private final ProposalDao proposals;
    private final CheckDao checks;
    private final CheckService checkService;
    private final Runnable afterChange;

    /** {@code afterChange} runs after a successful lock; the app uses it to refresh the idea suggestions. */
    public RadarService(LookupDao lookups, CorpusDao corpus, ProposalDao proposals, CheckDao checks, CheckService checkService,
                        Runnable afterChange) {
        this.lookups = lookups;
        this.corpus = corpus;
        this.proposals = proposals;
        this.checks = checks;
        this.checkService = checkService;
        this.afterChange = afterChange;
    }

    public int lock(User user, int checkId) throws ValidationException, NotFoundException, SQLException {
        return lock(user, checkId, false);
    }

    /**
     * First-start seeding only. A real class list can hold a title that repeats a past project, and the
     * supervisor must still see that group on the radar with its DUPLICATE verdict, so seeding locks it.
     * Students on the live site never reach this path and are still refused.
     */
    public int lockForSeed(User user, int checkId) throws ValidationException, NotFoundException, SQLException {
        return lock(user, checkId, true);
    }

    private int lock(User user, int checkId, boolean allowDuplicate)
            throws ValidationException, NotFoundException, SQLException {
        if (user.getGroupCode() == null) {
            throw new ValidationException("Your account has no group code, so there is no group to lock this idea for.");
        }
        CheckView check = checks.find(checkId);
        if (check == null || check.getUserId() != user.getId()) throw new NotFoundException("Check " + checkId + " not found");
        if ("PENDING".equals(check.getEngineStatus())) {
            // A lock always uses the final verdict, so finish the background comparison first.
            checkService.refineQuietly(checkId);
            check = checks.find(checkId);
        }
        if (Band.DUPLICATE.name().equals(check.getVerdict()) && !allowDuplicate) {
            throw new ValidationException("This idea is marked as already done. Rework it and check again before locking.");
        }
        Integer existing = proposals.findForGroup(user.getGroupCode());
        if (existing != null) {
            throw new ValidationException("Group " + user.getGroupCode() + " has already locked an idea this semester.");
        }

        Vocabulary vocab = lookups.vocabulary();
        Candidate mine = new Candidate(0, check.getTitle(), check.getTypeId(), new HashSet<>(check.getTags()),
                vocab.keywords(check.getTitle(), check.getAbstractText()));
        List<Candidate> others = proposals.activeCandidates(vocab, user.getGroupCode());
        SimilarityEngine engine = corpus.index(vocab).engine();

        int proposalId;
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            try {
                proposalId = proposals.insert(con, user.getGroupCode(), lookups.activeSemesterId(), checkId, user.getId());
                for (Candidate other : others) {
                    Breakdown b = engine.compare(mine, other);
                    if (b.ruleScore() >= Band.SIMILAR_FROM) {
                        proposals.insertCollision(con, other.id(), proposalId, b.ruleScore());
                    }
                }
                con.commit();
            } catch (SQLIntegrityConstraintViolationException e) {
                con.rollback();
                // Two members of the same group clicked at the same moment: the UNIQUE key stopped the second one.
                throw new ValidationException("Group " + user.getGroupCode() + " has already locked an idea this semester.");
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
        afterChange.run();
        return proposalId;
    }

    public List<ProposalView> radar() throws SQLException {
        return proposals.radar(null);
    }

    public Integer lockedProposal(User user) throws SQLException {
        return user.getGroupCode() == null ? null : proposals.findForGroup(user.getGroupCode());
    }
}
