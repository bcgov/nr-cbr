package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinPreview;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinRequest;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The bulletin — legacy's welcome-page message and its Administration → Bulletin Message screen.
 * Implemented by {@link ca.bc.gov.nrs.cbr.controller.v1.BulletinApiController}.
 */
@RequestMapping("/api/v1/bulletin")
public interface BulletinApiEndpoint {

  /**
   * The bulletin, its message HTML kept to a safe set of tags; an empty message when there is none.
   *
   * <p>Gated on {@link CbrAuthorities#READ}: it is shown on the Dashboard, where users land after
   * sign-in — as legacy showed it on its welcome page, after sign-in.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping
  ResponseEntity<BulletinResponse> bulletin();

  /**
   * Replaces the message, made safe, recording who and when.
   *
   * <p>Gated on {@link CbrAuthorities#ADMIN} — legacy's {@code /showBulletinAdmin}, the
   * {@code ADMINISTRATOR} role's. A 400 with {@code fieldErrors.message} when it is empty or longer
   * than the column holds.
   */
  @PreAuthorize(CbrAuthorities.ADMIN)
  @PutMapping
  ResponseEntity<BulletinResponse> saveBulletin(@RequestBody BulletinRequest request);

  /**
   * A draft as {@link #saveBulletin} would keep it — made safe by the same rules — for the
   * editor's live preview. Stores nothing, and checks nothing: the editor shows its own errors.
   *
   * <p>Gated on {@link CbrAuthorities#ADMIN}, as saving is: the editor's alone.
   */
  @PreAuthorize(CbrAuthorities.ADMIN)
  @PostMapping("/preview")
  ResponseEntity<BulletinPreview> previewBulletin(@RequestBody BulletinRequest request);
}
