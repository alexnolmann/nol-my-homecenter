<?php $music=(new MediaCentre\Music\MusicRepository($db))->libraries(true); ?>
<section><h2>Музыка с сервера</h2><p>Каталоги индексируются в фоне. Планшет получает список и слушает выбранный MP3 потоком; оригинальные файлы остаются на диске.</p><p class="muted">У пользователя PHP и Nginx должны быть права чтения каталога. Для планшета включите право «Музыка» в разделе «Планшеты». Повторное сканирование автоматически выполняется примерно раз в час.</p></section>
<?php foreach(array_merge($music,[['id'=>'','revision'=>1,'name'=>'','root_path'=>'','enabled'=>true]]) as $row): ?>
<section><h2><?= $e($row['name']?:'Добавить библиотеку') ?></h2>
<?php if($row['id']!==''): ?><p><?= $e(match($row['scan_status']){'queued'=>'В очереди','scanning'=>'Индексирование','ready'=>'Готово',default=>'Каталог недоступен: проверьте путь и права'}) ?> · треков: <?= $e($row['tracks']) ?> · обработано: <?= $e($row['scanned_files']) ?> · пропущено: <?= $e($row['skipped_files']) ?></p><p class="muted">Последний полный индекс: <?= $e($row['scanned_at'] ?? 'ещё не готов') ?> UTC</p><?php endif; ?>
<form method="post"><?php $hidden('music_save',$row);$input('name','Название',$row['name']);$input('root_path','Абсолютный путь на сервере',$row['root_path']); ?><label><input type="checkbox" name="enabled" value="1" <?= $row['enabled']?'checked':'' ?>> Доступна планшетам</label><button>Сохранить и индексировать</button></form>
<?php if($row['id']!==''): ?><form method="post"><?php $hidden('music_rescan',$row); ?><button>Обновить индекс</button></form><?php endif; ?></section>
<?php endforeach; ?>
