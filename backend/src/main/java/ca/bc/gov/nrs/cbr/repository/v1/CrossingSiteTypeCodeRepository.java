package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteTypeCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.CROSSING_SITE_TYPE_CODE}.
 */
@Repository
public interface CrossingSiteTypeCodeRepository extends JpaRepository<CrossingSiteTypeCodeEntity, String> {

  /**
   * Every code, ordered by description.
   *
   * <p>This is {@code CBR_GENERAL.FIND_SITE_TYPES}, which is a plain {@code SELECT … ORDER BY DESCRIPTION} over the
   * one table — no join, and no filter on {@code EFFECTIVE_DATE}/{@code EXPIRY_DATE}. Expired
   * codes are returned deliberately, for the reason set out on
   * {@link CrossingSiteStatusCodeRepository#findAllInDisplayOrder()}: this list feeds a search
   * filter, and records made years ago still carry codes that have since been retired.
   *
   * <p>Ordered by description rather than by a display order — unlike site statuses, these three
   * code tables have no {@code _XREF} sibling, so alphabetical is the legacy order rather than an
   * arbitrary choice made here.
   */
  List<CrossingSiteTypeCodeEntity> findAllByOrderByDescriptionAsc();
}
