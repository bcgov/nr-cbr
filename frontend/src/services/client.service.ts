import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { ClientSuggestion } from '@/types/client';

import { HttpClient, type APIConfig } from '@/config/api/types';

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
   * <p>Only maintainers already recorded on a site come back, so every suggestion leads to at least
   * one result.
   */
  searchClients(term: string): CancelablePromise<ClientSuggestion[]> {
    return this.doRequest<ClientSuggestion[]>(this.config, {
      method: 'GET',
      url: '/v1/clients',
      query: { term },
    });
  }
}
