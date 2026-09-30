package poc.gate.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import poc.gate.decision.Decision;
import poc.gate.decision.ReasonCode;

/**
 * Applies to every controller (like a FastAPI exception_handler). If the database is down no decision can be
 * recorded, so the gate gets 503 with an explicit MANUAL body; gate_sim also treats any non-200 as MANUAL.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ManualResponse(Decision.Outcome decision, ReasonCode reason) {
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ManualResponse databaseUnavailable(RuntimeException e) {
        log.error("database unavailable: {}", e.getClass().getSimpleName());
        return new ManualResponse(Decision.Outcome.MANUAL, ReasonCode.DB_UNAVAILABLE);
    }
}
