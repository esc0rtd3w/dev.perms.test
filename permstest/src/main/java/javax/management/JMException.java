package javax.management;

/** Minimal Android compatibility class for libraries that reference JMX exceptions. */
public class JMException extends Exception {
    public JMException() {
        super();
    }

    public JMException(String message) {
        super(message);
    }
}
