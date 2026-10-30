<?php
declare(strict_types=1);
namespace MediaCentre\Security;
use MediaCentre\Storage\Database;
final class RateLimiter
{
    public function __construct(private Database $db){}
    // A shared MySQL lock serializes limits across PHP workers; no passwords/codes/IP in storage.
    public function allow(string $bucket,int $maximum,int $seconds,int $now=0): bool
    {
        $now=$now ?: time();$key=hash('sha256',$bucket,true);
        return $this->db->transaction(function()use($key,$maximum,$seconds,$now):bool{
            $this->db->query('INSERT IGNORE INTO auth_limits(bucket_hash,window_started,attempts) VALUES(?,?,0)',[$key,$now]);
            $row=$this->db->one('SELECT * FROM auth_limits WHERE bucket_hash=? FOR UPDATE',[$key]);
            $start=(int)$row['window_started'];$attempts=(int)$row['attempts'];
            if($now-$start >= $seconds){$start=$now;$attempts=0;}
            if($attempts >= $maximum)return false;
            $this->db->query('UPDATE auth_limits SET window_started=?,attempts=? WHERE bucket_hash=?',[$start,$attempts+1,$key]);
            // Bound retention. Limits use fixed windows no longer than one hour.
            $this->db->query('DELETE FROM auth_limits WHERE window_started<? LIMIT 100',[$now-86400]);
            return true;
        });
    }
}
