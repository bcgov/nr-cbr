package ca.bc.gov.nrs.cbr.struct.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/** The columns a table is sorted by, as the request sends them. */
class HeaderSortTest {

  @Test
  @DisplayName("pairs each column with the direction in the same place")
  void pairsByPosition() {
    assertThat(HeaderSort.of(List.of(RepairSortColumn.STATUS, RepairSortColumn.NUMBER),
        List.of(Sort.Direction.DESC, Sort.Direction.ASC)))
        .containsExactly(
            new HeaderSort<>(RepairSortColumn.STATUS, Sort.Direction.DESC),
            new HeaderSort<>(RepairSortColumn.NUMBER, Sort.Direction.ASC));
  }

  @Test
  @DisplayName("sorts a column with no direction of its own ascending")
  void missingDirectionAscends() {
    assertThat(HeaderSort.of(List.of(RepairSortColumn.STATUS, RepairSortColumn.NUMBER),
        List.of(Sort.Direction.DESC)))
        .extracting(HeaderSort::direction)
        .containsExactly(Sort.Direction.DESC, Sort.Direction.ASC);
    assertThat(HeaderSort.of(List.of(RepairSortColumn.STATUS), null))
        .extracting(HeaderSort::direction)
        .containsExactly(Sort.Direction.ASC);
  }

  @Test
  @DisplayName("takes a column named twice at its first, and skips a blank one")
  void firstOfARepeatAndNoBlanks() {
    assertThat(HeaderSort.of(
        Arrays.asList(RepairSortColumn.STATUS, null, RepairSortColumn.STATUS),
        List.of(Sort.Direction.DESC, Sort.Direction.ASC, Sort.Direction.ASC)))
        .containsExactly(new HeaderSort<>(RepairSortColumn.STATUS, Sort.Direction.DESC));
  }

  @Test
  @DisplayName("is empty — legacy's order — when no column is sent")
  void noneIsLegacy() {
    assertThat(HeaderSort.<RepairSortColumn>of(null, null)).isEmpty();
    assertThat(HeaderSort.<RepairSortColumn>of(List.of(), List.of(Sort.Direction.DESC))).isEmpty();
  }
}
