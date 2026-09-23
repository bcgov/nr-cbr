package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.exception.SiteValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a refused site into a 400 the form can render field by field.
 *
 * <p>An advice rather than a {@code ResponseStatusException}, which is how every other error in this
 * application is raised: those carry a status and a sentence and nothing else, and a sentence is not
 * enough here. CBR shows every validation message beside the box it concerns, so the response has to
 * say which box — hence the extra {@code fieldErrors} property, a map of request field name to
 * message.
 *
 * <p>{@code detail} still carries a human sentence for anything that does not read the map: a
 * browser devtools pane, a curl, a log line.
 */
@RestControllerAdvice
public class SiteValidationAdvice {

  @ExceptionHandler(SiteValidationException.class)
  ProblemDetail handle(SiteValidationException failure) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
    problem.setTitle("Site cannot be saved");
    problem.setProperty("fieldErrors", failure.getFieldErrors());
    return problem;
  }
}
