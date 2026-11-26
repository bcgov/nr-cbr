package ca.bc.gov.nrs.cbr.service.v1;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRField;

/**
 * A report's rows handed to Jasper a batch at a time, as it fills, rather than built for the whole
 * report first.
 *
 * <p>The Inspections Conducted – Detail report for a province is 19,807 inspections, each with its
 * items, repairs and monitors. Built as one list before filling, that alone outgrew the pod's heap
 * (an {@code OutOfMemoryError} at {@code -Xmx480m}, before Jasper had started). Here a batch is
 * loaded when Jasper reaches it, and dropped when it moves on.
 *
 * <p>Fields are read from each row's map by name, as {@code JRMapCollectionDataSource} reads them.
 * Fill it inside the transaction the loader needs: it loads as Jasper asks.
 *
 * @param <K> the key a batch is loaded by: an inspection's or a structure's id
 */
final class BatchedRowSource<K> implements JRDataSource {

  private final List<K> keys;
  private final int batchSize;
  private final Function<List<K>, ? extends List<? extends Map<String, ?>>> load;

  private int nextBatch;
  private Iterator<? extends Map<String, ?>> rows = Collections.emptyIterator();
  private Map<String, ?> row;
  private int count;

  /**
   * @param keys      the report's keys, in its order
   * @param batchSize keys loaded at once
   * @param load      a batch's rows, in its keys' order
   */
  BatchedRowSource(List<K> keys, int batchSize,
      Function<List<K>, ? extends List<? extends Map<String, ?>>> load) {
    this.keys = keys;
    this.batchSize = batchSize;
    this.load = load;
  }

  @Override
  public boolean next() {
    while (!rows.hasNext()) {
      if (nextBatch >= keys.size()) {
        return false;
      }
      List<K> batch = keys.subList(nextBatch, Math.min(keys.size(), nextBatch + batchSize));
      nextBatch += batchSize;
      rows = load.apply(batch).iterator();
    }
    row = rows.next();
    count++;
    return true;
  }

  @Override
  public Object getFieldValue(JRField field) {
    return row.get(field.getName());
  }

  /** Rows handed to Jasper so far. */
  int count() {
    return count;
  }
}
