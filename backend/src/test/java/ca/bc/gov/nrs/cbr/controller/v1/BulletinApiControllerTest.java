package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.endpoint.v1.BulletinApiEndpoint;
import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.service.v1.BulletinService;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinPreview;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinRequest;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class BulletinApiControllerTest {

  private final BulletinService service = mock(BulletinService.class);
  private final BulletinApiController controller = new BulletinApiController(service);

  @Test
  @DisplayName("answers with the bulletin, and with it saved")
  void delegates() {
    BulletinResponse bulletin = new BulletinResponse("Hi", null, null);
    BulletinRequest request = new BulletinRequest("Hi");
    when(service.current()).thenReturn(bulletin);
    when(service.save(request)).thenReturn(bulletin);

    assertThat(controller.bulletin().getBody()).isEqualTo(bulletin);
    assertThat(controller.saveBulletin(request).getBody()).isEqualTo(bulletin);
    BulletinPreview preview = new BulletinPreview("Hi");
    when(service.preview(request)).thenReturn(preview);
    assertThat(controller.previewBulletin(request).getBody()).isEqualTo(preview);
  }

  @Test
  @DisplayName("lets anyone who can read see it, and an administrator alone save it")
  void gates() throws Exception {
    assertThat(BulletinApiEndpoint.class.getMethod("bulletin")
        .getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.READ);
    assertThat(BulletinApiEndpoint.class.getMethod("saveBulletin", BulletinRequest.class)
        .getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.ADMIN);
    assertThat(BulletinApiEndpoint.class.getMethod("previewBulletin", BulletinRequest.class)
        .getAnnotation(PreAuthorize.class).value()).isEqualTo(CbrAuthorities.ADMIN);
  }
}
