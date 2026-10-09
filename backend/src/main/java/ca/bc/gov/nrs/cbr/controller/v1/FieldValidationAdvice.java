package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a refused record into a 400 a form can render field by field — the same shape
 * {@link SiteValidationAdvice} gives a site: {@code detail} for a human, {@code fieldErrors} for
 * the form.
 */
@RestControllerAdvice
public class FieldValidationAdvice {

  @ExceptionHandler(FieldValidationException.class)
  ProblemDetail handle(FieldValidationException failure) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
    problem.setTitle(failure.getTitle());
    problem.setProperty("fieldErrors", failure.getFieldErrors());
    return problem;
  }
}
