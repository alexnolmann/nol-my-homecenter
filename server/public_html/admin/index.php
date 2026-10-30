<?php
declare(strict_types=1);
$config=require dirname(__DIR__,2).'/private/bootstrap.php';
header('Content-Type: text/html; charset=utf-8');header('Cache-Control: no-store');
header("Content-Security-Policy: default-src 'none'; style-src 'self'; script-src 'self'; img-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
header('X-Content-Type-Options: nosniff');header('Referrer-Policy: no-referrer');
if(!MediaCentre\Admin\Access::allowed($config,$_SERVER)){http_response_code(403);exit('Use the configured administration endpoint.');}
$method=$_SERVER['REQUEST_METHOD'] ?? '';
if(!in_array($method,['GET','POST'],true)){http_response_code(405);header('Allow: GET, POST');exit('Method not allowed.');}
$photoUpload=$method==='POST' && ($_POST['action'] ?? null)==='photo_upload' && str_starts_with(strtolower($_SERVER['CONTENT_TYPE'] ?? ''),'multipart/form-data');
if($method==='POST' && ($photoUpload?((int)($_SERVER['CONTENT_LENGTH'] ?? 0)>256*1024*1024):((int)($_SERVER['CONTENT_LENGTH'] ?? 0)>32768 || !str_starts_with(strtolower($_SERVER['CONTENT_TYPE'] ?? ''),'application/x-www-form-urlencoded') || $_FILES))){http_response_code(400);exit('Invalid form request.');}
try{
    $db=new MediaCentre\Storage\Database(MediaCentre\Storage\Connection::open($config));
    $admins=new MediaCentre\Security\AdminRepository($db);
    MediaCentre\Admin\Session::start(dirname(__DIR__,2).'/storage/admin-sessions',MediaCentre\Admin\Access::isSecure($_SERVER));
    $admin=MediaCentre\Admin\Session::current($admins);$message='';
    $db->adminActor=$admin['id'] ?? null;
    $db->adminAuthRevision=$admin===null?null:(int)$admin['auth_revision'];
    $section=$_GET['section'] ?? 'overview';
    if(!is_string($section) || !in_array($section,['overview','weather','radio','finance','photoframe','climate','music','clients','system'],true)){http_response_code(404);session_write_close();exit('Page not found.');}
    $photos=new MediaCentre\Photoframe\PhotoRepository($db,dirname(__DIR__,2).'/storage/photoframe');
    if($section==='photoframe' && isset($_GET['photo'])){
        if(!$admin){http_response_code(401);session_write_close();exit;}
        $id=$_GET['photo'];$hash=$_GET['hash'] ?? '';
        if(!is_string($id)||!preg_match('/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/iD',$id)||!is_string($hash)||!preg_match('/^[a-f0-9]{64}$/D',$hash)){http_response_code(400);session_write_close();exit;}
        $image=$photos->image($id,$hash,'thumb');session_write_close();if(!$image){http_response_code(404);exit;}
        header_remove('Content-Type');header('Content-Type: image/jpeg');header('Content-Length: '.$image['bytes']);readfile($image['path']);exit;
    }
    $catalogue=null;$fieldErrors=[];
    if($section==='radio'){
        $query=$_GET['q'] ?? '';$page=$_GET['page'] ?? '1';
        if(!is_string($query)||preg_match('//u',$query)!==1||preg_match_all('/./us',$query)>100||!is_string($page)||!preg_match('/^[1-9][0-9]{0,5}$/D',$page)||(int)$page>100000){http_response_code(400);session_write_close();exit('Invalid catalogue search.');}
        $catalogue=['query'=>$query,'page'=>(int)$page];
    }
    $forms=new MediaCentre\Admin\FormActions($db);
    if(isset($_SESSION['flash'])){$message=$_SESSION['flash'];unset($_SESSION['flash']);}
    if($method==='POST'){
        if(in_array($_POST['action'] ?? null,array_merge(MediaCentre\Admin\FormActions::ACTIONS,['pair_issue','client_revoke','client_rights','client_title','photo_upload','photo_toggle','photo_delete','photo_move']),true)){
            if(!$admin)$result=['status'=>401,'message'=>'Войдите в панель.'];
            elseif(!MediaCentre\Admin\Session::checkCsrf($_POST['csrf'] ?? null))$result=['status'=>403,'message'=>'Форма устарела. Откройте её заново.'];
            else{
                try{
                    $action=$_POST['action'];
                    if(in_array($action,['photo_upload','photo_toggle','photo_delete','photo_move'],true)){
                        if($section!=='photoframe')throw new InvalidArgumentException();
                        if($action==='photo_upload'){
                            MediaCentre\Validation\Input::keys($_POST,['csrf','action']);
                            if(count($_FILES)!==1||!isset($_FILES['photos']))throw new InvalidArgumentException('Выберите фотографии для загрузки.');
                            $files=MediaCentre\Photoframe\PhotoRepository::batch($_FILES['photos']);$added=0;$failed=[];
                            foreach($files as $index=>$file){try{$photos->add($file);$added++;}catch(InvalidArgumentException $error){$failed[]='№ '.($index+1).': '.$error->getMessage();}}
                            if($added===0)throw new InvalidArgumentException(implode(' ',array_slice($failed,0,3)));
                            $_SESSION['flash']='Загружено фотографий: '.$added.'.'.($failed?' Пропущено: '.implode(' ',array_slice($failed,0,3)).(count($failed)>3?' И ещё '.(count($failed)-3).'.':''):'');
                        }elseif($action==='photo_toggle'){
                            MediaCentre\Validation\Input::keys($_POST,['csrf','action','enabled']);
                            $photos->toggle(($_POST['enabled'] ?? '')==='1');$_SESSION['flash']='Доступность фоторамки изменена.';
                        }else{
                            MediaCentre\Validation\Input::keys($_POST,$action==='photo_move'?['csrf','action','id','direction']:['csrf','action','id']);
                            $id=MediaCentre\Validation\Input::uuid($_POST['id'] ?? null);
                            if($action==='photo_delete'){$photos->delete($id);$_SESSION['flash']='Фотография удалена.';}
                            else{$direction=$_POST['direction'] ?? null;if(!in_array($direction,['up','down'],true))throw new InvalidArgumentException();$photos->move($id,$direction==='up'?-1:1);$_SESSION['flash']='Порядок фотографий изменён.';}
                        }
                    }elseif($action==='pair_issue'){
                        MediaCentre\Validation\Input::keys($_POST,['csrf','action','location_id','template_client_id','scopes']);
                        $scopes=MediaCentre\Admin\FormModel::rights(['scopes'=>$_POST['scopes'] ?? [],'revision'=>1])->values['scopes'];
                        $issued=(new MediaCentre\Security\PairingRepository($db))->issue($admin['id'],(int)$admin['auth_revision'],MediaCentre\Validation\Input::uuid($_POST['location_id'] ?? null),($_POST['template_client_id'] ?? '')===''?null:MediaCentre\Validation\Input::uuid($_POST['template_client_id']),$scopes);
                        $_SESSION['pair_once']=$issued['code'];$_SESSION['flash']='Код выдан на 10 минут. Он действует один раз.';
                    }elseif($action==='client_revoke'){
                        MediaCentre\Validation\Input::keys($_POST,['csrf','action','client_id','revision']);
                        (new MediaCentre\Security\PairingRepository($db))->revoke(MediaCentre\Validation\Input::uuid($_POST['client_id'] ?? null),MediaCentre\Admin\FormActions::revision($_POST['revision'] ?? null),$admin['id']);$_SESSION['flash']='Доступ планшета отозван.';
                    }elseif($action==='client_title'){
                        MediaCentre\Validation\Input::keys($_POST,['csrf','action','client_id','revision','display_title']);
                        (new MediaCentre\Client\SettingsRepository($db))->setDisplayTitle(MediaCentre\Validation\Input::uuid($_POST['client_id'] ?? null),MediaCentre\Admin\FormActions::revision($_POST['revision'] ?? null),$_POST['display_title'] ?? null);$_SESSION['flash']='Заголовок планшета сохранён.';
                    }elseif($action==='client_rights'){
                        MediaCentre\Validation\Input::keys($_POST,['csrf','action','client_id','revision','scopes']);
                        $scopes=MediaCentre\Admin\FormModel::rights(['scopes'=>$_POST['scopes'] ?? [],'revision'=>1])->values['scopes'];
                        (new MediaCentre\Client\RightsRepository($db))->change(MediaCentre\Validation\Input::uuid($_POST['client_id'] ?? null),MediaCentre\Admin\FormActions::revision($_POST['revision'] ?? null),$scopes);$_SESSION['flash']='Права планшета изменены.';
                    }else $_SESSION['flash']=$forms->save($_POST);
                    $result=['status'=>303,'redirect'=>'/admin/?section='.$section];
                }
                catch(MediaCentre\Storage\RevisionConflict){$result=['status'=>409,'message'=>'Запись уже изменилась. Перечитайте страницу перед сохранением.'];}
                catch(MediaCentre\Security\AccessDenied){MediaCentre\Admin\Session::reset();$admin=null;$result=['status'=>401,'message'=>'Сессия отозвана. Войдите заново.'];}
                catch(MediaCentre\Admin\FieldErrors $error){$fieldErrors=$error->fields;$result=['status'=>422,'message'=>'Проверьте поля, отмеченные ниже. Изменения не сохранены.'];}
                catch(InvalidArgumentException|OutOfBoundsException $error){$result=['status'=>422,'message'=>in_array($section,['photoframe','climate','music'],true)?$error->getMessage():'Проверьте поля: координаты, URL, идентификаторы, порядок и часовой пояс.'];}
                catch(PDOException){$result=['status'=>409,'message'=>'Такая запись уже существует либо связанный объект изменился.'];}
            }
        }else $result=(new MediaCentre\Admin\AuthController($admins,new MediaCentre\Security\RateLimiter($db)))->post($_POST,$_SERVER['REMOTE_ADDR'] ?? 'unknown');
        if($result['status']===303){session_write_close();header('Location: '.$result['redirect'],true,303);exit;}
        http_response_code($result['status']);if($result['status']===429)header('Retry-After: 900');$message=$result['message'];
        $admin=MediaCentre\Admin\Session::current($admins);
    }
    $pairCode=$_SESSION['pair_once'] ?? null;unset($_SESSION['pair_once']);
    $csrf=MediaCentre\Admin\Session::csrf();session_write_close();
    $e=MediaCentre\Admin\Pages::escape(...);
    if($admin){$view=$forms->view($catalogue);$photoState=$section==='photoframe'?$photos->state():null;$invalidForm=http_response_code()===422?$_POST:[];require dirname(__DIR__,2).'/private/views/admin/panel.php';}
    else require dirname(__DIR__,2).'/private/views/admin/auth.php';
}catch(Throwable){http_response_code(503);echo 'Панель временно недоступна.';}
