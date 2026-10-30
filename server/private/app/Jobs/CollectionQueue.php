<?php
declare(strict_types=1);
namespace MediaCentre\Jobs;
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Uuid;
final class CollectionQueue
{
    public function __construct(private Database $db){}
    public function sync(array $descriptions): void
    {
        $this->db->transaction(function()use($descriptions):void{
            $this->db->query('UPDATE collection_targets SET enabled=0');
            foreach($descriptions as $target){
                if(!in_array($target['kind'],['weather','cbr','moex'],true) || $target['key']!==$target['kind'].':'.\MediaCentre\Validation\Input::uuid($target['id']))throw new \InvalidArgumentException('Collection identity required.');
                $context=Database::json($target['context']);if(strlen($context)>65536)throw new \InvalidArgumentException('Bounded collection context required.');$hash=hash('sha256',$context,true);
                $this->db->query('INSERT INTO collection_targets(target_key,kind,object_id,configuration_hash,context,next_due_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE next_due_at=IF(configuration_hash<>?,UTC_TIMESTAMP(3),next_due_at),failure_count=IF(configuration_hash<>?,0,failure_count),last_error_code=IF(configuration_hash<>?,NULL,last_error_code),last_success_at=IF(configuration_hash<>?,NULL,last_success_at),last_attempt_at=IF(configuration_hash<>?,NULL,last_attempt_at),last_result=IF(configuration_hash<>?,NULL,last_result),context=?,configuration_hash=?,enabled=1',[$target['key'],$target['kind'],$target['id'],$hash,$context,$hash,$hash,$hash,$hash,$hash,$hash,$context,$hash]);
            }
        });
    }
    public function enqueue(int $limit=30,?string $key=null): int
    {
        if($limit<1 || $limit>100)throw new \InvalidArgumentException('Bounded scheduling pass required.');
        return $this->db->transaction(function()use($limit,$key):int{
            $rows=$this->db->all('SELECT * FROM collection_targets WHERE enabled=1 AND job_id IS NULL AND next_due_at<=UTC_TIMESTAMP(3)'.($key===null?'':' AND target_key=?').' ORDER BY (kind=\'weather\') DESC,next_due_at,target_key LIMIT '.$limit.' FOR UPDATE SKIP LOCKED',$key===null?[]:[$key]);$groups=[];
            foreach($rows as $row){$context=json_decode($row['context'],true,64,JSON_THROW_ON_ERROR);$group=$row['kind']==='moex'?'moex:'.$context['integration_id'].':'.$context['board']:$row['target_key'];$groups[$group][]=['key'=>$row['target_key'],'hash'=>bin2hex($row['configuration_hash']),'context'=>$context];}
            $count=0;
            foreach($groups as $group=>$targets)foreach(array_chunk($targets,\MediaCentre\Finance\MoexQuotesProvider::BATCH) as $batch){
                $id=Uuid::random();$kind=explode(':',$group,2)[0];$dedupe='collect:'.hash('sha256',implode(',',array_column($batch,'key')));
                $this->db->query("INSERT INTO jobs(id,queue,type,dedupe_key,payload,status,run_after) VALUES(?,'collection',?,?,?,'queued',UTC_TIMESTAMP(3))",[$id,$kind,$dedupe,Database::json(['targets'=>$batch])]);
                foreach($batch as $target)$this->db->query('UPDATE collection_targets SET job_id=? WHERE target_key=?',[$id,$target['key']]);$count++;
            }
            return $count;
        });
    }
    public function claim(string $owner): ?array
    {
        \MediaCentre\Validation\Input::uuid($owner);
        return $this->db->transaction(function()use($owner):?array{
            $row=$this->db->one("SELECT * FROM jobs WHERE queue='collection' AND ((status='queued' AND run_after<=UTC_TIMESTAMP(3)) OR (status='leased' AND lease_until<=UTC_TIMESTAMP(3))) ORDER BY (type='weather') DESC,run_after,id LIMIT 1 FOR UPDATE SKIP LOCKED");
            if(!$row)return null;
            $this->db->query("UPDATE jobs SET status='leased',lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 120 SECOND),attempts=attempts+1 WHERE id=?",[$owner,$row['id']]);
            $this->db->query('UPDATE collection_targets SET last_attempt_at=UTC_TIMESTAMP(3) WHERE job_id=?',[$row['id']]);
            $row['owner']=$owner;return $row;
        });
    }
    public static function delay(int $failures,?int $retryAfter=null): int {return max(min(3600,30*(2**min(7,max(0,$failures-1)))),min(86400,max(0,$retryAfter ?? 0)));}
    public function finish(array $job,array $outcomes,?int $retryAfter=null): void
    {
        $this->db->transaction(function()use($job,$outcomes,$retryAfter):void{
            $row=$this->db->one('SELECT status,lease_owner FROM jobs WHERE id=? FOR UPDATE',[$job['id']]);
            if(!$row || $row['status']!=='leased' || $row['lease_owner']!==$job['owner'] || !(int)$this->db->query('SELECT lease_until>UTC_TIMESTAMP(3) FROM jobs WHERE id=?',[$job['id']])->fetchColumn())throw new LeaseLost('Collection lease lost.');
            $targets=json_decode($job['payload'],true,64,JSON_THROW_ON_ERROR)['targets'];$error=null;
            foreach($targets as $captured){
                $target=$this->db->one('SELECT * FROM collection_targets WHERE target_key=? FOR UPDATE',[$captured['key']]);
                if(!$target || $target['job_id']!==$job['id'])continue;
                if(bin2hex($target['configuration_hash'])!==$captured['hash']){$this->db->query('UPDATE collection_targets SET job_id=NULL,next_due_at=UTC_TIMESTAMP(3) WHERE target_key=?',[$captured['key']]);continue;}
                $outcome=$outcomes[$captured['key']] ?? ['status'=>'failed','error'=>'INCOMPLETE_RESULT'];$success=$outcome['status']==='success';$code=$success?null:($outcome['error'] ?? 'PARTIAL_RESPONSE');
                if($code!==null && !preg_match('/^[A-Z_]{1,64}$/D',$code))throw new \InvalidArgumentException('Safe collection error required.');
                $failures=$success?0:min(1000,(int)$target['failure_count']+1);$seconds=$success?match($target['kind']){'weather'=>900,'cbr'=>21600,'moex'=>900}:self::delay($failures,$retryAfter);
                $this->db->query('UPDATE collection_targets SET job_id=NULL,last_success_at=IF(?,UTC_TIMESTAMP(3),last_success_at),last_result=?,last_error_code=?,failure_count=?,next_due_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL ? SECOND) WHERE target_key=?',[(int)($success || ($outcome['has_data'] ?? false)),$success?'success':(($outcome['has_data'] ?? false)?'partial':'failed'),$code,$failures,$seconds,$captured['key']]);$error ??=$code;
            }
            $this->db->query("UPDATE jobs SET status=?,dedupe_key=NULL,lease_until=NULL,lease_owner=NULL,last_error_code=? WHERE id=?",[$error===null?'done':'failed',$error,$job['id']]);
        });
    }
    public function request(string $key): void
    {
        $this->db->transaction(function()use($key):void{if(!$this->db->one('SELECT target_key FROM collection_targets WHERE target_key=? AND enabled=1 FOR UPDATE',[$key]))throw new \OutOfBoundsException('Collection target unavailable.');$this->db->query("UPDATE collection_targets SET next_due_at=UTC_TIMESTAMP(3) WHERE target_key=? AND job_id IS NULL AND (last_attempt_at IS NULL OR last_attempt_at<=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 30 SECOND)) AND (last_error_code IS NULL OR last_error_code<>'RATE_LIMITED' OR next_due_at<=UTC_TIMESTAMP(3))",[$key]);if($this->db->adminActor!==null)$this->db->collectionRequestAudit($key);});
        $this->enqueue(1,$key);
    }
    public function cleanup(): void {$this->db->query("DELETE FROM jobs WHERE queue='collection' AND status IN ('done','failed') AND updated_at<DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 7 DAY) LIMIT 100");}
}
