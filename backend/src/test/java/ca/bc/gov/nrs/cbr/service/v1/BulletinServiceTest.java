package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.model.v1.BulletinEntity;
import ca.bc.gov.nrs.cbr.repository.v1.BulletinRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinRequest;
import ca.bc.gov.nrs.cbr.struct.v1.BulletinResponse;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The bulletin, against the database: read, saved in place, and kept to safe HTML. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(BulletinService.class)
class BulletinServiceTest {

  private static final LocalDateTime ENTERED = LocalDateTime.of(2019, 3, 4, 9, 30);

  @Autowired
  private BulletinService service;

  @Autowired
  private BulletinRepository bulletins;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\ADMIN");
    bulletins.deleteAll();
  }

  private BulletinEntity given(String message) {
    BulletinEntity bulletin = bulletins.save(BulletinEntity.builder().description(message)
        .entryUserid("LEGACY").entryTimestamp(ENTERED).updateUserid("LEGACY")
        .updateTimestamp(ENTERED).build());
    entityManager.flush();
    entityManager.clear();
    return bulletin;
  }

  @Test
  @DisplayName("is an empty message when there is no bulletin")
  void none() {
    assertThat(service.current()).isEqualTo(new BulletinResponse("", null, null));
  }

  @Test
  @DisplayName("reads the bulletin, who last saved it and when")
  void reads() {
    given("Road closures: <b>Bowron FSR</b> km 12.");

    assertThat(service.current()).isEqualTo(
        new BulletinResponse("Road closures: <b>Bowron FSR</b> km 12.", "LEGACY", ENTERED));
  }

  @Test
  @DisplayName("strips what is not safe, from a row written by legacy as well as on save")
  void sanitizes() {
    given("<p onclick=\"steal()\">Hi<script>alert(1)</script></p>"
        + "<a href=\"javascript:alert(1)\">x</a><a href=\"https://gov.bc.ca\">BC</a><br>"
        + "<img src=x onerror=alert(1)>");

    assertThat(service.current().message()).isEqualTo(
        "<p>Hi</p><a rel=\"nofollow\">x</a><a href=\"https://gov.bc.ca\" rel=\"nofollow\">BC</a><br>");
    assertThat(BulletinService.sanitize("<ul><li><i>One</i></li></ul>\nTwo & three"))
        .isEqualTo("<ul><li><i>One</i></li></ul>\nTwo &amp; three");
  }

  @Test
  @DisplayName("previews a draft as Save would keep it, storing nothing")
  void previews() {
    assertThat(service.preview(new BulletinRequest(" <b onclick=\"x()\">Hi</b><script>x</script> ")))
        .isEqualTo(new ca.bc.gov.nrs.cbr.struct.v1.BulletinPreview("<b>Hi</b>"));
    assertThat(service.preview(new BulletinRequest(null)).message()).isEmpty();
    assertThat(bulletins.findAll()).isEmpty();
  }

  @Test
  @DisplayName("rewrites the one bulletin in place, as the signed-in administrator")
  void savesInPlace() {
    Long id = given("Old").getBulletinId();

    BulletinResponse saved = service.save(new BulletinRequest("  New <script>x</script> "));
    entityManager.flush();
    entityManager.clear();

    assertThat(saved.message()).isEqualTo("New");
    assertThat(saved.updatedBy()).isEqualTo("IDIR\\ADMIN");
    assertThat(bulletins.findAll()).singleElement().satisfies(row -> {
      assertThat(row.getBulletinId()).isEqualTo(id);
      assertThat(row.getDescription()).isEqualTo("New");
      assertThat(row.getEntryUserid()).isEqualTo("LEGACY");
      assertThat(row.getUpdateUserid()).isEqualTo("IDIR\\ADMIN");
      assertThat(row.getUpdateTimestamp()).isAfter(ENTERED);
    });
  }

  @Test
  @DisplayName("inserts the bulletin the first time, when there is none")
  void insertsFirst() {
    service.save(new BulletinRequest("Welcome"));
    entityManager.flush();
    entityManager.clear();

    assertThat(bulletins.findAll()).singleElement().satisfies(row -> {
      assertThat(row.getDescription()).isEqualTo("Welcome");
      assertThat(row.getEntryUserid()).isEqualTo("IDIR\\ADMIN");
    });
  }

  @Test
  @DisplayName("refuses an empty message, or one longer than the column, beside the field")
  void refuses() {
    for (String message : new String[] {null, "  ", "<script>x</script>"}) {
      assertThatThrownBy(() -> service.save(new BulletinRequest(message)))
          .isInstanceOf(FieldValidationException.class)
          .extracting(failure -> ((FieldValidationException) failure).getFieldErrors())
          .isEqualTo(Map.of("message", "Bulletin Message is required."));
    }
    BulletinRequest tooLong = new BulletinRequest("é".repeat(1001));
    assertThatThrownBy(() -> service.save(tooLong))
        .isInstanceOf(FieldValidationException.class)
        .extracting(failure -> ((FieldValidationException) failure).getFieldErrors())
        .isEqualTo(Map.of("message", "Bulletin Message cannot exceed 2000 characters; it is 2002."));
  }
}
