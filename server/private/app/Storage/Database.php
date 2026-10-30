<?php
declare(strict_types=1);
namespace MediaCentre\Storage;
final class Database
{
    public ?array $clientActor=null;
    public ?string $adminActor=null;
    public ?int $adminAuthRevision=null;
    public ?array $jobActor=null;
    public function __construct(public readonly \PDO $pdo){}
    public function query(string $sql,array $values=[]): \PDOStatement {$statement=$this->pdo->prepare($sql);$statement->execute($values);return $statement;}
    public function one(string $sql,array $values=[]): ?array {$row=$this->query($sql,$values)->fetch();return $row===false?null:$row;}
    public function all(string $sql,array $values=[]): array {return $this->query($sql,$values)->fetchAll();}
    public function transaction(callable $operation): mixed
    {
        if($this->pdo->inTransaction())throw new \LogicException('Nested transaction is not allowed.');
        for($attempt=0;;$attempt++) {
            $this->pdo->beginTransaction();
            try {
                if($this->jobActor!==null){
                    $job=$this->one('SELECT status,lease_owner FROM jobs WHERE id=? FOR SHARE',[$this->jobActor['id']]);
                    if(!$job || $job['status']!=='leased' || $job['lease_owner']!==$this->jobActor['owner'] || !(int)$this->query('SELECT lease_until>UTC_TIMESTAMP(3) FROM jobs WHERE id=?',[$this->jobActor['id']])->fetchColumn())throw new \MediaCentre\Jobs\LeaseLost('Collection lease lost.');
                }
                if($this->clientActor!==null){
                    $row=$this->one('SELECT scopes,token_hash,revoked_at,auth_revision FROM clients WHERE id=? FOR SHARE',[$this->clientActor['id']]);
                    if(!$row || $row['revoked_at']!==null || $row['token_hash']!==$this->clientActor['token_hash'])throw new \MediaCentre\Security\AccessDenied(401);
                    if(isset($this->clientActor['auth_revision']) && (int)$row['auth_revision']!==$this->clientActor['auth_revision'])throw new \MediaCentre\Security\AccessDenied(403);
                    if(array_diff($this->clientActor['required'],json_decode($row['scopes'],true,32,JSON_THROW_ON_ERROR)))throw new \MediaCentre\Security\AccessDenied(403);
                    if(isset($this->clientActor['server_id'])){
                        $server=$this->one('SELECT server_id FROM app_state WHERE id=1 FOR SHARE');
                        if(!$server || $server['server_id']!==$this->clientActor['server_id'])throw new \MediaCentre\Security\AccessDenied(401);
                    }
                }
                if($this->adminActor!==null && $this->adminAuthRevision!==null){
                    $user=$this->one('SELECT enabled,auth_revision FROM admin_users WHERE id=? FOR SHARE',[$this->adminActor]);
                    if(!$user || !$user['enabled'] || (int)$user['auth_revision']!==$this->adminAuthRevision)throw new \MediaCentre\Security\AccessDenied(401);
                }
                $result=$operation($this);$this->pdo->commit();return $result;
            }
            catch(\Throwable $error){
                if($this->pdo->inTransaction())$this->pdo->rollBack();
                if($attempt<2 && $error instanceof \PDOException && in_array((int)($error->errorInfo[1] ?? 0),[1205,1213],true)){usleep(20000*($attempt+1));continue;}
                throw $error;
            }
        }
    }
    public function lockSettings(string $client,int $expected,string $revision='revision'): array
    {
        if(!in_array($revision,['revision','finance_list_revision','favorites_revision'],true))throw new \LogicException('Invalid revision column.');
        if(!$this->pdo->inTransaction())throw new \LogicException('Lock requires transaction.');
        $row=$this->one('SELECT * FROM client_settings WHERE client_id=? FOR UPDATE',[$client]);
        if(!$row)throw new \OutOfBoundsException('Client settings not found.');
        if((int)$row[$revision]!==$expected || $expected<1)throw new RevisionConflict('Revision conflict.');
        if($expected===PHP_INT_MAX)throw new \OverflowException('Revision exhausted.');
        return $row;
    }
    public static function json(array $value): string {return json_encode($value,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);}
    public function audit(string $action,string $type,string $id,array $fields,?int $old,int $new): void
    {
        if(!$this->pdo->inTransaction())throw new \LogicException('Audit requires transaction.');
        if(!in_array($action,['create','update','replace','rights','delete'],true) || !in_array($type,['client','settings','location','station','instrument','integration','favorites','watchlist','photoframe','music'],true))throw new \InvalidArgumentException('Unknown audit action/object.');
        \MediaCentre\Validation\Input::uuid($id);
        $allowed=['name','latitude','longitude','timezone','stream_url','genres','country_code','enabled','identity','type','settings','credentials','scopes','location_id','selected_station_id','radio_volume','display_title','items'];
        foreach($fields as $field)if(!is_string($field) || !in_array($field,$allowed,true))throw new \InvalidArgumentException('Unsafe audit field.');
        $this->query('INSERT INTO audit_events(id,admin_id,client_id,action,object_type,object_id,changes) VALUES(?,?,?,?,?,?,?)',[\MediaCentre\Shared\Uuid::random(),$this->adminActor,$this->clientActor['id'] ?? null,$action,$type,$id,self::json(['fields'=>array_values($fields),'old_revision'=>$old,'new_revision'=>$new])]);
    }
    public function securityAudit(string $action,string $type,string $id,?string $admin,?int $old,int $new): void
    {
        if(!$this->pdo->inTransaction())throw new \LogicException('Audit requires transaction.');
        if(!in_array($action,['admin_create','admin_password','pair_issue','pair_use','client_revoke'],true) || !in_array($type,['admin','pairing','client'],true))throw new \InvalidArgumentException('Unknown security audit.');
        $this->query('INSERT INTO audit_events(id,admin_id,action,object_type,object_id,changes) VALUES(?,?,?,?,?,?)',[\MediaCentre\Shared\Uuid::random(),$admin,$action,$type,$id,self::json(['old_revision'=>$old,'new_revision'=>$new])]);
    }
    public function collectionRequestAudit(string $key): void
    {
        if(!$this->pdo->inTransaction() || !preg_match('/^(weather|cbr|moex):([a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12})$/D',$key,$m))throw new \InvalidArgumentException('Collection audit identity required.');
        $this->query('INSERT INTO audit_events(id,admin_id,action,object_type,object_id,changes) VALUES(?,?,?,?,?,?)',[\MediaCentre\Shared\Uuid::random(),$this->adminActor,'refresh','collection',$m[2],self::json(['fields'=>[]])]);
    }
}
