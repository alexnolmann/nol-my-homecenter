<?php
declare(strict_types=1);
namespace MediaCentre\Admin;
use MediaCentre\Storage\Database;
use MediaCentre\Validation\Input;
use MediaCentre\Shared\Uuid;
final class FormActions
{
    public const ACTIONS=['location','station','instrument','watchlist','client_location','weather_provider','refresh','climate_interval','climate_add','climate_rename','climate_delete','music_save','music_rescan'];
    public function __construct(private Database $db){}
    public static function revision(mixed $value): int
    {
        if(!is_string($value) || !preg_match('/^[1-9][0-9]{0,17}$/D',$value))throw new \InvalidArgumentException('Revision required.');
        return Input::revision((int)$value);
    }
    private static function flag(mixed $value): bool
    {
        if($value!==null && $value!=='1')throw new \InvalidArgumentException('Invalid checkbox.');return $value==='1';
    }
    public function save(array $form): string
    {
        $action=$form['action'] ?? '';
        $common=['csrf','action','section','id','revision'];
        if(in_array($action,['music_save','music_rescan'],true)){
            Input::keys($form,$action==='music_save'?['csrf','action','id','revision','name','root_path','enabled']:['csrf','action','id','revision']);
            $repo=new \MediaCentre\Music\MusicRepository($this->db);
            if($action==='music_save'){
                if(!is_string($form['name'] ?? null)||!is_string($form['root_path'] ?? null))throw new \InvalidArgumentException('Укажите название и путь.');
                $repo->save(($form['id'] ?? '')===''?null:Input::uuid($form['id']),self::revision($form['revision'] ?? null),$form['name'],$form['root_path'],self::flag($form['enabled'] ?? null));
            }else $repo->rescan(Input::uuid($form['id'] ?? null),self::revision($form['revision'] ?? null));
            return 'Индексирование музыки запрошено. Обновите страницу, чтобы увидеть результат.';
        }
        if(str_starts_with($action,'climate_')){
            $fields=match($action){'climate_interval'=>['poll_minutes'],'climate_add','climate_rename'=>['address','name'],'climate_delete'=>['address'],default=>throw new \InvalidArgumentException('Unknown climate action.')};
            Input::keys($form,array_merge(['csrf','action','client_id','revision'],$fields));
            foreach($fields as $field)if(!is_string($form[$field] ?? null))throw new \InvalidArgumentException('Заполните поля датчика.');
            (new \MediaCentre\Climate\ClimateRepository($this->db))->edit(Input::uuid($form['client_id'] ?? null),self::revision($form['revision'] ?? null),$action,$form);
            return 'Настройки климата сохранены. Планшет применит их при следующей связи с панелью.';
        }
        if($action==='refresh'){
            Input::keys($form,['csrf','action','target_key']);
            if(!is_string($form['target_key'] ?? null))throw new \InvalidArgumentException('Collection target required.');
            \MediaCentre\Jobs\CollectionStatus::refresh($this->db,$form['target_key']);
            return 'Обновление запрошено. Сбор выполняется в фоне с учётом ограничений источника.';
        }
        if($action==='weather_provider'){
            Input::keys($form,['csrf','action','provider','revision','visual_crossing_key','clear_visual_crossing_key']);
            $provider=$form['provider'] ?? null;
            if(!is_string($provider)||!array_key_exists($provider,\MediaCentre\Weather\WeatherSettingsRepository::PROVIDERS))throw new FieldErrors(['provider'=>'Выберите одного из трёх поставщиков.']);
            $raw=$form['visual_crossing_key'] ?? '';
            if(!is_string($raw))throw new FieldErrors(['visual_crossing_key'=>'Некорректный ключ API.']);
            $key=trim($raw);if($key==='')$key=null;
            $clear=self::flag($form['clear_visual_crossing_key'] ?? null);
            try{(new \MediaCentre\Weather\WeatherSettingsRepository($this->db))->save($provider,self::revision($form['revision'] ?? null),$key,$clear);}
            catch(\InvalidArgumentException){throw new FieldErrors(['visual_crossing_key'=>'Для Visual Crossing нужен ключ: 8–128 латинских букв, цифр, «-» или «_».']);}
            return 'Поставщик погоды сохранён. Новый сбор будет запланирован в фоне.';
        }
        if($action==='location'){
            Input::keys($form,array_merge($common,['name','latitude','longitude','timezone']));
            $edit=($form['id'] ?? '')!=='';$id=$edit?Input::uuid($form['id']):Uuid::random();
            $fields=array_intersect_key($form,array_flip(['name','latitude','longitude','timezone']));
            if($edit)$fields['revision']=self::revision($form['revision'] ?? null);
            $model=FormModel::location($fields,$edit);$v=$model->values;$repo=new \MediaCentre\Weather\LocationRepository($this->db);
            if($edit)$repo->update($id,$model->revision,$v['name'],$v['latitude'],$v['longitude'],$v['timezone']);else $repo->create($id,$v['name'],$v['latitude'],$v['longitude'],$v['timezone']);
            return 'Место сохранено.';
        }
        if($action==='station'){
            Input::keys($form,array_merge($common,['name','stream_url','genres','country_code','enabled']));
            if(!is_string($form['genres'] ?? null))throw new \InvalidArgumentException('Genres required.');
            $edit=($form['id'] ?? '')!=='';$id=$edit?Input::uuid($form['id']):Uuid::random();
            $fields=array_intersect_key($form,array_flip(['name','stream_url','country_code']));
            $fields['genres']=trim($form['genres'])===''?[]:array_map('trim',explode(',',$form['genres']));$fields['enabled']=self::flag($form['enabled'] ?? null);
            if($edit)$fields['revision']=self::revision($form['revision'] ?? null);
            $model=FormModel::station($fields,$edit);$v=$model->values;$repo=new \MediaCentre\Radio\StationRepository($this->db);
            if($edit)$repo->update($id,$model->revision,$v['name'],$v['stream_url'],$v['genres'],$v['country_code'],$v['enabled']);
            else{
                $repo->create($id,$v['name'],$v['stream_url'],$v['genres'],$v['country_code'],$v['enabled']);
            }
            return 'Станция сохранена. Сервер не скачивает поток.';
        }
        if($action==='instrument'){
            Input::keys($form,array_merge($common,['type','symbol','board','name','enabled']));
            $type=$form['type'] ?? '';
            if(!in_array($type,['currency','share'],true))throw new FieldErrors(['type'=>'Выберите курс ЦБ или акцию MOEX.']);
            $source=$this->db->one('SELECT id FROM integrations WHERE type=? ORDER BY created_at,id LIMIT 1',[$type==='currency'?'cbr':'moex_iss']);
            if(!$source)throw new \InvalidArgumentException('Fixed provider is not initialized.');
            $edit=($form['id'] ?? '')!=='';$id=$edit?Input::uuid($form['id']):Uuid::random();
            $fields=['type'=>$type,'symbol'=>$form['symbol'] ?? null,'name'=>$form['name'] ?? null,'board'=>$type==='currency'?'':($form['board'] ?? null),'exchange'=>$type==='currency'?'':'MOEX','quote_currency'=>'RUB','integration_id'=>$source['id']];
            if($edit)$fields['revision']=self::revision($form['revision'] ?? null);
            $model=FormModel::instrument($fields,$edit);$enabled=self::flag($form['enabled'] ?? null);$repo=new \MediaCentre\Finance\InstrumentRepository($this->db);
            if($edit)$repo->update($id,$model->revision,$model->values,$enabled);
            else $repo->create($id,$model->values,$enabled);
            return 'Инструмент сохранён. Новый снимок будет собран в фоне.';
        }
        if($action==='watchlist'){
            Input::keys($form,array_merge($common,['client_id','items']));
            $client=Input::uuid($form['client_id'] ?? null);$entries=$form['items'] ?? [];
            if(!is_array($entries) || count($entries)>50)throw new \InvalidArgumentException('Bounded list required.');
            $items=[];$orders=[];
            foreach($entries as $id=>$entry){
                Input::uuid($id);if(!is_array($entry))throw new \InvalidArgumentException('Invalid entry.');
                Input::keys($entry,['included','order','dashboard']);
                if(!self::flag($entry['included'] ?? null))continue;
                $order=filter_var($entry['order'] ?? null,FILTER_VALIDATE_INT,['options'=>['min_range'=>0,'max_range'=>999]]);
                if($order===false || isset($orders[$order]))throw new FieldErrors(['items['.$id.'][order]'=>'Порядок: разные целые числа от 0 до 999.']);$orders[$order]=true;
                $items[$order]=['id'=>$id,'show_on_dashboard'=>self::flag($entry['dashboard'] ?? null)];
            }
            ksort($items);(new \MediaCentre\Client\OrderedListRepository($this->db))->watchlist($client,self::revision($form['revision'] ?? null),array_values($items));
            return 'Порядок и поднабор главной сохранены.';
        }
        if($action==='client_location'){
            Input::keys($form,array_merge($common,['client_id','location_id']));
            (new \MediaCentre\Client\SettingsRepository($this->db))->update(Input::uuid($form['client_id'] ?? null),self::revision($form['revision'] ?? null),['location_id'=>Input::uuid($form['location_id'] ?? null)]);
            return 'Место планшета сохранено.';
        }
        throw new \InvalidArgumentException('Unknown configuration action.');
    }
    public function view(?array $catalogue=null): array
    {
        $page=null;
        if($catalogue===null)$stations=$this->db->all('SELECT * FROM radio_stations ORDER BY name,id');
        else {
            $query=$catalogue['query'] ?? '';$requested=$catalogue['page'] ?? 1;
            if(!is_string($query)||preg_match('//u',$query)!==1||preg_match_all('/./us',$query)>100||!is_int($requested)||$requested<1||$requested>100000)throw new \InvalidArgumentException('Invalid catalogue query.');
            $query=trim($query);$pattern='%'.str_replace(['!','%','_'],['!!','!%','!_'],$query).'%';
            $where=$query===''?'':" WHERE name LIKE ? ESCAPE '!' OR stream_url LIKE ? ESCAPE '!' OR CAST(genres AS CHAR) LIKE ? ESCAPE '!'";$params=$query===''?[]:[$pattern,$pattern,$pattern];
            $total=(int)$this->db->query('SELECT COUNT(*) FROM radio_stations'.$where,$params)->fetchColumn();$pages=max(1,(int)ceil($total/20));$number=min($requested,$pages);$offset=($number-1)*20;
            $stations=$this->db->all('SELECT * FROM radio_stations'.$where.' ORDER BY name,id LIMIT 20 OFFSET '.$offset,$params);
            $page=['query'=>$query,'number'=>$number,'pages'=>$pages,'total'=>$total];
        }
        return ['locations'=>$this->db->all('SELECT * FROM locations ORDER BY name,id'),'stations'=>$stations,'station_page'=>$page,
            'weather_provider'=>(new \MediaCentre\Weather\WeatherSettingsRepository($this->db))->state(),
            'instruments'=>$this->db->all('SELECT i.*,q.price quote_price,q.price_kind quote_kind,q.effective_date quote_effective,q.quote_as_of quote_trade,q.fetched_at quote_fetched,q.valid_until quote_valid_until,q.last_error_code quote_error FROM finance_instruments i LEFT JOIN finance_quotes q ON q.instrument_id=i.id AND q.instrument_revision=i.revision ORDER BY i.type,i.symbol,i.board,i.id'),
            'clients'=>$this->db->all("SELECT c.id,c.name,c.auth_revision,c.scopes,c.revoked_at,c.last_seen_at,c.app_version,(c.token_hash IS NOT NULL) paired,s.location_id,s.revision,s.finance_list_revision,COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.preferences,'$.display_title')),'Мой дом') display_title FROM clients c JOIN client_settings s ON s.client_id=c.id ORDER BY c.name,c.id"),
            'watchlists'=>$this->db->all('SELECT * FROM client_finance_watchlist ORDER BY client_id,sort_order'),
            'collections'=>\MediaCentre\Jobs\CollectionStatus::read($this->db)];
    }
}
