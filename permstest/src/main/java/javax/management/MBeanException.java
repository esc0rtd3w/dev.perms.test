package javax.management;

/** Minimal Android compatibility class for libraries that reference JMX exceptions. */
public class MBeanException extends JMException {
    private final Exception targetException;

    public MBeanException(Exception exception) {
        super(exception == null ? null : exception.toString());
        this.targetException = exception;
    }

    public MBeanException(Exception exception, String message) {
        super(message);
        this.targetException = exception;
    }

    public Exception getTargetException() {
        return targetException;
    }
}
