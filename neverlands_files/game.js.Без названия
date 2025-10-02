var fr_size = 240;
var is_ctrl = 0;
var is_alt = 0;
var ChatTimerID = -1;
var ChatDelay = 12;
var ChatFyo = 0;
var lmid = -1;
var latrus = 0;
var OnlineDelay = 60;
var OnlineTimerOn = -1;
var OnlineStop = 1;
var OnlineScrollPosition = 0;
var ChatClearTimerID = -1;
var ChatClearDelay = 600;
var ChatClearSize = 12228;

function change_chatsize(side)
{
       if(side == 1) fr_size += 60;
       else if(side == 0)
       {
              fr_size -= 60;
    	      if(fr_size < 0) fr_size = 0;
       }
       document.getElementById("mainframes").rows = "*,8,1,"+fr_size+",1,30,0";
}

function say_to(login)
{
       var actionlog = top.frames['main_top'].ActionFormUse;
       if((actionlog != null) && (actionlog != ""))
       {
              var login2 = login.replace('%','');
	      top.frames['main_top'].document.all(actionlog).value = login2;
	      top.frames['main_top'].document.all(actionlog).focus();
       }
       else
       {
              if(is_ctrl)
  	      {
    	             while(login.indexOf(' ') >=0) login = login.replace (' ', '%20');
    		     while(login.indexOf('+') >=0) login = login.replace ('+', '%2B');
    		     while(login.indexOf('#') >=0) login = login.replace ('#', '%23');
    		     while(login.indexOf('=') >=0) login = login.replace ('=', '%3D');
    		     window.open('./pinfo.cgi?'+login, '_blank');
              }
  	      else if(is_alt && (login.indexOf ('%') < 0))
  	      {
    	             top.frames['ch_buttons'].document.FBT.text.focus ();
    		     if(top.frames['ch_buttons'].document.FBT.text.value.length < 255)
    		     top.frames['ch_buttons'].document.FBT.text.value = '%<'+login+'> ' + top.frames['ch_buttons'].document.FBT.text.value;
              }
  	      else
  	      {
    	             top.frames['ch_buttons'].document.FBT.text.focus ();
    		     if(top.frames['ch_buttons'].document.FBT.text.value.length < 255)
    		     top.frames['ch_buttons'].document.FBT.text.value = '<'+login+'> '+top.frames['ch_buttons'].document.FBT.text.value;
              }
       }
}

function say_private(login)
{
       var actionlog = top.frames['main_top'].ActionFormUse;
       if((actionlog != null) && (actionlog != ""))
       {
              var login2 = login.replace('%','');
	      top.frames['main_top'].document.all(actionlog).value=login2;
	      top.frames['main_top'].document.all(actionlog).focus();
       }
       else
       {
              if(is_ctrl)
  	      {
    	             while(login.indexOf(' ') >=0) login = login.replace (' ', '%20');
    		     while(login.indexOf('+') >=0) login = login.replace ('+', '%2B');
    		     while(login.indexOf('#') >=0) login = login.replace ('#', '%23');
    		     while(login.indexOf('=') >=0) login = login.replace ('=', '%3D');
    		     window.open('./pinfo.cgi?'+login, '_blank');
              }
  	      else
  	      {
    	             top.frames['ch_buttons'].document.FBT.text.focus();
    		     if(top.frames['ch_buttons'].document.FBT.text.value.length < 255)
    		     top.frames['ch_buttons'].document.FBT.text.value = '%<'+login+'> ' + top.frames['ch_buttons'].document.FBT.text.value;
              }
       }
}

function ch_refresh_a()
{
       if(ChatFyo == 2) top.frames['ch_refr'].location='/ch.php?show=1&fyo=2';
}

function ch_refresh()
{
       if(ChatTimerID >= 0) clearTimeout(ChatTimerID);
       ChatTimerID = setTimeout('ch_refresh()', ChatDelay*1000);
       top.frames['ch_refr'].location='/ch.php?'+Math.random()+'&show=1&fyo='+ChatFyo;
}

function ch_stop_refresh()
{
       if(ChatTimerID >= 0) clearTimeout (ChatTimerID);
       ChatTimerID = -1;
}

