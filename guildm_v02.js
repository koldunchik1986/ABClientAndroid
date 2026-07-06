var d = document;

var ratings = [
    {id: 1, name: 'Каллиграфия'},
    {id: 2, name: 'Ювелирное дело'},
    {id: 3, name: 'Ремесленник'},
    {id: 4, name: 'Доктор'},
    {id: 5, name: 'Алхимия'},
    {id: 6, name: 'Развитие горного дела'},
    {id: 7, name: 'Рыбалка'},
    {id: 8, name: 'Охота'},
    {id: 9, name: 'Кулинария'},
    {id: 10, name: 'Лесозаготовка'},
    {id: 11, name: 'Плотник'},
    {id: 12, name: 'Сталевар'},
    {id: 13, name: 'Травник'},
    {id: 14, name: 'Торговец'}
];

function updateData(response)
{
    bavail['inf'][0] = (response['b'][0] ? response['b'][0] : '');
    bavail['inv'][0] = (response['b'][1] ? response['b'][1] : '');
    bavail['up'][0] = (response['b'][2] ? response['b'][2] : '');

    document.getElementById('main_buttons').innerHTML = ButtonDraw();

    for(var i=0; i<response['ba'].length; i++)
        basic_act[i] = response['ba'][i];

    if (response['ar'] && response['ar'][0] && response['ar'][1])
    {
        if (response['ar'][0] == 'ERROR' || response['ar'][0] == 'SUCCESS')
            MessBoxDiv(response['ar'][1]);
    }

    build[12] = response['q'];

    return true;
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
        str += '<input type=button class=fr_but value="Квесты" onclick=\'ButClick("quest");\'>';
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
        case 'quest': QActive(build[12]); break;
        case 'inf': goloc = 'main.php?get_id=56&act=10&go=inf&vcode='+bavail[id][0]; break;
        case 'inv': goloc = 'main.php?get_id=56&act=10&go=inv&vcode='+bavail[id][0]; break;
        case 'up': goloc = 'main.php?get_id=56&act=10&go=up&vcode='+bavail[id][0]; break;
    }
    if(goloc)
    {
        for(var j=0; j<bavail[id][1].length; j++) goloc += '&'+bavail[id][1][j][0]+'='+bavail[id][1][j][1];
        window.location = goloc;
    }
}

function view_guildm()
{
	view_build_top();
    d.write('<table cellpadding=0 cellspacing=0 border=0 align=center width=760>');
    d.write('<tr><td><img src=http://image.neverlands.ru/1x1.gif width=1 height=10></td></tr>');
    d.write('<tr><td>');
    d.write('<table width=100% cellpadding=0 cellspacing=2>');
    d.write('<tr><td>');

    d.write('<table cellpadding=0 cellspacing=1 width=100% border=0>');
	d.write('<tr> <td bgcolor=#f0f0f0 width=33%><div align=center><font class=nickname><b><img src=http://image.neverlands.ru/1x1.gif width=1 height=18 align=absmiddle border=0>Рейтинг игроков</div></td>');
    d.write('<td bgcolor=#f0f0f0 width=33%><div align=center><select name="ratingId" id="ratingId" onchange="changeRatingPeriod(this.options[this.selectedIndex].value, 0)"><option value=0>Текущая неделя</option><option value=1>Прошлая неделя</option></div></td>');
    d.write('<td bgcolor=#f0f0f0 width=33%><div align=center><select name="ratingId" id="ratingId" onchange="changeRatingType(this.options[this.selectedIndex].value, 0)"><option value=0> - Выберите рейтинг - </option>');
    var r;
    for(var i in ratings) {
        if (ratings.hasOwnProperty(i)) {
            r = ratings[i];
            d.write('<option value="' + r['id'] + '">' + r['name'] + '</option>');
        }
    }
    d.write('</select></div></td> </tr>');
    d.write('</table>');

    d.write('<table cellpadding=0 cellspacing=0 border=0 align=center width=760>');
    d.write('<tr><td><img src=http://image.neverlands.ru/gameplay/hdi/corner0.gif width=69 height=66></td><td background=http://image.neverlands.ru/gameplay/hdi/fill0.gif><img src=http://image.neverlands.ru/1x1.gif width=622 height=1></td><td><img src=http://image.neverlands.ru/gameplay/hdi/corner1.gif width=69 height=66></td></tr>');
    d.write('<tr><td background=http://image.neverlands.ru/gameplay/hdi/fill1.gif><img src=http://image.neverlands.ru/1x1.gif width=1 height=54></td>');
    d.write('<td>');
    d.write('<table cellpadding=5 cellspacing=0 border=0 align=center width=100%>');
    d.write('<tr><td><div align=justify class=nickname id="ratingContent">');

    d.write('В рейтингах учитывается колличество успешных подходов к работе по соответствующим профессиям (для производственных профессий), либо колличество добытых ресурсов (для добывающих профессий). <br>');
    d.write('Рейтинг для каждой профессии считается отдельно и никак не связан с другими рейтингами. <br>');
    d.write('Подсчет очков осуществляется в течение недели, начинается в 00:00 понедельника и заканчивается в 23:59 воскресенья. <br>');
    d.write('Получить награды за попадание в топ рейтинга по итогам прошлой недели можно, выбрав вкладку "прошлая неделя" и соответствующий рейтинг.');
    // write ratings here

    d.write('</div></td></tr> </table>');
    d.write('</td><td background=http://image.neverlands.ru/gameplay/hdi/fill2.gif></td></tr>');
    d.write('<tr><td><img src=http://image.neverlands.ru/gameplay/hdi/corner2.gif width=69 height=66></td><td background=http://image.neverlands.ru/gameplay/hdi/fill3.gif></td><td><img src=http://image.neverlands.ru/gameplay/hdi/corner3.gif width=69 height=66></td></tr>');
    d.write('</table>');

    d.write('</td></tr> </table>');
    d.write('</td></tr> </table>');


	view_build_bottom();
}

