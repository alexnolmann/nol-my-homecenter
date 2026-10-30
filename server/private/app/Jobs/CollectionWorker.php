<?php
declare(strict_types=1);
namespace MediaCentre\Jobs;
use MediaCentre\Storage\Database;
use MediaCentre\Providers\{Transport,ProviderError};
use MediaCentre\Shared\Uuid;
final class CollectionWorker
{
    public function __construct(private Database $db,private Transport $http){}
    public function run(int $limit=3,?callable $progress=null,?callable $shouldStop=null): int
    {
        if($limit<1 || $limit>10)throw new \InvalidArgumentException('Bounded worker pass required.');$queue=new CollectionQueue($this->db);$count=0;
        for($n=0;$n<$limit;$n++){
            if($shouldStop!==null && $shouldStop())break;
            if($progress!==null)$progress($count);
            $job=$queue->claim(Uuid::random());if(!$job)break;$count++;$outcomes=[];$retry=null;
            $this->db->jobActor=['id'=>$job['id'],'owner'=>$job['owner']];
            try{
                $targets=json_decode($job['payload'],true,64,JSON_THROW_ON_ERROR)['targets'];$active=[];
                foreach($targets as $target){
                    $current=$this->db->one('SELECT enabled,configuration_hash,job_id FROM collection_targets WHERE target_key=?',[$target['key']]);
                    if(!$current || !$current['enabled'] || $current['job_id']!==$job['id'] || bin2hex($current['configuration_hash'])!==$target['hash']){$outcomes[$target['key']]=['status'=>'failed','error'=>'CONFIGURATION_CHANGED'];continue;}$active[]=$target;
                }
                if($active){
                    if($job['type']==='weather'){$ok=(new \MediaCentre\Weather\WeatherCollector($this->db,$this->http))->collect($active[0]['context']);$outcomes[$active[0]['key']]=$ok?['status'=>'success','has_data'=>true]:['status'=>'failed','error'=>'CONFIGURATION_CHANGED'];}
                    elseif($job['type']==='cbr'){$result=(new \MediaCentre\Finance\CbrCollector($this->db,$this->http))->collect($active[0]['context']['instruments']);$outcomes[$active[0]['key']]=['status'=>$result['partial'] || $result['invalidated']?'partial':'success','error'=>$result['partial']?'REFERENCE_OR_PRICE_UNAVAILABLE':($result['invalidated']?'CONFIGURATION_CHANGED':null),'has_data'=>$result['saved']>0];}
                    elseif($job['type']==='moex'){$result=(new \MediaCentre\Finance\MoexCollector($this->db,$this->http))->collect(array_column($active,'context'));foreach($active as $target)$outcomes[$target['key']]=$result['items'][$target['context']['id']];}
                    else throw new \LogicException('Unknown collection type.');
                }
                $queue->finish($job,$outcomes);
            }catch(LeaseLost){/* Another worker owns this job; its results and targets are untouched. */}
            catch(\Throwable $error){
                $code=$error instanceof ProviderError?$error->reason:'COLLECTION_ERROR';$retry=$error instanceof ProviderError?$error->retryAfter:null;
                $targets=json_decode($job['payload'],true,64,JSON_THROW_ON_ERROR)['targets'];foreach($targets as $target)$outcomes[$target['key']]=['status'=>'failed','error'=>$code];
                try{$queue->finish($job,$outcomes,$retry);}catch(LeaseLost){}
            }finally{$this->db->jobActor=null;if($progress!==null)$progress($count);}
        }
        return $count;
    }
}
