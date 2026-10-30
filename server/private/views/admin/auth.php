<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Управление медиацентром</title><link rel="stylesheet" href="/assets/admin.css"><body><main>
<h1>Управление медиацентром</h1>
<?php if($message!==''): ?><p role="alert"><?= $e($message) ?></p><?php endif; ?>
<?php if(!$admin): ?>
<form method="post" action="/admin/"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="login">
<label>Логин <input name="login" autocomplete="username" required maxlength="64"></label>
<label>Пароль <input name="password" type="password" autocomplete="current-password" required maxlength="72"></label><button>Войти</button></form>
<?php else: ?>
<p>Вы вошли как <?= $e($admin['login']) ?>.</p>
<form method="post" action="/admin/"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="logout"><button>Выйти</button></form>
<h2>Смена пароля</h2><form method="post" action="/admin/"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="password">
<label>Текущий пароль <input name="old_password" type="password" autocomplete="current-password" required></label>
<label>Новый пароль <input name="new_password" type="password" autocomplete="new-password" required minlength="12" maxlength="72"></label>
<label>Повторите новый пароль <input name="confirm_password" type="password" autocomplete="new-password" required></label><button>Изменить пароль и выйти</button></form>
<?php endif; ?></main></body></html>
