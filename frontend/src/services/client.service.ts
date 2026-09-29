import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { ClientSuggestion } from '@/types/client';

import { HttpClient, type APIConfig } from '@/config/api/types';

/**
 * Which clients a lookup may suggest. There is no unscoped option: a suggestion the user cannot
 * act on offers a filter guaranteed to match nothing, and they have no way to tell that from a
 * filter that simply found nothing.
 */
export type ClientScope = 'MAINTAINERS' | 'ROAD_FILE_HOLDERS';

/**
 * The Forest Client lookup behind the Designated Maintainer field, backed by `/api/v1/clients`.
 */
export class ClientService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  /**
   * Suggestions for a term the user is part-way through typing.
   *
   * <p>The backend decides what the term means — all digits is a client number, anything else is
   * matched against client name, division name and city — so nothing is parsed here. It answers a
   * term it cannot match with an empty list rather than an error, which is what lets this be called
   * on a keystroke.
   *
   * <p>The scope decides which set is searched, not how the term is read — all digits is a client
   * number and anything else is a name, either side of it. Whichever set, every suggestion leads
   * somewhere: a maintainer has sites, a road-file holder has roads.
   */
  searchClients(
    term: string,
    scope: ClientScope = 'MAINTAINERS',
  ): CancelablePromise<ClientSuggestion[]> {
    return this.doRequest<ClientSuggestion[]>(this.config, {
      method: 'GET',
      url: '/v1/clients',
      query: { term, scope },
    });
  }

  /**
   * The locations of one client that actually maintain a site.
   *
   * <p>Fills the Location filter beside the client on Site Search. Narrowed to locations in use:
   * offering one no site names would give a filter guaranteed to match nothing, which reads as a
   * broken search rather than an empty one.
   */
  clientLocations(clientNumber: string): CancelablePromise<ClientSuggestion[]> {
    return this.doRequest<ClientSuggestion[]>(this.config, {
      method: 'GET',
      url: '/v1/clients/locations',
      query: { clientNumber },
    });
  }
}
