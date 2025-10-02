var Category = 0;
var SubCategory = 0;
var bavail = [];

var current_cat = 0;
var current_uid = 0;

var mtowerTypes = {1: 'Башня огня', 2: 'Башня воды', 3: 'Башня воздуха', 4: 'Башня земли'};


function updateData(response)
{
    if (response['error'] && response['error'] == 'session')
        document.location = document.location;

    bavail['inf'][0] = (response['b'][0] ? response['b'][0] : '');
    bavail['inv'][0] = (response['b'][1] ? response['b'][1] : '');
    bavail['up'][0] = (response['b'][2] ? response['b'][2] : '');

    document.getElementById('main_buttons').innerHTML = ButtonDraw();

    for(var code in response['ba'])
    {
        if (response['ba'].hasOwnProperty(code))
            bcodes[code] = response['ba'][code];
    }

    if (response['ar'] && response['ar'][0] && response['ar'][1])
    {
        if (response['ar'][0] == 'ERROR' || response['ar'][0] == 'SUCCESS')
            MessBoxDiv(response['ar'][1]);
    }

    if (response['buildings'])
    {
        buildings = response['buildings'];
        drawCastle();
    }

    return true;
}

function declension(num, expressions)
{
    var result;
    var count = num % 100;
    if (count >= 5 && count <= 20)
    {
        result = expressions['2'];
    }
    else
    {
        count = count % 10;
        if (count == 1)
            result = expressions['0'];
        else if (count >= 2 && count <= 4)
            result = expressions['1'];
        else
            result = expressions['2'];
    }
    return result;
}

function view_build_top()
{
    if(build[11])
    {
        parent.frames["ch_list"].location = "/ch.php?lo=1";
    }

    ins_HP();
    d.write('<div id="topbar">');
    d.write('<div class=nick>'+sh_align(build[2],0)+sh_sign(build[3],build[4],build[5])+'<b>'+build[0]+'</b>['+build[1]+']&nbsp;</div><div class=hpmp><div class=hp><img src=http://image.neverlands.ru/gameplay/hp.gif width=0 height=6 border=0 id=fHP><img src=http://image.neverlands.ru/gameplay/nohp.gif width=0 height=6 border=0 id=eHP></div><div class=mp><img src=http://image.neverlands.ru/gameplay/ma.gif width=0 height=6 border=0 id=fMP><img src=http://image.neverlands.ru/gameplay/noma.gif width=0 height=6 border=0 id=eMP></div></div><div id=hbar></div></td></tr>');

    d.write('<div class=exit><a href="javascript: top.exit_redir()"><img src=http://image.neverlands.ru/exit.gif align=absmiddle width=15 height=15 border=0></a></div><div align="center" id="main_buttons">'+ButtonGen()+'</div>');
    d.write('</div>');
    cha_HP();

    d.write('<div id="topbarlines"></div>');
}

function view_build_bottom()
{
    d.write('<div id="rating_image">'+view_t()+'</div>');
}

function ButtonGen()
{
    bavail = [];
    for(var i=0; i<mapbt.length; i++)
        bavail[mapbt[i][0]] = [mapbt[i][2],mapbt[i][3]];
    return ButtonDraw();
}

function ButtonDraw()
{
    var str = '';
    if (build[12])
        str += '<input type=button class=fr_but value="Квесты" onclick=\'QActive("'+build[12]+'");\'>';
    for(var i=0; i<mapbt.length; i++)
    {
        if (bavail[mapbt[i][0]][0] != '')
            str += ' <input type=button class=fr_but id="'+mapbt[i][0]+'" value="'+mapbt[i][1]+'" onclick=\'ButClick("'+mapbt[i][0]+'")\'>';
    }
    return str;
}

function ButClick(id)
{
    var goloc = '';
    switch(id)
    {
        case 'inf': goloc = 'main.php?get_id=56&act=10&go=inf&vcode='+bavail[id][0]; break;
        case 'inv': goloc = 'main.php?get_id=56&act=10&go=inv&vcode='+bavail[id][0]; break;
        case 'up': goloc = 'main.php?get_id=56&act=10&go=up&vcode='+bavail[id][0]; break;
    }
    if(goloc)
    {
        for(var j=0; j<bavail[id][1].length; j++) goloc += '&'+bavail[id][1][j][0]+'='+bavail[id][1][j][1];
        document.location = goloc;
    }
}

var timers = [];
function timer_add(name, seconds, action, days)
{
    timers[timers.length] = new Object({
        "active": true,
        "name": name,
        "from": new Date(),
        "seconds": seconds,
        "days": days,
        "action": (seconds > 0 ? action : false)
    });
}

function timers_clear()
{
    timers = [];
}

function timer_delete(name)
{
    for(var i = 0; i < timers.length; i++)
        if (timers[i]["name"] == name)
            timers[i]["active"] = false;
}

function timers_go()
{
    var now = new Date();
    for(var i = 0; i < timers.length; i++)
        if (timers[i]["active"])
        {
            var name = timers[i]["name"];
            var from = timers[i]["from"];
            var seconds = timers[i]["seconds"];
            var totalRemains = (from.getTime() + (seconds * 1000) - now.getTime());
            if (totalRemains > 1)
            {
                var RemainsSec = (parseInt(totalRemains / 1000));

                var RemainsFullDays = 0;
                if (timers[i]["days"])
                {
                    RemainsFullDays = (parseInt(RemainsSec / 86400));
                    RemainsSec -= RemainsFullDays * 86400;
                }

                var RemainsFullHours = (parseInt(RemainsSec / 3600));
                if (RemainsFullHours < 10) { RemainsFullHours = "0" + RemainsFullHours };
                var secInLastHour = RemainsSec - RemainsFullHours * 3600;
                var RemainsMinutes = (parseInt(secInLastHour / 60));
                if (RemainsMinutes < 10) { RemainsMinutes = "0" + RemainsMinutes };
                var lastSec = secInLastHour - RemainsMinutes * 60;
                if (lastSec < 10) { lastSec = "0" + lastSec };
                if (document.getElementById('timer_'+name))
                    document.getElementById('timer_'+name).innerHTML = (RemainsFullDays ? RemainsFullDays + ' ' + declension(RemainsFullDays,['день','дня','дней']) + ' ' : '') + RemainsFullHours + ":" + RemainsMinutes + ":" + lastSec;
            }
            else
            {
                if (document.getElementById('timer_'+name))
                    document.getElementById('timer_'+name).innerHTML = '00:00:00';
                if (timers[i]["action"])
                {
                    var action = timers[i]["action"];
                    action();
                    timers[i]["action"] = false;
                }
                timers[i]["active"] = false;
            }
        }
}
timers_go();
setInterval("timers_go()", 100);

function view_castle()
{
    view_build_top();
    var t = '<div style="margin: 10px auto; width: 1064px; height: 357px;" id="castle_images">';

    t += '</div>';
    t += '<div id="building" style="margin: 10px auto; width: 1064px;"></div>';
    d.write(t);
    view_build_bottom();
    drawCastle();
}

