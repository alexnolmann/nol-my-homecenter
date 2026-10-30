<?php
declare(strict_types=1);
namespace MediaCentre\Providers;
final class HttpResult
{
    public function __construct(public readonly string $body,public readonly string $fetchedAt,public readonly ?string $expiresAt=null){}
}
