# Application boundaries

Composer maps `MediaCentre\` to this directory. The public entry point handles HTTP only; providers, storage and jobs stay outside the web root.

| Module | Responsibility |
| --- | --- |
| Shared | Configuration, clocks, revisions and common value types |
| Http | Routing, requests/responses, middleware and API serialization |
| Weather | Forecast snapshots, normalization and later provider adapter |
| Finance | Exact decimal quotes, CBR/MOEX dates and later provider adapters |
| Radio | Saved station catalogue and favorites; never audio proxying |
| Client | Bootstrap, preferences and heartbeat; pairing follows in H2/H3 |
| Admin | Server-rendered templates/menu; writes follow in H3 |
| Jobs | maintenance lifecycle; persistent queue follows in H2/H4 |
| Home | Reserved, disabled until H8 |
| H0 | Preserved hardware/provider diagnostic helpers |

Modules expose plain application values, independent of FastRoute. JSON Schema validation uses Opis; versions are fixed in composer.lock. External I/O is not performed while displaying fixture screens.
