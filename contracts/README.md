# API contracts

JSON Schema and shared synthetic scenarios live under `v1`. The Docker build copies them outside the public web root.

Client-owned preferences use `PATCH /api/v1/client/preferences`. Android API 19 clients may use `POST` on that same path with the same partial JSON body. Both methods require bearer identity, `preferences:write`, `revision` and `If-Match`; both reject revoked clients, foreign fields and stale revisions. This compatibility route is not a general HTTP method override and does not authorize administrator cookies.

Station objects may include `genres` as separate labels; the original joined `genre` string remains for older clients. Paginated catalogues include `available_genres` from the complete permitted catalogue, independent of search, active genre and favorites filters. `genre` query matching uses membership in the separate labels. `total`, `limit` and `offset` describe the filtered page.
