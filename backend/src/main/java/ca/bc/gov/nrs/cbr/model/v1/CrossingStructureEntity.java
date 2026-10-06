package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * {@code THE.CROSSING_STRUCTURE} — a bridge or culvert standing on a crossing site.
 *
 * <p><b>Only the columns its readers need.</b> The real table is wide and is the root of the
 * inspection, repair and monitoring trees; much of it is unmapped, and arrives with the structure
 * detail screen. What is here serves the guard on deleting a site; Inspection Search — which
 * reaches the site through this table, filters on the structure's name and type, and reads
 * {@code CLOSE_PROXIMITY_IND} for one of its four toggles; and Structure Search, which filters on
 * the replacement years, the load ratings and the completeness columns below. Read-only there:
 * nothing in CBR writes those yet.
 *
 * <p>{@code crossingSiteId} stays a plain column <em>as well as</em> an association. The column is
 * what the delete guard counts by — loading a site in order to count its children is the wrong way
 * round — while {@link #site} is what a search traverses to reach the site's road, district and
 * project file. Both map {@code CRS_CS_FK}; the association is read-only
 * ({@code insertable = false}) so only one of them can ever write it.
 */
@Entity
@Table(name = "CROSSING_STRUCTURE", schema = "THE")
@Getter
@ToString(exclude = {"site", "typeClass"})
@EqualsAndHashCode(of = "crossingStructureId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingStructureEntity {

  @Id
  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  @Column(name = "CROSSING_SITE_ID", length = 14)
  private String crossingSiteId;

  /** "The display name of the structure entity." Labelled "Structure #" on both search screens. */
  @Column(name = "CROSSING_STRUCTURE_NAME", length = 14)
  private String crossingStructureName;

  /**
   * The discriminator: bridge → {@code FOREST_SERVICE_BRIDGE}, culvert →
   * {@code FOREST_SERVICE_CULVERT}. It also decides which inspection subsections and repair types
   * apply (cbr-data-model.local.md §2).
   */
  @Column(name = "STRUCTURE_TYPE_CLASS_CODE", length = 10)
  private String structureTypeClassCode;

  /**
   * {@code 'Y'} or {@code 'N'} — whether a close-proximity inspection is required.
   *
   * <p>On the structure, not on the inspection, which is why Inspection Search has to traverse to
   * get at it.
   */
  @Column(name = "CLOSE_PROXIMITY_IND", length = 1)
  private String closeProximityInd;

  /**
   * {@code 'Y'} or {@code 'N'} — "Determines if the current structure is active or not."
   *
   * <p>An archived structure is still a row, and still holds its site down: archiving is a change of
   * state, not a deletion, and the inspection history hangs off it. Legacy reports the two cases
   * separately when it refuses a site delete, which is why this column is mapped at all.
   */
  @Column(name = "ACTIVE_IND", length = 1)
  private String activeInd;

  /**
   * The structure's current capacity in tons, denormalized from {@code STRUCTURE_LOAD_RATING}.
   *
   * <p>Not a value anything sets directly: it is a copy of whichever rating
   * {@code LoadRatingService.currentLoadRatingId} resolves for this structure, and it is rewritten
   * whenever the set of ratings changes — including when an inspection that supplied one is deleted.
   * {@code null} when the structure has no rating at all.
   */
  @Setter
  @Column(name = "CURRENT_LOAD_RATING")
  private BigDecimal currentLoadRating;

  /**
   * {@code 'Y'} or {@code 'N'} — whether the structure's capacity is unknown.
   *
   * <p>Set to {@code 'Y'} when the rating that was current is removed, which is the first of the two
   * corrections an inspection delete makes to its parent structure.
   */
  @Setter
  @Column(name = "LOAD_RATING_UNKNOWN_INDICATOR", length = 1)
  private String loadRatingUnknownIndicator;

  /**
   * The capacity in tons the superstructure was built to. With {@link #currentLoadRating}, what
   * Structure Search's "Downrated Structure?" compares.
   */
  @Column(name = "DESIGN_LOAD_RATING")
  private BigDecimal designLoadRating;

  /** "Year Superstructure Installed" on Structure Search. */
  @Column(name = "YEAR_BUILT")
  private Integer yearBuilt;

  /** "Estimated Load Restriction" — a year, though the column is named as a date. */
  @Column(name = "FULL_LOG_HAUL_TRFFC_RPLCMNT_DT")
  private Integer fullLogHaulReplacementYear;

  /** "Estimated Replacement" — a year, though the column is named as a date. */
  @Column(name = "LIGHT_VEHICLE_TRFFC_RPLCMNT_DT")
  private Integer lightVehicleReplacementYear;

  /** "Estimated Closure" — a year, though the column is named as a date. */
  @Column(name = "ESTIMATED_CLOSURE_DATE")
  private Integer estimatedClosureYear;

  /** {@code 'Y'} or {@code 'N'}. */
  @Column(name = "PORTABLE_STRUCTURE_IND", length = 1)
  private String portableStructureInd;

  @Column(name = "SPECIAL_EQUIPMENT_RQMT_CODE", length = 10)
  private String specialEquipmentRqmtCode;

  /** Read by "Incomplete Data?" only. */
  @Column(name = "NEXT_PLANNED_INSPECTION_DATE")
  private LocalDate nextPlannedInspectionDate;

  /** Read by "Incomplete Data?" only. */
  @Column(name = "STRUCTURE_SOURCE_CODE", length = 10)
  private String structureSourceCode;

  /**
   * Who last changed the row, as {@code IDIR\jsmith}. Written by archiving — legacy passed back
   * the values it had read, so an archive never recorded who did it.
   */
  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;

  // The structure page's Details tab. Read-only so far; legacy's names are kept on the columns.

  @Column(name = "BUILT_BY_CODE", length = 10)
  private String builtByCode;

  @Column(name = "YEAR_FABRICATED")
  private Integer yearFabricated;

  @Column(name = "INVENTORY_ADDED_YEAR")
  private Integer inventoryAddedYear;

  /** "End of Design Life (year)" — a year, despite the name. */
  @Column(name = "ORIGINAL_REPLACEMENT_DATE")
  private Integer originalReplacementYear;

  @Column(name = "AS_BUILT_INFO_PRESENT_IND", length = 1)
  private String asBuiltInfoPresentInd;

  @Column(name = "INSTALLATION_COST")
  private Long installationCost;

  @Column(name = "MATERIAL_COST")
  private Long materialCost;

  @Column(name = "ESTIMATED_REPLACEMENT_COST")
  private Long estimatedReplacementCost;

  @Column(name = "ESTIMATED_REPLACEMENT_COST_CMT", length = 2000)
  private String estimatedReplacementCostCmt;

  @Column(name = "DESIGN_VEHICLE_LOAD_CODE", length = 10)
  private String designVehicleLoadCode;

  @Column(name = "DESIGN_VEHICLE_CMT", length = 2000)
  private String designVehicleCmt;

  @Column(name = "LOAD_POSTING_SIGN_WRRNTD_CODE", length = 10)
  private String loadPostingSignWrrntdCode;

  /**
   * The Type/Class column's description. Read-only, as {@link #site} is.
   *
   * <p>{@code NO_CONSTRAINT} because the database already has the foreign key and this mapping only
   * reads it; left to itself, Hibernate's generated test schema would add one of its own, and every
   * fixture that names a type would have to create the code row first.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "STRUCTURE_TYPE_CLASS_CODE", insertable = false, updatable = false,
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  private StructureTypeClassCodeEntity typeClass;

  /** The site the structure stands on <em>now</em> — see the note on {@code crossingSiteId} above. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "CROSSING_SITE_ID", insertable = false, updatable = false)
  private CrossingSiteEntity site;
}
