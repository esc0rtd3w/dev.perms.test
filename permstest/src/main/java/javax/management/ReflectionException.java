package javax.management;

/** Minimal Android compatibility class for libraries that reference JMX exceptions. */
public class ReflectionException extends JMException {
    private final Exception targetException;

    public ReflectionException(Exception exception) {
        super(exception == null ? null : exception.toString());
        this.targetException = exception;
    }

    public ReflectionException(Exception exception, String message) {
        super(message);
        this.targetException = exception;
    }

    public Exception getTargetException() {
        return targetException;
    }
}
