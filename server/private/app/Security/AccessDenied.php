<?php
declare(strict_types=1);
namespace MediaCentre\Security;
final class AccessDenied extends \RuntimeException
{
    public function __construct(public readonly int $status){parent::__construct('Access denied.');}
}
