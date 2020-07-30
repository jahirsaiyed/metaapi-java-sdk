package metaapi.cloudsdk.lib.clients.errorHandler;

/**
 * Base class for API errors. Contains indication of HTTP status.
 */
public class ApiError extends Exception {
    
    private static final long serialVersionUID = 1L;
    public int status;
    public String name;
    private String code;
    private Object[] args; // TODO: Clarify args type

    /**
     * ApiError constructor
     * @param message error message
     * @param status HTTP status
     */
    public ApiError(String message, int status) {
        super(message);
        this.name = this.getClass().getSimpleName();
        this.status = status;
    }

    /**
     * Sets error code, used for i18n
     * @param code error code for i18n
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Returns error code used for i18n
     * @return error code
     */
    public String getCode() {
        return this.code;
    }

    /**
     * Set message arguments for i18n
     * @param args arguments for i18n
     */
    public void setArguments(Object[] args) {
        this.args = args;
    }

    /**
     * Returns message arguments for i18n
     * @return message arguments for i18n
     */
    public Object[] getArguments() {
        return this.args;
    }
}