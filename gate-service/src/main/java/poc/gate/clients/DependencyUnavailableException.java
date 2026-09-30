package poc.gate.clients;

import poc.gate.decision.ReasonCode;

/** A downstream service failed (timeout, error, down). Carries the reason code the gate should report. */
public class DependencyUnavailableException extends RuntimeException {

    private final ReasonCode reason;

    public DependencyUnavailableException(ReasonCode reason, Throwable cause) {
        super(reason + ": " + cause.getClass().getSimpleName(), cause);
        this.reason = reason;
    }

    public ReasonCode reason() {
        return reason;
    }
}