function ch_refresh_n()
{
       if(ChatTimerID >= 0) clearTimeout (ChatTimerID);
       ChatTimerID = setTimeout('ch_refresh()', ChatDelay*1000);
}

function set_lmid(nlmid)
{
       if(nlmid == '')
       nlmid = -1;
       var fb = top.frames['ch_buttons'].document.FBT;
       if(fb)
       {
              lmid = nlmid;
    	      fb.lmid.value = nlmid;
       }
}

function save_scroll_p()
{
       OnlineScrollPosition = top.frames['ch_list'].document.body.scrollTop;
}
  
function reload(now)
{
       if(!OnlineStop && (OnlineTimerOn < 0 || now))
       {
              var tm = now ? 2000 : OnlineDelay*1000;
    	      OnlineTimerOn = setTimeout('online_reload('+now+')', tm);
       }
}

function online_reload(now)
{
       if(OnlineTimerOn >= 0)
       {
              clearTimeout(OnlineTimerOn);
    	      if(!OnlineStop) OnlineTimerOn = setTimeout ('online_reload(0)', OnlineDelay * 1000);
    	      else OnlineTimerOn = -1;
       }
       if(!OnlineStop || now) top.frames['ch_list'].location = '/ch.php?lo=1';
}

function ch_refresh_clr()
{
       if(ChatClearTimerID >= 0) clearTimeout(ChatClearTimerID);
       ChatClearTimerID = setTimeout ('ch_refresh_clr()', ChatClearDelay*1000);
       var s = top.frames['chmain'].document.all('msg').innerHTML;
       if(s.length > ChatClearSize)
       {
              var j = s.lastIndexOf('<BR>', s.length - ChatClearSize);
    	      top.frames['chmain'].document.all('msg').innerHTML = s.substring(j, s.length);
       }
}

function clr_input()
{
       if(top.frames["ch_buttons"].document.FBT.pactiondo.checked == true) top.frames["ch_buttons"].document.FBT.pactiondo.checked = false;
       if(top.frames["ch_buttons"].document.FBT.text)
       {
              top.frames["ch_buttons"].document.FBT.text.value = '';
   	      top.frames["ch_buttons"].document.FBT.text.focus();
       }
}

function clr_chat()
{
       if(top.frames['chmain'].document.all('msg'))
       {
              top.frames['chmain'].document.all('msg').innerHTML = '';
   	      top.frames["ch_buttons"].document.FBT.text.focus();
       }
}

function change_chatsetup()
{
    if(ChatFyo == 0)
    {
        ChatFyo = 1;
        top.frames['ch_buttons'].document.FBT.fyo.value = 1;
        top.frames['ch_buttons'].document.FBT.schat.src = 'http://image.neverlands.ru/chat/bb3_me.gif';
        top.frames['ch_buttons'].document.FBT.schat.alt = 'Режим чата (Показывать только личные сообщения)';
        top.frames['ch_buttons'].document.FBT.schat.title = 'Режим чата (Показывать только личные сообщения)';
    }
    else if(ChatFyo == 1)
    {
        ChatFyo = 2;
        top.frames['ch_buttons'].document.FBT.fyo.value = 2;
        ch_stop_refresh();
        top.frames['ch_buttons'].document.FBT.schat.src = 'http://image.neverlands.ru/chat/bb3_none.gif';
        top.frames['ch_buttons'].document.FBT.schat.alt = 'Режим чата (Не показывать сообщения)';
        top.frames['ch_buttons'].document.FBT.schat.title = 'Режим чата (Не показывать сообщения)';
    }
    else
    {
        ChatFyo = 0;
        top.frames['ch_buttons'].document.FBT.fyo.value = 0;
        ch_refresh();
        top.frames['ch_buttons'].document.FBT.schat.src = 'http://image.neverlands.ru/chat/bb3_all.gif';
        top.frames['ch_buttons'].document.FBT.schat.alt = 'Режим чата (Показывать все сообщения)';
        top.frames['ch_buttons'].document.FBT.schat.title = 'Режим чата (Показывать все сообщения)';
    }
}

