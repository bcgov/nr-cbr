package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CbrOrgUnitRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.SpecialAccessRequirementCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StrctreInspectionTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureTypeClassCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionStatusCodeRepository;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Reference lookups that populate the UI's dropdowns. All ten are live.
 *
 * <h3>Two ways into the data, and when each applies</h3>
 * These are read with Spring Data JPA — an entity, a repository, a query — because each legacy
 * procedure is a bare {@code SELECT} with an {@code ORDER BY} and there is no business logic to
 * preserve. Anything that <em>does</em> have logic behind it stays on the stored procedure through
 * {@link ca.bc.gov.nrs.cbr.repository.AbstractCbrRepository}; that is the line between the two, not
 * "new code versus old".
 *
 * <h3>The predicates are not obvious, and they are not this layer's business</h3>
 * Several of the legacy queries do something that reads as a defect and is not: site statuses and
 * structure types are inner-joined to an xref that decides whether a code is published at all;
 * management areas return <em>only</em> expired org units; and the code lists disagree with each
 * other about expired codes — six keep them, structure type/class alone drops them, because legacy
 * asks for that one list with a different flag. Each is explained where the query lives, in the
 * repository. This class deliberately holds none of that
 * — it fetches, caches and maps, so that a change to what a lookup means is a change in one place.
 *
 * <h3>Caching</h3>
 * Code tables change a few times a decade and every screen loads several, so each list is cached
 * under its own name. {@code @EnableCaching} is on {@code CbrApiApplication} and no cache manager is
 * configured, which means Spring's simple {@code ConcurrentMapCacheManager}: entries never expire,
 * so a code added by a DBA migration appears on the next pod restart rather than immediately. That
 * is the right trade for data of this shape, but it is a trade — if a lookup ever needs to change
 * within a deploy, it needs a real cache with a TTL, not a workaround here.
 *
 * <p>Management areas take a parameter, so that cache holds one entry per district — Spring's
 * default key generator includes the arguments. Left as the default rather than pinned with an
 * explicit SpEL key, because a key expression evaluates to {@code null} for a null argument and the
 * backing {@code ConcurrentHashMap} rejects null keys; the default wraps the arguments in a
 * {@code SimpleKey} instead and cannot produce one.
 *
 * <h3>Mapping</h3>
 * By hand rather than through MapStruct, which is what the EDUC API this pattern follows uses. A
 * mapper earns its keep on wide structures with matching field names; here the two fields never
 * match — every code table's primary key is named after the table
 * ({@code crossingSiteStatusCode}, {@code crossingSiteTypeCode}, …) while the response field is
 * always {@code code} — so each entity would need its own explicit {@code @Mapping} anyway, and CBR
 * would carry an annotation processor to write one line per lookup.
 */
@Service
public class ConfigurationService {

  private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

  private final CrossingSiteStatusCodeRepository crossingSiteStatusCodeRepository;
  private final StructureInspectionStatusCodeRepository structureInspectionStatusCodeRepository;
  private final SpecialAccessRequirementCodeRepository specialAccessRequirementCodeRepository;
  private final CrossingSiteTypeCodeRepository crossingSiteTypeCodeRepository;
  private final StructureTypeClassCodeRepository structureTypeClassCodeRepository;
  private final StrctreInspectionTypeCodeRepository strctreInspectionTypeCodeRepository;
  private final InspectionReportStatusCodeRepository inspectionReportStatusCodeRepository;
  private final CbrOrgUnitRepository cbrOrgUnitRepository;

