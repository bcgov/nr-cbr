package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.LoadPostingSignWrrntdCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.LOAD_POSTING_SIGN_WRRNTD_CODE} — the Load Posting Signs select. */
@Repository
public interface LoadPostingSignWrrntdCodeRepository extends JpaRepository<LoadPostingSignWrrntdCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's lists left them. */
  @Query("""
      SELECT code
        FROM LoadPostingSignWrrntdCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<LoadPostingSignWrrntdCodeEntity> findAllCurrent();
}
