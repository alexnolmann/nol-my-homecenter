<?php
declare(strict_types=1);
namespace MediaCentre\Security;
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Uuid;
use MediaCentre\Storage\RevisionConflict;

final class AdminRepository
{
    public function __construct(private Database $db){}
    public static function login(string $login): string
    {
        $login=strtolower(trim($login));
        if(!preg_match('/^[a-z0-9][a-z0-9._-]{2,63}$/D',$login))throw new \InvalidArgumentException('Login must contain 3–64 ASCII characters.');
        return $login;
    }
    public static function password(string $password): string
    {
        // PASSWORD_DEFAULT currently uses bcrypt: reject its silent 72-byte truncation.
        if(strlen($password)<12 || strlen($password)>72 || str_contains($password,"\0"))throw new \InvalidArgumentException('Password must contain 12–72 bytes.');
        return $password;
    }
    public function bootstrap(string $login,string $password): string
    {
        $login=self::login($login);$hash=password_hash(self::password($password),PASSWORD_DEFAULT);
        $key='mc-first-admin-'.substr(hash('sha256',(string)$this->db->query('SELECT DATABASE()')->fetchColumn()),0,32);
        if((int)$this->db->query('SELECT GET_LOCK(?,10)',[$key])->fetchColumn()!==1)throw new \RuntimeException('Bootstrap locked.');
        try{return $this->db->transaction(function()use($login,$hash):string{
            if($this->db->query('SELECT COUNT(*) FROM admin_users')->fetchColumn()>0)throw new \LogicException('Administrator already exists; bootstrap refused.');
            $id=Uuid::random();$this->db->query('INSERT INTO admin_users(id,login,password_hash) VALUES(?,?,?)',[$id,$login,$hash]);
            $this->db->securityAudit('admin_create','admin',$id,$id,null,1);return $id;
        });}finally{$this->db->query('SELECT RELEASE_LOCK(?)',[$key]);}
    }
    public function find(string $id): ?array {return $this->db->one('SELECT id,login,enabled,auth_revision FROM admin_users WHERE id=?',[$id]);}
    public function authenticate(string $login,string $password): ?array
    {
        if(strlen($login)>128 || strlen($password)>72 || str_contains($password,"\0"))return null;
        $row=$this->db->one('SELECT * FROM admin_users WHERE login=?',[strtolower(trim($login))]);
        // Same password_verify work for unknown/disabled accounts; hash is never an account password.
        $dummy='$2y$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';
        $verified=password_verify($password,$row['password_hash'] ?? $dummy);
        if(!$row || !$row['enabled'] || !$verified)return null;
        return $this->db->transaction(function()use($row,$password):?array{
            $current=$this->db->one('SELECT * FROM admin_users WHERE id=? FOR UPDATE',[$row['id']]);
            if(!$current || !$current['enabled'] || $current['password_hash']!==$row['password_hash'] || $current['auth_revision']!==$row['auth_revision'])return null;
            if(password_needs_rehash($current['password_hash'],PASSWORD_DEFAULT))$this->db->query('UPDATE admin_users SET password_hash=? WHERE id=?',[password_hash($password,PASSWORD_DEFAULT),$row['id']]);
            $this->db->query('UPDATE admin_users SET last_login_at=UTC_TIMESTAMP(3) WHERE id=?',[$row['id']]);
            return $this->find($row['id']);
        });
    }
    public function changePassword(string $id,int $revision,string $old,string $new): void
    {
        self::password($old);
        $hash=password_hash(self::password($new),PASSWORD_DEFAULT);
        $this->db->transaction(function()use($id,$revision,$old,$hash):void{
            $row=$this->db->one('SELECT * FROM admin_users WHERE id=? FOR UPDATE',[$id]);
            if(!$row || !$row['enabled'] || (int)$row['auth_revision']!==$revision)throw new RevisionConflict('Administrator changed.');
            if(!password_verify($old,$row['password_hash']))throw new \InvalidArgumentException('Current password invalid.');
            $this->db->query('UPDATE admin_users SET password_hash=?,auth_revision=auth_revision+1 WHERE id=?',[$hash,$id]);
            $this->db->securityAudit('admin_password','admin',$id,$id,$revision,$revision+1);
        });
    }
}
