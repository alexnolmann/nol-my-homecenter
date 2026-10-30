<?php
declare(strict_types=1);
namespace MediaCentre\Shared;

final class AppConfig
{
    public function __construct(public readonly array $values)
    {
        if (!in_array($values['environment'] ?? '', ['development', 'production'], true)) throw new \InvalidArgumentException('Unknown environment.');
        if (($values['features']['home'] ?? null) !== false) throw new \InvalidArgumentException('Home integration must remain disabled.');
        if ($values['environment'] === 'production' && ($values['fixtures'] ?? false)) throw new \InvalidArgumentException('Production cannot serve fixtures.');
        $httpHost=$values['transport']['http_host'] ?? null;
        if($httpHost!==null && (!is_string($httpHost) || !filter_var($httpHost,FILTER_VALIDATE_DOMAIN,FILTER_FLAG_HOSTNAME))) throw new \InvalidArgumentException('HTTP host must be a DNS name without scheme, path or port.');
    }

    public static function load(string $serverRoot): self
    {
        $private = $serverRoot . '/private/config';
        $local = is_file($private . '/local.php') ? require $private . '/local.php' : [];
        $environment = getenv('APP_ENV') ?: ($local['environment'] ?? 'production');
        if (!in_array($environment, ['development', 'production'], true)) throw new \RuntimeException('APP_ENV must be development or production.');
        $values = require $private . '/' . $environment . '.php';
        // Local development settings never override production environment invariants.
        if ($environment === 'development') $values = array_replace_recursive($values, $local);
        return new self($values);
    }

    public function isDevelopment(): bool { return $this->values['environment'] === 'development'; }
}