function drawCastle()
{
    var t = '';
    var map = '';
    var path = 'http://image.neverlands.ru/gameplay/castle/';

    t += '<img src="'+path+'back.png" style="position: absolute; z-index: 10;" />';
    t += '<img src="'+path+'sky.png" style="position: absolute; z-index: 11;" />';
    if (buildings[1] > 0)
    {
        t += '<img src="'+path+'fence_'+buildings[1]+'.png" style="position: absolute; z-index: 12;" />';
        map += '<area shape="poly" coords="810,156,805,171,859,192,983,187,995,124,807,155" href="#" onclick="view_building(\'wall\'); return false;" alt="Стена" onmouseover="tooltip(this,\'Стена\')" onmouseout="hide_info(this)" />';
        map += '<area shape="poly" coords="63,137,64,173,0,192,0,144" href="#" onclick="building(\'wall\'); return false;" alt="Стена" onmouseover="tooltip(this,\'Стена\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[2] > 0)
    {
        t += '<img src="'+path+'mtower_'+buildings[2]+'.png" style="position: absolute; z-index: 14;" />';
        map += '<area shape="poly" coords="329,14,321,161,302,177,301,205,322,214,377,228,403,218,387,94,361,11,343,3" href="#" onclick="view_building(\'mtower\'); return false;" alt="Башня Магов" onmouseover="tooltip(this,\'Башня Магов\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[0] > 0)
    {
        t += '<img src="'+path+'main_'+buildings[0]+'.png" style="position: absolute; z-index: 16;" />';
        map += '<area shape="poly" coords="557,14,469,132,475,155,501,197,702,213,758,184,758,135,723,12,624,0" href="#" onclick="view_building(\'main\'); return false;" alt="Цитадель" onmouseover="tooltip(this,\'Цитадель\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[3] > 0)
    {
        t += '<img src="'+path+'fountain_'+buildings[3]+'.png" style="position: absolute; z-index: 18;" />';
        map += '<area shape="poly" coords="586,285,643,217,670,217,705,251,704,261,690,288" href="#" onclick="view_building(\'fountain\'); return false;" alt="Фонтан" onmouseover="tooltip(this,\'Фонтан\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[4] > 0)
    {
        t += '<img src="'+path+'pond_'+buildings[4]+'.png" style="position: absolute; z-index: 20;" />';
        map += '<area shape="poly" coords="376,293,436,275,541,288,559,324,509,349,413,346,368,321" href="#" onclick="view_building(\'pond\'); return false;" alt="Пруд" onmouseover="tooltip(this,\'Пруд\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[5] > 0)
    {
        t += '<img src="'+path+'prison_'+buildings[5]+'.png" style="position: absolute; z-index: 22;" />';
        map += '<area shape="poly" coords="68,95,75,172,258,182,261,144,228,51,207,31,112,16,81,34" href="#" onclick="view_building(\'prison\'); return false;" alt="Тюрьма" onmouseover="tooltip(this,\'Тюрьма\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[6] > 0)
    {
        t += '<img src="'+path+'portal_'+buildings[6]+'.png" style="position: absolute; z-index: 24;" />';
        map += '<area shape="poly" coords="423,197,420,261,493,261,486,192,473,172,434,170" href="#" onclick="view_building(\'portal\'); return false;" alt="Портал" onmouseover="tooltip(this,\'Портал\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[7] > 0)
    {
        t += '<img src="'+path+'hall_'+buildings[7]+'.png" style="position: absolute; z-index: 26;" />';
        map += '<area shape="poly" coords="719,215,789,150,800,153,799,176,863,202,869,239,792,292,718,285" href="#" onclick="view_building(\'hall\'); return false;" alt="Дипломатический Холл" onmouseover="tooltip(this,\'Дипломатический Холл\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[8] > 0)
    {
        t += '<img src="'+path+'storage_'+buildings[8]+'.png" style="position: absolute; z-index: 28;" />';
        map += '<area shape="poly" coords="262,354,256,275,97,210,0,213,0,351" href="#" onclick="view_building(\'storage\'); return false;" alt="Хранилище" onmouseover="tooltip(this,\'Хранилище\')" onmouseout="hide_info(this)" />';
    }
    if (buildings[9] > 0)
    {
        t += '<img src="'+path+'barrack_'+buildings[9]+'.png" style="position: absolute; z-index: 30;" />';
        map += '<area shape="poly" coords="953,318,948,231,997,212,995,48,1040,49,1057,118,1056,318" href="#" onclick="view_building(\'barrack\'); return false;" alt="Бараки" onmouseover="tooltip(this,\'Бараки\')" onmouseout="hide_info(this)" />';
    }

    t += '<img src="http://image.neverlands.ru/1x1.gif" width="1064" height="357" style="position: absolute; z-index: 50;" usemap="#castleMap" />';
    t += '<map id="castleMap" name="castleMap">'+map+'</map>';
    document.getElementById('castle_images').innerHTML = t;
}

function update_page()
{
    document.location = document.location;
}

function view_building(building)
{
    var add = '';
    if (arguments.length > 1)
    {
        for(var i = 1; i < arguments.length; i++)
            add += '&p' + i + '=' + arguments[i];
    }

    AjaxGet('castle_ajax.php?action=building&building='+building+'&vcode='+bcodes[building]+add+'&r='+Math.random()+'', function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        var data = response['r'];
        if (building == 'main' || data.canAccess)
            showBuilding(building, data);
        else
            showUnavailable(data);
    });

    return false;
}

function showUnavailable(response)
{
    var t = '';
    t += '<div style="text-align: center">';
    t += '<br />Идёт строительство здания "<b>'+response.name+'</b>" ('+response.nextLevel+' ур)<br />';
    t += 'Во время строительства постройка недоступна.';
    t += '</div>';
    document.getElementById('building').innerHTML = t;
}

function showBuilding(building, response)
{
    switch (building)
    {
        case 'main': showMain(response); break;
        case 'portal': showPortal(response); break;
        case 'fountain': showFountain(response); break;
        case 'pond': showPond(response); break;
        case 'storage': showStorage(response); break;
        case 'mtower': showMTower(response); break;
        case 'hall': showHall(response); break;
    }
}

function showMain(response)
{
    var t = '';
    var i = 0;
    var r, building, res;

    response.buildTime = parseInt(response.buildTime, 10);
    if (response.build != '' && response.buildTime > 0)
    {
        t += '<fieldset><legend align="center"><b>Строительство здания "'+response.name+'" ('+response.nextLevel+' ур)</b></legend><br /><div style="text-align: center;">';
        if (response.timeLeft > 0)
        {
            t += 'До окончания строительства осталось <b id="timer_building"></b><br />';
            timer_add('building', response.timeLeft+1, update_page, true);
            if (response.forceEndPrice > 0)
            {
                if (response.canForceEnd)
                    t += '<br /><input type="button" class="invbut" value="Завершить строительство мгновенно за '+response.forceEndPrice+' DNV" onclick="if (confirm(\'Вы уверены, что хотите завершить строительство мгновенно за '+response.forceEndPrice+' DNV?\')) forceEndBuilding(\''+response.forceEndVcode+'\')" /><br />';
                else
                    t += '<br /><input type="button" class="lbutdis" value="Завершить строительство мгновенно за '+response.forceEndPrice+' DNV" disabled /><br />';
            }

            if (response.acceleratePrice > 0)
            {
                if (response.canAccelerate)
                    t += '<br /><input type="button" class="invbut" value="Ускорить строительство за '+response.acceleratePrice+' NV" onclick="if (confirm(\'Вы уверены, что хотите ускорить строительство мгновенно за '+response.acceleratePrice+' NV?\')) accelerateBuilding(\''+response.accelerateVcode+'\')" /><br />';
                else
                    t += '<br /><input type="button" class="lbutdis" value="Ускорить строительство за '+response.acceleratePrice+' NV" disabled /><br />';
            }
        }
        else
        {
            t += 'Строительство окончено<br />';
            t += '<br /><input type="button" class="invbut" value="Завершить строительство" onclick="finishBuilding(\''+response.vcode+'\')" /><br />';
        }
        t += '<br /></div></fieldset>';
    }
    else if (response.build != '')
    {

        t += '<fieldset><legend align="center"><b>Строительство здания "'+response.name+'" ('+response.nextLevel+' ур)</b></legend><div style="text-align: center; margin: 0px auto; width: 1000px;"><br />Требуемые ресурсы:<br /><table cellpadding=0 cellspacing=0 border=0 style="border-left: 1px solid #e0e0e0; border-top: 1px solid #e0e0e0;"><tr>';
        if (response.hasOwnProperty('res'))
        {
            for(r in response.res)
            {
                res = response.res[r];
                res['need'] = parseFloat(res['need']);
                res['cur'] = parseFloat(res['cur']);
                i++;
                t += '<td width="125" align="center" style="border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0;" bgcolor=#FCFAF3 valign="top"><font class=weaponch><br><img src=http://image.neverlands.ru/resources/'+res['rid']+'.gif width=60 height=60 onmouseover="tooltip(this,\''+res['name']+'\')" onmouseout="hide_info(this)"><br />'+res['name']+'<br />'+
                    '<b><font color="'+(res['need'] > res['cur'] ? '#a52a2a' : '#00cc00')+'">'+res['cur'] + ' / '+res['need']+'</font></b>'+
                    '<br>У вас есть: '+res['massa']+'<br />'+
                    (res['massa'] > 0 && res['need'] > res['cur'] ? '<br /><input type="text" value="0" name="res_'+res['rid']+'" id="res_'+res['rid']+'" size=3 class=weaponch />&nbsp;<input type="button" name="add_res" value="Внести" class=invbut onclick="buildingAddRes('+res['rid']+',document.getElementById(\'res_'+res['rid']+'\').value,\''+res['v']+'\');" />' : '')+
                    '</font><br>';

                t += '<br></td>';
                if (i % 8 == 0)
                    t += '</tr><tr>';
            }
        }
        t += '</table>';
        if (response.canStart)
            t += '<div style="margin: 10px auto; width: 200px;"><input type="button" class="invbut" value="Начать строительство" onclick="startBuildingTimer(\''+response.vcode+'\')" style="width: 200px;"></div>';

        t += '</table></div><br /></div></fieldset>';
    }
    else
    {
        t += '<fieldset><legend align="center"><b>Доступные постройки</b></legend><div style="margin: 0 auto; width: 1000px;"><br /><table cellpadding=0 cellspacing=0 border=0 style="border-left: 1px solid #e0e0e0; border-top: 1px solid #e0e0e0;"><tr>';

        if (response.hasOwnProperty('buildings'))
        {
            for(r in response.buildings)
            {
                if (response.buildings.hasOwnProperty(r))
                {
                    building = response.buildings[r];
                    i++;
                    if (building.status == 'ok')
                    {
                        t += '<td width="250" align="center" style="border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0;" bgcolor=#FCFAF3 valign="top"><font class=weaponch><br><b>'+building['name']+' ('+building['nextLevel']+' ур)</b><br /><img src="http://image.neverlands.ru/gameplay/castle/shop/'+building['build']+'_'+building['nextLevel']+'.jpg" width=180 height=91 onmouseover="tooltip(this,\''+building['name']+'\')" onmouseout="hide_info(this)"><br /><br />'+
                            '<br>Время строительства: '+building['buildTime']+' ч'+
                            '<br>Стоимость: '+building['startPrice']+' NV<br /><br />'+
                            '<input type="button" name="startBuilding" value="Начать строительство" class=invbut onclick="startBuilding(\''+building['build']+'\',\''+building['vcode']+'\');" />'+
                            '</font><br>';
                    }
                    else if (building.status == 'finished')
                    {
                        t += '<td width="250" align="center" style="border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0;" bgcolor=#FCFAF3 valign="top"><font class=weaponch><br><b>'+building['name']+'</b><br /><img src="http://image.neverlands.ru/gameplay/castle/shop/'+building['build']+'_'+building['level']+'.jpg" width=180 height=91 onmouseover="tooltip(this,\''+building['name']+'\')" onmouseout="hide_info(this)"><br /><br />'+
                            '<br><br />'+
                            '<input type="button" name="startBuilding" value="Здание достроено" class=lbutdis disabled />'+
                            '</font><br>';
                    }
                    else if (building.status == 'money')
                    {
                        t += '<td width="250" align="center" style="border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0;" bgcolor=#FCFAF3 valign="top"><font class=weaponch><br><b>'+building['name']+' ('+building['nextLevel']+' ур)</b><br /><img src="http://image.neverlands.ru/gameplay/castle/shop/'+building['build']+'_'+building['nextLevel']+'.jpg" width=180 height=91 onmouseover="tooltip(this,\''+building['name']+'\')" onmouseout="hide_info(this)"><br /><br />'+
                            '<br>Время строительства: '+building['buildTime']+' ч'+
                            '<br>Стоимость: '+building['startPrice']+' NV<br /><br />'+
                            '<input type="button" name="startBuilding" value="Недостаточно средств" class=lbutdis disabled />'+
                            '</font><br>';
                    }
                    else
                    {
                        t += '<td width="250" align="center" style="border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0;" bgcolor=#FCFAF3 valign="top"><font class=weaponch><br><b>'+building['name']+'</b><br /><img src="http://image.neverlands.ru/gameplay/castle/shop/'+building['build']+'_'+building['nextLevel']+'.jpg" width=180 height=91 onmouseover="tooltip(this,\''+building['name']+'\')" onmouseout="hide_info(this)"><br /><br />'+
                            '<br><br />'+
                            '<input type="button" name="startBuilding" value="Здание недоступно" class=lbutdis disabled />'+
                            '</font><br>';
                    }

                    t += '<br></td>';
                    if (i % 4 == 0)
                        t += '</tr><tr>';
                }
            }
        }
        t += '</table><br /></div></fieldset>';
    }
    document.getElementById('building').innerHTML = t;
}

function startBuilding(building, vcode)
{
    var data = {
        action: 'startBuilding',
        building: building,
        vcode: vcode
    };

    AjaxPost('castle_ajax.php?r='+Math.random()+'', data, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showMain(response['r']);
    });
}

function buildingAddRes(resId, massa, vcode)
{
    var data = {
        action: 'buildingResAdd',
        massa: parseInt(massa, 10),
        resId: resId,
        vcode: vcode
    };

    AjaxPost('castle_ajax.php?r='+Math.random()+'', data, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showMain(response['r']);
    });
}

function startBuildingTimer(vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'startBuildingTimer', vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showMain(response['r']);
    });
}

function forceEndBuilding(vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'forceEndBuilding', vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showMain(response['r']);
    });
}

function finishBuilding(vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'finishBuilding', vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showMain(response['r']);
    });
}

