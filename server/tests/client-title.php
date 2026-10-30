<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);
$location=MediaCentre\Shared\Uuid::random();$client=MediaCentre\Shared\Uuid::random();
$assert=static fn(bool $ok,string $message)=>$ok ?: throw new RuntimeException($message);
try{
    (new MediaCentre\Weather\LocationRepository($db))->create($location,'Title test','59.938630','30.314130','Europe/Moscow');
    (new MediaCentre\Client\ClientRepository($db))->create($client,'ASUS test',$location);
    $settings=new MediaCentre\Client\SettingsRepository($db);
    $assert($settings->setDisplayTitle($client,1,'Кухня')===2,'Title revision did not advance.');
    $assert((new MediaCentre\Storage\ReadModel($db))->load($client)['bootstrap']['data']['settings']['display_title']==='Кухня','Bootstrap omitted the per-tablet title.');
    $row=array_values(array_filter((new MediaCentre\Admin\FormActions($db))->view()['clients'],fn(array $item)=>$item['id']===$client))[0] ?? null;
    $assert($row!==null && $row['display_title']==='Кухня','Admin panel did not receive the title.');
    try{$settings->setDisplayTitle($client,1,'Старое');throw new RuntimeException('Stale title update accepted.');}catch(MediaCentre\Storage\RevisionConflict){}
    try{$settings->setDisplayTitle($client,2,str_repeat('Я',25));throw new RuntimeException('Overlong title accepted.');}catch(InvalidArgumentException){}
    echo "PASS: tablet title is isolated, revisioned, bounded and available in admin and bootstrap.\n";
}finally{
    $db->query('DELETE FROM client_settings WHERE client_id=?',[$client]);
    $db->query('DELETE FROM clients WHERE id=?',[$client]);
    $db->query('DELETE FROM locations WHERE id=?',[$location]);
}