  public ConfigurationService(
      CrossingSiteStatusCodeRepository crossingSiteStatusCodeRepository,
      StructureInspectionStatusCodeRepository structureInspectionStatusCodeRepository,
      SpecialAccessRequirementCodeRepository specialAccessRequirementCodeRepository,
      CrossingSiteTypeCodeRepository crossingSiteTypeCodeRepository,
      StructureTypeClassCodeRepository structureTypeClassCodeRepository,
      StrctreInspectionTypeCodeRepository strctreInspectionTypeCodeRepository,
      InspectionReportStatusCodeRepository inspectionReportStatusCodeRepository,
      CbrOrgUnitRepository cbrOrgUnitRepository) {
    this.crossingSiteStatusCodeRepository = crossingSiteStatusCodeRepository;
    this.structureInspectionStatusCodeRepository = structureInspectionStatusCodeRepository;
    this.specialAccessRequirementCodeRepository = specialAccessRequirementCodeRepository;
    this.crossingSiteTypeCodeRepository = crossingSiteTypeCodeRepository;
    this.structureTypeClassCodeRepository = structureTypeClassCodeRepository;
    this.strctreInspectionTypeCodeRepository = strctreInspectionTypeCodeRepository;
    this.inspectionReportStatusCodeRepository = inspectionReportStatusCodeRepository;
    this.cbrOrgUnitRepository = cbrOrgUnitRepository;
  }

  /**
   * Site statuses — {@code THE.CROSSING_SITE_STATUS_CODE}, in dropdown display order.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_SITE_STATUSES}. Which rows come back, and in what order, is
   * decided in {@link CrossingSiteStatusCodeRepository#findAllInDisplayOrder()} — including the two
   * things that look like omissions there and are not.
   */
  @Cacheable("siteStatusCodes")
  public List<CodeOptionResponse> getSiteStatusCodes() {
    return crossingSiteStatusCodeRepository.findAllInDisplayOrder().stream()
        .map(entity ->
            new CodeOptionResponse(entity.getCrossingSiteStatusCode(), entity.getDescription()))
        .toList();
  }

  /**
   * Structure inspection statuses — {@code THE.STRUCTURE_INSPCTN_STATUS_CODE}, by description.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_INSPECTION_STATUSES}.
   */
  @Cacheable("structureInspectionStatusCodes")
  public List<CodeOptionResponse> getStructureInspectionStatusCodes() {
    return structureInspectionStatusCodeRepository.findAllByOrderByDescriptionAsc().stream()
        .map(entity -> new CodeOptionResponse(
            entity.getStructureInspectionStatusCode(), entity.getDescription()))
        .toList();
  }

  /**
   * Special access requirements — {@code THE.SPECIAL_ACCESS_RQMT_CODE}, by description.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_SPECIAL_ACCESS_RQMTS}.
   */
  @Cacheable("specialAccessCodes")
  public List<CodeOptionResponse> getSpecialAccessCodes() {
    return specialAccessRequirementCodeRepository.findAllByOrderByDescriptionAsc().stream()
        .map(entity -> new CodeOptionResponse(
            entity.getSpecialAccessRequirementCode(), entity.getDescription()))
        .toList();
  }

  /**
   * Site types — {@code THE.CROSSING_SITE_TYPE_CODE}, by description.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_SITE_TYPES}.
   */
  @Cacheable("siteTypeCodes")
  public List<CodeOptionResponse> getSiteTypeCodes() {
    return crossingSiteTypeCodeRepository.findAllByOrderByDescriptionAsc().stream()
        .map(entity ->
            new CodeOptionResponse(entity.getCrossingSiteTypeCode(), entity.getDescription()))
        .toList();
  }

  /**
   * Structure types and classes — {@code THE.STRUCTURE_TYPE_CLASS_CODE}, in dropdown display order.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_STRUCTURE_TYPES}. This is the one code list on the
   * Inspection Search form that hides retired codes; see
   * {@link StructureTypeClassCodeRepository#findAllCurrentInDisplayOrder()} for why that is legacy's
   * behaviour rather than an inconsistency introduced here.
   */
  @Cacheable("structureTypeClassCodes")
  public List<CodeOptionResponse> getStructureTypeClassCodes() {
    return structureTypeClassCodeRepository.findAllCurrentInDisplayOrder().stream()
        .map(entity ->
            new CodeOptionResponse(entity.getStructureTypeClassCode(), entity.getDescription()))
        .toList();
  }

