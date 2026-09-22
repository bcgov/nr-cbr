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
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.FOREST_FILE_CLIENT} — who holds a road file.
 *
 * <p>Mapped for one reason: the road search shows a client name beside each road and lets the user
 * search on it, and this is the only thing that connects a {@code FOREST_FILE_ID} to a client.
 * Legacy joins the same table for the same purpose.
 *
 * <p>Three columns of eight. The rest — the location code, the client type, the audit columns —
 * belong to tenure administration, which is not what this application does with the row.
 */
@Entity
@Immutable
@Table(name = "FOREST_FILE_CLIENT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestFileClientSkey")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForestFileClientEntity {

  @Id
  @Column(name = "FOREST_FILE_CLIENT_SKEY")
  private Long forestFileClientSkey;

  @Column(name = "FOREST_FILE_ID", length = 10)
  private String forestFileId;

  @Column(name = "CLIENT_NUMBER", length = 8)
  private String clientNumber;
}
