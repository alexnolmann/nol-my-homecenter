<?php
declare(strict_types=1);
namespace MediaCentre\Providers;
final class ProviderError extends \RuntimeException
{
    public function __construct(public readonly string $reason,public readonly ?int $retryAfter=null){parent::__construct('Provider request failed: '.$reason);}
}
