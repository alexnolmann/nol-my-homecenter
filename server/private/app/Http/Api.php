<?php
declare(strict_types=1);
namespace MediaCentre\Http;

use FastRoute\Dispatcher;
use MediaCentre\Shared\AppConfig;
use MediaCentre\Shared\ContractValidator;
use MediaCentre\Shared\FixtureRepository;
use MediaCentre\Shared\Readiness;

final class Api
{
    private Dispatcher $router;
    private ContractValidator $validator;
    public function __construct(private AppConfig $config, private string $root)
    {
        $this->validator = new ContractValidator(dirname($root) . '/contracts/v1/schema.json');
        $this->router = \FastRoute\simpleDispatcher(static function (\FastRoute\RouteCollector $r): void {
            foreach (['bootstrap','dashboard','weather','finance','radio/stations','client/preferences'] as $path) $r->addRoute('GET', '/api/v1/' . $path, $path);
            $r->addRoute('GET','/api/v1/radio/stations/{id}', 'station');
            $r->addRoute('GET','/api/v1/plugins/photoframe/manifest','photoManifest');
            $r->addRoute('GET','/api/v1/plugins/photoframe/photos/{id}/{hash}','photoImage');
            $r->addRoute(['PUT','DELETE'],'/api/v1/radio/favorites/{id}', 'favorite');
            $r->addRoute(['PATCH','POST'],'/api/v1/client/preferences','preferencesPatch');
            $r->addRoute('GET','/api/v1/music/libraries','musicLibraries');
            $r->addRoute('GET','/api/v1/music/browse','musicBrowse');
            $r->addRoute(['GET','HEAD'],'/api/v1/music/stream/{library}/{generation}/{id}','musicStream');
            $r->addRoute('GET','/api/v1/client/climate','climate');
            $r->addRoute('POST','/api/v1/client/climate/config','climateConfig');
            $r->addRoute('POST','/api/v1/client/climate/report','climateReport');
            $r->addRoute('POST','/api/v1/client/heartbeat','heartbeat');
            $r->addRoute('POST','/api/v1/weather/refresh','weatherRefresh');
            $r->addRoute('POST','/api/v1/pair','pair');
        });
    }
    /** @return array{status:int,headers:array,body:?array} */
    public function handle(string $method, string $path, array $query, array $headers, string $body,string $remoteAddress='unknown'): array
    {
        if ($path === '/health/live') return $method === 'GET' ? $this->response(200,['live'=>true]) : $this->error(405,'METHOD_NOT_ALLOWED','Use GET.', ['Allow'=>'GET']);
        if ($path === '/health/ready') {
            if ($method !== 'GET') return $this->error(405,'METHOD_NOT_ALLOWED','Use GET.', ['Allow'=>'GET']);
            $ready = Readiness::check($this->config, $this->root . '/storage');
            return $this->response($ready['ready'] ? 200 : 503, $ready);
        }
        $route = $this->router->dispatch($method, $path);
        if ($route[0] === Dispatcher::NOT_FOUND) return $this->error(404,'NOT_FOUND','Route not found.');
        if ($route[0] === Dispatcher::METHOD_NOT_ALLOWED) return $this->error(405,'METHOD_NOT_ALLOWED','Method not allowed.', ['Allow'=>implode(', ', $route[1])]);
        $scenario = $query['scenario'] ?? 'database';
        if (!is_string($scenario) || !in_array($scenario,['success','empty','partial','stale','database'],true)) return $this->error(400,'INVALID_REQUEST','Unknown data scenario.');
        $handler=$route[1];$db=null;$client=null;
        if($handler==='pair'){
            if($query)return $this->error(400,'INVALID_REQUEST','Pairing has no query parameters.');
            try{
                $db=new \MediaCentre\Storage\Database(\MediaCentre\Storage\Connection::open($this->config));
                $limits=new \MediaCentre\Security\RateLimiter($db);
                $global=$limits->allow('pair-global',60,600);$ip=$limits->allow('pair-ip:'.$remoteAddress,10,600);
                if(!$global || !$ip)return $this->error(429,'TOO_MANY_REQUESTS','Pairing attempt limit reached.',['Retry-After'=>'600']);
                if(strlen($body)>32768)return $this->error(400,'INVALID_REQUEST','Request too large.');
                try{$input=json_decode($body,false,64,JSON_THROW_ON_ERROR);}catch(\JsonException){return $this->error(400,'INVALID_REQUEST','Malformed JSON.');}
                if(!$this->validator->valid('pairRequest',$input))return $this->error(400,'INVALID_REQUEST','Request does not match contract.');
                $paired=(new \MediaCentre\Security\PairingRepository($db))->consume($input->code,$input->client_name,$input->client_version);
                if(!$paired)return $this->error(401,'INVALID_PAIRING','Code invalid, expired or already used.');
                return $this->response(200,['data'=>$paired,'meta'=>self::writeMeta()],['Cache-Control'=>'no-store']);
            }catch(\InvalidArgumentException){return $this->error(400,'INVALID_REQUEST','Pairing fields invalid.');}
            catch(\Throwable){return $this->error(503,'NOT_READY','Pairing unavailable.');}
        }
        // The legacy dev token is restricted to explicitly selected synthetic fixtures.
        $fixture=$scenario!=='database';
        if($fixture){
            $token=$this->config->values['development']['token'] ?? null;
            if(!$this->config->isDevelopment() || !$this->config->values['fixtures'])return $this->error(400,'INVALID_REQUEST','Fixtures disabled.');
            if(!is_string($token) || strlen($token)<32 || !hash_equals('Bearer '.$token,$headers['authorization'] ?? ''))return $this->error(401,'UNAUTHORIZED','Fixture token required.');
        }else{
            if(!preg_match('/^Bearer [a-f0-9]{64}$/D',$headers['authorization'] ?? ''))return $this->error(401,'UNAUTHORIZED','Client token required.',['WWW-Authenticate'=>'Bearer']);
            try{$db=new \MediaCentre\Storage\Database(\MediaCentre\Storage\Connection::open($this->config));$client=(new \MediaCentre\Security\ClientAccess($db))->authenticate($headers['authorization']);}
            catch(\Throwable){return $this->error(503,'NOT_READY','Client authorization unavailable.');}
            if(!$client)return $this->error(401,'UNAUTHORIZED','Client token invalid or revoked.',['WWW-Authenticate'=>'Bearer']);
            $required=match($handler){'weather','weatherRefresh'=>['weather:read'],'finance'=>['finance:read'],'radio/stations','station'=>['radio:read'],'photoManifest','photoImage'=>['photoframe:read'],'musicLibraries','musicBrowse','musicStream'=>['music:read'],'favorite'=>['radio:read','favorites:write'],'preferencesPatch','climateConfig'=>['preferences:write'],'heartbeat','climateReport'=>['heartbeat:write'],default=>['preferences:read']};
            if(array_diff($required,$client['scopes']))return $this->error(403,'FORBIDDEN','Client permission required.');
            if(array_diff(array_keys($query),(str_starts_with($handler,'music')?['library','folder','generation','limit','offset','recursive']:['scenario','days','q','genre','favorites','limit','offset'])))return $this->error(400,'INVALID_REQUEST','Unknown query field.');
            $db->clientActor=['id'=>$client['id'],'auth_revision'=>(int)$client['auth_revision'],'token_hash'=>hash('sha256',substr($headers['authorization'],7),true),'required'=>$required];
            if(isset($headers['x-mediacentre-server-id'])){
                try{$db->clientActor['server_id']=\MediaCentre\Validation\Input::uuid($headers['x-mediacentre-server-id']);}
                catch(\InvalidArgumentException){return $this->error(400,'INVALID_REQUEST','Invalid server identity.');}
            }
        }
        if(str_starts_with($handler,'music')){
            if($fixture||trim($body)!=='')return $this->error(400,'INVALID_REQUEST','Music requires a paired client and no body.');
            try{
                $music=new \MediaCentre\Music\MusicRepository($db);
                if($handler==='musicStream'){
                    if($query)throw new \InvalidArgumentException();
                    return $music->stream($route[2]['library'],$route[2]['generation'],$route[2]['id']);
                }
                if($handler==='musicLibraries'){
                    if($query)throw new \InvalidArgumentException();
                    $data=['items'=>$music->libraries()];
                }else $data=$music->browse($query);
                $result=['data'=>$data,'meta'=>self::writeMeta()];
                if(!$this->validator->valid($handler==='musicLibraries'?'musicLibrariesResponse':'musicBrowseResponse',json_decode(\MediaCentre\Storage\Database::json($result))))throw new \RuntimeException('Music contract violation.');
                return $this->response(200,$result,['Cache-Control'=>'private, no-store']);
            }catch(\MediaCentre\Security\AccessDenied $error){return $this->error($error->status,'FORBIDDEN','Client access changed.');}
            catch(\InvalidArgumentException){return $this->error(400,'INVALID_REQUEST','Invalid music parameters.');}
            catch(\OutOfBoundsException){return $this->error(404,'NOT_FOUND','Music unavailable; refresh the catalogue.');}
            catch(\Throwable){return $this->error(503,'NOT_READY','Music unavailable.');}
        }
        if(in_array($handler,['climate','climateConfig','climateReport'],true)){
            if($fixture)return $this->error(501,'NOT_IMPLEMENTED','Climate requires a paired client.');
            if($query)return $this->error(400,'INVALID_REQUEST','Climate has no query parameters.');
            try{
                $repo=new \MediaCentre\Climate\ClimateRepository($db);
                if($handler==='climate'){
                    if(trim($body)!=='')return $this->error(400,'INVALID_REQUEST','GET has no body.');
                    $state=$repo->state($client['id']);
                }else{
                    if(strlen($body)>32768)return $this->error(400,'INVALID_REQUEST','Request too large.');
                    try{$input=json_decode($body,false,32,JSON_THROW_ON_ERROR);}catch(\JsonException){return $this->error(400,'INVALID_REQUEST','Malformed JSON.');}
                    if(!$this->validator->valid($handler==='climateConfig'?'climateConfigRequest':'climateReportRequest',$input))return $this->error(400,'INVALID_REQUEST','Request does not match contract.');
                    $input=json_decode($body,true,32,JSON_THROW_ON_ERROR);
                    if($handler==='climateConfig')$state=$repo->replace($client['id'],$input['revision'],$input['poll_minutes'],$input['sensors']);
                    else{$repo->report($client['id'],$input['revision'],$input['sensors']);return $this->response(200,['data'=>['accepted'=>true],'meta'=>self::writeMeta($input['revision'])],['Cache-Control'=>'no-store']);}
                }
                return $this->response(200,['data'=>$state,'meta'=>self::writeMeta($state['revision'])],['Cache-Control'=>'no-store']);
            }catch(\MediaCentre\Storage\RevisionConflict){return $this->error(409,'REVISION_CONFLICT','Climate changed; reload configuration.');}
            catch(\MediaCentre\Security\AccessDenied $denied){return $this->error($denied->status,$denied->status===401?'UNAUTHORIZED':'FORBIDDEN','Client access changed.');}
            catch(\InvalidArgumentException){return $this->error(422,'INVALID_REQUEST','Invalid climate values.');}
            catch(\Throwable){return $this->error(503,'NOT_READY','Climate unavailable.');}
        }
        if(in_array($handler,['photoManifest','photoImage'],true)){
            if($fixture||$query||trim($body)!=='')return $this->error(400,'INVALID_REQUEST','Photo requests have no parameters.');
            if($handler==='photoImage' && (!preg_match('/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/iD',$route[2]['id']) || !preg_match('/^[a-f0-9]{64}$/D',$route[2]['hash'])))return $this->error(400,'INVALID_REQUEST','Invalid photo identity.');
            try{
                $photos=new \MediaCentre\Photoframe\PhotoRepository($db,$this->root.'/storage/photoframe');
                if(!$photos->enabled())return $this->error(404,'NOT_FOUND','Photoframe disabled.');
                if($handler==='photoManifest'){
                    $state=$photos->state();$items=[];
                    foreach($state['items'] as $item)$items[]=['id'=>$item['id'],'width'=>(int)$item['width'],'height'=>(int)$item['height'],'bytes'=>(int)$item['bytes'],'url'=>'/api/v1/plugins/photoframe/photos/'.$item['id'].'/'.$item['full_hash']];
                    $meta=self::writeMeta($state['revision']);$meta['fetched_at']=null;
                    $result=['data'=>['revision'=>$state['revision'],'items'=>$items],'meta'=>$meta];$etag=self::etag($result);
                    if(in_array($etag,array_map('trim',explode(',',$headers['if-none-match'] ?? '')),true))return ['status'=>304,'headers'=>['Cache-Control'=>'private, no-cache','ETag'=>$etag],'body'=>null];
                    return $this->response(200,$result,['Cache-Control'=>'private, no-cache','ETag'=>$etag]);
                }
                $image=$photos->image($route[2]['id'],$route[2]['hash'],'full');if(!$image)return $this->error(404,'NOT_FOUND','Photo not found.');
                $etag='"'.$route[2]['hash'].'"';
                if(($headers['if-none-match'] ?? '')===$etag)return ['status'=>304,'headers'=>['Cache-Control'=>'private, no-cache','ETag'=>$etag],'body'=>null];
                return ['status'=>200,'headers'=>['Content-Type'=>'image/jpeg','Content-Length'=>(string)$image['bytes'],'Cache-Control'=>'private, no-cache','ETag'=>$etag],'body'=>null,'file'=>$image['path']];
            }catch(\MediaCentre\Security\AccessDenied $denied){return $this->error($denied->status,$denied->status===401?'UNAUTHORIZED':'FORBIDDEN','Client access changed.');}
            catch(\Throwable){return $this->error(503,'NOT_READY','Photoframe unavailable.');}
        }
        if($handler==='weatherRefresh'){
            if($fixture)return $this->error(501,'NOT_IMPLEMENTED','Synthetic fixture writes are disabled.');
            if(array_keys($query)!==[] || trim($body)!=='')return $this->error(400,'INVALID_REQUEST','Refresh has no parameters or body.');
            try{
                $settings=(new \MediaCentre\Client\SettingsRepository($db))->read($client['id']);
                if(!$settings)throw new \OutOfBoundsException('Location unavailable.');
                \MediaCentre\Jobs\CollectionStatus::refresh($db,'weather:'.$settings['location_id']);
                return $this->response(202,['data'=>['queued'=>true],'meta'=>self::writeMeta()],['Cache-Control'=>'no-store']);
            }catch(\MediaCentre\Security\AccessDenied $denied){return $this->error($denied->status,$denied->status===401?'UNAUTHORIZED':'FORBIDDEN','Client access changed.');}
            catch(\Throwable){return $this->error(503,'NOT_READY','Weather refresh unavailable.');}
        }
        if($scenario==='database'){
            try{$fixtures=(new \MediaCentre\Storage\ReadModel($db,(bool)($this->config->values['development']['test_data'] ?? false)))->load($client['id']);}
            catch(\MediaCentre\Security\AccessDenied $denied){return $this->error($denied->status,$denied->status===401?'UNAUTHORIZED':'FORBIDDEN','Client access changed.');}
            catch(\Throwable){return $this->error(503,'NOT_READY','Stored panel data unavailable.');}
            $fixtures=self::scoped($fixtures,$client['scopes']);
        }else {
            if(!$this->config->values['fixtures'])return $this->error(503,'NOT_READY','Fixtures disabled.');
            $fixtures = (new FixtureRepository(dirname($this->root) . '/contracts/v1/fixtures'))->load($scenario);
        }
        $handler = $route[1];
        $wrap = static fn(array $data): array => ['data'=>$data,'meta'=>$fixtures['meta']];
        if (isset($route[2]['id']) && !preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iD', $route[2]['id'])) return $this->error(400,'INVALID_REQUEST','Invalid station ID.');
        if ($handler === 'weather' && isset($query['days']) && !in_array($query['days'],['7','14'],true)) return $this->error(400,'INVALID_REQUEST','Only days=7 or days=14 is supported.');
        if ($handler === 'weather' && ($query['days'] ?? '7') === '7')$fixtures['weather']['data']['days']=array_slice($fixtures['weather']['data']['days'],0,7);
        if ($handler === 'radio/stations') {
            $q=$query['q'] ?? ''; $genre=$query['genre'] ?? ''; $favorite=$query['favorites'] ?? '0';
            $limit=filter_var($query['limit'] ?? '20', FILTER_VALIDATE_INT, ['options'=>['min_range'=>1,'max_range'=>50]]);
            $offset=filter_var($query['offset'] ?? '0', FILTER_VALIDATE_INT, ['options'=>['min_range'=>0]]);
            if (!is_string($q) || preg_match('//u',$q) !== 1 || preg_match_all('/./us',$q) > 100 || !is_string($genre) || preg_match('//u',$genre) !== 1 || preg_match_all('/./us',$genre) > 60 || !in_array($favorite,['0','1'],true) || $limit === false || $offset === false) return $this->error(400,'INVALID_REQUEST','Invalid search or pagination.');
            $available=[];
            foreach($fixtures['stations'] as &$station){$station['genres']=$station['genres'] ?? array_values(array_filter(array_map('trim',explode(',',$station['genre'])),static fn(string $label):bool=>$label!==''));foreach($station['genres'] as $label)$available['genre:'.$label]=$label;}unset($station);
            $genres=array_values($available);sort($genres,SORT_STRING);
            $items=array_values(array_filter($fixtures['stations'], static fn(array $s): bool => ($q === '' || preg_match('/'.preg_quote($q,'/').'/iu', $s['name']) === 1) && ($genre === '' || in_array($genre,$s['genres'],true)) && ($favorite === '0' || $s['favorite'])));
            usort($items, static fn(array $a,array $b): int => [$a['name'],$a['id']] <=> [$b['name'],$b['id']]);
            $result=$wrap(['items'=>array_slice($items,$offset,$limit),'total'=>count($items),'limit'=>$limit,'offset'=>$offset,'available_genres'=>$genres]); $definition='stations';
        } elseif ($handler === 'station' || $handler === 'favorite') {
            $matches=array_values(array_filter($fixtures['stations'], static fn(array $s): bool => $s['id'] === $route[2]['id']));
            if (!$matches) return $this->error(404,'NOT_FOUND','Station not found.');
            if ($handler === 'favorite') {
                if (trim($body) !== '') return $this->error(400,'INVALID_REQUEST','Favorite request has no body.');
                if($fixture)return $this->error(501,'NOT_IMPLEMENTED','Synthetic fixture writes are disabled.');
                try{$revision=(new \MediaCentre\Client\OrderedListRepository($db))->favorite($client['id'],$route[2]['id'],$method==='PUT');}
                catch(\MediaCentre\Security\AccessDenied $denied){return $this->error($denied->status,$denied->status===401?'UNAUTHORIZED':'FORBIDDEN','Client access changed.');}
                catch(\InvalidArgumentException){return $this->error(422,'INVALID_REQUEST','Station unavailable.');}
                return $this->response(200,['data'=>['station_id'=>$route[2]['id'],'favorite'=>$method==='PUT','revision'=>$revision],'meta'=>self::writeMeta($revision)],['Cache-Control'=>'no-store']);
            }
            $result=$wrap($matches[0]); $definition='stationResponse';
        } elseif (in_array($handler,['heartbeat','pair','preferencesPatch'],true)) {
            if (strlen($body) > 32768) return $this->error(400,'INVALID_REQUEST','Request too large.');
            try {$input=json_decode($body,false,64,JSON_THROW_ON_ERROR);} catch (\JsonException) {return $this->error(400,'INVALID_REQUEST','Malformed JSON.');}
            $inputDefinition=['heartbeat'=>'heartbeatRequest','pair'=>'pairRequest','preferencesPatch'=>'preferencesPatch'][$handler];
            if (!$this->validator->valid($inputDefinition,$input)) return $this->error(400,'INVALID_REQUEST','Request does not match contract.');
            if ($handler === 'preferencesPatch') {
                if (!isset($headers['if-match'])) return $this->error(428,'PRECONDITION_REQUIRED','If-Match required.');
                if ($headers['if-match'] !== self::etag($fixtures['preferences']) || $input->revision !== $fixtures['preferences']['data']['revision']) return $this->error(412,'REVISION_CONFLICT','Preferences changed; reload them.');
            }
            if($handler==='preferencesPatch' && !$fixture){
                $patch=(array)$input;unset($patch['revision']);
                if(array_key_exists('selected_station_id',$patch) && !in_array('radio:read',$client['scopes'],true))return $this->error(403,'FORBIDDEN','Radio permission required.');
                try{(new \MediaCentre\Client\SettingsRepository($db))->update($client['id'],$input->revision,$patch);$result=self::scoped((new \MediaCentre\Storage\ReadModel($db,(bool)($this->config->values['development']['test_data'] ?? false)))->load($client['id']),$client['scopes'])['preferences'];}
                catch(\MediaCentre\Storage\RevisionConflict){return $this->error(412,'REVISION_CONFLICT','Preferences changed; reload them.');}
                catch(\MediaCentre\Security\AccessDenied $denied){return $this->error($denied->status,$denied->status===401?'UNAUTHORIZED':'FORBIDDEN','Client access changed.');}
                catch(\InvalidArgumentException){return $this->error(422,'INVALID_REQUEST','Preference value unavailable.');}
                return $this->response(200,$result,['Cache-Control'=>'no-store','ETag'=>self::etag($result)]);
            }
            if ($handler !== 'heartbeat') return $this->error(501,'NOT_IMPLEMENTED','Synthetic fixture writes are disabled.');
            if(!$fixture){
                try{$db->transaction(fn()=>(new \MediaCentre\Client\ClientRepository($db))->heartbeat($client['id'],$input->client_version));}
                catch(\MediaCentre\Security\AccessDenied $denied){return $this->error($denied->status,$denied->status===401?'UNAUTHORIZED':'FORBIDDEN','Client access changed.');}
            }
            $result=$wrap(['received_at'=>gmdate('Y-m-d\TH:i:s\Z'),'next_heartbeat_seconds'=>60]); $definition='heartbeatResponse';
        } elseif ($handler === 'dashboard') {
            $result=$fixtures['dashboard'] ?? $wrap(['weather'=>$fixtures['weather'],'finance'=>$fixtures['finance'],'radio'=>['favorites'=>array_values(array_filter($fixtures['stations'],static fn(array $s): bool => $s['favorite'])),'selected_station_id'=>$fixtures['preferences']['data']['selected_station_id']]]); $definition='dashboard';
        } else {
            $key=$handler === 'client/preferences' ? 'preferences' : $handler;
            $result=$fixtures[$key]; $definition=$key === 'preferences' ? 'preferencesResponse' : $key;
        }
        if (!$this->validator->valid($definition, json_decode(json_encode($result,JSON_THROW_ON_ERROR)))) throw new \RuntimeException('Response contract violation.');
        $cache=['Cache-Control'=>'private, no-cache'];
        if ($method === 'GET') {
            $etag=self::etag($result); $cache['ETag']=$etag;
            $matches=array_map('trim',explode(',', $headers['if-none-match'] ?? ''));
            if (in_array($etag,$matches,true) || in_array('W/'.$etag,$matches,true) || in_array('*',$matches,true)) return ['status'=>304,'headers'=>$cache,'body'=>null];
        } else $cache=['Cache-Control'=>'no-store'];
        return $this->response(200,$result,$cache);
    }
    public static function etag(array $body): string {return '"'.hash('sha256',json_encode($body,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR)).'"';}
    private static function writeMeta(int $revision=1): array {return ['test_data'=>false,'scenario'=>'database','revision'=>$revision,'freshness'=>'fresh','data_as_of'=>null,'fetched_at'=>gmdate('Y-m-d\TH:i:s\Z'),'valid_until'=>null,'source'=>'local configuration'];}
    private static function scoped(array $data,array $scopes): array
    {
        $weather=in_array('weather:read',$scopes,true);$finance=in_array('finance:read',$scopes,true);$radio=in_array('radio:read',$scopes,true);
        $data['bootstrap']['data']['features']['weather']=$weather;$data['bootstrap']['data']['features']['finance']=$finance;$data['bootstrap']['data']['features']['radio']=$radio;
        $data['bootstrap']['data']['features']['photoframe']=($data['bootstrap']['data']['features']['photoframe'] ?? false)&&in_array('photoframe:read',$scopes,true);
        if(!$weather)$data['dashboard']['data']['weather']=['data'=>['location'=>$data['preferences']['data']['location'],'current'=>['temperature_c'=>null,'condition'=>'unknown'],'days'=>[]],'meta'=>array_replace(self::writeMeta(),['freshness'=>'empty','fetched_at'=>null,'source'=>'permission not granted'])];
        if(!$finance){$data['dashboard']['data']['finance']=['data'=>['base_currency'=>'RUB','list_revision'=>1,'items'=>[]],'meta'=>array_replace(self::writeMeta(),['freshness'=>'empty','fetched_at'=>null,'source'=>'permission not granted'])];$data['bootstrap']['data']['finance_list_revision']=1;}
        if(!$radio){$data['dashboard']['data']['radio']=['favorites'=>[],'selected_station_id'=>null];$data['bootstrap']['data']['settings']['selected_station_id']=null;$data['preferences']['data']['selected_station_id']=null;}
        if(!$weather || !$finance || !$radio)$data['dashboard']['meta']=array_replace($data['dashboard']['meta'],['revision'=>1,'data_as_of'=>null,'fetched_at'=>null,'valid_until'=>null,'freshness'=>'partial','source'=>'permitted stored blocks']);
        return $data;
    }
    private function response(int $status,array $body,array $headers=[]): array {return ['status'=>$status,'headers'=>$headers,'body'=>$body];}
    private function error(int $status,string $code,string $message,array $headers=[]): array
    {
        $bytes=random_bytes(16); $bytes[6]=chr((ord($bytes[6]) & 15) | 64); $bytes[8]=chr((ord($bytes[8]) & 63) | 128); $hex=bin2hex($bytes);
        $uuid=substr($hex,0,8).'-'.substr($hex,8,4).'-'.substr($hex,12,4).'-'.substr($hex,16,4).'-'.substr($hex,20);
        return $this->response($status,['error'=>['code'=>$code,'message'=>$message],'request_id'=>$uuid],['Cache-Control'=>'no-store']+$headers);
    }
}
