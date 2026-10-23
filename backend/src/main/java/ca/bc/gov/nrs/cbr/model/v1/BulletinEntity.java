package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * {@code THE.BULLETIN} — the message shown to users when they come to the application, which an
 * administrator maintains (legacy's Administration → Bulletin Message).
 *
 * <p>Legacy keeps one row: {@code CBR.FIND_LATEST_BULLETIN} reads it and
 * {@code CBR.UPDATE_BULLETIN} rewrites it in place, by id. Nothing in legacy inserts one.
 */
@Entity
@Table(name = "BULLETIN", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "bulletinId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulletinEntity {

  /** From {@code THE.BULLETIN_SEQ}, for the first row of a database that has none. */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bulletinSeq")
  @SequenceGenerator(name = "bulletinSeq", sequenceName = "THE.BULLETIN_SEQ", allocationSize = 1)
  @Column(name = "BULLETIN_ID")
  private Long bulletinId;

  /** The message, as HTML. {@code VARCHAR2(2000 BYTE)}. */
  @Setter
  @Column(name = "DESCRIPTION", length = 2000, nullable = false)
  private String description;

  @Column(name = "ENTRY_USERID", length = 30, nullable = false)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP", nullable = false)
  private LocalDateTime entryTimestamp;

  @Setter
  @Column(name = "UPDATE_USERID", length = 30, nullable = false)
  private String updateUserid;

  @Setter
  @Column(name = "UPDATE_TIMESTAMP", nullable = false)
  private LocalDateTime updateTimestamp;
}
