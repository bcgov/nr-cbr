package ca.bc.gov.nrs.cbr.model.v1;

/**
 * A work item's {@code STATUS} in {@code THE.CBR_BATCH_JOB_ITEM} — the values its check constraint
 * {@code CBR_BATCH_JOB_ITEM_CK1} allows.
 */
public enum BatchJobItemStatus {
  /** To be processed. */
  READY,
  /** Processed. */
  DONE,
  /** Processing failed; the cause is in the backend log. Set back to {@link #READY} to retry. */
  ERROR
}
