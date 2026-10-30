<?php
declare(strict_types=1);
namespace MediaCentre\Admin;
/** Public constant messages only; values and underlying exception text never escape. */
final class FieldErrors extends \InvalidArgumentException
{
    public function __construct(public readonly array $fields){parent::__construct('Check the highlighted fields.');}
}
