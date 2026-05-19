using System;
using System.Collections.Generic;

namespace ANClient.Kazna
{
    internal sealed class KaznaSnapshot
    {
        internal DateTime GeneratedAt;
        internal string SourceUrl;
        internal string CurrentWca;
        internal string CurrentCategoryTitle;
        internal readonly List<KaznaCategory> Categories = new List<KaznaCategory>();
        internal readonly List<KaznaItem> Items = new List<KaznaItem>();

        internal KaznaItem FindItemByUid(string uid)
        {
            if (string.IsNullOrEmpty(uid))
                return null;

            for (var i = 0; i < Items.Count; i++)
            {
                if (string.Equals(Items[i].Uid, uid.Trim(), StringComparison.OrdinalIgnoreCase))
                    return Items[i];
            }

            return null;
        }
    }
}
