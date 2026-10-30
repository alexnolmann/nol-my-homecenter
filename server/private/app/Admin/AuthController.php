<?php
declare(strict_types=1);
namespace MediaCentre\Admin;
use MediaCentre\Security\AdminRepository;
use MediaCentre\Security\RateLimiter;
final class AuthController
{
    public function __construct(private AdminRepository $admins,private RateLimiter $limits){}
    /** Validated POST. The caller checks the configured transport before opening a session. */
    public function post(array $form,string $ip): array
    {
        if(!Session::checkCsrf($form['csrf'] ?? null))return ['status'=>403,'message'=>'Форма устарела. Откройте её заново.'];
        $action=$form['action'] ?? '';
        if($action==='login'){
            $login=$form['login'] ?? '';$password=$form['password'] ?? '';
            if(!is_string($login) || !is_string($password) || strlen($login)>128 || strlen($password)>72)return ['status'=>422,'message'=>'Неверный логин или пароль.'];
            $ipAllowed=$this->limits->allow('login-ip:'.$ip,20,900);
            if(!$ipAllowed)return ['status'=>429,'message'=>'Слишком много попыток. Повторите через 15 минут.'];
            $accountAllowed=$this->limits->allow('login-account:'.strtolower(trim($login)),5,900);
            if(!$ipAllowed || !$accountAllowed)return ['status'=>429,'message'=>'Слишком много попыток. Повторите через 15 минут.'];
            $admin=$this->admins->authenticate($login,$password);
            if(!$admin)return ['status'=>401,'message'=>'Неверный логин или пароль.'];
            Session::login($admin);return ['status'=>303,'redirect'=>'/admin/'];
        }
        $admin=Session::current($this->admins);
        if(!$admin)return ['status'=>401,'message'=>'Войдите в панель.'];
        if($action==='logout'){Session::reset();return ['status'=>303,'redirect'=>'/admin/'];}
        if($action==='password'){
            if(!is_string($form['old_password'] ?? null) || !is_string($form['new_password'] ?? null) || $form['new_password']!==($form['confirm_password'] ?? null))return ['status'=>422,'message'=>'Проверьте пароль и подтверждение.'];
            try{$this->admins->changePassword($admin['id'],(int)$admin['auth_revision'],$form['old_password'],$form['new_password']);}
            catch(\InvalidArgumentException){return ['status'=>422,'message'=>'Проверьте текущий пароль. Новый пароль: 12–72 байта.'];}
            catch(\MediaCentre\Security\AccessDenied|\MediaCentre\Storage\RevisionConflict){Session::reset();return ['status'=>401,'message'=>'Сессия отозвана. Войдите заново.'];}
            Session::reset();return ['status'=>303,'redirect'=>'/admin/'];
        }
        return ['status'=>400,'message'=>'Неизвестное действие.'];
    }
}
