<?php
declare(strict_types=1);
namespace MediaCentre\Shared;

final class FixtureRepository
{
    public function __construct(private string $directory) {}
    public function load(string $scenario): array
    {
        if (!in_array($scenario, ['success','empty','partial','stale'], true)) throw new \InvalidArgumentException('Unknown scenario.');
        return json_decode((string) file_get_contents($this->directory . '/' . $scenario . '.json'), true, 512, JSON_THROW_ON_ERROR);
    }
}