function change_chatspeed()
{
    if(ChatTimerID >= 0) clearTimeout (ChatTimerID);
    if(ChatDelay == 10) ChatDelay = 30;
    else if(ChatDelay == 30) ChatDelay = 60;
    else ChatDelay = 10;
    ChatTimerID = setTimeout('ch_refresh()', ChatDelay*1000);
    top.frames['ch_buttons'].document.FBT.spchat.src = 'http://image.neverlands.ru/chat/bb_'+ChatDelay+'.gif';
    top.frames['ch_buttons'].document.FBT.spchat.alt = 'Скорость обновления (раз в '+ChatDelay+' секунд)';
    top.frames['ch_buttons'].document.FBT.spchat.title = 'Скорость обновления (раз в '+ChatDelay+' секунд)';
}

function change_latrus()
{
    if(latrus == 0)
    {
        latrus = 1;
        top.frames['ch_buttons'].document.FBT.lrchat.src = 'http://image.neverlands.ru/chat/bb4_ac.gif';
        top.frames['ch_buttons'].document.FBT.lrchat.alt = 'LAT <-> RUS (Транслит включён)';
        top.frames['ch_buttons'].document.FBT.lrchat.title = 'LAT <-> RUS (Транслит включён)';
    }
    else
    {
        latrus = 0;
        top.frames['ch_buttons'].document.FBT.lrchat.src = 'http://image.neverlands.ru/chat/bb4_nc.gif';
        top.frames['ch_buttons'].document.FBT.lrchat.alt = 'LAT <-> RUS (Транслит выключен)';
        top.frames['ch_buttons'].document.FBT.lrchat.title = 'LAT <-> RUS (Транслит выключен)';
    }
}

function start()
{
    ChatTimerID = setTimeout('ch_refresh()', 1000);
    OnlineTimerOn = setTimeout('online_reload(true)', 0);
    ChatClearTimerID = setTimeout('ch_refresh_clr()', ChatClearDelay*1000);
}

function exit_confirm()
{
    return confirm('Вы действительно хотите покинуть игру?');
}

function delete_confirm(wnametxt)
{
    return confirm('Вы действительно хотите выбросить "'+wnametxt+'"?');
}

function exit_redir()
{
    if(exit_confirm()) location = 'exit.php';
}

function DeleteTrue(wname)
{
    if(delete_confirm(wname)) return true;
}

function helpwin(open_page)
{
    url_open = 'http://faq.neverlands.ru/help/'+open_page;
    viewwin = open(url_open,"helpWindow","width=455, height=400, status=no, toolbar=no, menubar=no, resizable=no, scrollbars=yes");
}

function seeroom(open_room)
{
    var url_open = '/ch.php?lo=1&r='+open_room;
    seeroomwin = open(url_open,"SeeRoomWindow","width=300, height=500, status=no, toolbar=no, menubar=no, resizable=no, scrollbars=yes");
}

function clan_private()
{
    top.frames['ch_buttons'].document.FBT.text.focus();
    top.frames['ch_buttons'].document.FBT.text.value = '%clan% ' + top.frames['ch_buttons'].document.FBT.text.value;
}

function view_frames()
{
    document.write('<frameset rows="*,8,1,240,1,30,0" FRAMEBORDER=0 FRAMESPACING=0 BORDER=0 id=mainframes>');
    document.write('<frame src="./main.php" name=main_top scrolling=YES>');
    document.write('<frame src="./ch/resize.html" name=resize scrolling=NO NoResize>');
    document.write('<frame src="./ch/temp.html" name=temp_f scrolling=NO NoResize>');
    document.write('<frameset cols="*,300">');
    document.write('<frame src="./ch/msg.php" name=chmain scrolling=YES MARGINWIDTH=2 MARGINHEIGHT=2>');
    document.write('<frame src="./ch.php?lo=1" name=ch_list scrolling=YES FRAMEBORDER=0 BORDER=0 FRAMESPACING=0 MARGINWIDTH=3 MARGINHEIGHT=0>');
    document.write('</frameset>');
    document.write('<frame src="./ch/tempw.html" name=temp_s scrolling=NO noResize>');
    document.write('<frame src="./ch/but.php" name=ch_buttons scrolling=NO noResize>');
    document.write('<FRAME target="_top" name=ch_refr src="./ch/refr.html" noResize scrolling="no">');
    document.write('</frameset>');
}