<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);$forms=new MediaCentre\Admin\FormActions($db);$uuid=MediaCentre\Shared\Uuid::random(...);
$name='H3 '.bin2hex(random_bytes(8));
$location=['action'=>'location','id'=>'','name'=>$name,'latitude'=>'59.938630','longitude'=>'30.314130','timezone'=>'Europe/Moscow'];$forms->save($location);
$row=$db->one('SELECT * FROM locations WHERE name=?',[$name]);$location['id']=$row['id'];$location['revision']='1';$location['name']=$name.' updated';$forms->save($location);
try{$forms->save($location);throw new RuntimeException('Stale form overwrote row.');}catch(MediaCentre\Storage\RevisionConflict){}
$station=['action'=>'station','id'=>'','name'=>$name,'stream_url'=>'https://example.invalid/original','genres'=>'Jazz, Ambient','country_code'=>'RU','enabled'=>'1'];$forms->save($station);
$saved=$db->one('SELECT * FROM radio_stations WHERE name=?',[$name]);
$station['id']=$saved['id'];$station['revision']='1';$station['stream_url']='https://secret:password@example.invalid/';
try{$forms->save($station);throw new RuntimeException('URL credentials accepted.');}catch(InvalidArgumentException){}
if($db->one('SELECT revision FROM radio_stations WHERE id=?',[$saved['id']])['revision']!==1)throw new RuntimeException('Invalid form changed data.');
foreach(['cbr','moex_iss'] as $type)if(!$db->one('SELECT id FROM integrations WHERE type=?',[$type]))(new MediaCentre\Integration\IntegrationRepository($db))->create($uuid(),$type,[]);
$symbol='H3_'.strtoupper(bin2hex(random_bytes(4)));$instrument=['action'=>'instrument','id'=>'','type'=>'share','symbol'=>$symbol,'board'=>'TQBR','name'=>'<script>alert(1)</script>','enabled'=>'1'];$forms->save($instrument);
$share=$db->one('SELECT * FROM finance_instruments WHERE symbol=?',[$symbol]);$instrument['id']=$share['id'];$instrument['revision']='1';unset($instrument['enabled']);$forms->save($instrument);
if($db->one('SELECT enabled FROM finance_instruments WHERE id=?',[$share['id']])['enabled'])throw new RuntimeException('Disable failed.');
$client=$uuid();(new MediaCentre\Client\ClientRepository($db))->create($client,$name,$row['id']);
$watch=['action'=>'watchlist','client_id'=>$client,'revision'=>'1','items'=>[$share['id']=>['included'=>'1','order'=>'2','dashboard'=>'1']]];$forms->save($watch);
$list=(new MediaCentre\Client\OrderedListRepository($db))->readWatchlist($client);if(count($list)!==1 || !$list[0]['show_on_dashboard'])throw new RuntimeException('Watchlist form failed.');
$admin=['login'=>'test'];$section='finance';$message='';$csrf='test-csrf';$view=$forms->view();$e=MediaCentre\Admin\Pages::escape(...);
ob_start();require dirname(__DIR__).'/private/views/admin/panel.php';$html=ob_get_clean();
if(str_contains($html,'<script>alert') || !str_contains($html,'&lt;script&gt;'))throw new RuntimeException('Form XSS escaped incorrectly.');
echo "PASS: browser form adapters, invalid URL rollback, revisions, MOEX BOARDID, disable, dashboard list and HTML escaping.\n";
