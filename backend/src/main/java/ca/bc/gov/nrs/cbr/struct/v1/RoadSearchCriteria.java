package ca.bc.gov.nrs.cbr.struct.v1;

import lombok.Builder;

/**
 * What the road search dialog asks on, field for field with legacy's {@code road_search.jsp}.
 *
 * <p>Every one is optional and every one is a partial, case-insensitive match — the same
 * {@code Search.LIKE} treatment the other CBR searches give their text criteria. All six blank is
 * a legitimate search that returns the first page of everything, which is what legacy does when
 * the dialog is submitted empty.
 */
@Builder
public record RoadSearchCriteria(
    String forestServiceRoad,
    String forestFileId,
    String roadSectionId,
    /** {@code FILE_TYPE_CODE} — B01 road permit, B40 forest service road, S01/S02 special use. */
    String tenureType,
    String clientName,
    String clientNumber) {}