function accelerateBuilding(vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'accelerateBuilding', vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showMain(response['r']);
    });
}

function showPortal(response)
{
    var t = '';
    var tp;
    var i = 0;
    var price = '';

    t += '<fieldset><legend align="center"><b>Телепорт</b></legend><div style="text-align: center">';
    t += '<br />Бесплатных телепортов: <b>'+response['tp_free_left']+'</b>&nbsp;&nbsp;&nbsp;Платных телепортов: <b>'+response['tp_paid_left']+'</b>';
    t += '</div><br />';

    if (response['tp_free_left'] == 0 && response['tp_paid_left'] > 0)
        price = '<br>Стоимость: <b>' + response['tp_paid_price'] + ' NV';

    t += '<table cellpadding=0 cellspacing=0 border=0 align=center width=760><tr><td bgcolor=#CCCCCC><table cellpadding=4 cellspacing=1 border=0 width=100%><tr>';
    for(var num in response.points)
    {
        if (response.points.hasOwnProperty(num))
        {
            i++;
            tp = response.points[num];
            t += '<td bgcolor=#FFFFFF align=center class=freetxt width=25%><b>'+tp['name']+'</b><br><br>';
            t += '<img src="http://image.neverlands.ru/map/'+tp['x']+'_'+tp['y']+'.jpg" width=100 height=100 /><br>'+price+'<br><img src=http://image.neverlands.ru/1x1.gif width=1 height=5><br>';
            if (response['tp_free_left'] > 0)
                t += '<input type=button value=Телепорт class=invbut onclick="teleport('+tp['x']+','+tp['y']+',\''+tp['vcode']+'\', 1)" />';
            else if (response['tp_paid_left'] > 0)
                t += '<input type=button value=Телепорт class=invbut onclick="teleport('+tp['x']+','+tp['y']+',\''+tp['vcode']+'\', 2)" />';
            else
                t += '<input type=button value=Телепорт class=lbutdis DISABLED />';
            t += '</td>';
            if (i % 4 == 0)
                t += '</tr><tr>';
        }
    }
    if (i % 4 > 0)
    {
        for(var kk = (4 - i % 4); kk >0; kk--)
            t += '<td bgcolor=#FFFFFF align=center class=freetxt width=25%>&nbsp;</td>';
    }

    t += '</tr></table></td></tr><tr><td><img src=http://image.neverlands.ru/1x1.gif width=1 height=2></td></tr></table><br /></fieldset>';

    if (response['call_base_left'] > 0 || response['call_adv_left'] > 0)
    {
        t += '<br /><div style="text-align: center"><fieldset><legend align="center"><b>Призыв</b></legend>';
        if (response['call_base_left'] > 0)
        {

            t += '<br />Призывов: <b>'+response['call_base_left']+'</b><br /><br />';
            t += 'Ник: <input type="text" class="freetxt" name="call_nickname" id="call_nickname" value="" />&nbsp;';
            if (response['call_base_vcode'] != '')
                t += '<input type="button" class="invbut" value="Призвать за '+response['call_base_price']+' DNV" onclick="if (confirm(\'Вы уверены, что хотите призвать игрока за '+response['call_base_price']+' DNV?\')) call(document.getElementById(\'call_nickname\').value, \''+response['call_base_vcode']+'\', 1)" />';
            else
                t += '<input type="button" class="lbutdis" value="Призвать за '+response['call_base_price']+' DNV" disabled />';
        }
        else if (response['call_adv_left'] > 0)
        {
            t += '<br />Дополнительных призывов: <b>'+response['call_adv_left']+'</b><br /><br />';
            t += 'Ник: <input type="text" class="freetxt" name="call_nickname" id="call_nickname" value="" />&nbsp;';
            if (response['call_adv_vcode'] != '')
                t += '<input type="button" class="invbut" value="Призвать за '+response['call_adv_price']+' DNV" onclick="if (confirm(\'Вы уверены, что хотите призвать игрока за '+response['call_adv_price']+' DNV?\')) call(document.getElementById(\'call_nickname\').value, \''+response['call_adv_vcode']+'\', 2)" />';
            else
                t += '<input type="button" class="lbutdis" value="Призвать за '+response['call_adv_price']+' DNV" disabled />';
        }
        t += '<br /><br /></fieldset></div><br />';
    }

    document.getElementById('building').innerHTML = t;
}

