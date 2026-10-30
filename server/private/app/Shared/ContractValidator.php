<?php
declare(strict_types=1);
namespace MediaCentre\Shared;

final class ContractValidator
{
    private object $schema;
    private \Opis\JsonSchema\Validator $validator;
    public function __construct(string $path)
    {
        $this->schema = json_decode((string) file_get_contents($path), false, 512, JSON_THROW_ON_ERROR);
        $this->validator = new \Opis\JsonSchema\Validator();
    }
    public function valid(string $definition, mixed $data): bool
    {
        if (!isset($this->schema->{'$defs'}->{$definition})) throw new \InvalidArgumentException('Unknown contract definition.');
        $schema = (object) ['$schema'=>$this->schema->{'$schema'}, '$defs'=>$this->schema->{'$defs'}, '$ref'=>'#/$defs/' . $definition];
        return $this->validator->validate($data, $schema)->isValid();
    }
}
