<?php
declare(strict_types=1);
namespace MediaCentre\Finance;
use MediaCentre\Storage\Database;
use MediaCentre\Providers\{Transport,ProviderError};
final class CbrCollector
{
    public function __construct(private Database $db,private Transport $http){}
    public function collect(array $instruments,?string $requested=null): array
    {
        if($this->db->pdo->inTransaction())throw new \LogicException('Provider request must run outside SQL transaction.');
        $repository=new QuoteRepository($this->db);
        try{$result=(new CbrRatesProvider($this->http))->fetch($requested);}
        catch(ProviderError $error){foreach($instruments as $instrument)$repository->failure($instrument['id'],(int)$instrument['revision'],$error->reason,gmdate('Y-m-d\TH:i:s\Z'));throw $error;}
        $saved=0;$missing=0;$invalidated=0;$items=[];
        foreach($instruments as $instrument){
            $quote=$result['quotes'][$instrument['symbol']] ?? null;
            if($quote===null){$missing++;$repository->failure($instrument['id'],(int)$instrument['revision'],'PRICE_UNAVAILABLE',gmdate('Y-m-d\TH:i:s\Z'));$items[$instrument['id']]=['status'=>'failed','error'=>'PRICE_UNAVAILABLE'];continue;}
            if($repository->save($instrument['id'],(int)$instrument['revision'],$quote)){$saved++;$items[$instrument['id']]=['status'=>$quote['payload']['reference_error']===null?'success':'partial','error'=>$quote['payload']['reference_error'],'has_data'=>true];}
            else{$invalidated++;$items[$instrument['id']]=['status'=>'failed','error'=>'CONFIGURATION_CHANGED'];}
        }
        return ['saved'=>$saved,'missing'=>$missing,'invalidated'=>$invalidated,'partial'=>$result['partial'] || $missing>0,'items'=>$items];
    }
}
