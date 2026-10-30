<?php
declare(strict_types=1);
namespace MediaCentre\Jobs;
use MediaCentre\Storage\Database;
final class CollectionPlanner
{
    public static function describe(Database $db): array
    {
        $targets=[];
        $weather=(new \MediaCentre\Weather\WeatherSettingsRepository($db))->state();
        foreach($db->all('SELECT l.* FROM locations l WHERE EXISTS(SELECT 1 FROM client_settings s JOIN clients c ON c.id=s.client_id WHERE s.location_id=l.id AND c.revoked_at IS NULL) ORDER BY l.id') as $location)$targets[]=['key'=>'weather:'.$location['id'],'kind'=>'weather','id'=>$location['id'],'context'=>$location+['provider'=>$weather['provider'],'provider_revision'=>$weather['revision']]];
        $groups=[];
        foreach($db->all("SELECT i.*,g.settings source_settings,g.revision source_revision,g.type source_type FROM finance_instruments i JOIN integrations g ON g.id=i.integration_id WHERE i.enabled=1 AND g.type IN ('cbr','moex_iss') AND EXISTS(SELECT 1 FROM client_finance_watchlist w JOIN clients c ON c.id=w.client_id WHERE w.instrument_id=i.id AND c.revoked_at IS NULL) ORDER BY i.id") as $instrument){
            $settings=json_decode($instrument['source_settings'],true,32,JSON_THROW_ON_ERROR);$type=$instrument['source_type'];
            if(($settings['data_mode'] ?? 'unknown')!==($type==='cbr'?'official':'public_delayed'))continue;
            if($type==='cbr')$groups[$instrument['integration_id']][]=$instrument;
            else $targets[]=['key'=>'moex:'.$instrument['id'],'kind'=>'moex','id'=>$instrument['id'],'context'=>$instrument];
        }
        foreach($groups as $source=>$instruments)$targets[]=['key'=>'cbr:'.$source,'kind'=>'cbr','id'=>$source,'context'=>['integration_id'=>$source,'instruments'=>$instruments]];
        return $targets;
    }
}
