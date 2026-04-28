using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using System.Web;
using ABClient.ABForms;
using ABClient.Helpers;

namespace ABClient.PostFilter
{
    internal static partial class Filter
    {
        private const string AlchemyTraceChain = "auto_cut_trace";
        private const string AlchemyCaptchaUrlPrefix = "http://www.neverlands.ru/modules/code/code.php?";
        private static PendingAlchemyCut pendingAlchemyCut;

        private static byte[] AlchemyAjaxPhp(string address, byte[] array)
        {
            if (array == null || array.Length == 0)
            {
                return array;
            }

            var html = Russian.Codepage.GetString(array);
            if (string.IsNullOrEmpty(html))
            {
                return array;
            }

            if (address.IndexOf("act=1", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                ProcessAlchemyAct1(html);
                return array;
            }

            if (address.IndexOf("act=3", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                ProcessAlchemyAct3(html);
            }

            return array;
        }

        private static void ProcessAlchemyAct1(string html)
        {
            if (!AppVars.DoHerbAutoCut)
            {
                return;
            }

            var state = ParseResourceState(html);
            if (state == null || state.Resources.Count == 0)
            {
                if (html.Trim().Equals("ERR", StringComparison.OrdinalIgnoreCase))
                {
                    AutoCutRuntime.ScheduleLookRetry("alchemy_act1_err");
                }

                AppLog.d(AlchemyTraceChain, "AlchemyAjaxPhp", "act1: no resource state");
                return;
            }

            RegisterObservedResources(state);
            AutoCutRuntime.ClearDueHerbTimersForCurrentCell("alchemy_act1");

            ResourceCandidate selected = null;
            for (var i = 0; i < state.Resources.Count; i++)
            {
                var resource = state.Resources[i];
                if (resource.AvailableCount <= 0 || string.IsNullOrEmpty(resource.CutVcode))
                {
                    continue;
                }

                if (FormMain.IsHerbAutoCut(resource.Name))
                {
                    selected = resource;
                    break;
                }
            }

            if (selected == null)
            {
                AppLog.d(AlchemyTraceChain, "AlchemyAjaxPhp", "act1: no selected available herb, resources=" + state.Resources.Count);
                AutoCutRuntime.OnScanWithoutSelectedHerb("alchemy_act1");
                return;
            }

            var selectedCut = new PendingAlchemyCut(selected, state.CaptchaToken, HasMoreSelectedAvailableAfterCut(state, selected), DateTime.Now);
            if (!AppVars.AutoCutArmedSickle)
            {
                pendingAlchemyCut = selectedCut;
                AppVars.AutoCutCheckSickle = true;
                AppVars.FightLink = string.Empty;
                RequestMainPhpReload("act1: selected herb waits for sickle check, herb=" + selected.Name);
                return;
            }

            if (AutoCutRuntime.NeedsMassSnapshotBeforeCut())
            {
                pendingAlchemyCut = selectedCut;
                AutoCutRuntime.RequestMassSnapshotBeforeCut("alchemy_act1:" + selected.ResId);
                AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", "act1: selected herb waits for mass snapshot, herb=" + selected.Name);
                return;
            }

            pendingAlchemyCut = selectedCut;
            DispatchPendingAlchemyCut(pendingAlchemyCut);
        }

        private static bool ResumePendingAlchemyCutAfterPreparation(string source)
        {
            var current = pendingAlchemyCut;
            if (current == null || current.IsExpired || !AppVars.DoHerbAutoCut || !AppVars.AutoCutArmedSickle)
            {
                return false;
            }

            if (AutoCutRuntime.NeedsMassSnapshotBeforeCut())
            {
                AutoCutRuntime.RequestMassSnapshotBeforeCut("resume_after_" + source + ":" + current.Resource.ResId);
                AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", "pending cut resume waits for mass snapshot: herb=" + current.Resource.Name + ", source=" + source);
                return true;
            }

            DispatchPendingAlchemyCut(current);
            AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", "pending cut resumed: herb=" + current.Resource.Name + ", source=" + source);
            return true;
        }

        private static void RegisterObservedResources(ResourceState state)
        {
            if (state == null || AppVars.Profile == null)
            {
                return;
            }

            var catalogChanged = AutoCutCatalog.EnsureProfileCatalog(AppVars.Profile);
            var listBuilder = new StringBuilder();
            for (var i = 0; i < state.Resources.Count; i++)
            {
                var resource = state.Resources[i];
                if (resource == null || string.IsNullOrEmpty(resource.Name))
                {
                    continue;
                }

                int growthMinutes;
                int.TryParse(resource.RTime, NumberStyles.Integer, CultureInfo.InvariantCulture, out growthMinutes);
                catalogChanged |= AutoCutCatalog.RegisterObservedHerb(
                    AppVars.Profile,
                    resource.ResId,
                    resource.Name,
                    growthMinutes,
                    AppVars.Profile.MapLocation ?? string.Empty);

                listBuilder.Append(resource.Name);
                listBuilder.Append(':');
                listBuilder.Append(resource.AvailableCount > 0 ? '1' : '0');
                listBuilder.Append('|');
            }

            if (listBuilder.Length > 0)
            {
                FormMain.HerbsList(listBuilder.ToString());
            }

            if (catalogChanged)
            {
                AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", "catalog updated from RESO@, resources=" + state.Resources.Count);
                AppVars.Profile.Save();
            }
        }

        private static void RequestMainPhpReload(string reason)
        {
            AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", reason);
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new ReloadMainPhpInvokeDelegate(AppVars.MainForm.ReloadMainPhpInvoke),
                        new object[] { });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static void DispatchPendingAlchemyCut(PendingAlchemyCut cut)
        {
            if (cut == null || cut.IsExpired)
            {
                return;
            }

            var captchaToken = (cut.CaptchaToken ?? string.Empty).Trim();
            var captchaRequired = captchaToken.Length > 0 && !captchaToken.Equals("00000", StringComparison.OrdinalIgnoreCase);
            var code = captchaRequired ? "????" : "1";
            AppVars.FightLink = cut.Resource.BuildFinishUrl(code);
            if (captchaRequired)
            {
                AppVars.CodeAddress = AlchemyCaptchaUrlPrefix + captchaToken;
                AppVars.CodePng = null;
                AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", "captcha pending: herb=" + cut.Resource.Name);
            }
            else
            {
                AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", "no-captcha pending: herb=" + cut.Resource.Name);
            }
        }

        private static void ProcessAlchemyAct3(string html)
        {
            var lower = html.ToLowerInvariant();
            if (lower.IndexOf("невер", StringComparison.OrdinalIgnoreCase) >= 0 &&
                lower.IndexOf("код", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                AppLog.w(AlchemyTraceChain, "AlchemyAjaxPhp", "act3: wrong protection/captcha code response");
                pendingAlchemyCut = null;
                AppVars.FightLink = string.Empty;
                AutoCutRuntime.ScheduleLookRetry("wrong_captcha:alchemy_act3");
                return;
            }

            if (lower.IndexOf("всё прошло успешно", StringComparison.OrdinalIgnoreCase) < 0 &&
                lower.IndexOf("все прошло успешно", StringComparison.OrdinalIgnoreCase) < 0)
            {
                return;
            }

            var foundGarbage = lower.IndexOf(AutoCutRuntime.GarbageItemName.ToLowerInvariant(), StringComparison.OrdinalIgnoreCase) >= 0;

            var current = pendingAlchemyCut;
            pendingAlchemyCut = null;
            AppVars.FightLink = string.Empty;
            if (foundGarbage)
            {
                AutoCutRuntime.RequestGarbageCleanupAfterCut(current == null || current.IsExpired ? "alchemy_act3_unmatched" : "alchemy_act3");
            }

            if (current == null || current.IsExpired)
            {
                AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", "act3 success without pending cut");
                return;
            }

            FormMain.TraceCut(current.Resource.Name);
            AutoCutRuntime.OnCutSuccess(current.RetrySameCellAfterCut, "alchemy_act3", current.Resource.MassAsDouble());
            AppLog.i(AlchemyTraceChain, "AlchemyAjaxPhp", "act3 success: herb=" + current.Resource.Name);
        }

        private static bool HasMoreSelectedAvailableAfterCut(ResourceState state, ResourceCandidate selected)
        {
            if (state == null || selected == null)
            {
                return false;
            }

            for (var i = 0; i < state.Resources.Count; i++)
            {
                var resource = state.Resources[i];
                if (resource == null || resource.AvailableCount <= 0 || string.IsNullOrEmpty(resource.CutVcode) || !FormMain.IsHerbAutoCut(resource.Name))
                {
                    continue;
                }

                var remaining = resource.AvailableCount;
                if (IsSameResource(resource, selected))
                {
                    remaining--;
                }

                if (remaining > 0)
                {
                    return true;
                }
            }

            return false;
        }

        private static bool IsSameResource(ResourceCandidate left, ResourceCandidate right)
        {
            if (left == null || right == null)
            {
                return false;
            }

            if (!string.IsNullOrEmpty(left.ResId) && left.ResId.Equals(right.ResId, StringComparison.Ordinal))
            {
                return true;
            }

            return !string.IsNullOrEmpty(left.Name) && left.Name.Equals(right.Name, StringComparison.OrdinalIgnoreCase);
        }

        private static ResourceState ParseResourceState(string html)
        {
            if (string.IsNullOrEmpty(html) || !html.StartsWith("RESO@", StringComparison.OrdinalIgnoreCase))
            {
                return null;
            }

            var parts = html.Split('@');
            if (parts.Length < 6)
            {
                return null;
            }

            var payload = parts[5].Trim();
            if (payload.Length == 0)
            {
                return null;
            }

            var tokens = SplitTopLevel(TrimOuterArray(payload));
            if (tokens.Count < 5)
            {
                return null;
            }

            var state = new ResourceState
                            {
                                CaptchaToken = Unquote(tokens[1]),
                                RX = Unquote(tokens[2]),
                                RY = Unquote(tokens[3])
                            };
            for (var i = 4; i < tokens.Count; i++)
            {
                var resource = ParseResourceCandidate(tokens[i], state.RX, state.RY);
                if (resource != null)
                {
                    state.Resources.Add(resource);
                }
            }

            return state;
        }

        private static ResourceCandidate ParseResourceCandidate(string token, string rx, string ry)
        {
            var fields = SplitTopLevel(TrimOuterArray(token));
            if (fields.Count < 12)
            {
                return null;
            }

            var resource = new ResourceCandidate
                               {
                                   ResId = Unquote(fields[0]),
                                   Name = Unquote(fields[1]),
                                   LTime = Unquote(fields[2]),
                                   RTime = Unquote(fields[3]),
                                   Uid = Unquote(fields[4]),
                                   Curs = Unquote(fields[5]),
                                   Mass = Unquote(fields[6]),
                                   P = Unquote(fields[7]),
                                   CutVcode = Unquote(fields[9]),
                                   RType = Unquote(fields[10]),
                                   RX = rx,
                                   RY = ry
                               };
            int available;
            resource.AvailableCount = int.TryParse(Unquote(fields[8]), NumberStyles.Integer, CultureInfo.InvariantCulture, out available)
                                          ? available
                                          : 0;
            return resource;
        }

        private static string TrimOuterArray(string value)
        {
            var result = (value ?? string.Empty).Trim();
            if (result.StartsWith("[", StringComparison.Ordinal) && result.EndsWith("]", StringComparison.Ordinal))
            {
                result = result.Substring(1, result.Length - 2);
            }

            return result;
        }

        private static List<string> SplitTopLevel(string value)
        {
            var result = new List<string>();
            if (value == null)
            {
                return result;
            }

            var current = new StringBuilder();
            var depth = 0;
            var quote = '\0';
            for (var i = 0; i < value.Length; i++)
            {
                var c = value[i];
                if (quote != '\0')
                {
                    current.Append(c);
                    if (c == '\\' && i + 1 < value.Length)
                    {
                        i++;
                        current.Append(value[i]);
                        continue;
                    }

                    if (c == quote)
                    {
                        quote = '\0';
                    }

                    continue;
                }

                if (c == '\'' || c == '"')
                {
                    quote = c;
                    current.Append(c);
                    continue;
                }

                if (c == '[')
                {
                    depth++;
                    current.Append(c);
                    continue;
                }

                if (c == ']')
                {
                    depth--;
                    current.Append(c);
                    continue;
                }

                if (c == ',' && depth == 0)
                {
                    result.Add(current.ToString().Trim());
                    current.Length = 0;
                    continue;
                }

                current.Append(c);
            }

            result.Add(current.ToString().Trim());
            return result;
        }

        private static string Unquote(string value)
        {
            var result = (value ?? string.Empty).Trim();
            if (result.Length >= 2 &&
                ((result[0] == '\'' && result[result.Length - 1] == '\'') ||
                 (result[0] == '"' && result[result.Length - 1] == '"')))
            {
                result = result.Substring(1, result.Length - 2);
            }

            return result.Replace("\\'", "'").Replace("\\\"", "\"");
        }

        private sealed class ResourceState
        {
            internal string CaptchaToken;
            internal string RX;
            internal string RY;
            internal readonly List<ResourceCandidate> Resources = new List<ResourceCandidate>();
        }

        private sealed class ResourceCandidate
        {
            internal string ResId;
            internal string Name;
            internal string LTime;
            internal string RTime;
            internal string Uid;
            internal string Curs;
            internal string Mass;
            internal string P;
            internal int AvailableCount;
            internal string CutVcode;
            internal string RType;
            internal string RX;
            internal string RY;

            internal string BuildFinishUrl(string code)
            {
                return "alchemy_ajax.php?act=3"
                       + "&res_id=" + Url(ResId)
                       + "&r_x=" + Url(RX)
                       + "&r_y=" + Url(RY)
                       + "&r_time=" + Url(RTime)
                       + "&r_type=" + Url(RType)
                       + "&uid=" + Url(Uid)
                       + "&curs=" + Url(Curs)
                       + "&mass=" + Url(Mass)
                       + "&p=" + Url(P)
                       + "&l_time=" + Url(LTime)
                       + "&vcode=" + Url(CutVcode)
                       + "&code=" + (code == "????" ? "????" : Url(code))
                       + "&r=" + DateTime.Now.Ticks.ToString(CultureInfo.InvariantCulture);
            }

            internal double MassAsDouble()
            {
                double result;
                return double.TryParse((Mass ?? string.Empty).Trim().Replace(',', '.'), NumberStyles.Any, CultureInfo.InvariantCulture, out result) ? result : 0d;
            }

            private static string Url(string value)
            {
                return HttpUtility.UrlEncode(value ?? string.Empty, AppVars.Codepage);
            }
        }

        private sealed class PendingAlchemyCut
        {
            internal readonly ResourceCandidate Resource;
            internal readonly string CaptchaToken;
            internal readonly bool RetrySameCellAfterCut;
            private readonly DateTime createdAt;

            internal PendingAlchemyCut(ResourceCandidate resource, string captchaToken, bool retrySameCellAfterCut, DateTime now)
            {
                Resource = resource;
                CaptchaToken = captchaToken;
                RetrySameCellAfterCut = retrySameCellAfterCut;
                createdAt = now;
            }

            internal bool IsExpired
            {
                get { return DateTime.Now.Subtract(createdAt).TotalSeconds > 120; }
            }
        }
    }
}
