<?php
declare(strict_types=1);
namespace MediaCentre\Client;
use MediaCentre\Storage\Database;
use MediaCentre\Storage\RevisionConflict;
use MediaCentre\Admin\FormModel;
final class RightsRepository
{
    public function __construct(private Database $db){}
    // Administrative application service only; no HTTP route before H3.
    public function change(string $client,int $expected,array $scopes): int
    {
        $model=FormModel::rights(['scopes'=>$scopes,'revision'=>$expected]);
        return $this->db->transaction(function()use($client,$expected,$model):int{
            $row=$this->db->one('SELECT auth_revision FROM clients WHERE id=? FOR UPDATE',[$client]);
            if(!$row)throw new \OutOfBoundsException('Client not found.');
            if((int)$row['auth_revision']!==$expected)throw new RevisionConflict('Rights revision conflict.');
            $this->db->query('UPDATE clients SET scopes=?,auth_revision=auth_revision+1 WHERE id=?',[Database::json($model->values['scopes']),$client]);
            $this->db->audit('rights','client',$client,['scopes'],$expected,$expected+1);
            return $expected+1;
        });
    }
}