function teleport(x, y, vcode, type)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'teleport', x: x, y: y, vcode: vcode, type: type}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        if (response['r']['teleported'])
            update_page();
        else
            showBuilding('portal', response['r']);
    });
}

function call(nickname, vcode, type)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'call', nickname: nickname, vcode: vcode, type: type}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('portal', response['r']);
    });
}

function showFountain(response)
{
    var t = '';
    var tp;
    var i = 0;
    var price = '';

    t += '<fieldset><legend align="center"><b>Фонтан</b></legend><div style="text-align: center">';
    t += '<br />Осталось HP: <b>'+response['hp_left']+'</b><br /><br />';
    t += 'Восстановить: <input type="text" class="freetxt" name="restore_hp" id="restore_hp" value="0" size="6" /> HP&nbsp;';
    if (response['hp_left'] > 0)
        t += '<input type="button" class="invbut" value="Восстановить" onclick="restoreHP(document.getElementById(\'restore_hp\').value, \''+response['hp_vcode']+'\')" />';
    else
        t += '<input type="button" class="lbutdis" value="Восстановить" disabled />';
    if (response['hp_effect'] > 0)
        t += '<br /><br /><i>Вода из Фонтана обладает волшебними свойствами.<br />Испив воды на 1000 HP, Вы получите эффект Источник Жизни, временно увеличивающий максимальное значение Ваших HP на '+response['hp_effect']+' единиц.</i>';
    t += '</div><br />';
    t += '<br /></fieldset>';

    document.getElementById('building').innerHTML = t;
}

function restoreHP(hp, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'restoreHP', hp: hp, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('fountain', response['r']);
    });
}

function showPond(response)
{
    var t = '';
    var tp;
    var i = 0;
    var price = '';

    t += '<fieldset><legend align="center"><b>Пруд</b></legend><div style="text-align: center">';
    t += '<br />Осталось MP: <b>'+response['mp_left']+'</b><br /><br />';
    t += 'Восстановить: <input type="text" class="freetxt" name="restore_mp" id="restore_mp" value="0" size="6" /> MP&nbsp;';
    if (response['mp_left'] > 0)
        t += '<input type="button" class="invbut" value="Восстановить" onclick="restoreMP(document.getElementById(\'restore_mp\').value, \''+response['mp_vcode']+'\')" />';
    else
        t += '<input type="button" class="lbutdis" value="Восстановить" disabled />';
    if (response['mp_effect'] > 0)
        t += '<br /><br /><i>Вода из Пруда обладает волшебними свойствами.<br />Испив воды на 1000 MP, Вы получите эффект Источник Магии, временно увеличивающий максимальное значение Ваших MP на '+response['mp_effect']+' единиц.</i>';
    t += '</div><br />';
    t += '<br /></fieldset>';

    document.getElementById('building').innerHTML = t;
}

function restoreMP(mp, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'restoreMP', mp: mp, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('pond', response['r']);
    });
}

function showStorage(response, tab, category)
{
    if (response.hasOwnProperty('tab'))
        tab = parseInt(response['tab'], 10);
    else if (typeof tab == 'undefined')
        tab = 1;

    if (response.hasOwnProperty('category'))
        category = parseInt(response['category']);
    else if (typeof category == 'undefined')
        category = 0;

    var t = '';

    t += '<fieldset><legend align="center"><b>Хранилище</b></legend><div style="margin: 0 auto; width: 1000px"><br />';
    var tabs = ['Хранилище','Инвентарь','Налоги'];
    t +=  '<table cellpadding=0 cellspacing=0 border=0 align=center width=1000><tr><td bgcolor=#CCCCCC><table cellpadding=4 cellspacing=1 border=0 width=100%><tr>';
    for(var i = 0; i < tabs.length; i++)
        t += '<td bgcolor="#'+(tab == (i+1) ? 'E0E0E0' : 'FFFFFF')+'" align=center width="'+Math.floor(100 / tabs.length)+'%" id="tab_'+i+'"><b><a href="#" onclick="view_building(\'storage\','+(i+1)+', \'\'); return false;"><font class=category>'+tabs[i]+'</font></a></b></td>';

    t += '</tr></table></td></tr><tr><td><img src=http://image.neverlands.ru/1x1.gif width=1 height=2></td></tr></table>';

    if (tab == 3)
    {
        t += showTaxes(response);
    }
    else
    {
        t += '<table cellpadding=0 cellspacing=0 border=0 align=center width=1000><tr><td bgcolor=#CCCCCC id="Dynamic" width="100%">'+response['items']+'</td></tr></table>';
    }
    t += '<br /></div></fieldset>';
    document.getElementById('building').innerHTML = t;

    if (response['taxHistoryVcode'] && response['taxHistoryVcode'] != '')
    {
        Calendar.setup({
            inputField : "date_from",
            ifFormat   : "%Y-%m-%d",
            button     : "from_d",
            align      : "Br",
            weekNumbers: false,
            showsTime   : false,
            timeFormat  : 24
        });
        Calendar.setup({
            inputField : "date_to",
            ifFormat   : "%Y-%m-%d",
            button     : "from_t",
            align      : "Br",
            weekNumbers: false,
            showsTime   : false,
            timeFormat  : 24
        });
    }
}

