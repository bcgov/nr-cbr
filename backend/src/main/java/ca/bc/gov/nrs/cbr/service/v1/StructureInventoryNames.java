package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.BuiltByCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.DesignVehicleLoadCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureSourceCodeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.BuiltByCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CbrOrgUnitRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.DesignVehicleLoadCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureSourceCodeRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * The names the Structure Inventory – Detailed CSV writes for what a structure stores as a code or
 * a number: its codes' descriptions, its maintainer's name, its org units' names.
 *
 * <p>Every code is read, expired ones included, as legacy's {@code STRCTRE_INVNTRY_DTL_CSV}
 * left-joins the whole table: a structure recorded years ago still carries a code since retired.
 * Org units are named from {@code CBR_ORG_UNIT}, as legacy names them, which holds the retired
 * districts that management areas are.
 */
@Component
public class StructureInventoryNames {

  private final DesignVehicleLoadCodeRepository designVehicles;
  private final BuiltByCodeRepository builtBy;
  private final CrossingSiteTypeCodeRepository siteTypes;
  private final StructureSourceCodeRepository sources;
  private final StructureInspectionStatusCodeRepository inspectionStatuses;
  private final ClientLocationRepository clients;
  private final CbrOrgUnitRepository orgUnits;

  public StructureInventoryNames(
      DesignVehicleLoadCodeRepository designVehicles,
      BuiltByCodeRepository builtBy,
      CrossingSiteTypeCodeRepository siteTypes,
      StructureSourceCodeRepository sources,
      StructureInspectionStatusCodeRepository inspectionStatuses,
      ClientLocationRepository clients,
      CbrOrgUnitRepository orgUnits) {
    this.designVehicles = designVehicles;
    this.builtBy = builtBy;
    this.siteTypes = siteTypes;
    this.sources = sources;
    this.inspectionStatuses = inspectionStatuses;
    this.clients = clients;
    this.orgUnits = orgUnits;
  }

  /** The descriptions of every code the CSV names, by table. */
  Codes codes() {
    return new Codes(
        byCode(designVehicles.findAll(), DesignVehicleLoadCodeEntity::getDesignVehicleLoadCode,
            DesignVehicleLoadCodeEntity::getDescription),
        byCode(builtBy.findAll(), BuiltByCodeEntity::getBuiltByCode,
            BuiltByCodeEntity::getDescription),
        byCode(siteTypes.findAll(), CrossingSiteTypeCodeEntity::getCrossingSiteTypeCode,
            CrossingSiteTypeCodeEntity::getDescription),
        byCode(sources.findAll(), StructureSourceCodeEntity::getStructureSourceCode,
            StructureSourceCodeEntity::getDescription),
        byCode(inspectionStatuses.findAll(),
            StructureInspectionStatusCodeEntity::getStructureInspectionStatusCode,
            StructureInspectionStatusCodeEntity::getDescription));
  }

  /** Maintainers' names by client number, a batch at a time. */
  Map<String, String> clientNames(Collection<String> numbers) {
    Map<String, String> names = new HashMap<>();
    for (List<String> batch : batches(numbers.stream().filter(Objects::nonNull).distinct().toList())) {
      for (ClientPublicEntity client : clients.findClientsByNumber(batch)) {
        if (client.getClientName() != null) {
          names.putIfAbsent(client.getClientNumber(), client.getClientName());
        }
      }
    }
    return names;
  }

  /** Org units' names by number, a batch at a time. */
  Map<Long, String> orgUnitNames(Collection<Long> numbers) {
    Map<Long, String> names = new HashMap<>();
    for (List<Long> batch : batches(numbers.stream().filter(Objects::nonNull).distinct().toList())) {
      for (CbrOrgUnitEntity orgUnit : orgUnits.findAllById(batch)) {
        names.putIfAbsent(orgUnit.getOrgUnitNo(), orgUnit.getOrgUnitName());
      }
    }
    return names;
  }

  private static <T> List<List<T>> batches(List<T> values) {
    int size = StructureInventoryReportSupport.BATCH;
    return IntStream.range(0, (values.size() + size - 1) / size)
        .mapToObj(batch -> values.subList(batch * size, Math.min(values.size(), (batch + 1) * size)))
        .toList();
  }

  private static <E> Map<String, String> byCode(
      List<E> rows, Function<E, String> code, Function<E, String> description) {
    Map<String, String> descriptions = new HashMap<>();
    rows.forEach(row -> descriptions.put(code.apply(row), description.apply(row)));
    return descriptions;
  }

  /** The code tables' descriptions, each by its code. */
  record Codes(
      Map<String, String> designVehicles,
      Map<String, String> builtBy,
      Map<String, String> siteTypes,
      Map<String, String> sources,
      Map<String, String> inspectionStatuses) {

    /** A code's description, the code itself when its table lacks it, or blank when unset. */
    static String describe(Map<String, String> table, String code) {
      if (code == null) {
        return "";
      }
      String description = table.get(code);
      return description == null ? code : description;
    }
  }
}
