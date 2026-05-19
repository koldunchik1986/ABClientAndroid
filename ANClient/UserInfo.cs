namespace ANClient
{
    public class UserInfo
    {
        public UserInfo()
        {
            SlotsCodes = new string[0];
            SlotsNames = new string[0];
            EffectsCodes = new string[0];
            EffectsNames = new string[0];
            EffectsSizes = new string[0];
            EffectsLefts = new string[0];
            PlayerId = string.Empty;
            Nick = string.Empty;
            Level = string.Empty;
            Align = string.Empty;
            InclinationName = string.Empty;
            ClanCode = string.Empty;
            ClanNumber = string.Empty;
            ClanSign = string.Empty;
            ClanIco = string.Empty;
            ClanName = string.Empty;
            ClanStatus = string.Empty;
            Sex = string.Empty;
            ChatMuted = string.Empty;
            ForumMuted = string.Empty;
            Location = string.Empty;
            GeoLocation = string.Empty;
            FightLog = string.Empty;
            WarLogNumber = string.Empty;
            EffectIds = string.Empty;
            EffectStates = string.Empty;
        }

        public string[] SlotsCodes;
        public string[] SlotsNames;
        public string[] EffectsCodes;
        public string[] EffectsNames;
        public string[] EffectsSizes;
        public string[] EffectsLefts;
        public string PlayerId;
        public string Nick;
        public string Level;
        public int PlayerLevel;
        public string Align;
        public int Inclination;
        public string InclinationName;
        public string ClanCode;
        public string ClanNumber;
        public string ClanSign;
        public string ClanIco;
        public string ClanName;
        public string ClanStatus;
        public string Sex;
        public int Gender;
        public int BlockStatus;
        public int JailStatus;
        public bool Disabled;
        public bool Jailed;
        public string ChatMuted;
        public string ForumMuted;
        public int MuteSeconds;
        public int MuteForumSeconds;
        public int OnlineStatus;
        public bool Online;
        public string Location;
        public string GeoLocation;
        public string FightLog;
        public string WarLogNumber;
        public string EffectIds;
        public string EffectStates;
        public int HpCur;
        public int HpMax;
        public int MaCur;
        public int MaMax;
        public int Tied;
    }
}
