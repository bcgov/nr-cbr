package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.V_CLIENT_PUBLIC} — a client's name by its number.
 *
 * <p>Read-only: the target is a view onto Forest Client. Structure Search reads the names of a
 * page's maintainers through {@code findAllById}, one query per page rather than one per row.
 */
@Repository
public interface ClientPublicRepository extends JpaRepository<ClientPublicEntity, String> {}
