package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.model.v1.BulletinEntity;
import ca.bc.gov.nrs.cbr.repository.v1.BulletinRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinPreview;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinRequest;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bulletin: the message users see when they come to the application, which an administrator
 * maintains (legacy's {@code BulletinAction} and {@code BulletinAdminAction}).
 *
 * <p>Legacy stored and printed the message as raw HTML, so an administrator could put a script on
 * every user's welcome page. Here it is HTML kept to {@link Safelist#basic()} — paragraphs, line
 * breaks, bold and italic, lists, links (http, https and mailto, made {@code rel="nofollow"}) — both
 * when it is saved and when it is read, so a row written before, by legacy, is made safe too.
 *
 * <p>As legacy, one row, rewritten in place; the first save of a database with none inserts it.
 * {@code DESCRIPTION} is {@code NOT NULL}, and Oracle stores an empty string as null, so a message
 * is required, as legacy's save — which would have failed on one — effectively required it.
 */
@Service
public class BulletinService {

  /** {@code DESCRIPTION}'s length: {@code VARCHAR2(2000 BYTE)}. */
  static final int MAX_BYTES = 2000;
  static final String FIELD = "message";

  private static final Safelist ALLOWED = Safelist.basic();
  /** Kept as written: no reflowing of the administrator's line breaks and spacing. */
  private static final Document.OutputSettings AS_WRITTEN =
      new Document.OutputSettings().prettyPrint(false);

  private final BulletinRepository bulletins;
  private final LoggedUserHelper loggedUser;

  public BulletinService(BulletinRepository bulletins, LoggedUserHelper loggedUser) {
    this.bulletins = bulletins;
    this.loggedUser = loggedUser;
  }

  /** The bulletin, its message made safe; an empty message when there is none. */
  @Transactional(readOnly = true)
  public BulletinResponse current() {
    return bulletins.findFirstByOrderByEntryTimestampDescBulletinIdDesc()
        .map(BulletinService::response)
        .orElseGet(() -> new BulletinResponse("", null, null));
  }

  /** Saves a new message, made safe, as the signed-in administrator. */
  @Transactional
  public BulletinResponse save(BulletinRequest request) {
    String message = sanitize(request == null ? null : request.message()).strip();
    if (message.isEmpty()) {
      throw invalid("Bulletin Message is required.");
    }
    int bytes = message.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > MAX_BYTES) {
      throw invalid("Bulletin Message cannot exceed " + MAX_BYTES + " characters; it is " + bytes
          + ".");
    }
    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    BulletinEntity bulletin = bulletins.findFirstByOrderByEntryTimestampDescBulletinIdDesc()
        .orElseGet(() -> BulletinEntity.builder().entryUserid(user).entryTimestamp(now).build());
    bulletin.setDescription(message);
    bulletin.setUpdateUserid(user);
    bulletin.setUpdateTimestamp(now);
    return response(bulletins.save(bulletin));
  }

  /**
   * A draft as Save would keep it, for the editor's preview: made safe, nothing stored, nothing
   * checked — the box shows its own errors.
   */
  public BulletinPreview preview(BulletinRequest request) {
    return new BulletinPreview(sanitize(request == null ? null : request.message()).strip());
  }

  /** The message kept to the safe tags; text outside any tag is kept as it is. */
  static String sanitize(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return Jsoup.clean(html, "", ALLOWED, AS_WRITTEN);
  }

  private static BulletinResponse response(BulletinEntity bulletin) {
    return new BulletinResponse(sanitize(bulletin.getDescription()), bulletin.getUpdateUserid(),
        bulletin.getUpdateTimestamp());
  }

  private static FieldValidationException invalid(String message) {
    return new FieldValidationException("The bulletin cannot be saved", Map.of(FIELD, message));
  }
}
