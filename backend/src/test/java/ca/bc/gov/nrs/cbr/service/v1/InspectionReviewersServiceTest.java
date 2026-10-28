package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.client.CssApiClient;
import ca.bc.gov.nrs.cbr.client.CssApiClient.CssUser;
import ca.bc.gov.nrs.cbr.security.CbrRoles;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionReviewerResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class InspectionReviewersServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");

  private final CssApiClient css = mock(CssApiClient.class);
  private MutableClock clock;
  private InspectionReviewersService service;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(NOW);
    service = new InspectionReviewersService(css, clock);
    when(css.isConfigured()).thenReturn(true);
  }

  private static CssUser idir(String username, String first, String last) {
    return new CssUser("guid-" + username + "@idir", first, last,
        Map.of("idir_username", List.of(username)));
  }

  @Test
  @DisplayName("lists the role's holders as Last, First, by last name then first, each once")
  void listsTheHolders() {
    when(css.usersWithRole(CbrRoles.INSPECTION_REVIEWER)).thenReturn(List.of(
        idir("ZOE", "Zoe", "adams"),
        idir("JSMITH", "Jane", "Smith"),
        idir("ABAKER", "Al", "Adams"),
        idir("jsmith", "Jane", "Smith")));

    assertThat(service.getInspectionReviewers()).containsExactly(
        new InspectionReviewerResponse("IDIR\\ABAKER", "Adams, Al"),
        new InspectionReviewerResponse("IDIR\\ZOE", "adams, Zoe"),
        new InspectionReviewerResponse("IDIR\\JSMITH", "Smith, Jane"));
  }

  @Test
  @DisplayName("a holder with a name missing shows what there is, else the user ID")
  void displayNames() {
    assertThat(InspectionReviewersService.displayName(idir("A", "Al", null))).isEqualTo("Al");
    assertThat(InspectionReviewersService.displayName(idir("A", " ", "Adams"))).isEqualTo("Adams");
    assertThat(InspectionReviewersService.displayName(idir("A", null, null))).isEqualTo("IDIR\\A");
  }

  @Test
  @DisplayName("a holder CSS gives no identity for is left out")
  void noIdentity() {
    when(css.usersWithRole(CbrRoles.INSPECTION_REVIEWER))
        .thenReturn(List.of(new CssUser(null, "No", "One", null)));

    assertThat(service.getInspectionReviewers()).isEmpty();
  }

  @Test
  @DisplayName("holds the list for five minutes, then asks again")
  void holdsTheList() {
    when(css.usersWithRole(CbrRoles.INSPECTION_REVIEWER))
        .thenReturn(List.of(idir("JSMITH", "Jane", "Smith")));

    service.getInspectionReviewers();
    clock.now = NOW.plus(InspectionReviewersService.FRESH).minusSeconds(1);
    service.getInspectionReviewers();
    verify(css, times(1)).usersWithRole(CbrRoles.INSPECTION_REVIEWER);

    clock.now = NOW.plus(InspectionReviewersService.FRESH);
    service.getInspectionReviewers();
    verify(css, times(2)).usersWithRole(CbrRoles.INSPECTION_REVIEWER);
  }

  @Test
  @DisplayName("when CSS fails, keeps the last list it gave, and asks again next time")
  void keepsTheLastList() {
    when(css.usersWithRole(CbrRoles.INSPECTION_REVIEWER))
        .thenReturn(List.of(idir("JSMITH", "Jane", "Smith")))
        .thenThrow(new ResourceAccessException("down"));

    List<InspectionReviewerResponse> first = service.getInspectionReviewers();
    clock.now = NOW.plus(InspectionReviewersService.FRESH);

    assertThat(service.getInspectionReviewers()).isEqualTo(first).hasSize(1);
    service.getInspectionReviewers();
    verify(css, times(3)).usersWithRole(CbrRoles.INSPECTION_REVIEWER);
  }

  @Test
  @DisplayName("CSS's error is logged on one line, cut short")
  void oneLine() {
    assertThat(InspectionReviewersService.oneLine("{\"message\":\n\"bad\"}\r\n"))
        .isEqualTo("{\"message\": \"bad\"}");
    assertThat(InspectionReviewersService.oneLine("x".repeat(400))).hasSize(300);
    assertThat(InspectionReviewersService.oneLine(null)).isEmpty();
  }

  @Test
  @DisplayName("when CSS fails first time, the list is empty")
  void emptyOnFirstFailure() {
    when(css.usersWithRole(CbrRoles.INSPECTION_REVIEWER))
        .thenThrow(new ResourceAccessException("down"));

    assertThat(service.getInspectionReviewers()).isEmpty();
  }

  @Test
  @DisplayName("unconfigured, the list is empty and CSS is not asked")
  void unconfigured() {
    when(css.isConfigured()).thenReturn(false);

    assertThat(service.getInspectionReviewers()).isEmpty();
    verify(css, never()).usersWithRole(CbrRoles.INSPECTION_REVIEWER);
  }

  /** A clock the test moves. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }
}
