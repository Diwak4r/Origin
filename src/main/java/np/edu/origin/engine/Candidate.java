package np.edu.origin.engine;

import java.util.Set;

/**
 * One idea in the form the engine understands: canonical tags, meaningful keywords and a type.
 * Used for the student's idea and for every corpus project / class proposal it is compared with.
 *
 * @param id       database id of the corpus project or proposal (0 for the idea being checked)
 * @param title    display title
 * @param typeId   project type id
 * @param tags     canonical tag names (synonyms already folded)
 * @param keywords meaningful words from title + abstract (stop words removed, synonyms folded)
 */
public record Candidate(int id, String title, int typeId, Set<String> tags, Set<String> keywords) {
}
