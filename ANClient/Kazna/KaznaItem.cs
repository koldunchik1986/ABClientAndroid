using System;

namespace ANClient.Kazna
{
    internal sealed class KaznaItem
    {
        internal string Uid;
        internal int RowIndex;
        internal string DisplayName;
        internal string BaseName;
        internal string Owner;
        internal string DurabilityText;
        internal int CurrentDurability;
        internal int MaxDurability;
        internal string Status;
        internal bool Free;
        internal string ArtifactCoefficient;
        internal string TakeUrl;
        internal string DonateUrl;
        internal string SourceUrl;
        internal string CategoryWca;
        internal string CategoryTitle;
        internal string RowHtml;

        internal bool HasUid
        {
            get { return !string.IsNullOrEmpty(Uid); }
        }

        internal bool HasTakeAction
        {
            get { return !string.IsNullOrEmpty(TakeUrl); }
        }

        internal bool HasDonateAction
        {
            get { return !string.IsNullOrEmpty(DonateUrl); }
        }

        internal bool HasArtifactCoefficient
        {
            get { return !string.IsNullOrEmpty(ArtifactCoefficient); }
        }

        internal bool IsRare
        {
            get { return !HasArtifactCoefficient && MaxDurability >= 300; }
        }

        internal bool IsOrdinary
        {
            get { return !HasArtifactCoefficient && MaxDurability >= 0 && MaxDurability < 300; }
        }

        internal string StableKey
        {
            get
            {
                if (HasUid)
                    return Uid;

                return (RowIndex + "|" + Safe(DisplayName) + "|" + Safe(Owner) + "|" + Safe(DurabilityText)).ToLowerInvariant();
            }
        }

        internal static string Safe(string value)
        {
            return value ?? string.Empty;
        }
    }
}
