package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.BulletinApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.BulletinService;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinPreview;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinRequest;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The bulletin. Mappings and authorization are declared on {@link BulletinApiEndpoint}. */
@RestController
public class BulletinApiController implements BulletinApiEndpoint {

  private final BulletinService bulletins;

  public BulletinApiController(BulletinService bulletins) {
    this.bulletins = bulletins;
  }

  @Override
  public ResponseEntity<BulletinResponse> bulletin() {
    return ResponseEntity.ok(bulletins.current());
  }

  @Override
  public ResponseEntity<BulletinResponse> saveBulletin(BulletinRequest request) {
    return ResponseEntity.ok(bulletins.save(request));
  }

  @Override
  public ResponseEntity<BulletinPreview> previewBulletin(BulletinRequest request) {
    return ResponseEntity.ok(bulletins.preview(request));
  }
}
