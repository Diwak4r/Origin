package np.edu.origin.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * A real problem from the problem bank. Seeded ones come from the team's field list;
 * student ones are copied in by the trg_check_problem trigger.
 */
public record Problem(int id, int domainId, String statement, String affected, String solution,
                      int primaryTypeId, Integer altTypeId, String source, Set<String> tags,
                      LocalDateTime createdAt) {
}
