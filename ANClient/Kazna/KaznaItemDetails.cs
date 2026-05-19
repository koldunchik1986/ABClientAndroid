using System;

namespace ANClient.Kazna
{
    internal sealed class KaznaItemDetails
    {
        internal string Uid;
        internal string Name;
        internal string ImageUrl;
        internal string PropertiesText;
        internal long UpdatedAtTicks;

        internal KaznaItemDetails(string uid, string name, string imageUrl, string propertiesText, long updatedAtTicks)
        {
            Uid = Safe(uid);
            Name = Safe(name);
            ImageUrl = Safe(imageUrl);
            PropertiesText = Safe(propertiesText);
            UpdatedAtTicks = updatedAtTicks;
        }

        internal bool HasImage
        {
            get { return !string.IsNullOrEmpty(ImageUrl); }
        }

        internal bool HasProperties
        {
            get { return !string.IsNullOrEmpty(PropertiesText); }
        }

        internal bool HasKnownDetails
        {
            get { return HasImage || HasProperties || !string.IsNullOrEmpty(Name); }
        }

        private static string Safe(string value)
        {
            return value == null ? string.Empty : value.Trim();
        }
    }
}
