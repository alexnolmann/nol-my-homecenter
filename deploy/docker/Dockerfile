FROM composer:2.9.4@sha256:22a95bd2bc3cf6ff19550a246208138d35f0015602db3d67dc6841945324acc1 AS composer
FROM php:8.4.19-fpm-bookworm@sha256:7b0f2acae67bd1678a12abcb359023c4e4fd168a87f4704f5a75c96f0a5406d5
RUN apt-get update && apt-get install -y --no-install-recommends libcurl4-openssl-dev libxml2-dev libsodium-dev libjpeg62-turbo-dev libpng-dev \
    && docker-php-ext-configure gd --with-jpeg \
    && for ext in pdo_mysql curl sodium xml simplexml bcmath gd exif; do \
       php -r "exit(extension_loaded('$ext') ? 0 : 1);" || docker-php-ext-install "$ext"; done \
    && rm -rf /var/lib/apt/lists/*
COPY --from=composer /usr/bin/composer /usr/local/bin/composer
WORKDIR /app/server
COPY server/composer.json server/composer.lock ./
RUN composer install --no-dev --no-interaction --no-progress --prefer-dist --classmap-authoritative
COPY server/ ./
RUN composer dump-autoload --no-dev --classmap-authoritative && composer check-platform-reqs \
    && mkdir -p storage && chown www-data:www-data storage
COPY contracts/ /app/contracts/
COPY deploy/docker/php.ini /usr/local/etc/php/conf.d/mediacentre.ini
USER www-data
CMD ["php-fpm"]
