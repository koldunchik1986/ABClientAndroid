using System;
using System.Collections.Generic;
using System.IO;
using System.Windows.Forms;
using Newtonsoft.Json.Linq;

namespace ANClient.Kazna
{
    internal static class KaznaItemDetailsCache
    {
        private const string TraceChain = "KAZNA_TRACE";
        private static readonly object LockObject = new object();

        internal static Dictionary<string, KaznaItemDetails> LoadAll()
        {
            lock (LockObject)
            {
                return LoadAllLocked(CurrentProfileNickForStorage());
            }
        }

        internal static void MergeFromInventoryDetails(List<KaznaItemDetails> details)
        {
            if (details == null || details.Count == 0)
                return;

            lock (LockObject)
            {
                var profileNick = CurrentProfileNickForStorage();
                var current = LoadAllLocked(profileNick);
                var parsed = 0;
                var changed = 0;
                for (var i = 0; i < details.Count; i++)
                {
                    var detail = details[i];
                    if (detail == null || string.IsNullOrEmpty(detail.Uid))
                        continue;

                    parsed++;
                    KaznaItemDetails oldValue;
                    current.TryGetValue(detail.Uid, out oldValue);
                    var merged = Merge(oldValue, detail);
                    if (!Same(oldValue, merged))
                    {
                        current[merged.Uid] = merged;
                        changed++;
                    }
                }

                if (changed > 0)
                {
                    SaveAllLocked(current, profileNick);
                    AppLog.i(TraceChain, "KaznaItemDetailsCache", "uid details updated: parsed=" + parsed + ", changed=" + changed + ", file=" + GetCacheFile(profileNick));
                }
            }
        }

        private static KaznaItemDetails Merge(KaznaItemDetails oldValue, KaznaItemDetails newValue)
        {
            if (oldValue == null)
                return new KaznaItemDetails(newValue.Uid, newValue.Name, newValue.ImageUrl, newValue.PropertiesText, DateTime.Now.Ticks);

            return new KaznaItemDetails(
                FirstNonEmpty(newValue.Uid, oldValue.Uid),
                FirstNonEmpty(newValue.Name, oldValue.Name),
                FirstNonEmpty(newValue.ImageUrl, oldValue.ImageUrl),
                FirstNonEmpty(newValue.PropertiesText, oldValue.PropertiesText),
                DateTime.Now.Ticks);
        }

        private static Dictionary<string, KaznaItemDetails> LoadAllLocked(string profileNick)
        {
            var result = new Dictionary<string, KaznaItemDetails>(StringComparer.OrdinalIgnoreCase);
            var file = GetCacheFile(profileNick);
            if (!File.Exists(file))
                return result;

            try
            {
                var root = JObject.Parse(File.ReadAllText(file, System.Text.Encoding.UTF8));
                var items = root["items"] as JArray;
                if (items == null)
                    return result;

                for (var i = 0; i < items.Count; i++)
                {
                    var item = items[i] as JObject;
                    if (item == null)
                        continue;

                    var detail = new KaznaItemDetails(
                        (string)item["uid"],
                        (string)item["name"],
                        (string)item["imageUrl"],
                        (string)item["propertiesText"],
                        (long?)item["updatedAtTicks"] ?? 0L);
                    if (!string.IsNullOrEmpty(detail.Uid))
                        result[detail.Uid] = detail;
                }
            }
            catch (Exception ex)
            {
                AppLog.w(TraceChain, "KaznaItemDetailsCache", "uid details read failed", ex);
            }

            return result;
        }

        private static void SaveAllLocked(Dictionary<string, KaznaItemDetails> detailsByUid, string profileNick)
        {
            try
            {
                var file = GetCacheFile(profileNick);
                var dir = Path.GetDirectoryName(file);
                if (!Directory.Exists(dir))
                    Directory.CreateDirectory(dir);

                var root = new JObject();
                root["generatedAtTicks"] = DateTime.Now.Ticks;
                var items = new JArray();
                foreach (var detail in detailsByUid.Values)
                {
                    if (detail == null || string.IsNullOrEmpty(detail.Uid))
                        continue;

                    var item = new JObject();
                    item["uid"] = detail.Uid;
                    item["name"] = detail.Name;
                    item["imageUrl"] = detail.ImageUrl;
                    item["propertiesText"] = detail.PropertiesText;
                    item["updatedAtTicks"] = detail.UpdatedAtTicks;
                    items.Add(item);
                }

                root["items"] = items;
                File.WriteAllText(file, root.ToString(), System.Text.Encoding.UTF8);
            }
            catch (Exception ex)
            {
                AppLog.e(TraceChain, "KaznaItemDetailsCache", "uid details save failed", ex);
            }
        }

        private static string GetCacheFile(string profileNick)
        {
            return Path.Combine(Path.Combine(Path.Combine(Application.StartupPath, "info"), SanitizeProfileName(profileNick)), "kazna" + Path.DirectorySeparatorChar + "uids.txt");
        }

        private static string CurrentProfileNickForStorage()
        {
            if (AppVars.Profile != null && !string.IsNullOrEmpty(AppVars.Profile.UserNick))
                return AppVars.Profile.UserNick.Trim();

            return "profile";
        }

        private static string SanitizeProfileName(string value)
        {
            var safe = string.IsNullOrEmpty(value) ? "profile" : value.Trim();
            var invalid = Path.GetInvalidFileNameChars();
            for (var i = 0; i < invalid.Length; i++)
                safe = safe.Replace(invalid[i], '_');

            return string.IsNullOrEmpty(safe) ? "profile" : safe;
        }

        private static string FirstNonEmpty(string first, string second)
        {
            return string.IsNullOrEmpty(first) ? (second ?? string.Empty) : first;
        }

        private static bool Same(KaznaItemDetails first, KaznaItemDetails second)
        {
            if (first == null || second == null)
                return first == second;

            return string.Equals(first.Uid, second.Uid, StringComparison.Ordinal) &&
                   string.Equals(first.Name, second.Name, StringComparison.Ordinal) &&
                   string.Equals(first.ImageUrl, second.ImageUrl, StringComparison.Ordinal) &&
                   string.Equals(first.PropertiesText, second.PropertiesText, StringComparison.Ordinal);
        }
    }
}
