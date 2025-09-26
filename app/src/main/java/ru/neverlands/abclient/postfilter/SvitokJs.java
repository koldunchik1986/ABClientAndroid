package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class SvitokJs {
    public static byte[] process(byte[] array) {
        // TODO: Исправить и раскомментировать проблемную замену
        /*
        String js = new String(array, Russian.CODEPAGE);

        String oldString = "document.all(\"transfer\").innerHTML = '<form action=main.php method=POST><input type=hidden name=magicrestart value=\"1\"><input type=hidden name=magicreuid value=\'"+wuid+"'><input type=hidden name=vcode value=\'"+wmcode+"'><input type=hidden name=post_id value=46><table cellpadding=0 cellspacing=0 border=0 width=100%><tr><td bgcolor=#B9A05C><table cellpadding=3 cellspacing=1 border=0 width=100%><tr><td width=100% bgcolor=#FCFAF3><font class=nickname><b>Использовать \""+wnametxt+'\" сейчас?</b></div></td></tr><tr><td bgcolor=#FCFAF3><font class=nickname><b>Кому:</b> <INPUT TYPE=\"text\" name=fornickname class=LogintextBox value=\""+wnickname+'\" maxlength=25> <input type=submit value=\"выполнить\" class=lbut> <input type=button class=lbut onclick=\"closeform()\" value=\" x \"></td></tr></table></td></tr></table></FORM>";

        String newString = "document.all(\"transfer\").innerHTML = '<form action=main.php method=POST><input type=hidden name=magicrestart value=\"1\"><input type=hidden name=magicreuid value=\'"+wuid+"'><input type=hidden name=vcode value=\'"+wmcode+"'><input type=hidden name=post_id value=46><table cellpadding=0 cellspacing=0 border=0 width=100%><tr><td bgcolor=#B9A05C><table cellpadding=3 cellspacing=1 border=0 width=100%><tr><td width=100% bgcolor=#FCFAF3><font class=nickname><b>Использовать \""+wnametxt+'\" сейчас?</b></div></td></tr><tr><td bgcolor=#FCFAF3><font class=nickname><b>Кому:</b> <INPUT TYPE=\"text\" name=fornickname class=LogintextBox value=\""+wnickname+'\" maxlength=25> <input type=submit value=\"выполнить\" class=lbut onclick=\"AndroidBridge.TraceDrinkPotion(fornickname.value, '\'"+wnametxt+'\'')"> <input type=button class=lbut onclick=\"closeform()\" value=\" x \"></td></tr></table></td></tr></table></FORM>";

        js = js.replace(oldString, newString);

        return js.getBytes(Russian.CODEPAGE);
        */
        return array;
    }
}