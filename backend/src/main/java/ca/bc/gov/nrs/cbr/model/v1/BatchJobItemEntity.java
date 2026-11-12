package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.CBR_BATCH_JOB_ITEM} — one row per item a scheduled batch job has to process, in the
 * manner of EDUC-STUDENT-ASSESSMENT-API's staged rows: a job fills its rows, the scheduler hands
 * out the {@code READY} ones, and each is marked {@code DONE} or {@code ERROR}.
 *
 * <p>{@code ITEM_ID}'s meaning depends on the job; for {@code FILE_COPY} it is the attachment's
 * {@code FILE_ID}. Not a foreign key, so the record can be deleted while its item is pending.
 * Deployed from nr-mof-db ({@code V999999999999.5__CBR_BATCH_JOB_ITEM.sql}).
 */
@Entity
@Table(name = "CBR_BATCH_JOB_ITEM", schema = "THE")
@IdClass(BatchJobItemEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode(of = {"jobName", "itemId"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchJobItemEntity {

  @Id
  @Column(name = "JOB_NAME", length = 50)
  private String jobName;

  @Id
  @Column(name = "ITEM_ID")
  private Long itemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", length = 10, nullable = false)
  private BatchJobItemStatus status;

  @Column(name = "UPDATE_TIMESTAMP", nullable = false)
  private LocalDateTime updateTimestamp;

  /** The composite key. */
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String jobName;
    private Long itemId;
  }
}
