package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.FILE_ATTACHMENT_TYPE_CODE} — What an attached file shows or is.
 *
 * <p>Read only to decode a stored code on the structure page's Documents &amp; Photos tab, where
 * the description is the link that opens the file.
 */
@Entity
@Immutable
@Table(name = "FILE_ATTACHMENT_TYPE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "fileAttachmentTypeCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileAttachmentTypeCodeEntity {

  @Id
  @Column(name = "FILE_ATTACHMENT_TYPE_CODE", length = 10)
  private String fileAttachmentTypeCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
