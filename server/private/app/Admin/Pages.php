<?php
declare(strict_types=1);
namespace MediaCentre\Admin;
final class Pages
{
    public const SECTIONS=['overview'=>'Сводка','weather'=>'Погода','radio'=>'Радиостанции','finance'=>'Финансы','clients'=>'Планшеты','system'=>'Система','diagnostics'=>'Диагностика H0'];
    public static function escape(mixed $value): string {return htmlspecialchars((string)($value ?? '—'),ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8');}
    public static function age(?string $utc,?int $now=null): string
    {
        if($utc===null)return 'Нет данных';
        try{$at=(new \DateTimeImmutable($utc,new \DateTimeZone('UTC')))->getTimestamp();}catch(\Exception){return 'Нет данных';}
        $seconds=($now ?? time())-$at;
        if($seconds<0)return 'Дата в будущем · проверьте часы';
        if($seconds<60)return $seconds.' с назад';
        if($seconds<3600)return intdiv($seconds,60).' мин назад';
        if($seconds<86400)return intdiv($seconds,3600).' ч назад';
        return intdiv($seconds,86400).' дн назад';
    }
    public static function render(string $section,string $scenario,array $fixtures,array $diagnostics): void
    {
        $title=self::SECTIONS[$section];
        $escape=self::escape(...);
        require dirname(__DIR__,2).'/views/admin/layout.php';
    }
}
