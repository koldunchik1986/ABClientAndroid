using System;

namespace ANClient.ExtMap
{
    public class AncCell
    {
        public string RegNum { set; get; }
        public string Label { set; get; }
        public int Cost { set; get; }
        public DateTime Visited { set; get; }
        public DateTime Verified { set; get; }
    }
}
