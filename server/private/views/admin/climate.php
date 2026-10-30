<?php
$climateRepo=new \MediaCentre\Climate\ClimateRepository($db);
$climateHidden=static function(string $action,string $client,int $revision)use($hidden,$e):void{
    $hidden($action);?><input type="hidden" name="client_id" value="<?= $e($client) ?>"><input type="hidden" name="revision" value="<?= $e($revision) ?>"><?php
};
?>
<section><p>Датчики Xiaomi LYWSD03MMC читаются по Bluetooth самим планшетом. Панель хранит последние переданные показания. Настройки синхронизируются примерно раз в минуту, пока приложение открыто и экран включён; при отсутствии связи планшет продолжает опрос самостоятельно.</p><p>Новый датчик можно найти во вкладке «Климат» на планшете или добавить здесь по Bluetooth ID (MAC-адресу). До трёх датчиков на планшет. Время показаний ниже — московское.</p><a href="/admin/?section=climate">Обновить показания панели</a></section>
<?php foreach($view['clients'] as $tablet):
$climate=$climateRepo->state($tablet['id']);$disabled=$tablet['revoked_at']!==null || !$tablet['paired'];
?>
<section class="climate-tablet"><h2><?= $e($tablet['name']) ?></h2><p>Версия: <?= $e($tablet['app_version'] ?? 'не сообщена') ?><?= $disabled?' · доступ отключён':'' ?></p>
<form method="post"><?php $climateHidden('climate_interval',$tablet['id'],$climate['revision']); ?><label>Автообновление датчиков (минуты) <input type="number" name="poll_minutes" min="1" max="120" required value="<?= $e($climate['poll_minutes']) ?>"></label><button <?= $disabled?'disabled':'' ?>>Сохранить интервал</button></form>
<p>По умолчанию — раз в 15 минут. Опрос при выключенном экране приостановлен; кнопка обновления на планшете работает сразу.</p>
<?php if(!$climate['sensors']): ?><p>Датчиков пока нет<?= !$climate['initialized']?' · сохранённые на планшете появятся после первого подключения обновлённого приложения':'' ?>.</p><?php endif; ?>
<div class="climate-grid">
<?php foreach($climate['sensors'] as $sensor):
$latest=$sensor['latest'];$stale=$latest===null || (int)floor(microtime(true)*1000)-$latest['measured_at']>max(15,2*$climate['poll_minutes'])*60000;
$when=$latest===null?null:(new DateTimeImmutable('@'.intdiv($latest['measured_at'],1000)))->setTimezone(new DateTimeZone('Europe/Moscow'))->format('d.m.Y H:i');
?>
<div class="climate-card"><h3><?= $e($sensor['name']) ?></h3><p class="climate-id">Bluetooth ID: <code><?= $e($sensor['address']) ?></code></p>
<p class="climate-values"><?= $latest===null?'— °C · — %':$e(number_format($latest['temperature'],1,',','').' °C · '.$latest['humidity'].' %') ?></p>
<p><?= $latest===null?'Показаний пока нет':$e(($stale?'Последние показания: ':'Обновлено: ').$when) ?></p>
<?php if($latest!==null): ?><p>Батарейка: <?= $e(number_format($latest['battery_millivolts']/1000,2,',','')) ?> В</p><?php endif; ?>
<?php if($sensor['status']==='unavailable'): ?><p>Последняя попытка: датчик недоступен</p><?php elseif($sensor['status']==='bluetooth_off'): ?><p>Bluetooth выключен или нет разрешения</p><?php endif; ?>
<form method="post"><?php $climateHidden('climate_rename',$tablet['id'],$climate['revision']); ?><input type="hidden" name="address" value="<?= $e($sensor['address']) ?>"><label>Название <input name="name" maxlength="40" value="<?= $e($sensor['name']) ?>" required></label><button <?= $disabled?'disabled':'' ?>>Переименовать</button></form>
<form method="post" data-confirm="Удалить датчик из панели и с планшета?"><?php $climateHidden('climate_delete',$tablet['id'],$climate['revision']); ?><input type="hidden" name="address" value="<?= $e($sensor['address']) ?>"><button <?= $disabled?'disabled':'' ?>>Удалить</button></form>
</div><?php endforeach; ?></div>
<?php if(count($climate['sensors'])<3): ?><h3>Добавить датчик по Bluetooth ID</h3><form method="post"><?php $climateHidden('climate_add',$tablet['id'],$climate['revision']); ?><label>Название <input name="name" maxlength="40" placeholder="Спальня" required></label><label>Bluetooth ID (MAC) <input name="address" maxlength="17" pattern="([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}" placeholder="AA:BB:CC:DD:EE:FF" required></label><button <?= $disabled?'disabled':'' ?>>Добавить</button></form><?php endif; ?>
</section><?php endforeach; ?>
<?php if(!$view['clients']): ?><section><p>Сначала зарегистрируйте планшет на вкладке «Планшеты».</p></section><?php endif; ?>
