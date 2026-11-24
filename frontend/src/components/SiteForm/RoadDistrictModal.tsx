import { Button, RadioButton, RadioButtonGroup } from '@carbon/react';
import { useState, type FC } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';
import { Modal } from '@/components/Modal';

import type { RoadSectionResponse } from '@/services/road.service';
import type { OrgUnitOption } from '@/types/configuration';

import './roadDistrictModal.scss';

/** The coordinates the user has entered on the form, as the form shows them. Blank when none. */
export type EnteredLocation = {
  longitude: string;
  latitude: string;
  utm: string;
};

type Props = {
  /** The road section, which lies in two or more districts. */
  road: RoadSectionResponse;
  /** CBR's Forest Districts, to name the road's. */
  forestDistricts: OrgUnitOption[];
  /** Where the user has said the site is, to judge which district it falls in. */
  location: EnteredLocation;
  /** What the form holds now, so reopening the pop-up starts from it. */
  orgUnitNo: string;
  onConfirm: (orgUnitNo: string) => void;
  onClose: () => void;
};

/** "DPG - Prince George Natural Resource District", as everywhere else in the app. */
const label = (code: string | null, name: string | null): string =>
  [code, name].filter(Boolean).join(' - ');

/** "1.20 km of this section", or nothing when LRMOPS's answer does not say. */
const lengthText = (metres: number | null | undefined): string =>
  metres === null || metres === undefined ? '' : `${(metres / 1000).toFixed(2)} km of this section`;

/**
 * Picks the Forest District for a site on a road section that crosses a district boundary
 * (business, 2026-11-23). The Management Area is then picked on the form, from the chosen
 * district's areas.
 *
 * <p>Opens by itself when such a road is entered and no district of it is chosen yet; the form shows
 * Forest District read-only, so this is the one place it is set. Styled as the app's other dialogs
 * (`MonitorDialog`): passive, its own Cancel and confirm buttons, no close on a click outside. Shows what can help decide: the road, how much of it lies in each
 * district (when LRMOPS says), and the coordinates the user has entered.
 *
 * <p>Nothing is written to the form until the choice is confirmed; Cancel leaves it as it was.
 */
const RoadDistrictModal: FC<Props> = ({
  road,
  forestDistricts,
  location,
  orgUnitNo,
  onConfirm,
  onClose,
}) => {
  const choices = road.districts
    .filter((district) => district.orgUnitNo !== null)
    .map((district) => {
      const unit = forestDistricts.find(
        (option) => String(option.orgUnitNo) === String(district.orgUnitNo),
      );
      return {
        orgUnitNo: String(district.orgUnitNo),
        text: unit
          ? label(unit.orgUnitCode, unit.orgUnitName)
          : (district.code ?? `Org unit ${district.orgUnitNo}`),
        length: lengthText(district.lengthMetres),
      };
    });

  const [district, setDistrict] = useState(
    choices.some((choice) => choice.orgUnitNo === orgUnitNo) ? orgUnitNo : '',
  );

  const hasLocation = Boolean(location.longitude || location.latitude || location.utm);

  return (
    <Modal
      open
      modalHeading="Choose the Forest District"
      passiveModal
      preventCloseOnClickOutside
      onRequestClose={onClose}
      data-testid="road-district-modal"
      className="road-district-modal"
    >
      <p className="road-district-modal__intro">
        This road section crosses a district boundary and lies in {choices.length} districts. Choose
        the one this site is in.
      </p>

      <h4 className="road-district-modal__heading">Road</h4>
      <div className="road-district-modal__details">
        <ReadOnlyField label="Project File ID#" value={road.forestFileId} />
        <ReadOnlyField label="Br." value={road.roadSectionId} />
        <ReadOnlyField label="Forest Service Road" value={road.forestServiceRoad} />
        <ReadOnlyField label="Tenure Type" value={road.tenureType} />
        <ReadOnlyField label="Client Name" value={road.clientName} />
      </div>

      {/* Only when the form has coordinates — a new site's usually has none yet, as the pop-up opens

          once the road is entered, before the coordinate fields. */}

      {hasLocation && (
        <>
          <h4 className="road-district-modal__heading">Site location entered</h4>
          <div className="road-district-modal__details">
            <ReadOnlyField label="Longitude" value={location.longitude} />
            <ReadOnlyField label="Latitude" value={location.latitude} />
            <ReadOnlyField label="UTM" value={location.utm} />
          </div>
        </>
      )}

      <RadioButtonGroup
        name="road-district-choice"
        legendText="Forest District"
        orientation="horizontal"
        className="road-district-modal__choices"
        valueSelected={district}
        onChange={(selected) => setDistrict(String(selected))}
      >
        {choices.map((choice) => (
          <RadioButton
            key={choice.orgUnitNo}
            id={`road-district-${choice.orgUnitNo}`}
            data-testid={`road-district-${choice.orgUnitNo}`}
            value={choice.orgUnitNo}
            labelText={
              <span className="road-district-modal__choice">
                <span>{choice.text}</span>
                {choice.length && (
                  <span className="road-district-modal__length">{choice.length}</span>
                )}
              </span>
            }
          />
        ))}
      </RadioButtonGroup>

      <div className="road-district-modal__actions">
        <Button kind="tertiary" size="md" onClick={onClose}>
          Cancel
        </Button>
        <Button
          kind="primary"
          size="md"
          disabled={district === ''}
          onClick={() => onConfirm(district)}
          data-testid="road-district-confirm"
        >
          Use this district
        </Button>
      </div>
    </Modal>
  );
};

export default RoadDistrictModal;
