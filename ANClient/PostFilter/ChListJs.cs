namespace ANClient.PostFilter
{
    using Helpers;
    using Properties;

    internal static partial class Filter
    {
        private static byte[] ChListJs()
        {
            return Russian.Codepage.GetBytes(InjectContactEffects(Resources.ch_list.Replace("alt=", "title=")));
        }

        private static string InjectContactEffects(string script)
        {
            if (string.IsNullOrEmpty(script))
                return script;

            const string effectsMarker = "    if (str_array[3].length>1)";
            const string effectsLoader = "    var wmlabEffects = '';\r\n    try { wmlabEffects = window.external.GetEffectHtmlOfContact(login); } catch(e) { wmlabEffects = ''; }\r\n\r\n";
            if (script.IndexOf(effectsMarker, System.StringComparison.Ordinal) >= 0)
            {
                script = script.Replace(effectsMarker, effectsLoader + effectsMarker);
            }

            const string infoSuffix = "\" target=_blank><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>\" + sleeps";
            const string infoSuffixWithEffects = "\" target=_blank><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>\" + wmlabEffects + sleeps";
            return script.Replace(infoSuffix, infoSuffixWithEffects);
        }
    }
}
