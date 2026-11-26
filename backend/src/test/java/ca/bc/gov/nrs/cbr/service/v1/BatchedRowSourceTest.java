package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.design.JRDesignField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link BatchedRowSource}: rows loaded a batch at a time, as Jasper reaches them. */
class BatchedRowSourceTest {

  private static JRField field(String name) {
    JRDesignField field = new JRDesignField();
    field.setName(name);
    return field;
  }

  @Test
  @DisplayName("loads each batch only when the rows before it are used up, in order")
  void loadsAsRead() {
    List<List<Integer>> loaded = new ArrayList<>();
    BatchedRowSource<Integer> source = new BatchedRowSource<>(List.of(1, 2, 3, 4, 5), 2, batch -> {
      loaded.add(batch);
      // Two rows for key 2, none for key 3: a batch's rows need not be one per key.
      return batch.stream()
          .flatMap(key -> key == 3 ? java.util.stream.Stream.<Map<String, Object>>empty()
              : key == 2 ? java.util.stream.Stream.of(Map.of("K", (Object) 2), Map.of("K", 22))
              : java.util.stream.Stream.of(Map.<String, Object>of("K", key)))
          .toList();
    });

    assertThat(loaded).isEmpty();
    List<Object> read = new ArrayList<>();
    while (source.next()) {
      read.add(source.getFieldValue(field("K")));
      if (read.size() == 1) {
        assertThat(loaded).containsExactly(List.of(1, 2));
      }
    }

    assertThat(read).containsExactly(1, 2, 22, 4, 5);
    assertThat(loaded).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
    assertThat(source.count()).isEqualTo(5);
  }

  @Test
  @DisplayName("has no rows for no keys, and loads nothing")
  void empty() {
    BatchedRowSource<Long> source = new BatchedRowSource<>(List.of(), 1000, batch -> {
      throw new AssertionError("nothing to load");
    });

    assertThat(source.next()).isFalse();
    assertThat(source.count()).isZero();
  }
}
