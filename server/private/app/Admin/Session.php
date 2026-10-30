<?php
declare(strict_types=1);
namespace MediaCentre\Admin;
use MediaCentre\Security\AdminRepository;
final class Session
{
    public const IDLE=1800, ABSOLUTE=43200;
    public static function start(string $storage,bool $secure=true): void
    {
        if(session_status()===PHP_SESSION_ACTIVE)throw new \LogicException('Session already active.');
        if(!is_dir($storage) && !mkdir($storage,0700,true))throw new \RuntimeException('Session storage unavailable.');
        ini_set('session.use_strict_mode','1');ini_set('session.use_only_cookies','1');ini_set('session.use_trans_sid','0');
        ini_set('session.save_path',$storage);ini_set('session.gc_maxlifetime',(string)self::ABSOLUTE);
        session_name('MCADMIN');
        session_set_cookie_params(['lifetime'=>0,'path'=>'/admin','secure'=>$secure,'httponly'=>true,'samesite'=>'Strict']);
        session_start();
        if(!isset($_SESSION['csrf']))$_SESSION['csrf']=bin2hex(random_bytes(32));
    }
    public static function csrf(): string {return $_SESSION['csrf'];}
    public static function checkCsrf(mixed $value): bool {return is_string($value) && hash_equals(self::csrf(),$value);}
    public static function valid(array $state,?array $admin,int $now): bool
    {
        return $admin!==null && (bool)$admin['enabled'] && ($state['admin_id'] ?? null)===$admin['id']
            && ($state['auth_revision'] ?? null)===(int)$admin['auth_revision']
            && isset($state['started'],$state['seen']) && $now >= $state['started'] && $now >= $state['seen']
            && $now-$state['seen']<self::IDLE && $now-$state['started']<self::ABSOLUTE;
    }
    public static function current(AdminRepository $repo): ?array
    {
        $admin=isset($_SESSION['admin_id'])?$repo->find($_SESSION['admin_id']):null;
        if(self::valid($_SESSION,$admin,time())){$_SESSION['seen']=time();return $admin;}
        if(isset($_SESSION['admin_id']))self::reset();
        return null;
    }
    public static function login(array $admin): void
    {
        session_regenerate_id(true);
        $_SESSION=['admin_id'=>$admin['id'],'auth_revision'=>(int)$admin['auth_revision'],'started'=>time(),'seen'=>time(),'csrf'=>bin2hex(random_bytes(32))];
    }
    public static function reset(): void {session_regenerate_id(true);$_SESSION=['csrf'=>bin2hex(random_bytes(32))];}
}
