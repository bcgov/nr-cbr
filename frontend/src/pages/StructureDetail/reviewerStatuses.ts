import { useAuthorization } from '@/hooks/useAuthorization';

/** Required and Not Required: an inspection reviewer's, as legacy's list; the server agrees. */
const REVIEWER_STATUSES: ReadonlySet<string> = new Set(['REQ', 'NRQ']);

/**
 * The repair or monitoring statuses this user may set, and the one the dialog opens with: a status
 * they may not set opens blank, as legacy's list opens without it.
 */
export const useReviewerStatuses = <T extends { code: string }>(
  options: T[] | undefined,
  stored: string,
) => {
  const { isInspectionReviewer } = useAuthorization();
  return {
    offered: (options ?? []).filter(
      (option) => isInspectionReviewer || !REVIEWER_STATUSES.has(option.code),
    ),
    initial: !isInspectionReviewer && REVIEWER_STATUSES.has(stored) ? '' : stored,
  };
};