  /**
   * Inspection types — {@code THE.STRCTRE_INSPECTION_TYPE_CODE}, by description.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_INSPECTION_TYPE_CODES}, which returns its rows in no
   * particular order — see
   * {@link StrctreInspectionTypeCodeRepository#findAllByOrderByDescriptionAsc()}.
   */
  @Cacheable("inspectionTypeCodes")
  public List<CodeOptionResponse> getInspectionTypeCodes() {
    return strctreInspectionTypeCodeRepository.findAllByOrderByDescriptionAsc().stream()
        .map(entity -> new CodeOptionResponse(
            entity.getStrctreInspectionTypeCode(), entity.getDescription()))
        .toList();
  }

  /**
   * Inspection report statuses — {@code THE.INSPECTION_REPORT_STATUS_CODE}, by description.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_INSPCTN_RPT_STATUSES}. Includes the expired {@code ACC} and
   * the machine-set {@code OFL}, both deliberately — see
   * {@link InspectionReportStatusCodeRepository#findAllByOrderByDescriptionAsc()}.
   */
  @Cacheable("inspectionReportStatusCodes")
  public List<CodeOptionResponse> getInspectionReportStatusCodes() {
    return inspectionReportStatusCodeRepository.findAllByOrderByDescriptionAsc().stream()
        .map(entity -> new CodeOptionResponse(
            entity.getInspectionReportStatusCode(), entity.getDescription()))
        .toList();
  }

  /**
   * Forest districts — the current districts, by name.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_FOREST_DISTRICTS}. Read through the {@code CBR_ORG_UNIT}
   * view, which CBR does not own.
   */
  @Cacheable("forestDistricts")
  public List<OrgUnitResponse> getForestDistricts() {
    return cbrOrgUnitRepository.findForestDistricts().stream()
        .map(ConfigurationService::toOrgUnit)
        .toList();
  }

  /**
   * BCTS business areas, by name.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_BUSINESS_AREAS}. Read through the {@code CBR_ORG_UNIT}
   * view, which CBR does not own. Unlike management areas this takes no parameter: a business area
   * is BC Timber Sales' own geography rather than a subdivision of a forest district, and a site
   * can carry one of each.
   */
  @Cacheable("businessAreas")
  public List<OrgUnitResponse> getBusinessAreas() {
    return cbrOrgUnitRepository.findBusinessAreas().stream()
        .map(ConfigurationService::toOrgUnit)
        .toList();
  }

  /**
   * Management areas within one forest district, by name.
   *
   * <p>Replaces {@code CBR_GENERAL.FIND_MANAGEMENT_AREAS_BY_SLCTN}. A CBR management area is a
   * <em>former</em> district that now rolls up into the selected one — see
   * {@link CbrOrgUnitRepository#findManagementAreas(Long)}, which is worth reading before changing
   * anything here.
   *
   * <p>A non-numeric district is an empty list, not an error. The legacy action does the same, by
   * catching the {@code NumberFormatException} from {@code new Long(...)} and forwarding an empty
   * list: the parameter reaches the server as form text, and a malformed one means the dropdown was
   * never populated rather than that anything failed.
   *
   * @param forestDistrictOrgUnitNo the selected district's {@code ORG_UNIT_NO}, as submitted
   */
  @Cacheable("managementAreas")
  public List<OrgUnitResponse> getManagementAreas(String forestDistrictOrgUnitNo) {
    Long orgUnitNo = parseOrgUnitNo(forestDistrictOrgUnitNo);
    if (orgUnitNo == null) {
      return List.of();
    }
    return cbrOrgUnitRepository.findManagementAreas(orgUnitNo).stream()
        .map(ConfigurationService::toOrgUnit)
        .toList();
  }

  private static Long parseOrgUnitNo(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(value.trim());
    } catch (NumberFormatException ex) {
      log.debug("Ignoring non-numeric forest district org unit '{}'", value);
      return null;
    }
  }

  private static OrgUnitResponse toOrgUnit(CbrOrgUnitEntity entity) {
    // The number is a String on the wire because it is a <select> value, never arithmetic.
    return new OrgUnitResponse(
        String.valueOf(entity.getOrgUnitNo()), entity.getOrgUnitCode(), entity.getOrgUnitName());
  }
}
