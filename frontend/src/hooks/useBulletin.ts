import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import useDebounce from '@/hooks/useDebounce';

import type { BulletinResponse } from '@/services/bulletin.service';

import API from '@/services/APIs';

export const BULLETIN_QUERY_KEY = 'bulletin';

/**
 * The bulletin. Kept a minute: it changes when an administrator saves it, and the save refreshes it
 * here at once.
 */
export const useBulletin = () =>
  useQuery({
    queryKey: [BULLETIN_QUERY_KEY],
    queryFn: () => API.bulletin.bulletin(),
    staleTime: 60_000,
    // A notice: its absence stops nothing, so a failure is not retried at the user.
    retry: false,
  });

/** Saves a new message — an administrator's — and shows it wherever the bulletin is. */
export const useSaveBulletin = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (message: string) => API.bulletin.saveBulletin(message),
    onSuccess: (saved: BulletinResponse) => queryClient.setQueryData([BULLETIN_QUERY_KEY], saved),
  });
};

/** How long typing pauses before the preview is asked for. */
export const BULLETIN_PREVIEW_DEBOUNCE_MS = 300;

/**
 * A draft as Save would keep it, for the editor's live preview: asked of the server once typing
 * pauses, so it is made safe by the same rules as Save. The last preview stays while the next is on
 * its way, rather than blanking at every pause. Nothing is asked for an empty draft.
 */
export const useBulletinPreview = (draft: string) => {
  const debounced = useDebounce(draft, BULLETIN_PREVIEW_DEBOUNCE_MS);
  return useQuery({
    queryKey: [BULLETIN_QUERY_KEY, 'preview', debounced],
    queryFn: () => API.bulletin.previewBulletin(debounced),
    enabled: debounced.trim() !== '',
    staleTime: Infinity,
    placeholderData: keepPreviousData,
    retry: false,
  });
};