function showTaxes(response)
{
    var t = '';
    var i;
    t += '<div style="padding-top: 5px;">';
    t += '<table cellpadding="0" cellspacing="0" border="0" align="center" width="760"><tr><td></td></tr><tr><td><img src="http://image.neverlands.ru/1x1.gif" width="1" height="2"></td></tr></table>'+
        '<table cellpadding="0" cellspacing="0" border="0" align="center" width="740"><tr><td>';

    t += '<FIELDSET><LEGEND align=center><B>Налоги</B></LEGEND><table cellpadding=5 cellspacing=0 border=0 width=100%><tr><td bgcolor=#ffffff width=100%><div align=center><table cellpadding=2 cellspacing=0 border=0><tr><td align=center><font class=freetxt>'+(response['taxVcode'] != '' ? 'Оплачены до <font color="'+(response['taxPast'] ? '#FF0000' : '#000000')+'"><b>'+response['taxTill']+'</b></font>' : 'Оплата не требуется')+'</td></tr>';
    if (response['taxVcode'] != '')
    {
        t += '<tr><td align="center"><select class="LogintextBox2" style="width: 260px;" id="taxPeriod">';
        t += '<option value="1">1 месяц ('+(parseInt(response['taxValue'], 10))+' NV)</option>';
        t += '<option value="2">2 месяца ('+(parseInt(response['taxValue'], 10) * 2)+' NV)</option>';
        t += '<option value="3">3 месяца ('+(parseInt(response['taxValue'], 10) * 3)+' NV)</option>';
        t += '<option value="4">4 месяца ('+(parseInt(response['taxValue'], 10) * 4)+' NV)</option>';
        t += '<option value="5">5 месяцев ('+(parseInt(response['taxValue'], 10) * 5)+' NV)</option>';
        t += '<option value="6">6 месяцев ('+(parseInt(response['taxValue'], 10) * 6)+' NV)</option>';
        t += '</select> <input type=button class=lbut style="width: 150px;" onclick="if (confirm(\'Оплатить налог?\')) storageTaxPut(document.getElementById(\'taxPeriod\').options[document.getElementById(\'taxPeriod\').selectedIndex].value,  \''+response['taxVcode']+'\');" value="Оплатить налог"></td></tr>';
    }

    if (response['donationVcode'] != '')
    {
        t += '<tr><td>&nbsp;</td></tr><tr><td align=center class="nickname">Пожертвование в казну клана</font></td></tr>';
        t += '<tr><td align="center" class="nickname"><input type="text" style="width: 150px;" id="donationAmount" value="'+response['taxValue']+'" /> ';
        t += '<input type=button class=lbut style="width: 150px;" onclick="if (confirm(\'Внести пожертвование?\')) storageDonation(document.getElementById(\'donationAmount\').value,  \''+response['donationVcode']+'\');" value="Внести пожертвование"></td></tr>';
    }
    else if (response['donationTime'] != '')
    {
        t += '<tr><td>&nbsp;</td></tr><tr><td align=center><font class=freetxt>Пожертвование в казну клана будет доступно <b>'+response['donationTime']+'</b></font></td></tr>';
    }

    if (response['taxGetVcode'] != '')
    {
        t += '<tr><td>&nbsp;</td></tr><tr><td align="center" class="nickname">Собрано налогов: <b>'+response['taxesSum']+'</b> NV</font></td></tr>';
        t += '<tr><td align="center" class="nickname"><input type="text" style="width: 150px;" id="getTaxSum" value="'+response['taxesSum']+'" /> ';
        t += '<input type=button class=lbut style="width: 150px;" onclick="if (confirm(\'Снять сумму?\')) storageTaxGet(parseInt(document.getElementById(\'getTaxSum\').value, 10),  \''+response['taxGetVcode']+'\');" value="Снять деньги"></td></tr>';
    }

    if (response['taxHistoryVcode'] != '')
    {
        t += '<tr><td>&nbsp;</td></tr><tr><td align="center" class=freetxt>История платежей</td></tr>';
        t += '<tr><td align="center"> <input type=text id="date_from" name="date_from" class="calendat_input"> <img src="http://image.neverlands.ru/pinfo/cms_calendar.gif" align="absmiddle" id="from_d" title="" alt="" style="cursor: pointer;" border="0" width="18" height="18"> <input type=text id="date_to" name="date_to" class="calendat_input"> <img src="http://image.neverlands.ru/pinfo/cms_calendar.gif" align="absmiddle" id="from_t" title="" alt="" style="cursor: pointer;" border="0" width="18" height="18"> ';
        t += '<input type=button class=lbut style="width: 150px;" onclick="storageTaxHistory(\''+response['taxHistoryVcode']+'\');" value="Посмотреть"></td></tr>';

        if (response['history'])
        {
            t += '<tr><td>';
            t += '<table width=100% border=0 align=center cellpadding=0 cellspacing=0><tr><td bgcolor=#C96C21><table width=100% border=0 cellspacing=1 cellpadding=3 class="freetxt">';
            t += '<tr><td bgcolor="#FFFFFF"><b>Время</b></td><td bgcolor="#FFFFFF"><b>Действие</b></td><td bgcolor="#FFFFFF"><b>Пользователь</b></td><td bgcolor="#FFFFFF"><b>Сумма</b></td><td bgcolor="#FFFFFF"><b>Комментарии</b></td></tr>';

            var row, act, action, comment;
            var actions = ["", "Оплата", "Снятие", "Пожертвование","Перевод"];
            for (i in response['history'])
            {
                if (response['history'].hasOwnProperty(i))
                {
                    row = response['history'][i];
                    act = row['action'];

                    if (act == 1) {
                        comment = row['period'] + ' (мес)';
                    } else if (act == 4) {
                        comment = row['nickname_to'] + ' (' + row['comment'] + ')';
                    } else {
                        comment = '';
                    }

                    action = actions[act];
                    t += '<tr><td bgcolor="#FFFFFF">'+row['time']+'</td><td bgcolor="#FFFFFF">'+actions[act]+'</td><td bgcolor="#FFFFFF">'+row['nickname']+'</td><td bgcolor="#FFFFFF">'+row['sum']+'</td><td bgcolor="#FFFFFF" align="center">'+comment+'</td></tr>';
                }
            }
            t += '</table>';

            t += '</td></tr>';
        }
    }

    if (response['taxSendVcode'] != '')
    {
        t += '<tr><td>&nbsp;</td></tr><tr><td align="center" class=freetxt>Денежный перевод</td></tr>';
        t += '<tr><td align="center" class="nickname"> Кому: <select name="target" id="sendMoneyTarget"><option value=""> - Кому - </option>';
        for (i in response['clanMembers']) {
            if (response['clanMembers'].hasOwnProperty(i)) {
                t += '<option value="' + i + '">' + response['clanMembers'][i] + '</option>';
            }
        }
        t += '</select> Сколько: <input type=text id="sendMoneyCount" name="count"> NV </td></tr><tr><td align="center" class="nickname"> ' +
            'Причина перевода: <input type=text id="sendMoneyReason" name="reason" size="30"> ' +
            '<input type=button class=lbut style="width: 150px;" onclick="storageSendMoney(\'' + response['taxSendVcode'] + '\');" value="Отправить"></td></tr>';
    }

    t += '</table></div></td></tr></table></FIELDSET><br>';

    t += '<br></td></tr></table>';
    t += '</div>';

    return t;
}

function storageItemPut(uid, category, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'storageItemPut', uid: uid, category: category, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('storage', response['r']);
    });
}

function storageItemGet(uid, category, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'storageItemGet', uid: uid, category: category, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('storage', response['r']);
    });
}

function storageTaxPut(period, vcode)
{
    var data = {'action': 'storageTaxPut', 'period': period, 'vcode': vcode};

    AjaxPost('castle_ajax.php?action=storageTaxPut&vcode='+vcode+'&r='+Math.random()+'', data, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('storage', response['r']);
    });
}

function storageDonation(amount, vcode)
{
    var data = {'action': 'storageDonation', 'sum': amount, 'vcode': vcode};

    AjaxPost('castle_ajax.php?action=storageDonation&vcode='+vcode+'&r='+Math.random()+'', data, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('storage', response['r']);
    });
}

function storageTaxGet(sum, vcode)
{
    var data = {'action': 'storageTaxGet', 'sum': sum, 'vcode': vcode};

    AjaxPost('castle_ajax.php?action=storageTaxGet&vcode='+vcode+'&r='+Math.random()+'', data, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('storage', response['r']);
    });
}

