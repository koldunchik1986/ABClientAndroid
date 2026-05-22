namespace ANClient
{
    internal sealed class MineCell
    {
        internal string MineId;
        internal string Level;
        internal int X;
        internal int Y;
        internal string Img;
        internal int Usefull;

        internal bool CanMoveRight
        {
            get { return HasDirection('r'); }
        }

        internal bool CanMoveUp
        {
            get { return HasDirection('t'); }
        }

        internal bool CanMoveDown
        {
            get { return HasDirection('b'); }
        }

        internal bool CanMoveLeft
        {
            get { return HasDirection('l'); }
        }

        private bool HasDirection(char direction)
        {
            return !string.IsNullOrEmpty(Img) && Img.IndexOf(direction) >= 0;
        }
    }
}
