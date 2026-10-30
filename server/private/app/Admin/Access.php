<?php
declare(strict_types=1);
namespace MediaCentre\Admin;
use MediaCentre\Shared\AppConfig;

final class Access
{
    public static function allowed(AppConfig $config,array $server): bool
    {
        if (($server['MC_ADMIN_ALLOWED'] ?? '')!=='1') return false;
        if (self::isSecure($server)) return true;
        $host=$config->values['transport']['http_host'] ?? null;
        // Only a server-side opt-in and the exact configured virtual host enable HTTP.
        // Forwarded headers never select the transport or grant administrator access.
        return is_string($host) && $host!==''
            && in_array(strtolower($server['HTTP_HOST'] ?? ''),[strtolower($host),strtolower($host).':80'],true);
    }
    public static function isSecure(array $server): bool {return ($server['HTTPS'] ?? '')==='on';}
}
