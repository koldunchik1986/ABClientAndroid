namespace ANClient.Info
{
    using System;
    using System.Collections.Generic;
    using System.IO;
    using System.Text;
    using System.Windows.Forms;

    internal sealed class RecipeDatabase
    {
        private const string NormalizedFormatHeader = "#ABCLIENT_RECIPE_TABLES_V1";
        private const string NormalizedFileName = "recipe_tables.txt";
        private const string DbFileName = "recipe_tables_db.tsv";

        private static RecipeDatabase _cachedDatabase;

        private readonly List<RecipeSection> _sections = new List<RecipeSection>();
        private readonly List<RecipeItem> _allItems = new List<RecipeItem>();
        private readonly Dictionary<string, List<RecipeItem>> _itemsByImageFile = new Dictionary<string, List<RecipeItem>>(StringComparer.OrdinalIgnoreCase);

        private RecipeDatabase()
        {
        }

        internal string SourceFilePath { get; private set; }

        internal string ConsolidatedFilePath { get; private set; }

        internal static RecipeDatabase Load()
        {
            if (_cachedDatabase != null)
            {
                return _cachedDatabase;
            }

            var database = new RecipeDatabase();
            database.ParseNormalizedSource(database.ResolveSourcePath());
            database.RebuildIndex();
            database.WriteConsolidatedFile();
            _cachedDatabase = database;
            return _cachedDatabase;
        }

        internal IList<RecipeSection> GetSections()
        {
            return _sections.AsReadOnly();
        }

        internal IList<RecipeItem> GetAllItems()
        {
            return _allItems.AsReadOnly();
        }

        internal RecipeSection FindSection(string sectionKey)
        {
            if (string.IsNullOrEmpty(sectionKey))
            {
                return null;
            }

            foreach (RecipeSection section in _sections)
            {
                if (section.Key.Equals(sectionKey, StringComparison.OrdinalIgnoreCase))
                {
                    return section;
                }
            }

            return null;
        }

        internal string GetFirstSectionKey()
        {
            return _sections.Count == 0 ? string.Empty : _sections[0].Key;
        }

        internal bool HasImageFile(string imageFile)
        {
            return FindByImageFile(imageFile).Count > 0;
        }

        internal List<RecipeItem> FindByImageFile(string imageFile)
        {
            string normalized = NormalizeImageFile(imageFile);
            if (normalized.Length == 0)
            {
                return new List<RecipeItem>();
            }

            List<RecipeItem> items;
            if (!_itemsByImageFile.TryGetValue(normalized, out items))
            {
                return new List<RecipeItem>();
            }

            return new List<RecipeItem>(items);
        }

        internal static string NormalizeImageFile(string imageUrlOrFile)
        {
            if (string.IsNullOrEmpty(imageUrlOrFile))
            {
                return string.Empty;
            }

            string value = imageUrlOrFile.Trim();
            int queryIndex = value.IndexOf('?');
            if (queryIndex >= 0)
            {
                value = value.Substring(0, queryIndex);
            }

            int hashIndex = value.IndexOf('#');
            if (hashIndex >= 0)
            {
                value = value.Substring(0, hashIndex);
            }

            string lower = value.ToLowerInvariant();
            int gifIndex = lower.IndexOf(".gif", StringComparison.Ordinal);
            if (gifIndex < 0)
            {
                return string.Empty;
            }

            if (gifIndex + 4 < value.Length)
            {
                value = value.Substring(0, gifIndex + 4);
            }

            int slashIndex = Math.Max(value.LastIndexOf('/'), value.LastIndexOf('\\'));
            if (slashIndex >= 0 && slashIndex + 1 < value.Length)
            {
                value = value.Substring(slashIndex + 1);
            }

            return value.ToLowerInvariant();
        }

        private string ResolveSourcePath()
        {
            string startupPath = Application.StartupPath;
            string currentPath = Directory.GetCurrentDirectory();
            string[] candidates =
                {
                    Path.Combine(startupPath, @"Info\" + NormalizedFileName),
                    Path.Combine(startupPath, @"info\" + NormalizedFileName),
                    Path.Combine(startupPath, @"..\..\Info\" + NormalizedFileName),
                    Path.Combine(startupPath, @"..\..\..\app2\src\main\assets\info\" + NormalizedFileName),
                    Path.Combine(currentPath, @"ANClient\Info\" + NormalizedFileName),
                    Path.Combine(currentPath, @"app2\src\main\assets\info\" + NormalizedFileName)
                };

            foreach (string candidate in candidates)
            {
                string fullPath = GetFullPathSafe(candidate);
                if (!string.IsNullOrEmpty(fullPath) && File.Exists(fullPath))
                {
                    return fullPath;
                }
            }

            throw new FileNotFoundException("Не найдена база рецептов " + NormalizedFileName);
        }

        private static string GetFullPathSafe(string path)
        {
            try
            {
                return Path.GetFullPath(path);
            }
            catch (Exception)
            {
                return string.Empty;
            }
        }

        private void ParseNormalizedSource(string path)
        {
            SourceFilePath = path;
            var sectionsByKey = new Dictionary<string, RecipeSection>(StringComparer.OrdinalIgnoreCase);
            var itemsById = new Dictionary<string, RecipeItem>(StringComparer.OrdinalIgnoreCase);

            using (var reader = new StreamReader(path, Encoding.UTF8, true))
            {
                string firstLine = reader.ReadLine();
                if (!NormalizedFormatHeader.Equals(firstLine, StringComparison.Ordinal))
                {
                    throw new InvalidDataException("Неверный заголовок базы рецептов: " + firstLine);
                }

                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    if (line.Trim().Length == 0 || line.StartsWith("#", StringComparison.Ordinal))
                    {
                        continue;
                    }

                    string[] parts = line.Split(new[] {'\t'}, StringSplitOptions.None);
                    string recordType = parts.Length == 0 ? string.Empty : parts[0];
                    if ("SECTION".Equals(recordType, StringComparison.Ordinal))
                    {
                        if (parts.Length < 4)
                        {
                            continue;
                        }

                        RecipeSection section = GetOrCreateSection(sectionsByKey, parts[1], parts[2], parts[3]);
                        if (!_sections.Contains(section))
                        {
                            _sections.Add(section);
                        }
                    }
                    else if ("ITEM".Equals(recordType, StringComparison.Ordinal))
                    {
                        if (parts.Length < 9)
                        {
                            continue;
                        }

                        RecipeSection section = GetOrCreateSection(sectionsByKey, parts[2], parts[3], parts[4]);
                        if (!_sections.Contains(section))
                        {
                            _sections.Add(section);
                        }

                        string imageUrl = parts[7];
                        string imageFile = parts[8].Length == 0 ? NormalizeImageFile(imageUrl) : NormalizeImageFile(parts[8]);
                        if (imageFile.Length == 0)
                        {
                            continue;
                        }

                        var item = new RecipeItem(section.Key, section.Title, section.AssetPath, ParseIntSafe(parts[5]), parts[6], imageUrl, imageFile);
                        section.Items.Add(item);
                        _allItems.Add(item);
                        itemsById[parts[1]] = item;
                    }
                    else if ("FIELD".Equals(recordType, StringComparison.Ordinal))
                    {
                        if (parts.Length < 4)
                        {
                            continue;
                        }

                        RecipeItem item;
                        if (itemsById.TryGetValue(parts[1], out item) && parts[2].Length > 0 && parts[3].Length > 0)
                        {
                            item.Fields.Add(new RecipeField(parts[2], parts[3]));
                        }
                    }
                    else if ("RESOURCE".Equals(recordType, StringComparison.Ordinal))
                    {
                        if (parts.Length < 5)
                        {
                            continue;
                        }

                        RecipeItem item;
                        if (itemsById.TryGetValue(parts[1], out item))
                        {
                            string imageUrl = parts[2];
                            string imageFile = parts[3].Length == 0 ? NormalizeImageFile(imageUrl) : NormalizeImageFile(parts[3]);
                            if (imageFile.Length > 0)
                            {
                                item.Resources.Add(new RecipeResource(imageUrl, imageFile, parts[4]));
                            }
                        }
                    }
                }
            }
        }

        private RecipeSection GetOrCreateSection(Dictionary<string, RecipeSection> sectionsByKey, string key, string title, string assetPath)
        {
            RecipeSection section;
            if (!sectionsByKey.TryGetValue(key, out section))
            {
                section = new RecipeSection(key, title, assetPath);
                sectionsByKey.Add(key, section);
            }

            return section;
        }

        private static int ParseIntSafe(string value)
        {
            int result;
            return int.TryParse(value == null ? "0" : value.Trim(), out result) ? result : 0;
        }

        private void RebuildIndex()
        {
            _itemsByImageFile.Clear();
            foreach (RecipeItem item in _allItems)
            {
                List<RecipeItem> items;
                if (!_itemsByImageFile.TryGetValue(item.ItemImageFile, out items))
                {
                    items = new List<RecipeItem>();
                    _itemsByImageFile.Add(item.ItemImageFile, items);
                }

                items.Add(item);
            }
        }

        private void WriteConsolidatedFile()
        {
            try
            {
                string dir = Path.Combine(Application.StartupPath, "Info");
                if (!Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }

                ConsolidatedFilePath = Path.Combine(dir, DbFileName);
                using (var writer = new StreamWriter(ConsolidatedFilePath, false, new UTF8Encoding(false)))
                {
                    writer.WriteLine("section_key\tsection_title\timage_file\tname\timage_url\tfields\tresources");
                    foreach (RecipeItem item in _allItems)
                    {
                        writer.Write(Tsv(item.SectionKey));
                        writer.Write('\t');
                        writer.Write(Tsv(item.SectionTitle));
                        writer.Write('\t');
                        writer.Write(Tsv(item.ItemImageFile));
                        writer.Write('\t');
                        writer.Write(Tsv(item.Name));
                        writer.Write('\t');
                        writer.Write(Tsv(item.ItemImageUrl));
                        writer.Write('\t');
                        writer.Write(Tsv(FieldsToText(item.Fields)));
                        writer.Write('\t');
                        writer.Write(Tsv(ResourcesToText(item.Resources)));
                        writer.WriteLine();
                    }
                }
            }
            catch (Exception error)
            {
                AppLog.w("RecipeDatabase", "RECIPE_DB_WRITE_FAILED", error);
            }
        }

        private static string FieldsToText(List<RecipeField> fields)
        {
            var builder = new StringBuilder();
            foreach (RecipeField field in fields)
            {
                if (builder.Length > 0)
                {
                    builder.Append(" | ");
                }

                builder.Append(field.Name).Append('=').Append(field.Value);
            }

            return builder.ToString();
        }

        private static string ResourcesToText(List<RecipeResource> resources)
        {
            var builder = new StringBuilder();
            foreach (RecipeResource resource in resources)
            {
                if (builder.Length > 0)
                {
                    builder.Append(" | ");
                }

                builder.Append(resource.ImageFile).Append('=').Append(resource.Label);
            }

            return builder.ToString();
        }

        private static string Tsv(string value)
        {
            return value == null
                       ? string.Empty
                       : value.Replace('\t', ' ').Replace('\r', ' ').Replace('\n', ' ').Trim();
        }

        internal sealed class RecipeSection
        {
            internal RecipeSection(string key, string title, string assetPath)
            {
                Key = key;
                Title = title;
                AssetPath = assetPath;
                Items = new List<RecipeItem>();
            }

            internal string Key { get; private set; }

            internal string Title { get; private set; }

            internal string AssetPath { get; private set; }

            internal List<RecipeItem> Items { get; private set; }
        }

        internal sealed class RecipeItem
        {
            internal RecipeItem(string sectionKey, string sectionTitle, string sourceAsset, int tableIndex, string name, string itemImageUrl, string itemImageFile)
            {
                SectionKey = sectionKey;
                SectionTitle = sectionTitle;
                SourceAsset = sourceAsset;
                TableIndex = tableIndex;
                Name = name;
                ItemImageUrl = itemImageUrl;
                ItemImageFile = itemImageFile;
                Fields = new List<RecipeField>();
                Resources = new List<RecipeResource>();
            }

            internal string SectionKey { get; private set; }

            internal string SectionTitle { get; private set; }

            internal string SourceAsset { get; private set; }

            internal int TableIndex { get; private set; }

            internal string Name { get; private set; }

            internal string ItemImageUrl { get; private set; }

            internal string ItemImageFile { get; private set; }

            internal List<RecipeField> Fields { get; private set; }

            internal List<RecipeResource> Resources { get; private set; }
        }

        internal sealed class RecipeField
        {
            internal RecipeField(string name, string value)
            {
                Name = name;
                Value = value;
            }

            internal string Name { get; private set; }

            internal string Value { get; private set; }
        }

        internal sealed class RecipeResource
        {
            internal RecipeResource(string imageUrl, string imageFile, string label)
            {
                ImageUrl = imageUrl;
                ImageFile = imageFile;
                Label = label;
            }

            internal string ImageUrl { get; private set; }

            internal string ImageFile { get; private set; }

            internal string Label { get; private set; }
        }
    }
}
