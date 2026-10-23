package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.BulletinEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code THE.BULLETIN}. */
public interface BulletinRepository extends JpaRepository<BulletinEntity, Long> {

  /**
   * The bulletin shown: the latest entered — legacy's {@code FIND_LATEST_BULLETIN} meant to order
   * by {@code ENTRY_TIMESTAMP}, though its {@code ROWNUM} came first; there is one row.
   */
  Optional<BulletinEntity> findFirstByOrderByEntryTimestampDescBulletinIdDesc();
}
