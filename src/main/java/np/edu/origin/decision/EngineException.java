package np.edu.origin.decision;

/** Raised when the decision engine cannot answer: no key, network down, bad reply. Never shown to users. */
public class EngineException extends Exception {

    public EngineException(String message) {
        super(message);
    }

    public EngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
