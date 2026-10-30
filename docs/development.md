# Сборка и проверки

## Android

Используйте JDK 17, SDK platform 36 и build-tools 35.0.0. Gradle Wrapper закреплён в репозитории. На Windows `tools/android/bootstrap.ps1` подготавливает локальные инструменты в `.local`; этот каталог не публикуется.

Нужен локальный debug-keystore в `.local/signing/h0-debug.keystore`. Это воспроизводимый ключ разработки, а не production-подпись. Скрипт `tools/android/build.ps1` создаёт его сам. Для ручной/CI-сборки:

```powershell
keytool -genkeypair -keystore .local/signing/h0-debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Local Debug,O=Home Media Centre,C=XX'
```

Предварительно создайте `.local/signing`. На машине с локальными инструментами:

```powershell
./tools/android/build.ps1 -Verify
```

Или с собственными `JAVA_HOME` и `ANDROID_HOME`:

```powershell
cd android
./gradlew.bat --no-daemon :app:testDebugUnitTest :probe:testDebugUnitTest :app:assembleDebug :app:assembleRelease :probe:assembleDebug
cd ..
./tools/android/check-apk.ps1
```

На Linux используйте `./gradlew`. Release-подпись необязательна для проверки сборки; для установки релиза задайте `MC_SIGNING_KEYSTORE`, `MC_SIGNING_STORE_PASSWORD`, `MC_SIGNING_KEY_ALIAS`, `MC_SIGNING_KEY_PASSWORD` локально. Не коммитьте keystore или значения переменных. Локальный CA не входит в публичный исходный набор; при собственной HTTPS-разработке debug-клиент может использовать игнорируемый `android/app/src/debug/assets/development-ca.pem`.

## PHP / MySQL

```powershell
composer install --working-dir=server --no-interaction --prefer-dist
php server/tests/run.php
php tools/check-public.php
```

Для полного `php server/tests/ci.php` нужны **все пять** переменных `MC_TEST_DB_HOST`, `MC_TEST_DB_PORT`, `MC_TEST_DB_NAME`, `MC_TEST_DB_USER`, `MC_TEST_DB_PASSWORD`. Схема должна оканчиваться на `_test`, пользователь не должен быть root. Наборы изменяют только эту тестовую базу. Частичная конфигурация отвергается.

CI создаёт собственный случайный Sodium-ключ на время запуска, передаёт его дочерним тестам и удаляет в `finally`. Рабочий ключ существующего сервера для тестов не нужен.

Альтернатива Windows — `tools/testing/start-mysql.ps1 -MySqlRoot <путь-к-MySQL-8.4> -PhpPath php` и `php tools/testing/init-test-database.php`. Это отдельный локальный экземпляр с собственным datadir и случайными паролями в `.local`; он не должен заменять рабочий MySQL. Завершение: `tools/testing/stop-mysql.ps1 -PhpPath php`.

`deploy/osp/check-http.ps1 -OpenServerRoot <путь-к-OSPanel>` проверяет HTTP-маршруты в отдельном временном окружении. Для этого также нужна тестовая MySQL.

GitHub Actions оставлен **только с ручным запуском**. Публичная сборка не требует домашних ключей, CA, фотографий или логов исходного планшета.

## Перед публикацией

```powershell
php tools/check-public.php
git status --short
git diff --cached
```

Просмотрите будущий состав коммита и не используйте `git add -f` для локальных файлов. Проверка не выводит найденные секреты, но не заменяет ручную оценку новых данных. Публикуются исходники; APK и логи сборки — отдельные артефакты, а не часть Git-истории.
