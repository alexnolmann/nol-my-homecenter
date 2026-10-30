<?php
declare(strict_types=1);
namespace MediaCentre\Security;
final class SecretStore
{
    public function __construct(private string $keyPath,private string $keyId='local-v1'){}
    public function seal(string $object,array $secrets): array
    {
        \MediaCentre\Validation\Input::uuid($object);
        $key=$this->key();$nonce=random_bytes(SODIUM_CRYPTO_SECRETBOX_NONCEBYTES);
        $plaintext=json_encode(['object'=>$object,'values'=>$secrets],JSON_THROW_ON_ERROR);
        if(strlen($plaintext)>16384)throw new \InvalidArgumentException('Secrets too large.');
        try{$encrypted=sodium_crypto_secretbox($plaintext,$nonce,$key);}finally{sodium_memzero($plaintext);sodium_memzero($key);}
        return ['credentials_ciphertext'=>$encrypted,'nonce'=>$nonce,'key_id'=>$this->keyId];
    }
    public function open(string $object,array $record): array
    {
        if(($record['key_id'] ?? null)!==$this->keyId || strlen($record['nonce'] ?? '')!==24)throw new \RuntimeException('Secret unavailable.');
        $key=$this->key();
        try{$plaintext=sodium_crypto_secretbox_open($record['credentials_ciphertext'],$record['nonce'],$key);}finally{sodium_memzero($key);}
        if($plaintext===false)throw new \RuntimeException('Secret integrity check failed.');
        try{
            $value=json_decode($plaintext,true,32,JSON_THROW_ON_ERROR);
            if(($value['object'] ?? null)!==$object || !is_array($value['values'] ?? null))throw new \RuntimeException('Secret binding mismatch.');
            return $value['values'];
        }finally{sodium_memzero($plaintext);}
    }
    private function key(): string
    {
        if(!is_file($this->keyPath) || filesize($this->keyPath)!==32)throw new \RuntimeException('External Sodium key missing.');
        $key=file_get_contents($this->keyPath);
        if($key===false || strlen($key)!==32)throw new \RuntimeException('External Sodium key invalid.');
        return $key;
    }
}
