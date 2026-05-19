using System;
using System.Collections.Generic;
using System.Text;

namespace ANClient.Kazna
{
    internal static class KaznaSetStore
    {
        internal static List<KaznaSet> LoadSets()
        {
            return Parse(AppVars.Profile == null ? string.Empty : AppVars.Profile.ClanKaznaComplects);
        }

        internal static bool AddSet(string setName)
        {
            var safeName = SafeName(setName);
            if (string.IsNullOrEmpty(safeName) || AppVars.Profile == null)
                return false;

            var sets = LoadSets();
            for (var i = 0; i < sets.Count; i++)
            {
                if (sets[i].Name.Equals(safeName, StringComparison.OrdinalIgnoreCase))
                    return true;
            }

            sets.Add(new KaznaSet(safeName, new string[0]));
            Save(sets);
            return true;
        }

        internal static bool AddItemToSet(string setName, string uid)
        {
            var safeName = SafeName(setName);
            var safeUid = uid == null ? string.Empty : uid.Trim();
            if (string.IsNullOrEmpty(safeName) || string.IsNullOrEmpty(safeUid) || AppVars.Profile == null)
                return false;

            var sets = LoadSets();
            var found = false;
            for (var i = 0; i < sets.Count; i++)
            {
                if (!sets[i].Name.Equals(safeName, StringComparison.OrdinalIgnoreCase))
                    continue;

                found = true;
                if (!sets[i].ItemUids.Contains(safeUid))
                    sets[i].ItemUids.Add(safeUid);
            }

            if (!found)
                sets.Add(new KaznaSet(safeName, new[] { safeUid }));

            Save(sets);
            return true;
        }

        internal static bool RemoveItemFromSet(string setName, string uid)
        {
            var safeName = SafeName(setName);
            var safeUid = uid == null ? string.Empty : uid.Trim();
            if (string.IsNullOrEmpty(safeName) || string.IsNullOrEmpty(safeUid) || AppVars.Profile == null)
                return false;

            var sets = LoadSets();
            var changed = false;
            for (var i = 0; i < sets.Count; i++)
            {
                if (!sets[i].Name.Equals(safeName, StringComparison.OrdinalIgnoreCase))
                    continue;

                changed = sets[i].ItemUids.Remove(safeUid) || changed;
            }

            if (changed)
                Save(sets);

            return changed;
        }

        internal static bool DeleteSet(string setName)
        {
            var safeName = SafeName(setName);
            if (string.IsNullOrEmpty(safeName) || AppVars.Profile == null)
                return false;

            var sets = LoadSets();
            var changed = false;
            for (var i = sets.Count - 1; i >= 0; i--)
            {
                if (!sets[i].Name.Equals(safeName, StringComparison.OrdinalIgnoreCase))
                    continue;

                sets.RemoveAt(i);
                changed = true;
            }

            if (changed)
                Save(sets);

            return changed;
        }

        internal static KaznaSet FindSet(string setName)
        {
            var safeName = SafeName(setName);
            if (string.IsNullOrEmpty(safeName))
                return null;

            var sets = LoadSets();
            for (var i = 0; i < sets.Count; i++)
            {
                if (sets[i].Name.Equals(safeName, StringComparison.OrdinalIgnoreCase))
                    return sets[i];
            }

            return null;
        }

        private static List<KaznaSet> Parse(string source)
        {
            var result = new List<KaznaSet>();
            if (string.IsNullOrEmpty(source))
                return result;

            var entries = source.Split(new[] { ';' }, StringSplitOptions.RemoveEmptyEntries);
            for (var i = 0; i < entries.Length; i++)
            {
                var entry = entries[i];
                var index = entry.IndexOf('=');
                if (index <= 0 || index >= entry.Length - 1)
                    continue;

                var name = SafeName(entry.Substring(0, index));
                if (string.IsNullOrEmpty(name))
                    continue;

                var uids = entry.Substring(index + 1).Split(new[] { ':' }, StringSplitOptions.RemoveEmptyEntries);
                result.Add(new KaznaSet(name, uids));
            }

            return result;
        }

        private static void Save(List<KaznaSet> sets)
        {
            if (AppVars.Profile == null)
                return;

            var sb = new StringBuilder();
            for (var i = 0; i < sets.Count; i++)
            {
                var set = sets[i];
                if (set == null || string.IsNullOrEmpty(set.Name))
                    continue;

                if (sb.Length > 0)
                    sb.Append(';');
                sb.Append(SafeName(set.Name));
                sb.Append('=');
                sb.Append(string.Join(":", set.ItemUids.ToArray()));
            }

            AppVars.Profile.ClanKaznaComplects = sb.ToString();
            AppVars.Profile.Save();
        }

        private static string SafeName(string value)
        {
            if (string.IsNullOrEmpty(value))
                return string.Empty;

            return value.Trim().Replace("=", string.Empty).Replace(";", string.Empty).Replace("|", string.Empty);
        }
    }
}
