package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import ca.bc.gov.nrs.cbr.struct.v1.ClientScope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * How a typed term is turned into a query.
 *
 * <p>All of the decisions this service makes are about the <em>shape</em> of the term — is it a
 * number, is it long enough to be worth matching, how wide is the client number — so they are
 * tested against a mocked repository. What the queries then return is
 * {@code ClientLocationRepositoryTest}'s subject.
 */
@ExtendWith(MockitoExtension.class)
class ClientLookupServiceTest {

  @Mock
  private ClientLocationRepository clientLocations;

  @InjectMocks
  private ClientLookupService service;

  private String capturedText() {
    ArgumentCaptor<String> term = ArgumentCaptor.forClass(String.class);
    verify(clientLocations).findMaintainersByText(term.capture(), any(Pageable.class));
    return term.getValue();
  }

  private String capturedNumber() {
    ArgumentCaptor<String> number = ArgumentCaptor.forClass(String.class);
    verify(clientLocations).findMaintainersByClientNumber(number.capture(), any(Pageable.class));
    return number.getValue();
  }

  @Nested
  @DisplayName("a term that cannot match")
  class Unmatchable {

    /**
     * An empty list rather than a 400, and the reason is where this is called from: a keystroke.
     * Rejecting "ca" would raise an error for a word the user is still halfway through typing.
     */
    @Test
    @DisplayName("is answered with an empty list, without going to the database")
    void returnsEmptyWithoutQuerying() {
      assertThat(service.suggest(null, ClientScope.MAINTAINERS)).isEmpty();
      assertThat(service.suggest("", ClientScope.MAINTAINERS)).isEmpty();
      assertThat(service.suggest("   ", ClientScope.MAINTAINERS)).isEmpty();
      assertThat(service.suggest("ca", ClientScope.MAINTAINERS)).isEmpty();
      // Longer than CLIENT_NUMBER's stored width, so it is not a client number, and it has no
      // letters to be a name.
      assertThat(service.suggest("123456789", ClientScope.MAINTAINERS)).isEmpty();

      verifyNoInteractions(clientLocations);
    }

    @Test
    @DisplayName("counts characters after trimming, not before")
    void trimsBeforeMeasuring() {
      assertThat(service.suggest("  ca  ", ClientScope.MAINTAINERS)).isEmpty();

      verify(clientLocations, never()).findMaintainersByText(anyString(), any(Pageable.class));
    }
  }

  @Nested
  @DisplayName("an all-digit term")
  class ClientNumber {

    @Test
    @DisplayName("is zero-padded to the stored width and matched exactly")
    void padsToStoredWidth() {
      when(clientLocations.findMaintainersByClientNumber(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("66838", ClientScope.MAINTAINERS);

      assertThat(capturedNumber()).isEqualTo("00066838");
    }

    /**
     * No minimum length on this arm. A single digit is still an exact key — it asks for one client
     * and can return only that client's locations — where a single letter would match most of the
     * table.
     */
    @Test
    @DisplayName("is matched at any length up to the full width")
    void hasNoMinimumLength() {
      when(clientLocations.findMaintainersByClientNumber(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("5", ClientScope.MAINTAINERS);

      assertThat(capturedNumber()).isEqualTo("00000005");
    }

    @Test
    @DisplayName("is not re-padded when already the full width")
    void leavesAFullWidthNumberAlone() {
      when(clientLocations.findMaintainersByClientNumber(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("00066838", ClientScope.MAINTAINERS);

      assertThat(capturedNumber()).isEqualTo("00066838");
    }
  }

  @Nested
  @DisplayName("anything else")
  class FreeText {

    @Test
    @DisplayName("is upper-cased and wrapped in wildcards, so the match is case-insensitive")
    void foldsCaseAndWraps() {
      when(clientLocations.findMaintainersByText(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("Canfor", ClientScope.MAINTAINERS);

      assertThat(capturedText()).isEqualTo("%CANFOR%");
    }

    @Test
    @DisplayName("is trimmed, so trailing space does not become part of the match")
    void trimsTheTerm() {
      when(clientLocations.findMaintainersByText(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("  canfor  ", ClientScope.MAINTAINERS);

      assertThat(capturedText()).isEqualTo("%CANFOR%");
    }

    @Test
    @DisplayName("passes the repository's answer straight back")
    void returnsWhatTheRepositoryFound() {
      ClientLookupResult canfor =
          new ClientLookupResult("00001012", "00", "CANFOR CORPORATION", null, "Vancouver");
      when(clientLocations.findMaintainersByText(anyString(), any(Pageable.class)))
          .thenReturn(List.of(canfor));

      assertThat(service.suggest("canfor", ClientScope.MAINTAINERS)).containsExactly(canfor);
    }

    @Test
    @DisplayName("treats a term with any letter in it as a name, not a number")
    void aMixedTermIsAName() {
      when(clientLocations.findMaintainersByText(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("123A", ClientScope.MAINTAINERS);

      assertThat(capturedText()).isEqualTo("%123A%");
    }
  }

  @Nested
  @DisplayName("scoped to road-file holders")
  class RoadFileHolders {

    @Test
    @DisplayName("asks the road-file query instead, and never the maintainer one")
    void usesTheOtherSet() {
      // The two populations overlap without either containing the other — a company may hold a
      // road file and maintain no site — and they are reached through different tables, so one
      // query cannot stand in for the other.
      when(clientLocations.findRoadFileHolders(anyString(), isNull(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("canfor", ClientScope.ROAD_FILE_HOLDERS);

      verify(clientLocations).findRoadFileHolders(eq("%CANFOR%"), isNull(), any(Pageable.class));
      verify(clientLocations, never()).findMaintainersByText(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("reads a term the same way either side of the scope")
    void readsTheTermTheSameWay() {
      // All digits is a client number and anything else is a name, in both sets — a user moving
      // between the two screens should not have to learn two rules.
      when(clientLocations.findRoadFileHolders(isNull(), anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("66838", ClientScope.ROAD_FILE_HOLDERS);

      verify(clientLocations)
          .findRoadFileHolders(isNull(), eq("00066838"), any(Pageable.class));
    }

    @Test
    @DisplayName("applies the same floor to a term too short to narrow anything")
    void keepsTheMinimumLength() {
      service.suggest("ca", ClientScope.ROAD_FILE_HOLDERS);

      verifyNoInteractions(clientLocations);
    }
  }
}