function storageSendMoney(vcode)
{
    var target = document.getElementById('sendMoneyTarget').options[ document.getElementById('sendMoneyTarget').selectedIndex].value;
    var sum = document.getElementById('sendMoneyCount').value;
    var reason = document.getElementById('sendMoneyReason').value;
    var data = {action: 'storageSendMoney', target: target, sum: sum, reason: reason, vcode: vcode};

    AjaxPost('castle_ajax.php?action=storageSendMoney&vcode='+vcode+'&r='+Math.random()+'', data, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('storage', response['r']);
    });
}

function storageTaxHistory(vcode)
{
    var date_from = document.getElementById('date_from').value;
    var date_to = document.getElementById('date_to').value;
    if (date_from == '') { alert('Введите начальную дату.'); return false; }
    if (date_to == '') { alert('Введите конечную дату.'); return false; }
    var data = {
        action: 'storageTaxHistory',
        dateFrom: date_from,
        dateTo: date_to,
        vcode: vcode
    };

    AjaxPost('castle_ajax.php?action=storageTaxHistory&vcode='+vcode+'&r='+Math.random()+'', data, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('storage', response['r']);
    });
    return true;
}

function getMtowerChangeButtons(price, vcodes)
{
    var t = '';
    t += '<table cellpadding=0 cellspacing=0 border=0 style="border-left: 1px solid #e0e0e0; border-top: 1px solid #e0e0e0;"><tr>';
    for(var i in mtowerTypes)
    {
        if (mtowerTypes.hasOwnProperty(i))
        {
            t += '<td width="150" align="center" style="border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0;" bgcolor=#FCFAF3 valign="top">';
            t += '<font class=weaponch><br><img src="http://image.neverlands.ru/gameplay/castle/mtower-type-'+i+'.png" width=110 height=110 onmouseover="tooltip(this,\''+mtowerTypes[i]+'\')" onmouseout="hide_info(this)"><br />'+mtowerTypes[i]+'<br /><br />';
            if (price > 0)
                t += '<input type="button" name="add_res" value="Сменить за '+price+' DNV" class=invbut onclick="if (confirm(\'Вы уверены, что хотите сменить тип башни за '+price+' DNV?\')) mtowerChangeType('+i+', '+price+', \''+vcodes[i]+'\');" />';
            else
                t += '<input type="button" name="add_res" value="Сменить" class=invbut onclick="mtowerChangeType('+i+', '+price+', \''+vcodes[i]+'\');" />';
            t += '</font><br><br></td>';
        }
    }
    t += '</table>';

    return t;
}

function showMTower(response)
{
    var t = '';
    var tp;
    var i = 0;
    var price = '';

    if (response['type'] == 0)
    {
        t += '<fieldset><legend align="center"><b>Выберите тип магической башни</b></legend><div style="text-align: center; margin: 0 auto; width: 600px;"><br /><br />';
        t += getMtowerChangeButtons(0, response['type_vcodes']);
        t += '</div><br /></div></fieldset>';
    }
    else
    {
        t += '<fieldset><legend align="center"><b>Башня магов "'+mtowerTypes[response['type']]+'"</b></legend><div style="margin-left: 20px;" id="mtower-effects"><br />';
        if (response.hasOwnProperty('effects'))
        {
            var eff;
            for(var effId in response['effects'])
            {
                if (response['effects'].hasOwnProperty(effId))
                {
                    eff = response['effects'][effId];
                    t += '<input type="checkbox" name="eff_'+effId+'" id="eff_'+effId+'" value="'+effId+'-'+eff['price']+'" onclick="mtowerRecalculate();" ><label for="eff_'+effId+'">Тип: <b>'+eff['name']+'</b>, Эффект: <b>'+eff['count']+'</b>, Цена: <b>'+eff['price']+' NV</b></label><br />'
                }
            }
        }
        t += '<br /><input type="button" name="mtower-apply" id="mtower-apply" value="Применить за 0 NV" class=invbut onclick="mtowerApply(\''+response['vcode']+'\');" /><br />';
        t += '<center><a href="#" onclick="document.getElementById(\'mtower-change-block\').style.display = \'block\'; return false;">Сменить тип башни</a></center><br />';
        t += '<div style="text-align: center; margin: 0 auto; width: 600px; display: none" id="mtower-change-block"><br /><br />';
        t += getMtowerChangeButtons(response['change_price'], response['type_vcodes']);
        t += '</div><br /></div>';
        t += '<br /></fieldset>';
    }

    document.getElementById('building').innerHTML = t;
}

function mtowerChangeType(type, price, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'mtowerTypeChange', type: type, price: price, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('mtower', response['r']);
    });
}

function mtowerRecalculate()
{
    var items = document.getElementById('mtower-effects').getElementsByTagName('INPUT');
    var elm;
    var total = 0;
    var tmp;
    for(var i in items)
    {
        if (items.hasOwnProperty(i))
        {
            elm = items[i];
            if (elm.checked)
            {
                tmp = elm.value.split('-');
                total += parseInt(tmp[1]);
            }
        }
    }
    document.getElementById('mtower-apply').value = 'Применить за '+total+' NV';
}

function mtowerApply(vcode)
{
    var items = document.getElementById('mtower-effects').getElementsByTagName('INPUT');
    var effects = [];
    var elm;
    var tmp;

    for(var i in items)
    {
        if (items.hasOwnProperty(i))
        {
            elm = items[i];
            if (elm.checked)
            {
                tmp = elm.value.split('-');
                effects.push(parseInt(tmp[0]));
            }
        }
    }

    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'mtowerApply', effects: effects.join(','), vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('mtower', response['r']);
    });
}

function showHall(response, tab, category)
{
    if (response.hasOwnProperty('tab'))
        tab = parseInt(response['tab'], 10);
    else if (typeof tab == 'undefined')
        tab = 1;

    if (response.hasOwnProperty('category'))
        category = parseInt(response['category']);
    else if (typeof category == 'undefined')
        category = 0;

    var t = '';

    t += '<fieldset><legend align="center"><b>Дипломатический холл</b></legend><div style="margin: 0 auto; width: 1000px"><br />';
    var tabs = ['Войны','Союзы'];
    t +=  '<table cellpadding=0 cellspacing=0 border=0 align=center width=1000><tr><td bgcolor=#CCCCCC><table cellpadding=4 cellspacing=1 border=0 width=100%><tr>';
    for(var i = 0; i < tabs.length; i++)
        t += '<td bgcolor="#'+(tab == (i+1) ? 'E0E0E0' : 'FFFFFF')+'" align=center width="'+Math.floor(100 / tabs.length)+'%" id="tab_'+i+'"><b><a href="#" onclick="view_building(\'hall\','+(i+1)+', \'\'); return false;"><font class=category>'+tabs[i]+'</font></a></b></td>';

    t += '</tr></table></td></tr><tr><td><img src=http://image.neverlands.ru/1x1.gif width=1 height=2></td></tr></table><br />';

    if (tab == 1)
    {
        t += showWars(response);
    }
    else
    {
        t += showAlliances(response);
    }

    document.getElementById('building').innerHTML = t;
}

