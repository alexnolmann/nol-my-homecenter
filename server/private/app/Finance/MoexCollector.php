<?php
declare(strict_types=1);
namespace MediaCentre\Finance;
use MediaCentre\Providers\{Transport,ProviderError};
use MediaCentre\Storage\Database;
final class MoexCollector
{
    public function __construct(private Database $db,private Transport $http){}
    public function collect(array $instruments): array
    {
        if($this->db->pdo->inTransaction())throw new \LogicException('Provider request must run outside SQL transaction.');
        if(!$instruments)return ['saved'=>0,'missing'=>0,'invalidated'=>0];
        $board=$instruments[0]['board'];foreach($instruments as $instrument)if($instrument['board']!==$board)throw new \InvalidArgumentException('One board per batch required.');
        $repo=new QuoteRepository($this->db);
        try{$result=(new MoexQuotesProvider($this->http))->fetch($board,array_column($instruments,'symbol'));}
        catch(ProviderError $error){foreach($instruments as $instrument)$repo->failure($instrument['id'],(int)$instrument['revision'],$error->reason,gmdate('Y-m-d\TH:i:s\Z'));throw $error;}
        $saved=0;$missing=0;$invalidated=0;$items=[];
        foreach($instruments as $instrument){
            $quote=$result['quotes'][$instrument['symbol']] ?? null;
            if($quote===null || $quote['price']===null){$missing++;$code=$result['errors'][$instrument['symbol']] ?? 'PRICE_UNAVAILABLE';$repo->failure($instrument['id'],(int)$instrument['revision'],$code,gmdate('Y-m-d\TH:i:s\Z'));$items[$instrument['id']]=['status'=>'failed','error'=>$code];continue;}
            if($repo->save($instrument['id'],(int)$instrument['revision'],$quote)){$saved++;$items[$instrument['id']]=['status'=>'success','has_data'=>true];}else{$invalidated++;$items[$instrument['id']]=['status'=>'failed','error'=>'CONFIGURATION_CHANGED'];}
        }
        return ['saved'=>$saved,'missing'=>$missing,'invalidated'=>$invalidated,'items'=>$items];
    }
}
