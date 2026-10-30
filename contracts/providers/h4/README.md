# H4 provider fixtures

These are deliberately synthetic, cleaned responses with the provider field names and table shapes. They contain invented prices/dates and no live capture, credentials, headers, personal locations or client IDs. They are never served as working API snapshots.

- `open-meteo.json`: explicit SI units, seven local calendar days across a DST boundary, negative temperature, NULL and unknown WMO code. The suite also derives a negative UTC offset and malformed variants.
- `cbr-current.xml` / `cbr-previous.xml`: Value/Nominal, a gap in publication dates and exact decimal division. DTD/entity, duplicate, date and numeric failures are derived by the suite.
- `moex.json`: named ISS tables, a closed trading state, separate trade/session dates, an incompatible currency in one batch member, 12-digit decimal prices and a JSON integer above 2^53. The suite also checks absent/zero/unconfirmed trade fields.

Consumers: `server/tests/h4-weather.php`, `h4-cbr.php`, `h4-moex.php`. Run `deploy/h4/check.ps1` for the complete server regression. Real host checks are separate and never replace these deterministic normalization tests.
