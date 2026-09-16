package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Pins the lookup contract and, above all, that every one of its methods is guarded.
 *
 * <p>The tests below iterate over the declared methods rather than naming them, so a lookup added
 * later without a {@code @PreAuthorize} or without a mapping fails the build. That is the failure
 * worth catching: an unguarded lookup keeps working, the tests keep passing, and a role-less caller
 * quietly receives reference data.
 */
class ConfigurationApiEndpointTest {

  private static Method[] lookups() {
    return ConfigurationApiEndpoint.class.getDeclaredMethods();
  }

  @Test
  @DisplayName("is published under the versioned API path")
  void mappedUnderApiV1() {
    RequestMapping mapping = ConfigurationApiEndpoint.class.getAnnotation(RequestMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/api/v1/configuration");
  }

  @Test
  @DisplayName("every lookup is a GET on its own distinct path")
  void everyLookupIsMapped() {
    assertThat(lookups()).isNotEmpty();

    assertThat(lookups())
        .allSatisfy(method -> assertThat(method.getAnnotation(GetMapping.class))
            .as("%s has no @GetMapping", method.getName())
            .isNotNull());

    // Distinct paths: two @GetMapping values that collide fail at startup, not here, and the
    // message names the bean rather than the duplicated path.
    assertThat(Arrays.stream(lookups())
        .map(method -> method.getAnnotation(GetMapping.class).value()[0])
        .toList())
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("every lookup is gated on READ")
  void everyLookupRequiresRead() {
    assertThat(lookups())
        .allSatisfy(method -> {
          PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
          assertThat(preAuthorize).as("%s has no @PreAuthorize", method.getName()).isNotNull();
          assertThat(preAuthorize.value())
              .as("%s is not gated on READ", method.getName())
              .isEqualTo(CbrAuthorities.READ);
        });
  }

  @Test
  @DisplayName("management areas are always scoped to one forest district")
  void managementAreasRequireADistrict() {
    // The legacy screen shows no management areas until a district is chosen, and CBR has ~40
    // districts; an optional parameter would quietly turn an unscoped request into "every
    // management area in the province" rather than the empty list legacy returns.
    Method method = Arrays.stream(lookups())
        .filter(candidate -> candidate.getName().equals("getManagementAreas"))
        .findFirst()
        .orElseThrow();

    RequestParam param = method.getParameters()[0].getAnnotation(RequestParam.class);

    assertThat(param).isNotNull();
    assertThat(param.name()).isEqualTo("forestDistrictOrgUnitNo");
    assertThat(param.required()).isTrue();
  }
}
