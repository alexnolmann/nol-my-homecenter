<?php
declare(strict_types=1);
namespace MediaCentre\Weather;
use MediaCentre\Storage\Database;
use MediaCentre\Providers\{Transport,ProviderError};
final class WeatherCollector
{
    public function __construct(private Database $db,private Transport $http){}
    public function collect(array $location): bool
    {
        if($this->db->pdo->inTransaction())throw new \LogicException('Provider request must run outside SQL transaction.');
        $settings=(new WeatherSettingsRepository($this->db))->state();$provider=$location['provider'] ?? $settings['provider'];$providerRevision=(int)($location['provider_revision'] ?? $settings['revision']);
        if($settings['provider']!==$provider||$settings['revision']!==$providerRevision)return false;
        if($provider==='met_norway'){
            $old=$this->db->one('SELECT provider,valid_until,last_error_code FROM weather_snapshots WHERE location_id=?',[$location['id']]);
            if($old&&$old['provider']==='met_norway'&&$old['last_error_code']===null&&$old['valid_until']>gmdate('Y-m-d H:i:s'))return true;
        }
        try{$result=match($provider){
            'open_meteo'=>(new WeatherProvider($this->http))->fetch($location),
            'visual_crossing'=>(new VisualCrossingProvider($this->http))->fetch($location,(new WeatherSettingsRepository($this->db))->key() ?? ''),
            'met_norway'=>(new MetNorwayProvider($this->http))->fetch($location),
            default=>throw new ProviderError('CONFIGURATION_CHANGED')};}
        catch(ProviderError $error){(new SnapshotRepository($this->db))->failure($location['id'],(int)$location['revision'],$error->reason,gmdate('Y-m-d\TH:i:s\Z'),$provider,$providerRevision);throw $error;}
        return (new SnapshotRepository($this->db))->save($location['id'],(int)$location['revision'],$provider,$result['payload'],$result['data_as_of'],$result['fetched_at'],$result['valid_until'],false,$providerRevision);
    }
}
