package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.FILE_MIME_TYPE_XREF} — the file extensions an attachment may have, each with the MIME
 * type code it is stored under.
 *
 * <p>A file detail row's (extension, MIME type) pair is a foreign key to this table
 * ({@code CSF_FMTX_FK}), so the extensions listed here are exactly the ones a file can be stored
 * with. Legacy's validation joins it to {@code EFILE_EXTENSION_CODE} on mismatched columns; this
 * reads the pair as the foreign key does.
 */
@Entity
@Immutable
@Table(name = "FILE_MIME_TYPE_XREF", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
public class FileMimeTypeXrefEntity {

  @EmbeddedId
  private Key id;

  /** The two columns of the key. */
  @Embeddable
  @Getter
  @ToString
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {

    /** Upper case, without the dot — {@code JPG}, {@code PDF}. */
    @Column(name = "EFILE_EXTENSION_CODE", length = 10)
    private String efileExtensionCode;

    @Column(name = "FILE_MIME_TYPE_CODE", length = 10)
    private String fileMimeTypeCode;
  }
}
