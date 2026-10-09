package np.edu.origin.service;

/** The record does not exist, or the current user is not allowed to see it. Both show the same 404 page. */
public class NotFoundException extends Exception {

    public NotFoundException(String message) {
        super(message);
    }
}
