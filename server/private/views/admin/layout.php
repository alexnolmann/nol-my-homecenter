<!doctype html>
<html lang="ru">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title><?= $escape($title) ?> · MediaCentre</title><link rel="stylesheet" href="/assets/admin.css"></head>
<body><div class="shell"><nav aria-label="Разделы управления"><strong>MediaCentre</strong><p>Домашняя панель · H1</p>
<?php foreach (\MediaCentre\Admin\Pages::SECTIONS as $key=>$label): ?><a href="?section=<?= $escape($key) ?>&amp;scenario=<?= $escape($scenario) ?>" <?= $key===$section?'aria-current="page"':'' ?>><?= $escape($label) ?></a><?php endforeach ?>
<span class="disabled">Умный дом · поздние этапы</span></nav>
<main><header><p class="eyebrow">ЛОКАЛЬНОЕ DEVELOPMENT-ОКРУЖЕНИЕ</p><h1><?= $escape($title) ?></h1><p>Каркас управления. Настройка и вход администратора появятся в H3.</p></header>
<aside class="test">ТЕСТОВЫЕ ДАННЫЕ · <?= $escape($scenario) ?> · <?= $escape($fixtures['meta']['freshness']) ?>. Цены и прогноз синтетические.</aside>
<div class="scenarios" aria-label="Тестовые сценарии"><?php foreach (['success','empty','partial','stale'] as $case): ?><a href="?section=<?= $escape($section) ?>&amp;scenario=<?= $escape($case) ?>"><?= $escape($case) ?></a><?php endforeach ?></div>
<?php require __DIR__.'/content.php' ?>
<footer>PHP <?= $escape(PHP_VERSION) ?> · API v1 · features.home=false · формы записи появятся после хранения и авторизации</footer>
</main></div></body></html>
