package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * One Condition card's answers, as edited on the inspection's page: every answered question of its
 * group, at each span, pier and stringer. What it leaves out is no longer answered.
 *
 * @param items the group's answers, as Add Inspection sends them
 */
public record ConditionUpdateRequest(List<InspectionCreateRequest.Item> items) {}
