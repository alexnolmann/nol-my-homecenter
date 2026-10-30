# Shared synthetic fixtures

Each JSON file contains bootstrap, weather, finance, preferences, stations and metadata. The PHP API derives catalogue/detail/dashboard from these exact files; Android receives them through API rather than maintaining a separate sample set.

- success: seven days, USD/EUR/CNY in RUB, SBER/GAZP/LKOH on TQBR, three stations;
- empty: no forecast/instruments/stations, NULL current measurement and selected station;
- partial: NULL temperature/precipitation/price/change and one disabled station;
- stale: older snapshots/trades with expired valid_until and explicit stale state.

All values are fabricated. `meta.test_data=true` is mandatory and visible in the client/admin UI. Source labels are explicitly synthetic. Audio URLs use example.invalid; no stream is played in H1. Fixed fetch dates preserve reproducibility, independently of trade/rate dates. `9876.543210987` catches loss of precision through float conversions. Additive future fields exist in bootstrap, days, metadata and instruments; they must not crash the client. Missing measurements are NULL, not zero.
