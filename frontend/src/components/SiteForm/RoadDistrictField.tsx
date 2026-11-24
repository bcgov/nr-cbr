import { useState, type FC } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';

import RoadDistrictModal, { type EnteredLocation } from './RoadDistrictModal';

import type { RoadSectionResponse } from '@/services/road.service';

type Props = {
  /** The road section on the form, which lies in two or more districts. */
  road: RoadSectionResponse;
  /** The chosen district as it should read — see `districtText` in the form. Blank when none. */
  districtText: string;
  /** The form's `orgUnitNo`. */
  orgUnitNo: string;
  /** CBR's Forest Districts, to name the road's in the pop-up. */
  forestDistricts: Parameters<typeof RoadDistrictModal>[0]['forestDistricts'];
  /** The coordinates entered on the form, for the pop-up. */
  location: EnteredLocation;
  /** The field's error, shown under it in red. */
  error?: string;
  /** A district was chosen in the pop-up. */
  onChoose: (orgUnitNo: string) => void;
};

/**
 * Forest District when the road on the form crosses a district boundary (business, 2026-11-23):
 * the chosen district read-only, a link to (re)open the pop-up that chooses it, and the pop-up.
 *
 * <p><b>The pop-up opens by itself</b> while none of the road's districts is chosen — unless the
 * user cancelled it for this road. Derived rather than set in an effect: entering a different road
 * opens it again, choosing a district closes it. The link reopens it, after a cancel or to change
 * the choice.
 *
 * <p>Its own component so the form's render stays one decision about which district control to
 * show, and the pop-up's state lives with the pop-up.
 */
const RoadDistrictField: FC<Props> = ({
  road,
  districtText,
  orgUnitNo,
  forestDistricts,
  location,
  error,
  onChoose,
}) => {
  const roadKey = `${road.forestFileId}/${road.roadSectionId}`;
  const [dismissedRoad, setDismissedRoad] = useState<string | null>(null);
  const [reopened, setReopened] = useState(false);

  const chosen = road.districts.some((district) => String(district.orgUnitNo) === orgUnitNo);
  const open = reopened || (!chosen && dismissedRoad !== roadKey);

  return (
    <div className="site-form__road-district" data-testid="site-form-orgUnitNo-choice">
      <div className="site-form__road-district-value">
        <ReadOnlyField label="Forest District" value={districtText} emptyText="Not chosen" />
      </div>
      <span className="site-form__road-district-help">
        This road section lies in {road.districts.length} districts.{' '}
        {/* A link, not a button: the pop-up opens by itself, this only reopens it. */}
        <button
          type="button"
          className="cds--link site-form__road-district-link"
          data-testid="site-form-choose-district"
          onClick={() => setReopened(true)}
        >
          {chosen ? 'Change district' : 'Choose district'}
        </button>
      </span>
      {error && (
        <span className="site-form__road-district-error" data-testid="site-form-orgUnitNo-error">
          {error}
        </span>
      )}
      {open && (
        <RoadDistrictModal
          road={road}
          forestDistricts={forestDistricts}
          location={location}
          orgUnitNo={orgUnitNo}
          onClose={() => {
            setDismissedRoad(roadKey);
            setReopened(false);
          }}
          onConfirm={(choice) => {
            onChoose(choice);
            setReopened(false);
          }}
        />
      )}
    </div>
  );
};

export default RoadDistrictField;
