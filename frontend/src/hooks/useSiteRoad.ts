import { useEffect, useRef, type Dispatch, type SetStateAction } from 'react';

import { SITE_TYPE, type SiteFormValues } from '@/components/SiteForm/types';
import { useRecreationProjectName } from '@/hooks/useConfiguration';
import { useRoadSection } from '@/hooks/useRoadSection';

export type SiteRoad = {
  /** Forest Service Road — or, for a recreation site, its Project Name. Empty when unknown. */
  forestServiceRoad: string;
  forestServiceRoadLoading: boolean;
  /** The pair names a road a site can be put on. */
  roadResolved: boolean;
  /** Both halves are in, the lookup has settled, and they name no such road. */
  noRoad: boolean;
};

/**
 * The road a site's Project File ID# and Br. name, and what follows from it — shared by Add Site
 * and Site Detail's edit mode, where the pair can be changed.
 *
 * <p>A recreation site's Project File ID# names a recreation project rather than a road file, so
 * only one of the two lookups is ever asked. Both are debounced inside their hooks, and the road is
 * asked nothing until both halves are present — a road file alone names many sections.
 *
 * @param followRoad whether the road should write the Forest District into the form. Off while a
 *                   stored site is only being read: showing it must not change it.
 */
export const useSiteRoad = (
  site: SiteFormValues,
  setSite: Dispatch<SetStateAction<SiteFormValues>>,
  followRoad: boolean,
): SiteRoad => {
  const isRecreationSite = site.crossingSiteTypeCode === SITE_TYPE.RECREATION;
  const recreationProject = useRecreationProjectName(site.forestFileId, isRecreationSite);
  const road = useRoadSection(site.forestFileId, site.roadSectionId);

  /**
   * The district the road last wrote into the form, or `null` if the one there was not the road's.
   *
   * <p>What lets the road take back only what it gave: a storage site with no road picks its own
   * district, and losing the road must not wipe a choice the user made.
   */
  const districtFromRoad = useRef<string | null>(null);

  /**
   * The road sets the Forest District, as `SiteAction` does on every redisplay — and, unlike
   * legacy, takes it away again when there is no longer a road.
   *
   * <p>Legacy only ever writes the district: clear Project File ID#, or change the pair to one that
   * names no road, and the old road's district stays in a field the user cannot edit. Here it
   * follows the road both ways, once the lookup has settled — not while it is still in flight, or
   * the district would blink out and back on every keystroke. A road with no region gives no
   * district rather than leaving the previous road's.
   *
   * <p>Never for a recreation site, whose district is the user's choice from a list the file
   * narrows — writing the road's org unit there would overwrite what they picked.
   */
  useEffect(() => {
    if (!followRoad || isRecreationSite || road.isFetching) return;

    const hasRoad = Boolean(road.data);
    if (!hasRoad && districtFromRoad.current === null) return;

    const region = road.data?.orgUnitNo;
    const next = region === null || region === undefined ? '' : String(region);
    const hadFromRoad = districtFromRoad.current;

    setSite((current) => {
      // No road any more, and the user has since picked a district of their own: keep theirs.
      if (!hasRoad && current.orgUnitNo !== hadFromRoad) return current;
      return current.orgUnitNo === next
        ? current
        : { ...current, orgUnitNo: next, managementOrgUnitNo: '' };
    });
    districtFromRoad.current = hasRoad ? next : null;
  }, [followRoad, isRecreationSite, road.data, road.isFetching, setSite]);

  return {
    forestServiceRoad: isRecreationSite
      ? (recreationProject.data?.projectName ?? '')
      : (road.data?.forestServiceRoad ?? ''),
    forestServiceRoadLoading: isRecreationSite ? recreationProject.isFetching : road.isFetching,
    roadResolved: Boolean(road.data),
    noRoad:
      !isRecreationSite &&
      site.forestFileId.trim() !== '' &&
      site.roadSectionId.trim() !== '' &&
      road.isError &&
      !road.isFetching,
  };
};
