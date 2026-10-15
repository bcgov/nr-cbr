package ca.bc.gov.nrs.cbr.model.id;

import java.util.Properties;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

/**
 * {@link AssignableSequence}'s generator: Hibernate's sequence generator on the table's own
 * {@code <TABLE>_SEQ}, one value at a time as legacy's {@code NEXTVAL}, leaving an id already set
 * alone.
 *
 * <p>No-argument: Hibernate has Spring create it, so it cannot be handed the annotation.
 */
public class AssignableSequenceGenerator extends SequenceStyleGenerator {

  private static final String SCHEMA_NAME = "THE";

  @Override
  public void configure(Type type, Properties parameters, ServiceRegistry serviceRegistry)
      throws MappingException {
    // Hibernate hands over the bare table name; the sequences, like every CBR table, are THE's.
    String table = parameters.getProperty(TABLE);
    parameters.setProperty(SEQUENCE_PARAM,
        (table.contains(".") ? "" : SCHEMA_NAME + ".") + table + "_SEQ");
    parameters.setProperty(INCREMENT_PARAM, "1");
    super.configure(type, parameters, serviceRegistry);
  }

  @Override
  public boolean allowAssignedIdentifiers() {
    return true;
  }

  /** The id already set, if there is one; otherwise the sequence's next value. */
  @Override
  public Object generate(SharedSessionContractImplementor session, Object owner,
      Object currentValue, EventType eventType) {
    return currentValue != null ? currentValue : super.generate(session, owner);
  }
}
