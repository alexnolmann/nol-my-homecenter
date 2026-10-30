<?php
declare(strict_types=1);
namespace MediaCentre\Providers;
interface Transport {public function get(string $url): HttpResult;}
