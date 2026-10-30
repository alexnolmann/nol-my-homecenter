<?php
declare(strict_types=1);
namespace MediaCentre\Jobs;
use MediaCentre\Storage\Database;
final class CollectionStatus
{
    public static function read(Database $db): array
    {
        $current=[];foreach(CollectionPlanner::describe($db) as $target)$current[$target['key']]=hash('sha256',Database::json($target['context']));
        $rows=$db->all("SELECT t.target_key,t.kind,t.configuration_hash,t.enabled,t.last_attempt_at,t.last_success_at,t.last_result,t.last_error_code,t.next_due_at,t.failure_count,j.status job_status,COALESCE(l.name,i.symbol,'USD / EUR / CNY') label,i.board,w.fetched_at weather_fetched,w.data_as_of weather_as_of,q.fetched_at quote_fetched,q.quote_as_of,q.effective_date FROM collection_targets t LEFT JOIN jobs j ON j.id=t.job_id LEFT JOIN locations l ON t.kind='weather' AND l.id=t.object_id LEFT JOIN finance_instruments i ON t.kind='moex' AND i.id=t.object_id LEFT JOIN weather_snapshots w ON w.location_id=l.id AND w.location_revision=l.revision LEFT JOIN finance_quotes q ON q.instrument_id=i.id AND q.instrument_revision=i.revision ORDER BY t.kind,t.target_key");
        $result=[];foreach($rows as $row){if(!$row['enabled'] || !isset($current[$row['target_key']]))continue;$valid=hash_equals($current[$row['target_key']],bin2hex($row['configuration_hash']));unset($row['configuration_hash']);$row['current_configuration']=$valid;
            if($row['kind']==='cbr'){$source=substr($row['target_key'],4);$dates=$db->one('SELECT MIN(q.fetched_at) fetched_at,MIN(q.effective_date) effective_date FROM finance_quotes q JOIN finance_instruments i ON i.id=q.instrument_id AND i.revision=q.instrument_revision WHERE i.integration_id=? AND i.enabled=1',[$source]);$row['quote_fetched']=$dates['fetched_at'];$row['effective_date']=$dates['effective_date'];}
            if(!$valid){$row['last_success_at']=null;$row['last_result']=null;$row['last_error_code']=null;}$result[]=$row;
        }return $result;
    }
    public static function refresh(Database $db,string $key): void
    {
        if(!preg_match('/^(weather|cbr|moex):[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/D',$key))throw new \InvalidArgumentException('Collection target required.');
        $rows=self::read($db);$match=null;foreach($rows as $row)if($row['target_key']===$key)$match=$row;
        if(!$match || !$match['current_configuration'])throw new \OutOfBoundsException('Wait for configuration planning.');
        (new CollectionQueue($db))->request($key);
    }
}
