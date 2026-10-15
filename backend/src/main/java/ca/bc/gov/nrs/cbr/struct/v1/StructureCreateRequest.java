package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * A new structure — Add Structure, legacy's blank structure page reached from Site Detail. Its
 * sections are the structure page's own requests, so each is checked by the same rules.
 *
 * @param structureName {@code CROSSING_STRUCTURE_NAME}, typed; stored in capitals, as legacy
 * @param siteId        {@code CROSSING_SITE_ID}: a complete site that is not Proposed or closed
 * @param typeClassCode PB or TB (a bridge), CUL or WLC (a culvert)
 * @param details       the Structure card
 * @param bridge        the Bridge card, for PB or TB; its own Type/Class is ignored
 * @param culvert       the Culvert card, for CUL or WLC
 * @param spans         a bridge's spans: at least one, and more than its piers
 * @param piers         a bridge's piers
 * @param loadRating    Load Rating Details
 * @param replacement   Replacement Details — a P.Eng's; null or empty for anyone else
 * @param schedule      the inspection schedule
 * @param comment       a first general comment, or blank
 */
public record StructureCreateRequest(
    String structureName,
    String siteId,
    String typeClassCode,
    StructureDetailsRequest details,
    BridgeDetailsRequest bridge,
    CulvertDetailsRequest culvert,
    List<SpanRequest> spans,
    List<PierRequest> piers,
    LoadRatingDetailsRequest loadRating,
    ReplacementDetailsRequest replacement,
    InspectionScheduleRequest schedule,
    String comment) {}
