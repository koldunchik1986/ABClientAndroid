namespace ANClient.Kazna
{
    internal sealed class KaznaCategory
    {
        internal string Wca;
        internal string Title;
        internal string IconUrl;
        internal string Href;

        internal bool IsSame(KaznaCategory other)
        {
            return other != null && string.Equals(Wca, other.Wca, System.StringComparison.OrdinalIgnoreCase);
        }
    }
}
