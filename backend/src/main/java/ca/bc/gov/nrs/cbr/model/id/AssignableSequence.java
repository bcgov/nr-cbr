package ca.bc.gov.nrs.cbr.model.id;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

/**
 * An id drawn from the table's own Oracle sequence — {@code <TABLE>_SEQ} in the table's schema,
 * e.g. {@code THE.CROSSING_STRUCTURE_SEQ} — when none is set, and kept when one is.
 *
 * <p>For the tables CBR has long read and only now inserts into — {@code CROSSING_STRUCTURE} and
 * its bridge and culvert rows. A standard {@code @SequenceGenerator} would refuse an entity that
 * arrives with its id already set, which is how existing rows (and the tests' fixtures) are made.
 */
@IdGeneratorType(AssignableSequenceGenerator.class)
@Retention(RUNTIME)
@Target(FIELD)
public @interface AssignableSequence {}
