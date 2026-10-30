<?php
declare(strict_types=1);

namespace MediaCentre\H0;

use RuntimeException;

/** Compatibility checks only; no HTTP route, database writes or production scheduler. */
final class SourceProbe
{
    private string $directory;
    public array $requests = [];

    public function __construct(string $projectRoot)
    {
        $this->directory = $projectRoot . '/.local/h0/providers';
        if (!is_dir($this->directory) && !mkdir($this->directory, 0700, true)) {
            throw new RuntimeException('Cannot create private evidence directory.');
        }
    }

    public function fetch(string $url, string $name): string
    {
        $parts = parse_url($url);
        if (($parts['scheme'] ?? '') !== 'https' || !in_array($parts['host'] ?? '', [
            'geocoding-api.open-meteo.com', 'api.open-meteo.com', 'www.cbr.ru', 'iss.moex.com',
        ], true) || !preg_match('/^[a-z0-9-]+$/D', $name)) {
            throw new RuntimeException('Expected a fixed official HTTPS provider.');
        }
        $body = '';
        $curl = curl_init($url);
        curl_setopt_array($curl, [
            CURLOPT_CONNECTTIMEOUT => 6,
            CURLOPT_TIMEOUT => 25,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
            CURLOPT_USERAGENT => 'MediaCentre-H0/0.1 (personal compatibility probe)',
            CURLOPT_ENCODING => '',
            CURLOPT_WRITEFUNCTION => static function ($curl, string $chunk) use (&$body): int {
                if (strlen($body) + strlen($chunk) > 2 * 1024 * 1024) return 0;
                $body .= $chunk;
                return strlen($chunk);
            },
        ]);
        $ok = curl_exec($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        $verification = (int) curl_getinfo($curl, CURLINFO_SSL_VERIFYRESULT);
        $error = curl_error($curl);
        $elapsed = curl_getinfo($curl, CURLINFO_TOTAL_TIME);
        curl_close($curl);
        if ($ok === false || $status !== 200 || $verification !== 0) {
            throw new RuntimeException("$name: HTTP $status, TLS verification $verification; $error");
        }
        file_put_contents($this->directory . '/' . $name . '.raw', $body);
        $this->requests[$name] = [
            'url' => $url, 'http_status' => $status, 'tls_verify_result' => $verification,
            'received_at' => gmdate('c'), 'bytes' => strlen($body),
            'sha256' => hash('sha256', $body), 'elapsed_seconds' => $elapsed,
        ];
        return $body;
    }

    public function json(string $url, string $name): array
    {
        return json_decode($this->fetch($url, $name), true, 64, JSON_THROW_ON_ERROR);
    }

    public static function rows(array $response, string $block): array
    {
        if (!isset($response[$block]['columns'], $response[$block]['data'])) {
            throw new RuntimeException("ISS block missing: $block");
        }
        $columns = $response[$block]['columns'];
        return array_map(static fn (array $row): array => array_combine($columns, $row), $response[$block]['data']);
    }

    public function weather(): array
    {
        $geo = $this->json('https://geocoding-api.open-meteo.com/v1/search?' . http_build_query([
            'name' => 'Saint Petersburg', 'countryCode' => 'RU', 'count' => 10, 'language' => 'ru', 'format' => 'json',
        ]), 'weather-geocoding');
        $matches = array_values(array_filter($geo['results'] ?? [], static fn (array $row): bool =>
            ($row['country_code'] ?? '') === 'RU' && ($row['timezone'] ?? '') === 'Europe/Moscow'
            && ($row['feature_code'] ?? '') === 'PPLA' && ($row['population'] ?? 0) > 1_000_000
        ));
        if (count($matches) !== 1) throw new RuntimeException('Saint Petersburg must resolve unambiguously.');
        $place = array_intersect_key($matches[0], array_flip(['id', 'name', 'latitude', 'longitude', 'timezone', 'country_code', 'admin1']));
        $forecast = $this->json('https://api.open-meteo.com/v1/forecast?' . http_build_query([
            'latitude' => $place['latitude'], 'longitude' => $place['longitude'],
            'current' => 'temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m',
            'daily' => 'weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset',
            'timezone' => 'Europe/Moscow', 'forecast_days' => 7, 'wind_speed_unit' => 'ms',
        ]), 'weather-forecast');
        if (count($forecast['daily']['time'] ?? []) !== 7 || ($forecast['timezone'] ?? '') !== 'Europe/Moscow') {
            throw new RuntimeException('Expected seven forecast days in the chosen timezone.');
        }
        foreach (['weather_code', 'temperature_2m_max', 'temperature_2m_min', 'precipitation_probability_max', 'sunrise', 'sunset'] as $field) {
            if (count($forecast['daily'][$field] ?? []) !== 7) throw new RuntimeException("Weather field missing: $field");
        }
        return ['place' => $place, 'forecast' => $forecast];
    }

    public function currencies(): array
    {
        $requested = new \DateTimeImmutable('now', new \DateTimeZone('Europe/Moscow'));
        $raw = $this->fetch('https://www.cbr.ru/scripts/XML_daily.asp?' . http_build_query([
            'date_req' => $requested->format('d/m/Y'),
        ]), 'cbr-daily');
        if (stripos($raw, '<!DOCTYPE') !== false) throw new RuntimeException('Unexpected XML DTD.');
        libxml_use_internal_errors(true);
        $xml = simplexml_load_string($raw, \SimpleXMLElement::class, LIBXML_NONET);
        if ($xml === false || $xml->getName() !== 'ValCurs') throw new RuntimeException('Invalid CBR XML.');
        $date = \DateTimeImmutable::createFromFormat('!d.m.Y', (string) $xml['Date'], new \DateTimeZone('Europe/Moscow'));
        if ($date === false) throw new RuntimeException('Invalid CBR effective date.');
        $rates = [];
        foreach ($xml->Valute as $row) {
            $code = (string) $row->CharCode;
            if (!in_array($code, ['USD', 'EUR', 'CNY'], true)) continue;
            $nominal = (string) $row->Nominal;
            $value = str_replace(',', '.', (string) $row->Value);
            if (!preg_match('/^[1-9][0-9]*$/D', $nominal) || !preg_match('/^[0-9]+\.[0-9]+$/D', $value)) {
                throw new RuntimeException('Unexpected CBR decimal or nominal.');
            }
            $rates[$code] = [
                'base_currency' => $code, 'quote_currency' => 'RUB', 'nominal' => (int) $nominal,
                'source_value' => $value, 'rub_per_unit' => bcdiv($value, $nominal, 8),
                'effective_date' => $date->format('Y-m-d'), 'kind' => 'official_cbr_rate',
            ];
        }
        if (count($rates) !== 3) throw new RuntimeException('Expected USD, EUR and CNY in CBR response.');
        return ['requested_date' => $requested->format('Y-m-d'), 'effective_date' => $date->format('Y-m-d'), 'rates' => $rates];
    }

    public function shares(): array
    {
        $shares = [];
        foreach (['SBER', 'GAZP', 'LKOH'] as $symbol) {
            $response = $this->json("https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities/$symbol.json?iss.meta=on", 'moex-' . strtolower($symbol));
            $securities = self::rows($response, 'securities');
            $marketdata = self::rows($response, 'marketdata');
            if (count($securities) !== 1 || count($marketdata) !== 1
                || $securities[0]['SECID'] !== $symbol || $securities[0]['BOARDID'] !== 'TQBR'
                || $marketdata[0]['SECID'] !== $symbol || $marketdata[0]['BOARDID'] !== 'TQBR') {
                throw new RuntimeException("Unexpected security or board: $symbol");
            }
            $versions = self::rows($response, 'dataversion');
            if (count($versions) !== 1) throw new RuntimeException("Expected one ISS data version: $symbol");
            $shares[$symbol] = ['securities' => $securities[0], 'marketdata' => $marketdata[0], 'dataversion' => $versions[0]];
        }
        return $shares;
    }
}
