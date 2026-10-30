<?php
declare(strict_types=1);
namespace MediaCentre\Security;
use MediaCentre\Storage\Database;
final class ClientAccess
{
    public const SCOPES=['weather:read','finance:read','radio:read','photoframe:read','music:read','favorites:write','preferences:read','preferences:write','heartbeat:write'];
    public function __construct(private Database $db){}
    public function authenticate(string $authorization): ?array
    {
        if(!preg_match('/^Bearer ([a-f0-9]{64})$/D',$authorization,$match))return null;
        $client=$this->db->one('SELECT id,scopes,auth_revision FROM clients WHERE token_hash=? AND revoked_at IS NULL',[hash('sha256',$match[1],true)]);
        if($client)$client['scopes']=json_decode($client['scopes'],true,32,JSON_THROW_ON_ERROR);
        return $client;
    }
}
