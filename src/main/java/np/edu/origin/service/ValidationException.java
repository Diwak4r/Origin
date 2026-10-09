package np.edu.origin.service;

import java.util.Map;

/**
 * Thrown when user input breaks a rule. It carries one message per form field,
 * so the page can show each error next to the field it belongs to.
 */
public class ValidationException extends Exception {

    private final Map<String, String> fieldErrors;

    public ValidationException(Map<String, String> fieldErrors) {
        super(String.join(" ", fieldErrors.values()));
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public ValidationException(String message) {
        this(Map.of("form", message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
