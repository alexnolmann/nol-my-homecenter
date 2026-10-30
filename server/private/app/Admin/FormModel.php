<?php
declare(strict_types=1);
namespace MediaCentre\Admin;
use MediaCentre\Validation\Input;
final class FormModel
{
    public function __construct(public readonly string $type,public readonly array $values,public readonly ?int $revision){}
    private static function fields(array $rules): array
    {
        $values=[];$errors=[];
        foreach($rules as $key=>[$read,$message]){try{$values[$key]=$read();}catch(\InvalidArgumentException){$errors[$key]=$message;}}
        if($errors)throw new FieldErrors($errors);return $values;
    }
    public static function location(array $data,bool $edit=false): self
    {
        Input::keys($data,['name','latitude','longitude','timezone','revision']);
        $v=self::fields([
            'name'=>[fn()=>Input::text($data['name'] ?? null,128),'Введите город: от 1 до 128 символов.'],
            'latitude'=>[fn()=>Input::coordinate($data['latitude'] ?? null,true),'Широта от −90 до 90; десятичная точка, до 6 знаков.'],
            'longitude'=>[fn()=>Input::coordinate($data['longitude'] ?? null,false),'Долгота от −180 до 180; десятичная точка, до 6 знаков.'],
            'timezone'=>[fn()=>Input::timezone($data['timezone'] ?? null),'Выберите часовой пояс IANA, например Europe/Moscow.']
        ]);
        return new self('location',$v,$edit?Input::revision($data['revision'] ?? null):null);
    }
    public static function station(array $data,bool $edit=false): self
    {
        Input::keys($data,['name','stream_url','genres','country_code','enabled','revision']);
        $v=self::fields([
            'name'=>[fn()=>Input::text($data['name'] ?? null,255),'Введите название: от 1 до 255 символов.'],
            'stream_url'=>[fn()=>Input::url($data['stream_url'] ?? null),'Укажите полный HTTP/HTTPS URL без логина, пароля и #фрагмента.'],
            'genres'=>[function()use($data){$genres=$data['genres'] ?? null;if(!is_array($genres)||!array_is_list($genres)||count($genres)>10)throw new \InvalidArgumentException();$genres=array_map(fn($v)=>Input::text($v,60),$genres);if(count(array_unique($genres))!==count($genres))throw new \InvalidArgumentException();return $genres;},'До 10 разных жанров, каждый до 60 символов.'],
            'country_code'=>[function()use($data){$country=$data['country_code'] ?? null;if(!is_string($country)||!preg_match('/^[A-Z]{2}$/D',$country))throw new \InvalidArgumentException();return $country;},'Две заглавные латинские буквы, например RU.'],
            'enabled'=>[fn()=>Input::boolean($data['enabled'] ?? true),'Некорректный переключатель станции.']
        ]);
        return new self('station',$v,$edit?Input::revision($data['revision'] ?? null):null);
    }
    public static function instrument(array $data,bool $edit=false): self
    {
        Input::keys($data,['integration_id','type','symbol','exchange','board','quote_currency','name','revision']);
        $v=self::fields([
            'name'=>[fn()=>Input::text($data['name'] ?? null,255),'Введите название: от 1 до 255 символов.'],
            'type'=>[function()use($data){if(!in_array($data['type'] ?? null,['currency','share'],true))throw new \InvalidArgumentException();return $data['type'];},'Выберите курс ЦБ или акцию MOEX.'],
            'symbol'=>[function()use($data){$symbol=strtoupper(trim(Input::text($data['symbol'] ?? null,32)));if(($data['type'] ?? null)==='currency'?!in_array($symbol,['USD','EUR','CNY'],true):!preg_match('/^[A-Z0-9._-]{1,32}$/D',$symbol))throw new \InvalidArgumentException();return $symbol;},($data['type'] ?? null)==='currency'?'Для курса доступны USD, EUR и CNY.':'Укажите SECID латинскими буквами/цифрами, например SBER.'],
            'board'=>[function()use($data){if(($data['type'] ?? null)==='currency')return ''; $board=strtoupper(trim(Input::text($data['board'] ?? null,32)));if(!preg_match('/^[A-Z0-9_-]{1,32}$/D',$board))throw new \InvalidArgumentException();return $board;},'Укажите BOARDID латинскими буквами/цифрами, например TQBR.']
        ]);
        $data=array_replace($data,$v);
        $fields=\MediaCentre\Finance\InstrumentRepository::identity($data);
        unset($fields['revision'],$fields['instrument_key']);
        return new self('instrument',$fields,$edit?Input::revision($data['revision'] ?? null):null);
    }
    public static function rights(array $data): self
    {
        Input::keys($data,['scopes','revision']);
        $scopes=$data['scopes'] ?? null;
        if(!is_array($scopes) || !array_is_list($scopes) || count($scopes)>count(\MediaCentre\Security\ClientAccess::SCOPES) || count(array_unique($scopes,SORT_REGULAR))!==count($scopes))throw new \InvalidArgumentException('Distinct scopes required.');
        foreach($scopes as $scope)if(!is_string($scope) || !in_array($scope,\MediaCentre\Security\ClientAccess::SCOPES,true))throw new \InvalidArgumentException('Unsupported scope.');
        sort($scopes);
        return new self('client_rights',['scopes'=>$scopes],Input::revision($data['revision'] ?? null));
    }
    public static function preferences(array $patch): array
    {
        Input::keys($patch,['location_id','selected_station_id','radio_volume']);
        if(array_key_exists('location_id',$patch))$patch['location_id']=Input::uuid($patch['location_id']);
        if(array_key_exists('selected_station_id',$patch) && $patch['selected_station_id']!==null)$patch['selected_station_id']=Input::uuid($patch['selected_station_id']);
        if(array_key_exists('radio_volume',$patch) && (!is_int($patch['radio_volume']) || $patch['radio_volume']<0 || $patch['radio_volume']>100))throw new \InvalidArgumentException('Invalid volume.');
        return $patch;
    }
}
