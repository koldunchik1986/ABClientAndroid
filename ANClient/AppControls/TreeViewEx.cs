namespace ANClient.AppControls
{
    using System;
    using System.Collections.Generic;
    using System.Drawing;
    using System.IO;
    using System.Net;
    using System.Threading;
    using System.Windows.Forms;
    using ANClient.ANProxy;

    public class TreeViewEx : TreeView
    {
        private const int TvFirst = 0x1100;
        private const int TvmSetbkcolor = TvFirst + 29;
        private const int TvmSetextendedstyle = TvFirst + 44;
        private const int TvsExDoublebuffer = 0x0004;
        private const int EffectIconWidth = 15;
        private const int EffectIconHeight = 15;
        private const string EffectImageUrlPrefix = "http://image.neverlands.ru/pinfo/eff_";
        private const string BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        private static readonly object EffectImageSync = new object();
        private static readonly Dictionary<int, Image> EffectImageCache = new Dictionary<int, Image>();
        private static readonly Dictionary<int, bool> EffectImageLoading = new Dictionary<int, bool>();
        private static readonly Dictionary<int, bool> EffectImageMissingLogged = new Dictionary<int, bool>();

        private sealed class EffectImageDownloadState
        {
            internal TreeViewEx Tree;
            internal int EffectId;
        }

        public TreeViewEx()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.OptimizedDoubleBuffer | ControlStyles.AllPaintingInWmPaint, true);
            DrawMode = TreeViewDrawMode.OwnerDrawText;
            ItemHeight = Math.Max(ItemHeight, EffectIconHeight + 3);
            if (!NativeMethods.IsWinVista)
            {
                SetStyle(ControlStyles.UserPaint, true);
            }
        }

        private void UpdateExtendedStyles()
        {
            int Style = 0;

            if (DoubleBuffered)
            {
                Style |= TvsExDoublebuffer;
            }

            if (Style != 0)
            {
                NativeMethods.SendMessage(Handle, TvmSetextendedstyle, (IntPtr)TvsExDoublebuffer, (IntPtr)Style);
            }
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            UpdateExtendedStyles();
            if (!NativeMethods.IsWinXP)
            {
                NativeMethods.SendMessage(Handle, TvmSetbkcolor, IntPtr.Zero, (IntPtr)ColorTranslator.ToWin32(BackColor));
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            if (GetStyle(ControlStyles.UserPaint))
            {
                var m = new Message
                            {
                                HWnd = Handle,
                                Msg = 0x0318,
                                WParam = e.Graphics.GetHdc(),
                                LParam = (IntPtr)0x00000004
                            };
                DefWndProc(ref m);
                e.Graphics.ReleaseHdc(m.WParam);
            }

            base.OnPaint(e);
        }

        protected override void OnDrawNode(DrawTreeNodeEventArgs e)
        {
            if (e == null || e.Node == null)
            {
                base.OnDrawNode(e);
                return;
            }

            var contact = e.Node.Tag as ANClient.Contact;
            if (contact == null)
            {
                e.DrawDefault = true;
                base.OnDrawNode(e);
                return;
            }

            var effects = ANClient.ContactRenderHelper.ParseEffectStatesCsv(contact.EffectStates, contact.EffectIds);
            DrawContactNodeTextAndEffects(e, effects);
        }

        internal void InvalidateNodeRow(TreeNode node)
        {
            var rowBounds = GetNodeRowBounds(node);
            if (!rowBounds.IsEmpty)
            {
                Invalidate(rowBounds);
            }
        }

        private void DrawContactNodeTextAndEffects(DrawTreeNodeEventArgs e, List<ANClient.ContactRenderHelper.EffectState> effects)
        {
            var rowBounds = GetDrawRowBounds(e.Bounds);
            if (rowBounds.IsEmpty)
            {
                return;
            }

            var graphicsState = e.Graphics.Save();
            e.Graphics.SetClip(rowBounds);

            try
            {
                var selected = (e.State & TreeNodeStates.Selected) == TreeNodeStates.Selected;
                var nodeFont = e.Node.NodeFont ?? Font;
                var textColor = selected ? SystemColors.HighlightText : e.Node.ForeColor;
                if (textColor == Color.Empty)
                {
                    textColor = ForeColor;
                }

                var backColor = selected ? SystemColors.Highlight : BackColor;
                using (var backgroundBrush = new SolidBrush(backColor))
                {
                    e.Graphics.FillRectangle(backgroundBrush, rowBounds);
                }

                var flags = TextFormatFlags.NoPadding | TextFormatFlags.SingleLine | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis;
                var text = e.Node.Text ?? string.Empty;
                var textSize = TextRenderer.MeasureText(e.Graphics, text, nodeFont, new Size(int.MaxValue, int.MaxValue), flags);
                var textWidth = Math.Min(textSize.Width, Math.Max(0, rowBounds.Right - e.Bounds.Left));
                var textBounds = new Rectangle(e.Bounds.Left, e.Bounds.Top, textWidth, e.Bounds.Height);
                TextRenderer.DrawText(e.Graphics, text, nodeFont, textBounds, textColor, flags);

                var x = e.Bounds.Left + textSize.Width + 4;
                var y = e.Bounds.Top + Math.Max(0, (e.Bounds.Height - EffectIconHeight) / 2);
                foreach (var effect in effects)
                {
                    if (effect == null || effect.Id <= 0 || x >= ClientSize.Width)
                    {
                        continue;
                    }

                    var image = GetEffectImage(effect.Id);
                    if (image != null)
                    {
                        e.Graphics.DrawImage(image, new Rectangle(x, y, EffectIconWidth, EffectIconHeight));
                    }
                    else
                    {
                        QueueEffectImageDownload(effect.Id);
                        DrawMissingEffectIcon(e.Graphics, x, y, textColor);
                    }

                    x += EffectIconWidth + 2;
                    var counterText = ANClient.ContactRenderHelper.FormatEffectCounterCompactText(effect);
                    var counterSize = TextRenderer.MeasureText(e.Graphics, counterText, nodeFont, new Size(int.MaxValue, int.MaxValue), flags);
                    TextRenderer.DrawText(
                        e.Graphics,
                        counterText,
                        nodeFont,
                        new Rectangle(x, e.Bounds.Top, counterSize.Width, e.Bounds.Height),
                        textColor,
                        flags);
                    x += counterSize.Width + 6;
                }
            }
            finally
            {
                e.Graphics.Restore(graphicsState);
            }
        }

        private Rectangle GetDrawRowBounds(Rectangle labelBounds)
        {
            if (labelBounds.Width <= 0 || labelBounds.Height <= 0)
            {
                return Rectangle.Empty;
            }

            var rowBounds = FullRowSelect
                ? new Rectangle(labelBounds.Left, labelBounds.Top, Math.Max(0, ClientSize.Width - labelBounds.Left), labelBounds.Height)
                : labelBounds;
            return Rectangle.Intersect(rowBounds, ClientRectangle);
        }

        private Rectangle GetNodeRowBounds(TreeNode node)
        {
            if (node == null || IsDisposed)
            {
                return Rectangle.Empty;
            }

            var bounds = node.Bounds;
            if (bounds.Width <= 0 || bounds.Height <= 0)
            {
                return Rectangle.Empty;
            }

            var y = bounds.Top + Math.Max(0, bounds.Height / 2);
            if (y < 0 || y >= ClientSize.Height)
            {
                return Rectangle.Empty;
            }

            var x = Math.Min(Math.Max(bounds.Left + 1, 0), Math.Max(0, ClientSize.Width - 1));
            if (GetNodeAt(x, y) != node)
            {
                return Rectangle.Empty;
            }

            var rowBounds = FullRowSelect
                ? new Rectangle(bounds.Left, bounds.Top, Math.Max(0, ClientSize.Width - bounds.Left), Math.Max(bounds.Height, ItemHeight))
                : bounds;
            return Rectangle.Intersect(rowBounds, ClientRectangle);
        }

        private static Image GetEffectImage(int effectId)
        {
            lock (EffectImageSync)
            {
                Image cached;
                if (EffectImageCache.TryGetValue(effectId, out cached))
                {
                    return cached;
                }
            }

            var data = Cache.Get(BuildEffectImageUrl(effectId), false);
            var loaded = CreateEffectImage(data);
            if (loaded == null)
            {
                return null;
            }

            lock (EffectImageSync)
            {
                Image existing;
                if (EffectImageCache.TryGetValue(effectId, out existing))
                {
                    loaded.Dispose();
                    return existing;
                }

                EffectImageCache.Add(effectId, loaded);
                return loaded;
            }
        }

        private void QueueEffectImageDownload(int effectId)
        {
            lock (EffectImageSync)
            {
                if (EffectImageCache.ContainsKey(effectId) ||
                    EffectImageLoading.ContainsKey(effectId) ||
                    EffectImageMissingLogged.ContainsKey(effectId))
                {
                    return;
                }

                EffectImageLoading.Add(effectId, true);
            }

            ThreadPool.QueueUserWorkItem(DownloadEffectImageAsync, new EffectImageDownloadState { Tree = this, EffectId = effectId });
        }

        private static void DownloadEffectImageAsync(object state)
        {
            var downloadState = state as EffectImageDownloadState;
            if (downloadState == null || downloadState.EffectId <= 0)
            {
                return;
            }

            var effectId = downloadState.EffectId;
            var url = BuildEffectImageUrl(effectId);
            try
            {
                using (var client = new WebClient())
                {
                    var uri = new Uri(url);
                    if (!DirectGameRequestGuard.Prepare(client, uri, "TreeViewEx.EffectIcon"))
                    {
                        LogMissingEffectImage(effectId, "request blocked");
                        return;
                    }

                    client.Headers[HttpRequestHeader.UserAgent] = BrowserUserAgent;
                    client.Headers[HttpRequestHeader.Accept] = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8";
                    client.Headers[HttpRequestHeader.Referer] = "http://www.neverlands.ru/pinfo.cgi";
                    var data = client.DownloadData(uri);
                    if (data == null || data.Length == 0)
                    {
                        LogMissingEffectImage(effectId, "empty response");
                        return;
                    }

                    Cache.Store(url, data, true);
                    var image = CreateEffectImage(data);
                    if (image == null)
                    {
                        LogMissingEffectImage(effectId, "invalid image");
                        return;
                    }

                    lock (EffectImageSync)
                    {
                        if (EffectImageCache.ContainsKey(effectId))
                        {
                            image.Dispose();
                        }
                        else
                        {
                            EffectImageCache.Add(effectId, image);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                LogMissingEffectImage(effectId, ex.Message);
            }
            finally
            {
                lock (EffectImageSync)
                {
                    EffectImageLoading.Remove(effectId);
                }

                InvalidateAfterEffectImageDownload(downloadState.Tree, effectId);
            }
        }

        private static void InvalidateAfterEffectImageDownload(TreeViewEx tree, int effectId)
        {
            if (tree == null || tree.IsDisposed)
            {
                return;
            }

            try
            {
                if (tree.InvokeRequired)
                {
                    tree.BeginInvoke((MethodInvoker)delegate { InvalidateEffectNodes(tree, effectId); });
                }
                else
                {
                    InvalidateEffectNodes(tree, effectId);
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static void InvalidateEffectNodes(TreeViewEx tree, int effectId)
        {
            if (tree == null || tree.IsDisposed || effectId <= 0)
            {
                return;
            }

            foreach (TreeNode node in tree.Nodes)
            {
                InvalidateEffectNode(tree, node, effectId);
            }
        }

        private static void InvalidateEffectNode(TreeViewEx tree, TreeNode node, int effectId)
        {
            if (node == null)
            {
                return;
            }

            var contact = node.Tag as ANClient.Contact;
            if (contact != null && ContactHasEffect(contact, effectId) && node.IsVisible)
            {
                tree.InvalidateNodeRow(node);
            }

            foreach (TreeNode child in node.Nodes)
            {
                InvalidateEffectNode(tree, child, effectId);
            }
        }

        private static bool ContactHasEffect(ANClient.Contact contact, int effectId)
        {
            var effects = ANClient.ContactRenderHelper.ParseEffectStatesCsv(contact.EffectStates, contact.EffectIds);
            foreach (var effect in effects)
            {
                if (effect != null && effect.Id == effectId)
                {
                    return true;
                }
            }

            return false;
        }

        private static string BuildEffectImageUrl(int effectId)
        {
            return EffectImageUrlPrefix + effectId.ToString(System.Globalization.CultureInfo.InvariantCulture) + ".gif";
        }

        private static Image CreateEffectImage(byte[] data)
        {
            if (data == null || data.Length == 0)
            {
                return null;
            }

            try
            {
                using (var stream = new MemoryStream(data))
                using (var image = Image.FromStream(stream))
                {
                    return new Bitmap(image);
                }
            }
            catch
            {
                return null;
            }
        }

        private static void DrawMissingEffectIcon(Graphics graphics, int x, int y, Color textColor)
        {
            var bounds = new Rectangle(x, y, EffectIconWidth, EffectIconHeight);
            using (var pen = new Pen(textColor))
            {
                graphics.DrawRectangle(pen, bounds);
            }
        }

        private static void LogMissingEffectImage(int effectId, string reason)
        {
            lock (EffectImageSync)
            {
                if (EffectImageMissingLogged.ContainsKey(effectId))
                {
                    return;
                }

                EffectImageMissingLogged.Add(effectId, true);
            }

            AppLog.w("CONTACT_EFFECT_RENDER", "effect icon unavailable id=" + effectId.ToString(System.Globalization.CultureInfo.InvariantCulture) + " reason=" + (reason ?? string.Empty));
        }
    }

}
