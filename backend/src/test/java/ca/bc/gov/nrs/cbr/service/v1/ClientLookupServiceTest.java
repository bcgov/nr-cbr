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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    verify(clientLocations).findMaintainerClientsByText(term.capture(), any(Pageable.class));
    return term.getValue();
  }

  private String capturedNumber() {
    ArgumentCaptor<String> number = ArgumentCaptor.forClass(String.class);
    verify(clientLocations).findMaintainerClientsByNumber(number.capture(), any(Pageable.class));
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

      verify(clientLocations, never()).findMaintainerClientsByText(anyString(), any(Pageable.class));
    }
  }

  @Nested
  @DisplayName("an all-digit term")
  class ClientNumber {

    @Test
    @DisplayName("is wrapped in wildcards, so any run of its digits finds it")
    void matchesAFragment() {
      when(clientLocations.findMaintainerClientsByNumber(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("66838", ClientScope.MAINTAINERS);

      assertThat(capturedNumber()).isEqualTo("%66838%");
    }

    @Test
    @DisplayName("is still a fragment when it is already the full width")
    void matchesAWholeNumber() {
      // A whole number contains itself, so one rule covers both. Nothing needs to know whether the
      // user typed all eight digits.
      when(clientLocations.findMaintainerClientsByNumber(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("00066838", ClientScope.MAINTAINERS);

      assertThat(capturedNumber()).isEqualTo("%00066838%");
    }
  }

  @Nested
  @DisplayName("anything else")
  class FreeText {

    @Test
    @DisplayName("is upper-cased and wrapped in wildcards, so the match is case-insensitive")
    void foldsCaseAndWraps() {
      when(clientLocations.findMaintainerClientsByText(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("Canfor", ClientScope.MAINTAINERS);

      assertThat(capturedText()).isEqualTo("%CANFOR%");
    }

    @Test
    @DisplayName("is trimmed, so trailing space does not become part of the match")
    void trimsTheTerm() {
      when(clientLocations.findMaintainerClientsByText(anyString(), any(Pageable.class)))
          .thenReturn(List.of());

      service.suggest("  canfor  ", ClientScope.MAINTAINERS);

      assertThat(capturedText()).isEqualTo("%CANFOR%");
    }

    @Test
    @DisplayName("passes the repository's answer straight back")
    void returnsWhatTheRepositoryFound() {
      ClientLookupResult canfor =
          new ClientLookupResult("00001012", "00", "CANFOR CORPORATION", null, "Vancouver");
      when(clientLocations.findMaintainerClientsByText(anyString(), any(Pageable.class)))
          .thenReturn(List.of(canfor));

      assertThat(service.suggest("canfor", ClientScope.MAINTAINERS)).containsExactly(canfor);
    }

    @Test
    @DisplayName("treats a term with any letter in it as a name, not a number")
    void aMixedTermIsAName() {
      when(clientLocations.findMaintainerClientsByText(anyString(), any(Pageable.class)))
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
      verify(clientLocations, never()).findMaintainerClientsByText(anyString(), any(Pageable.class));
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
          .findRoadFileHolders(isNull(), eq("%66838%"), any(Pageable.class));
    }

    @Test
    @DisplayName("applies the same floor to a term too short to narrow anything")
    void keepsTheMinimumLength() {
      service.suggest("ca", ClientScope.ROAD_FILE_HOLDERS);

      verifyNoInteractions(clientLocations);
    }
  }

  @ParameterizedTest(name = "\"{0}\" asks the database for \"{1}\"")
  @CsvSource({
      "1286,     '%1286%'",
      "0128,     '%0128%'",
      "01286,    '%01286%'",
      "00001286, '%00001286%'",
      "0000,     '%0000%'",
  })
  @DisplayName("matches a client number as a fragment, not as a padded whole")
  void wrapsWhateverWasTyped(String typed, String expected) {
    // From a bug report: for client 00001286, "1286" found it and "0128" did not. Padding had made
    // the first into the whole number by luck and the second into 00000128, a different client.
    // Every string here is a run of digits out of the same number, and a contains finds it from
    // any of them.
    service.suggest(typed, ClientScope.MAINTAINERS);

    assertThat(capturedNumber()).isEqualTo(expected);
  }

  @Test
  @DisplayName("holds a number to the same three characters a name needs")
  void refusesATooShortNumber() {
    // The field has always said "min. 3 characters" and it was true only of names: a lone digit
    // used to pad into a whole client number and search for it. Matched as a fragment it would
    // answer with whatever the cap allowed instead, which is not a suggestion.
    assertThat(service.suggest("12", ClientScope.MAINTAINERS)).isEmpty();

    verify(clientLocations, never()).findMaintainerClientsByNumber(anyString(), any(Pageable.class));
  }
}