var currentType = 0;
var currentPeriod = 0;

function changeRatingPeriod(period)
{
    currentPeriod = period;
    loadRating();
}

function changeRatingType(type)
{
    currentType = type;
    loadRating();
}

function loadRating()
{
    if (currentType > 0) {
        AjaxGet('rating_ajax.php?action=getRating&type='+currentType+'&prev='+currentPeriod+'&vcode='+basic_act[0]+'&r='+Math.random()+'', function(xdata) {
            var response = JSON.parse(xdata);
            updateData(response);
            viewRating(response['r']);
        });
    }
}

function viewRating(response)
{
    var t, r, n;
    n = 0;
    t = '<table cellpadding=2 cellspacing=0 border=0 align=center><tr><td colspan=3><div align=center><font class=freetxt>' + (currentPeriod == 1 ? 'Прошлая неделя' : 'Текущая неделя') + '<br><br></div></td></tr><tr><td><div align=center><font class=freetxt><b>место</div></td><td><div align=center><font class=freetxt><b>игрок</div></td><td><div align=center><font class=freetxt><b>очки</div></td></tr>';
    if (response['rating']) {
        for(var i in response['rating']) {
            if (response['rating'].hasOwnProperty(i)) {
                n++;
                r = response['rating'][i];
                t += '<tr> <td><div align=center><font class=nickname><b>' + n + '</div></td>';
                t += '<td class=nickname>' + (r['signid'] != 'n' ? '<img src=http://image.neverlands.ru/signs/' + r['sign'] + ' width=15 height=12 border=0 align=absmiddle title="' + r['signname'] + '">' : '') + '&nbsp;' + r['nickname'] + '[' + r['level'] + ']<a href="pinfo.cgi?' + r['nickname'] + '" target=_blank><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a></td>';
                t += '<td><div align=center><b><font class=nickname>';
                if (r['collect']) {
                    t += ' <input type="button" class="invbut" name="collect" onclick="collect(\'' + r['collect'] + '\')" value="'+r['value']+' (Забрать награду)">';
                } else {
                    t += ' ' + r['value'];
                }
                t += '</div></td> </tr>';
            }
        }
    }
    t += '<tr><td colspan="3" align="center"><font class="freetxt"><br><br>Требуется установить рейтинг игроков на Вашем сайте? <br><b>Используйте файл с сервера проекта NeverLands</b>:<br> <a href=http://service.neverlands.ru/rate/weekly_'+currentType+'.txt target=_blank>http://service.neverlands.ru/rate/weekly_'+currentType+'.txt</a></font></td></tr>';
    t += '</table>';
    d.getElementById('ratingContent').innerHTML = t;
}

function collect(vcode)
{
    if (currentType > 0) {
        var data = {action: 'collect', vcode: vcode, type: currentType};
        AjaxGet('rating_ajax.php?action=collect&vcode='+vcode+'&type='+currentType+'&r='+Math.random()+'', function(xdata) {
            var response = JSON.parse(xdata);
            updateData(response);
            viewRating(response['r']);
        });
    }
}