function showWars(response)
{
    var i, j, k, clan, aclan;
    var t = '';
    t += 'Уровень: <b>'+response['clanLevel']+'</b><br />';
    t += 'Опыт: <b>'+response['clanExp']+' / '+response['clanExpLevel']+'</b> <a href="http://wiki.neverlands.ru/wiki/%D0%A2%D0%B0%D0%B1%D0%BB%D0%B8%D1%86%D0%B0_%D0%BA%D0%BB%D0%B0%D0%BD%D0%BE%D0%B2%D0%BE%D0%B3%D0%BE_%D0%BE%D0%BF%D1%8B%D1%82%D0%B0" target="_blank"><img src=http://image.neverlands.ru/help/6.gif width=15 height=15 border=0 alt="Помощь" align=absmiddle></a><br />';
    if (response['status'] == 0) {
        t += 'Статус: <b>МИР</b><br />';

        if (response['allianceMaster']) {
            t += 'Глава альянса: <img src="http://image.neverlands.ru/signs/' + response['allianceMaster']['sign'] + '" /> <b>' + response['allianceMaster']['name'] + '</b> ';
            if (response['allianceMaster']['exitVcode']) {
                t += '<input type="button" value="Выйти из альянса" onclick="if (confirm(\'Вы уверены, что хотите выйти из альянса?\')) allianceExit(\''+response['allianceMaster']['exitVcode']+'\');" />';
            }
            t += '<br />';
        }

        if (response['allianceTimeout']) {
            t += 'Запрет на вступление в альянс до: <b>' + response['allianceTimeout'] + '</b><br />';
        }

        if (response['clearWarDeclare']) {
            t += 'Сбросить таймер запрета на нападение <input type="button" class="invbut" value="Сбросить таймер за 100 DNV" onclick="if (confirm(\'Сбросить таймер за 100 DNV?\')) hallClearWarDeclare(\''+response['clearWarDeclare']+'\');" /><br />'
        }

        if (response['clanExpPayout']) {
            var payoutPrice = response['clanExpPayoutPrice'];
            t += 'Сбросить опыт клана <input type="button" class="invbut" value="Сбросить опыт за ' + payoutPrice + ' DNV" onclick="if (confirm(\'Сбросить опыт клана за ' + payoutPrice + ' DNV?\')) hallExpPauout(\''+response['clanExpPayout']+'\', ' + payoutPrice + ');" /><br />'
        }

        if (response['invites'].length > 0) {
            t += '<br /><b>Приглашения:</b><br />';
            for(i in response['invites']) {
                if (response['invites'].hasOwnProperty(i)) {
                    clan = response['invites'][i];
                    t += '<img src="http://image.neverlands.ru/signs/' + clan['sign'] + '" /> '+clan['name']+'&nbsp;<input type="button" name="allianceConfirm" onclick="allianceConfirm('+clan['id']+',\''+clan['confirmVcode']+'\');" value="Принять приглашение" />&nbsp;<input type="button" name="allianceDecline" onclick="allianceDecline('+clan['id']+',\''+clan['declineVcode']+'\');" value="Отклонить приглашение" /><br />';
                }
            }
        }

        if (response['clans'].length > 0) {
            t += '<br /><table cellpadding=0 cellspacing=0 border=0 style="border-left: 1px solid #e0e0e0; border-top: 1px solid #e0e0e0;">';
            var clanMem = {};
            var clanWar;
            for(i in response['clans']) {
                if (response['clans'].hasOwnProperty(i)) {
                    clanMem[response['clans'][i]['signid']] = response['clans'][i];
                }
            }
            for(i in response['clans']) {
                if (response['clans'].hasOwnProperty(i)) {
                    clan = response['clans'][i];
                    t += '<tr>';
                    t += '<td width="250" align="left" style="border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0; padding: 5px;" bgcolor=#FCFAF3 valign="top">';
                    t += '<font class=weaponch><img src="http://image.neverlands.ru/signs/'+clan['sign']+'" /> <b style="color: '+(clan['exp'] < 0 ? '#cd0000' : '#000000')+';">' + clan['name']+' ['+clan['level']+']</b>';
                    if (clan['alliance']) {
                        for(j in clan['alliance']) {
                            if (clan['alliance'].hasOwnProperty(j)) {
                                aclan = clan['alliance'][j];
                                t += '<br />&nbsp-&nbsp;<img src="http://image.neverlands.ru/signs/'+aclan['sign']+'" /> <b>' + aclan['name']+' ['+aclan['level']+']</b>';
                            }
                        }
                    }
                    t += '</td><td width="750" align="center" style="border-right: 1px solid #e0e0e0; border-bottom: 1px solid #e0e0e0; padding: 5px;" bgcolor=#FCFAF3 valign="top">';
                    if (clan['inWar']) {
                        clanWar = clanMem[clan['inWar']];
                        t += '<font color="#FF0000">В состоянии войны с </font><font class="weaponch"><img src="http://image.neverlands.ru/signs/'+clanWar['sign']+'" /> <b>' + clanWar['name']+' ['+clanWar['level']+']</b>';
                        if (clanWar['alliance']) {
                            for(j in clanWar['alliance']) {
                                if (clanWar['alliance'].hasOwnProperty(j)) {
                                    aclan = clanWar['alliance'][j];
                                    t += ', <img src="http://image.neverlands.ru/signs/'+aclan['sign']+'" /> <b>' + aclan['name']+' ['+aclan['level']+']</b>';
                                }
                            }
                        }
                        t += '</font>';
                    } else if (clan['vcode']) {
                        t += '<input type="button" name="showWar" id="bwar_' + clan['signid'] + '" onclick="document.getElementById(\'war_' + clan['signid'] + '\').style.display = \'block\'; document.getElementById(\'bwar_' + clan['signid'] + '\').style.display = \'none\';" value="Объявить войну" class="invbut" />';
                        t += '<div id="war_' + clan['signid'] + '" style="display: none">Размер контрибуции: <input type="text" name="price_' + clan['signid'] + '" id="price_' + clan['signid'] + '" value="" placeholder="'+clan['minContribution']+' - '+clan['maxContribution']+'" class="searchInput" /> NV&nbsp;&nbsp;<input id="premium_' + clan['signid'] + '" type="checkbox" value="1" /><label for="premium_' + clan['signid'] + '">Премиум война</label>&nbsp;&nbsp;<input type="button" name="add_res" value="Объявить войну" class=invbut onclick="if (confirm(\'Вы уверены, что хотите объявить войну?\')) hallDeclareWar(\'' + clan['signid'] + '\', \'' + clan['vcode'] + '\');" /></div>';
                    } else if (clan['timeout'] && clan['timeout'] != '') {
                        t += '<font class="weaponch">Таймаут до <b>' + clan['timeout'] + '</b></font>';
                    }
                    t += '</td>';
                    t += '</tr>';
                }
            }
            t += '</table>';
        }
    } else if (response['status'] == 1) {
        if (response['self'] == 1) {
            if (response['warClanAlliance'].length > 0) {
                t += 'Статус: <b>Объявлена война кланам <img src="http://image.neverlands.ru/signs/' + response['clanSign'] + '" /> ' + response['clanName'] + '';
                for(k in response['warClanAlliance']) {
                    if (response['warClanAlliance'].hasOwnProperty(k)) {
                        t += ', <img src="http://image.neverlands.ru/signs/' + response['warClanAlliance'][k]['clanSign'] + '" /> ' + response['warClanAlliance'][k]['clanName'];
                    }
                }
                t += '</b><br />';
            } else {
                t += 'Статус: <b>Объявлена война клану <img src="http://image.neverlands.ru/signs/' + response['clanSign'] + '" /> ' + response['clanName'] + '</b><br />';
            }
            t += 'Размер контрибуции: <b>'+response['price']+' NV</b><br />';
        } else {
            if (response['warClanAlliance'].length > 0) {
                t += 'Статус: <b>Объявлена война кланом <img src="http://image.neverlands.ru/signs/' + response['clanSign'] + '" /> ' + response['clanName'] + '';
                for (k in response['warClanAlliance']) {
                    if (response['warClanAlliance'].hasOwnProperty(k)) {
                        t += ', <img src="http://image.neverlands.ru/signs/' + response['warClanAlliance'][k]['clanSign'] + '" /> ' + response['warClanAlliance'][k]['clanName'];
                    }
                }
                t += '</b><br />';
            } else {
                t += 'Статус: <b>Объявлена война кланом <img src="http://image.neverlands.ru/signs/' + response['clanSign'] + '" /> ' + response['clanName'] + '</b><br />';
            }
            if (response['acceptWarVcode']) {
                t += '<input type="checkbox" id="acceptPremiumWar"><label for="acceptPremiumWar">Премиум война</label>&nbsp;&nbsp;<input type="button" name="acceptWar" value="Объявить войну" class=invbut onclick="if (confirm(\'Вы уверены, что хотите начать войну?\')) hallAcceptWar(\'' + response['acceptWarVcode'] + '\');" />&nbsp;';
            }
            if (response['contrubutionWarVcode']) {
                t += '<input type="button" name="contrubutionWar" value="Заплатить контрибуцию ' + response['price'] + ' NV + налог '+response['priceTax']+' NV" class=invbut onclick="if (confirm(\'Вы уверены, что хотите заплатить контрибуцию ' + response['price'] + ' NV + налог '+response['priceTax']+' NV?\')) hallPayContribution(\'' + response['contrubutionWarVcode'] + '\');" /><br />';
            }
        }
        t += 'Осталось времени: <b id="timer_war">'+response['timeLeft']+'</b>';
        if (response['allianceMaster']) {
            t += '<br />Глава альянса: <img src="http://image.neverlands.ru/signs/' + response['allianceMaster']['sign'] + '" /> <b>' + response['allianceMaster']['name'] + '</b> ';
            if (response['allianceMaster']['exitWarVcode']) {
                t += '<input type="button" value="Выйти из альянса" onclick="if (confirm(\'Вы уверены, что хотите выйти из альянса во время войны? Вам будет засчитано поражение.\')) allianceExit(\''+response['allianceMaster']['exitWarVcode']+'\');" />';
            }
            t += '<br />';
        }
        timer_add('war', response['timeLeft']+1, update_page, true);
    } else if (response['status'] == 2) {
        var color = response['enemyPoints'] > response['ownPoints'] ? '#FF0000' : '#196f3d';
        if (response['warClanAlliance'].length > 0) {
            t += 'Статус: <b>Война с кланами <img src="http://image.neverlands.ru/signs/' + response['clanSign'] + '" /> ' + response['clanName'] + '';
            for(k in response['warClanAlliance']) {
                if (response['warClanAlliance'].hasOwnProperty(k)) {
                    t += ', <img src="http://image.neverlands.ru/signs/' + response['warClanAlliance'][k]['clanSign'] + '" /> ' + response['warClanAlliance'][k]['clanName'];
                }
            }
            t += '</b><br />';
            t += 'Счёт войны: <font color='+color+'><b>'+response['ownPoints']+' - '+response['enemyPoints']+'</b></font><br />';
        } else {
            t += 'Статус: <b>Война с кланом <img src="http://image.neverlands.ru/signs/' + response['clanSign'] + '" /> ' + response['clanName'] + '"</b><br />';
            t += 'Счёт войны: <font color='+color+'><b>'+response['ownPoints']+' - '+response['enemyPoints']+'</b></font><br />';
        }
        if (response['self'] == 0 && response['contrubutionWarVcode']) {
            t += '<input type="button" name="contrubutionWar" class="invbut" value="Заплатить контрибуцию '+response['price']+' NV + налог '+response['priceTax']+' NV" class=invbut onclick="if (confirm(\'Вы уверены, что хотите заплатить контрибуцию '+response['price']+' NV + налог '+response['priceTax']+' NV?\')) hallPayContribution(\''+response['contrubutionWarVcode']+'\');" /><br />';
        }
        t += 'Осталось времени: <b id="timer_war">'+response['timeLeft']+'</b><br />';
        if (response['allianceMaster']) {
            t += 'Глава альянса: <img src="http://image.neverlands.ru/signs/' + response['allianceMaster']['sign'] + '" /> <b>' + response['allianceMaster']['name'] + '</b> ';
            if (response['allianceMaster']['exitWarVcode']) {
                t += '<input type="button" value="Выйти из альянса" onclick="if (confirm(\'Вы уверены, что хотите выйти из альянса во время войны? Вам будет засчитано поражение.\')) allianceExit(\''+response['allianceMaster']['exitWarVcode']+'\');" />';
            }
            t += '<br />';
        }
        timer_add('war', response['timeLeft']+1, update_page, true);
    }
    return t;
}

