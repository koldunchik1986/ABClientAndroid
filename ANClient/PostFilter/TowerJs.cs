using ANClient.Properties;
using ANClient.Helpers;

namespace ANClient.PostFilter
{
    internal static partial class Filter
    {
        private static byte[] TowerJs(byte[] array)
        {
            var html = Russian.Codepage.GetString(array);
            html = Resources.json2 + " " + html;
            return Russian.Codepage.GetBytes(html);
        }
    }
}