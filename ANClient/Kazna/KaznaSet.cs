using System.Collections.Generic;

namespace ANClient.Kazna
{
    internal sealed class KaznaSet
    {
        internal readonly string Name;
        internal readonly List<string> ItemUids;

        internal KaznaSet(string name, IEnumerable<string> itemUids)
        {
            Name = name == null ? string.Empty : name.Trim();
            ItemUids = new List<string>();
            if (itemUids == null)
                return;

            foreach (var uid in itemUids)
            {
                var safeUid = uid == null ? string.Empty : uid.Trim();
                if (!string.IsNullOrEmpty(safeUid) && !ItemUids.Contains(safeUid))
                    ItemUids.Add(safeUid);
            }
        }
    }
}