function showAlliances(response)
{
    var i, clan;
    var t = '';
    t += 'Максимальный размер альянса: <b>'+response['allianceSize']+'</b><br />';
    if (response['allianceMaster']) {
        t += '<b>Глава альянса</b>: <img src="http://image.neverlands.ru/signs/' + response['allianceMaster']['sign'] + '" /> '+response['allianceMaster']['name'];
        if (response['allianceMaster']['exitVcode']) {
            t += '<input type="button" class="invbut" value="Выйти из альянса" onclick="if confirm(\'Вы уверены, что хотите выйти из альянса?\') allianceExit(\''+response['allianceMaster']['exitVcode']+'\');" />';
        }
        t += '<br />';
    } else {
        if (response['alliance'].length > 0) {
            t += '<br /><b>Ваши вассалы:</b><br />';
            for (i in response['alliance']) {
                if (response['alliance'].hasOwnProperty(i)) {
                    clan = response['alliance'][i];
                    t += '<img src="http://image.neverlands.ru/signs/' + clan['sign'] + '" /> ' + clan['name'] + (clan['kickVcode'] ? '&nbsp;<input type="button" class="invbut" name="allianceKick" onclick="if (confirm(\'Вы уверены, что хотите выгнать этот клан?\')) allianceKick(\'' + clan['signid'] + '\',\'' + clan['kickVcode'] + '\');" value="Выгнать" />' : '' ) + '<br />';
                }
            }
        }

        if (response['allianceInvite']) {
            t += '<br />Пригласить в союз: <select name="allianceClanSelect" id="allianceClanSelect"><option value=""> - Выберите клан - </option>';
            for(i in response['freeClans']) {
                if (response['freeClans'].hasOwnProperty(i)) {
                    clan = response['freeClans'][i];
                    t += '<option value="'+clan['signid']+'">'+clan['name']+'</option>';
                }
            }
                t += '</select>&nbsp;<input type="button" class="invbut" name="allianceInvite" onclick="if (confirm(\'Вы уверены, что хотите отправить приглашение этому клану?\')) allianceInvite(document.getElementById(\'allianceClanSelect\').options[document.getElementById(\'allianceClanSelect\').selectedIndex].value, \''+response['allianceInvite']+'\');" value="Пригласить (100000 NV)" /><br />';
        }

        if (response['invites'] && response['invites'].length > 0) {
            t += '<br /><b>Высланные приглашения:</b><br />';
            for (i in response['invites']) {
                if (response['invites'].hasOwnProperty(i)) {
                    clan = response['invites'][i];
                    t += '<img src="http://image.neverlands.ru/signs/' + clan['sign'] + '" /> ' + clan['name'] + '&nbsp;<input type="button" class="invbut" name="allianceCancelInvite" onclick="allianceCancelInvite(' + clan['id'] + ',\'' + clan['vcode'] + '\');" value="Отменить приглашение" /><br />';
                }
            }
        }

    }
    return t;
}

function hallDeclareWar(signid, vcode)
{
    var price = parseInt(document.getElementById('price_'+signid).value, 10);
    if (price > 0 && price < 10000000) {
        var premium = document.getElementById('premium_' + signid).checked ? 1 : 0;
        AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'hallDeclareWar', signid: signid, price: price, premium: premium, vcode: vcode}, function(xdata) {
            var response = JSON.parse(xdata);
            updateData(response);
            showBuilding('hall', response['r']);
        });
    } else {
        alert('Неверная цена');
    }
}

function hallAcceptWar(vcode)
{
    var premium = document.getElementById('acceptPremiumWar').checked ? 1 : 0;
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'hallAcceptWar', premium: premium, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function hallPayContribution(vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'hallPayContribution', vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function hallClearWarDeclare(vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'hallClearWarDeclare', vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function hallExpPauout(vcode, price)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'hallClanExpPayout', price: price, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function allianceInvite(clan, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'allianceInvite', clan: clan, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function allianceCancelInvite(id, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'allianceCancelInvite', id: id, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function allianceConfirm(id, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'allianceConfirm', id: id, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function allianceDecline(id, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'allianceDecline', id: id, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function allianceExit(vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'allianceExit', vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}

function allianceKick(signid, vcode)
{
    AjaxPost('castle_ajax.php?r='+Math.random()+'', {action: 'allianceKick', signid: signid, vcode: vcode}, function(xdata) {
        var response = JSON.parse(xdata);
        updateData(response);
        showBuilding('hall', response['r']);
    });
}
