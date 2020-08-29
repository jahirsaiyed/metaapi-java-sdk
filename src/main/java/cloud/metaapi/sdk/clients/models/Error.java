package cloud.metaapi.sdk.clients.models;

import java.util.List;
import java.util.Optional;

import cloud.metaapi.sdk.clients.errorHandler.ValidationException;

/**
 * Contains an error message
 */
public class Error {
    /**
     * Error id
     */
    public int id;
    /**
     * Error name
     */
    public String error;
    /**
     * Error description
     */
    public String message;
    /**
     * Additional information about error. Used to supply validation error details
     */
    public Optional<List<ValidationException.ErrorDetail>> details = Optional.empty();
}
