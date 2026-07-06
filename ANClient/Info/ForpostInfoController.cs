namespace ANClient.Info
{
    using System;
    using System.Collections.Generic;
    using System.Collections.Specialized;
    using System.IO;
    using System.Globalization;
    using System.Net;
    using System.Text;
    using System.Text.RegularExpressions;
    using System.Threading;
    using System.Web;
    using System.Windows.Forms;
    using ANClient.ANProxy;
    using ANClient.AppControls;

    internal sealed class ForpostInfoController
    {
        private const string Tag = "ForpostInfoController";
        private const string UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";
        private const string GetCityBrowserAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";
        private const string BrowserAcceptLanguage = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7";
        private const string ImageWeaponBaseUrl = "http://image.neverlands.ru/weapon/";
        private const string CityHallUrlFormat = "http://service.neverlands.ru/info/cityhall_{0}.txt";
        private const string ForpostCityApiUrl = "http://www.neverlands.ru/modules/api/getcity.cgi?city1";
        private const int ForpostBuildingsDelayMs = 500;
        private const int ForpostBuildingsRetryDelayStepMs = 500;
        private const int ForpostBuildingsMaxDelayMs = 5000;

        private readonly WebBrowser _browser;
        private readonly List<RecipeDatabase.RecipeItem> _detailBackStack = new List<RecipeDatabase.RecipeItem>();
        private readonly object _forpostRetrySync = new object();

        private RecipeDatabase _recipeDatabase;
        private CityHallData _cityHall1;
        private CityHallData _cityHall2;
        private ForpostData _forpostData;
        private string _status = "Загрузка справочников...";
        private string _professionRatingStatus = "Рейтинги Проф: выберите категорию.";
        private string _mode = "cityhall";
        private string _selectedSectionKey;
        private string _searchQuery = string.Empty;
        private int _selectedCityHallId = 1;
        private int _selectedProfessionRatingId = ProfessionRatingRepository.GetFirstCategoryId();
        private int _forpostConsecutiveFailures;
        private RecipeDatabase.RecipeItem _selectedItem;
        private ProfessionRatingRepository.RatingTable _selectedProfessionRating;

        internal ForpostInfoController(WebBrowser browser)
            : this(browser, null)
        {
        }

        internal ForpostInfoController(WebBrowser browser, string initialAddress)
        {
            _browser = browser;
            ApplyInitialAddress(initialAddress);
        }

        private void ApplyInitialAddress(string address)
        {
            if (string.IsNullOrEmpty(address))
            {
                return;
            }

            Uri uri;
            try
            {
                uri = new Uri(address);
            }
            catch (UriFormatException)
            {
                return;
            }

            if (uri.Host.Equals("tables", StringComparison.OrdinalIgnoreCase))
            {
                _mode = "tables";
                return;
            }

            if (uri.Host.Equals("ratings", StringComparison.OrdinalIgnoreCase))
            {
                _mode = "ratings";
                NameValueCollection query = HttpUtility.ParseQueryString(uri.Query, Encoding.UTF8);
                ApplyProfessionRatingCategory(query["category"]);
                return;
            }

            if (uri.Host.Equals("forpost", StringComparison.OrdinalIgnoreCase))
            {
                NameValueCollection query = HttpUtility.ParseQueryString(uri.Query, Encoding.UTF8);
                string tab = query["tab"];
                if (!string.IsNullOrEmpty(tab))
                {
                    _mode = tab;
                }
            }
        }

        internal void Start()
        {
            Render();
            LoadAllData();
            if (_mode == "ratings")
            {
                OpenProfessionRating(_selectedProfessionRatingId, false);
            }
        }

        private void ApplyProfessionRatingCategory(string categoryRaw)
        {
            int categoryId;
            if (int.TryParse(categoryRaw, out categoryId) && ProfessionRatingRepository.FindCategory(categoryId) != null)
            {
                _selectedProfessionRatingId = categoryId;
                return;
            }

            if (_selectedProfessionRatingId == 0 || ProfessionRatingRepository.FindCategory(_selectedProfessionRatingId) == null)
            {
                _selectedProfessionRatingId = ProfessionRatingRepository.GetFirstCategoryId();
            }
        }

        private void OpenProfessionRating(int categoryId, bool forceRefresh)
        {
            ProfessionRatingRepository.Category category = ProfessionRatingRepository.FindCategory(categoryId);
            if (category == null)
            {
                return;
            }

            _mode = "ratings";
            _selectedProfessionRatingId = categoryId;
            _selectedItem = null;
            _detailBackStack.Clear();
            _selectedProfessionRating = forceRefresh ? null : ProfessionRatingRepository.GetCachedRating(categoryId);
            if (_selectedProfessionRating != null)
            {
                _professionRatingStatus = BuildProfessionRatingLoadedStatus(_selectedProfessionRating);
                Render();
                return;
            }

            _professionRatingStatus = "Загрузка рейтинга: " + category.Title + "...";
            Render();
            ThreadPool.QueueUserWorkItem(LoadProfessionRatingAsync, new LoadProfessionRatingState(categoryId, forceRefresh));
        }

        private void LoadProfessionRatingAsync(object state)
        {
            var loadState = (LoadProfessionRatingState)state;
            ProfessionRatingRepository.RatingTable table = null;
            string status;
            try
            {
                table = ProfessionRatingRepository.LoadRating(loadState.CategoryId, loadState.ForceRefresh);
                status = BuildProfessionRatingLoadedStatus(table);
            }
            catch (Exception error)
            {
                status = "Рейтинг недоступен: " + error.Message;
                ANClient.AppLog.w(Tag, "WEEKLY_RATING_LOAD_FAILED: id=" + loadState.CategoryId, error);
            }

            ProfessionRatingRepository.RatingTable finalTable = table;
            string finalStatus = status;
            SafeBeginInvoke(delegate
                {
                    if (_selectedProfessionRatingId == loadState.CategoryId)
                    {
                        _selectedProfessionRating = finalTable;
                        _professionRatingStatus = finalStatus;
                    }

                    Render();
                });
        }

        private static string BuildProfessionRatingLoadedStatus(ProfessionRatingRepository.RatingTable table)
        {
            if (table == null)
            {
                return "Рейтинг не загружен";
            }

            return "Рейтинг загружен: " + table.Category.Title + "; записей: " + table.Entries.Count + "; источник: " + table.SourceUrl;
        }

        internal void BeforeNavigate(object sender, WebBrowserExtendedNavigatingEventArgs e)
        {
            if (e == null || string.IsNullOrEmpty(e.Address))
            {
                return;
            }

            if (!e.Address.StartsWith("anclient://", StringComparison.OrdinalIgnoreCase))
            {
                return;
            }

            e.Cancel = true;
            HandleLocalUrl(e.Address);
        }

        private void HandleLocalUrl(string address)
        {
            Uri uri;
            try
            {
                uri = new Uri(address);
            }
            catch (UriFormatException)
            {
                return;
            }

            NameValueCollection query = HttpUtility.ParseQueryString(uri.Query, Encoding.UTF8);
            string host = uri.Host.ToLowerInvariant();
            if (host == "refresh")
            {
                if (_mode == "ratings")
                {
                    OpenProfessionRating(_selectedProfessionRatingId, true);
                    return;
                }

                LoadAllData();
                return;
            }

            if (host == "forpost")
            {
                string tab = query["tab"];
                if (!string.IsNullOrEmpty(tab))
                {
                    _mode = tab;
                }

                string city = query["city"];
                if (city == "1" || city == "2")
                {
                    _selectedCityHallId = int.Parse(city);
                }

                _selectedItem = null;
                _detailBackStack.Clear();
                Render();
                return;
            }

            if (host == "tables")
            {
                _mode = "tables";
                string section = query["section"];
                if (!string.IsNullOrEmpty(section))
                {
                    _selectedSectionKey = section;
                    _selectedItem = null;
                    _detailBackStack.Clear();
                }

                string search = query["search"];
                if (search != null)
                {
                    _searchQuery = search.Trim();
                    _selectedItem = null;
                    _detailBackStack.Clear();
                }

                Render();
                return;
            }

            if (host == "ratings")
            {
                ApplyProfessionRatingCategory(query["category"]);
                OpenProfessionRating(_selectedProfessionRatingId, query["refresh"] == "1");
                return;
            }

            if (host == "pinfo")
            {
                string nick = query["nick"];
                if (!string.IsNullOrEmpty(nick) && ANClient.AppVars.MainForm != null)
                {
                    ANClient.AppVars.MainForm.BeforeNewWindow(ProfessionRatingRepository.BuildPinfoUrl(nick));
                }

                return;
            }

            if (host == "item")
            {
                OpenItem(query["image"], query["name"], query["push"] == "1");
                return;
            }

            if (host == "back")
            {
                if (_detailBackStack.Count == 0)
                {
                    _selectedItem = null;
                }
                else
                {
                    _selectedItem = _detailBackStack[_detailBackStack.Count - 1];
                    _detailBackStack.RemoveAt(_detailBackStack.Count - 1);
                    _selectedSectionKey = _selectedItem.SectionKey;
                }

                _mode = "tables";
                Render();
            }
        }

        private void OpenItem(string imageFile, string requestedName, bool pushCurrent)
        {
            if (_recipeDatabase == null)
            {
                return;
            }

            List<RecipeDatabase.RecipeItem> matches = _recipeDatabase.FindByImageFile(imageFile);
            if (matches.Count == 0)
            {
                return;
            }

            RecipeDatabase.RecipeItem item = ChooseItemByName(matches, requestedName);
            if (item == null)
            {
                item = matches[0];
            }

            if (pushCurrent && _selectedItem != null && !ReferenceEquals(_selectedItem, item))
            {
                _detailBackStack.Add(_selectedItem);
            }
            else if (!pushCurrent)
            {
                _detailBackStack.Clear();
            }

            _selectedItem = item;
            _selectedSectionKey = item.SectionKey;
            _mode = "tables";
            Render();
        }

        private void LoadAllData()
        {
            _status = "Загрузка справочников...";
            Render();
            ThreadPool.QueueUserWorkItem(LoadAllDataAsync);
        }

        private void LoadAllDataAsync(object state)
        {
            RecipeDatabase recipeDatabase = null;
            CityHallData cityHall1 = null;
            CityHallData cityHall2 = null;
            ForpostData forpostData = null;
            bool needBrowserFallback = false;

            try
            {
                recipeDatabase = RecipeDatabase.Load();
            }
            catch (Exception error)
            {
                ANClient.AppLog.w(Tag, "RECIPE_DATABASE_LOAD_FAILED", error);
            }

            try
            {
                cityHall1 = ParseCityHall(FetchText(string.Format(CityHallUrlFormat, 1)), 1);
                cityHall2 = ParseCityHall(FetchText(string.Format(CityHallUrlFormat, 2)), 2);
            }
            catch (Exception error)
            {
                ANClient.AppLog.w(Tag, "CITY_HALL_LOAD_FAILED", error);
            }

            try
            {
                int failuresBeforeRequest;
                int delayMs = GetForpostBuildingsDelayMs(out failuresBeforeRequest);
                ANClient.AppLog.i(Tag, "GETCITY_DELAY_BEFORE_REQUEST: delayMs=" + delayMs + ", failures=" + failuresBeforeRequest);
                Thread.Sleep(delayMs);
                ANClient.AppLog.i(Tag, "GETCITY_DIRECT_START: url=" + ForpostCityApiUrl);
                string forpostRaw = FetchText(ForpostCityApiUrl);
                forpostData = ParseForpost(forpostRaw, ForpostCityApiUrl);
                ANClient.AppLog.i(Tag, "GETCITY_DIRECT_PARSED: buildings=" + ForpostBuildingCount(forpostData) + ", rawLength=" + TextLength(forpostRaw) + ", preview=" + PreviewText(forpostRaw));
                if (!HasForpostBuildings(forpostData))
                {
                    forpostData = null;
                    needBrowserFallback = true;
                    MarkForpostBuildingsFailure("direct_empty");
                    ANClient.AppLog.w(Tag, "GETCITY_DIRECT_EMPTY: retry via hidden WebBrowser");
                }
                else
                {
                    MarkForpostBuildingsSuccess("direct", ForpostBuildingCount(forpostData));
                }
            }
            catch (WebException error)
            {
                int statusCode = GetHttpStatusCode(error);
                if (IsBrowserFallbackStatus(statusCode))
                {
                    needBrowserFallback = true;
                    MarkForpostBuildingsFailure("http_" + statusCode);
                    ANClient.AppLog.w(Tag, "GETCITY_HTTP_" + statusCode + ": retry via hidden WebBrowser", error);
                }
                else
                {
                    MarkForpostBuildingsFailure("web_exception_" + statusCode);
                    ANClient.AppLog.w(Tag, "FORPOST_BUILDINGS_LOAD_FAILED", error);
                }
            }
            catch (Exception error)
            {
                MarkForpostBuildingsFailure("exception");
                ANClient.AppLog.w(Tag, "FORPOST_BUILDINGS_LOAD_FAILED", error);
            }

            RecipeDatabase finalRecipeDatabase = recipeDatabase;
            CityHallData finalCityHall1 = cityHall1;
            CityHallData finalCityHall2 = cityHall2;
            ForpostData finalForpostData = forpostData;
            bool finalNeedBrowserFallback = needBrowserFallback;
            SafeBeginInvoke(delegate
                {
                    bool hasNewForpostData = HasForpostBuildings(finalForpostData);
                    bool hadPreviousForpostData = HasForpostBuildings(_forpostData);
                    _recipeDatabase = finalRecipeDatabase;
                    _cityHall1 = finalCityHall1;
                    _cityHall2 = finalCityHall2;
                    if (hasNewForpostData || !hadPreviousForpostData)
                    {
                        _forpostData = finalForpostData;
                        if (hasNewForpostData)
                        {
                            ANClient.AppLog.i(Tag, "GETCITY_LAST_SUCCESS_UPDATED: buildings=" + ForpostBuildingCount(finalForpostData) + ", source=" + finalForpostData.SourceUrl);
                        }
                    }
                    else
                    {
                        ANClient.AppLog.w(Tag, "GETCITY_KEEP_LAST_SUCCESS: buildings=" + ForpostBuildingCount(_forpostData));
                    }

                    if (_recipeDatabase != null && string.IsNullOrEmpty(_selectedSectionKey))
                    {
                        _selectedSectionKey = _recipeDatabase.GetFirstSectionKey();
                    }

                    _status = BuildStatus(finalNeedBrowserFallback);
                    if (!hasNewForpostData && hadPreviousForpostData)
                    {
                        _status += "; здания сохранены из последней успешной загрузки";
                    }

                    Render();
                    if (finalNeedBrowserFallback)
                    {
                        if (HasForpostBuildings(_forpostData))
                        {
                            ANClient.AppLog.w(Tag, "GETCITY_WEBBROWSER_SUPPRESSED: last successful buildings kept");
                        }
                        else
                        {
                            FetchForpostViaHiddenBrowser();
                        }
                    }
                });
        }

        private string FetchText(string url)
        {
            bool isForpostCityApi = IsForpostCityApiUrl(url);
            var request = (HttpWebRequest)WebRequest.Create(GameServerSelector.RouteUrlToCurrentServer(url));
            request.Method = "GET";
            request.UserAgent = UserAgent;
            request.Accept = isForpostCityApi ? GetCityBrowserAccept : "text/plain,text/html,*/*";
            request.Timeout = 15000;
            if (isForpostCityApi)
            {
                ApplyGetCityBrowserRequestProfile(request);
            }

            if (!ANClient.ANProxy.DirectGameRequestGuard.Prepare(request, Tag + ".FetchText"))
            {
                throw new WebException("DIRECT_GAME_REQUEST_BLOCKED");
            }

            string cookies = string.Empty;
            if (url.IndexOf("neverlands.ru", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                if (!isForpostCityApi)
                {
                    cookies = BuildBestEffortCookieHeader();
                    if (!string.IsNullOrEmpty(cookies))
                    {
                        request.Headers.Add("Cookie", cookies);
                    }

                    request.Referer = "http://neverlands.ru/main.php";
                }
            }

            ANClient.AppLog.i(Tag, "FETCH_START: url=" + url + ", cookies=" + (cookies.Length > 0) + ", cookieLength=" + cookies.Length + ", harProfile=" + isForpostCityApi);
            try
            {
                using (var response = (HttpWebResponse)request.GetResponse())
                using (Stream stream = response.GetResponseStream())
                using (var reader = new StreamReader(stream, ANClient.AppVars.Codepage))
                {
                    string text = reader.ReadToEnd();
                    ANClient.AppLog.i(Tag, "FETCH_OK: url=" + url + ", status=" + (int)response.StatusCode + ", length=" + TextLength(text) + ", preview=" + PreviewText(text));
                    return text;
                }
            }
            catch (WebException error)
            {
                ANClient.AppLog.w(Tag, "FETCH_HTTP_FAILED: url=" + url + ", status=" + GetHttpStatusCode(error) + ", cookies=" + (cookies.Length > 0) + ", cookieLength=" + cookies.Length + ", responsePreview=" + ReadWebExceptionResponsePreview(error), error);
                throw;
            }
        }

        private static bool IsForpostCityApiUrl(string url)
        {
            return string.Equals(url, ForpostCityApiUrl, StringComparison.OrdinalIgnoreCase);
        }

        private static void ApplyGetCityBrowserRequestProfile(HttpWebRequest request)
        {
            request.KeepAlive = true;
            request.AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate;
            AddHeaderSafe(request, "Accept-Language", BrowserAcceptLanguage);
            AddHeaderSafe(request, "Cache-Control", "no-cache");
            AddHeaderSafe(request, "Pragma", "no-cache");
            AddHeaderSafe(request, "DNT", "1");
            AddHeaderSafe(request, "Upgrade-Insecure-Requests", "1");
        }

        private static void AddHeaderSafe(HttpWebRequest request, string name, string value)
        {
            try
            {
                request.Headers[name] = value;
            }
            catch (ArgumentException error)
            {
                ANClient.AppLog.w(Tag, "GETCITY_HEADER_SKIPPED: name=" + name, error);
            }
            catch (InvalidOperationException error)
            {
                ANClient.AppLog.w(Tag, "GETCITY_HEADER_SKIPPED: name=" + name, error);
            }
        }

        private static string BuildBestEffortCookieHeader()
        {
            var cookiesByName = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            AddCookiePairs(cookiesByName, CookiesManager.Obtain("neverlands.ru"));
            AddCookiePairs(cookiesByName, CookiesManager.Obtain("www.neverlands.ru"));
            if (cookiesByName.Count == 0)
            {
                return string.Empty;
            }

            var builder = new StringBuilder();
            foreach (KeyValuePair<string, string> pair in cookiesByName)
            {
                if (builder.Length > 0)
                {
                    builder.Append("; ");
                }

                builder.Append(pair.Key).Append('=').Append(pair.Value);
            }

            return builder.ToString();
        }

        private static void AddCookiePairs(Dictionary<string, string> output, string cookieHeader)
        {
            if (string.IsNullOrEmpty(cookieHeader))
            {
                return;
            }

            string[] pairs = cookieHeader.Split(';');
            foreach (string rawPair in pairs)
            {
                string pair = rawPair == null ? string.Empty : rawPair.Trim();
                int delimiter = pair.IndexOf('=');
                if (delimiter <= 0)
                {
                    continue;
                }

                string name = pair.Substring(0, delimiter).Trim();
                string value = pair.Substring(delimiter + 1).Trim();
                if (name.Length > 0)
                {
                    output[name] = value;
                }
            }
        }

        private static bool IsBrowserFallbackStatus(int statusCode)
        {
            return statusCode == 535 || statusCode == 536;
        }

        private static int GetHttpStatusCode(WebException error)
        {
            var response = error == null ? null : error.Response as HttpWebResponse;
            return response == null ? 0 : (int)response.StatusCode;
        }

        private static int TextLength(string text)
        {
            return text == null ? 0 : text.Length;
        }

        private static int ForpostBuildingCount(ForpostData data)
        {
            return data == null ? 0 : data.Buildings.Count;
        }

        private static bool HasForpostBuildings(ForpostData data)
        {
            return data != null && data.Buildings.Count > 0;
        }

        private int GetForpostBuildingsDelayMs(out int consecutiveFailures)
        {
            lock (_forpostRetrySync)
            {
                consecutiveFailures = _forpostConsecutiveFailures;
                return CalculateForpostBuildingsDelayMs(consecutiveFailures);
            }
        }

        private void MarkForpostBuildingsFailure(string reason)
        {
            int consecutiveFailures;
            int nextDelayMs;
            lock (_forpostRetrySync)
            {
                if (_forpostConsecutiveFailures < 1000)
                {
                    _forpostConsecutiveFailures++;
                }

                consecutiveFailures = _forpostConsecutiveFailures;
                nextDelayMs = CalculateForpostBuildingsDelayMs(consecutiveFailures);
            }

            ANClient.AppLog.w(Tag, "GETCITY_RETRY_BACKOFF: reason=" + reason + ", failures=" + consecutiveFailures + ", nextDelayMs=" + nextDelayMs);
        }

        private void MarkForpostBuildingsSuccess(string source, int buildings)
        {
            int previousFailures;
            lock (_forpostRetrySync)
            {
                previousFailures = _forpostConsecutiveFailures;
                _forpostConsecutiveFailures = 0;
            }

            if (previousFailures > 0)
            {
                ANClient.AppLog.i(Tag, "GETCITY_RETRY_BACKOFF_RESET: source=" + source + ", previousFailures=" + previousFailures + ", buildings=" + buildings);
            }
        }

        private static int CalculateForpostBuildingsDelayMs(int consecutiveFailures)
        {
            if (consecutiveFailures < 0)
            {
                consecutiveFailures = 0;
            }

            long delayMs = ForpostBuildingsDelayMs + ((long)consecutiveFailures * ForpostBuildingsRetryDelayStepMs);
            return delayMs > ForpostBuildingsMaxDelayMs ? ForpostBuildingsMaxDelayMs : (int)delayMs;
        }

        private static string PreviewText(string text)
        {
            if (string.IsNullOrEmpty(text))
            {
                return string.Empty;
            }

            string preview = text.Replace('\r', ' ').Replace('\n', ' ').Replace('\t', ' ').Trim();
            return preview.Length <= 240 ? preview : preview.Substring(0, 240);
        }

        private static string ReadWebExceptionResponsePreview(WebException error)
        {
            try
            {
                var response = error == null ? null : error.Response;
                if (response == null)
                {
                    return string.Empty;
                }

                Stream stream = response.GetResponseStream();
                if (stream == null)
                {
                    return string.Empty;
                }

                using (stream)
                using (var reader = new StreamReader(stream, ANClient.AppVars.Codepage))
                {
                    return PreviewText(reader.ReadToEnd());
                }
            }
            catch (Exception readError)
            {
                return "<response read failed: " + readError.Message + ">";
            }
        }

        private void FetchForpostViaHiddenBrowser()
        {
            if (_browser == null || _browser.IsDisposed || _browser.Parent == null)
            {
                ANClient.AppLog.w(Tag, "GETCITY_WEBBROWSER_SKIPPED: browser unavailable");
                return;
            }

            var hidden = new WebBrowser
                {
                    ScriptErrorsSuppressed = true,
                    Visible = false,
                    Width = 1,
                    Height = 1
                };
            var timer = new System.Windows.Forms.Timer { Interval = 15000 };
            Control parent = _browser.Parent;
            WebBrowserDocumentCompletedEventHandler completed = null;
            EventHandler timedOut = null;
            bool hasCookies;
            int cookieLength;
            string headers = BrowserHeaders(out hasCookies, out cookieLength);
            ANClient.AppLog.i(Tag, "GETCITY_WEBBROWSER_START: url=" + ForpostCityApiUrl + ", cookies=" + hasCookies + ", cookieLength=" + cookieLength);
            completed = delegate(object completedSender, WebBrowserDocumentCompletedEventArgs completedArgs)
                {
                    string completedUrl = completedArgs == null || completedArgs.Url == null ? string.Empty : completedArgs.Url.ToString();
                    string browserUrl = hidden.Url == null ? string.Empty : hidden.Url.ToString();
                    ANClient.AppLog.i(Tag, "GETCITY_WEBBROWSER_DOCUMENT_COMPLETED: completedUrl=" + completedUrl + ", browserUrl=" + browserUrl + ", readyState=" + hidden.ReadyState);
                    if (hidden.ReadyState != WebBrowserReadyState.Complete)
                    {
                        return;
                    }

                    if (completedArgs != null && completedArgs.Url != null && hidden.Url != null && !completedArgs.Url.Equals(hidden.Url))
                    {
                        return;
                    }

                    string text = string.Empty;
                    try
                    {
                        if (hidden.Document != null && hidden.Document.Body != null)
                        {
                            text = hidden.Document.Body.InnerText;
                        }
                    }
                    catch (Exception error)
                    {
                        ANClient.AppLog.w(Tag, "GETCITY_WEBBROWSER_READ_FAILED", error);
                    }

                    ANClient.AppLog.i(Tag, "GETCITY_WEBBROWSER_BODY: length=" + TextLength(text) + ", preview=" + PreviewText(text));

                    timer.Stop();
                    hidden.DocumentCompleted -= completed;
                    timer.Tick -= timedOut;
                    parent.Controls.Remove(hidden);
                    hidden.Dispose();
                    ApplyForpostBrowserResult(text);
                };
            timedOut = delegate
                {
                    ANClient.AppLog.w(Tag, "GETCITY_WEBBROWSER_TIMEOUT: url=" + ForpostCityApiUrl);
                    timer.Stop();
                    hidden.DocumentCompleted -= completed;
                    timer.Tick -= timedOut;
                    parent.Controls.Remove(hidden);
                    hidden.Dispose();
                    _status = BuildStatus(false) + "; WebBrowser fallback timeout";
                    if (HasForpostBuildings(_forpostData))
                    {
                        ANClient.AppLog.w(Tag, "GETCITY_KEEP_LAST_SUCCESS_AFTER_WEBBROWSER_TIMEOUT: buildings=" + ForpostBuildingCount(_forpostData));
                        _status += "; здания сохранены из последней успешной загрузки";
                    }

                    Render();
                };

            hidden.DocumentCompleted += completed;
            timer.Tick += timedOut;
            parent.Controls.Add(hidden);
            timer.Start();
            hidden.Navigate(ForpostCityApiUrl, null, null, headers);
        }

        private static string BrowserHeaders(out bool hasCookies, out int cookieLength)
        {
            var builder = new StringBuilder();
            hasCookies = false;
            cookieLength = 0;
            builder.Append("Cache-Control: no-cache\r\n");
            builder.Append("Pragma: no-cache\r\n");
            builder.Append("DNT: 1\r\n");
            builder.Append("Upgrade-Insecure-Requests: 1\r\n");
            return builder.ToString();
        }

        private void ApplyForpostBrowserResult(string text)
        {
            try
            {
                ForpostData parsed = ParseForpost(text, ForpostCityApiUrl + " via WebBrowser");
                ANClient.AppLog.i(Tag, "GETCITY_WEBBROWSER_PARSED: buildings=" + ForpostBuildingCount(parsed) + ", textLength=" + TextLength(text) + ", preview=" + PreviewText(text));
                if (parsed == null || parsed.Buildings.Count == 0)
                {
                    throw new InvalidOperationException("getcity WebBrowser returned no buildings");
                }

                MarkForpostBuildingsSuccess("webbrowser", ForpostBuildingCount(parsed));
                _forpostData = parsed;
                _status = BuildStatus(false);
            }
            catch (Exception error)
            {
                ANClient.AppLog.w(Tag, "GETCITY_WEBBROWSER_PARSE_FAILED", error);
                _status = BuildStatus(false) + "; WebBrowser fallback parse failed";
                if (HasForpostBuildings(_forpostData))
                {
                    ANClient.AppLog.w(Tag, "GETCITY_KEEP_LAST_SUCCESS_AFTER_WEBBROWSER_FAIL: buildings=" + ForpostBuildingCount(_forpostData));
                    _status += "; здания сохранены из последней успешной загрузки";
                }
            }

            Render();
        }

        private string BuildStatus(bool browserFallbackPending)
        {
            string status;
            if ((_cityHall1 != null || _cityHall2 != null) && _forpostData != null)
            {
                status = "Справочники загружены";
            }
            else if (_cityHall1 != null || _cityHall2 != null)
            {
                status = browserFallbackPending ? "Ратуша загружена, здания догружаются через WebBrowser" : "Ратуша загружена, здания недоступны";
            }
            else if (_forpostData != null)
            {
                status = "Здания загружены, справочник ратуши недоступен";
            }
            else
            {
                status = browserFallbackPending ? "Здания догружаются через WebBrowser" : "Справочники недоступны. Проверьте сеть или service.neverlands.ru";
            }

            if (_recipeDatabase != null)
            {
                status += "; база: " + ShortPath(_recipeDatabase.SourceFilePath);
            }
            else
            {
                status += "; база рецептов недоступна";
            }

            return status;
        }

        private static string ShortPath(string path)
        {
            if (string.IsNullOrEmpty(path))
            {
                return string.Empty;
            }

            string startupPath = Application.StartupPath;
            if (path.StartsWith(startupPath, StringComparison.OrdinalIgnoreCase))
            {
                return path.Substring(startupPath.Length).TrimStart('\\', '/');
            }

            return path;
        }

        private CityHallData ParseCityHall(string raw, int cityId)
        {
            var data = new CityHallData();
            data.Title = cityId == 1 ? "Форпост" : "Октал";
            if (raw == null)
            {
                return data;
            }

            string[] lines = raw.Replace("\r", string.Empty).Split('\n');
            if (lines.Length > 0)
            {
                data.HeaderFields.AddRange(lines[0].Split('|'));
            }

            if (lines.Length > 1)
            {
                string buildingsLine = lines[1].Replace("#", string.Empty).Trim();
                string[] buildings = buildingsLine.Split('@');
                foreach (string item in buildings)
                {
                    CityBuilding building = ParseCityBuilding(item);
                    if (building != null)
                    {
                        data.Buildings.Add(building);
                    }
                }
            }

            if (lines.Length > 2)
            {
                data.Footer = lines[2].Trim();
            }

            return data;
        }

        private CityBuilding ParseCityBuilding(string item)
        {
            if (string.IsNullOrEmpty(item) || item.Trim().Length == 0)
            {
                return null;
            }

            string[] parts = item.Split(',');
            var building = new CityBuilding();
            building.Raw = item.Trim();
            building.Name = Part(parts, 2, Part(parts, 0, "Строение"));
            building.Level = Part(parts, 4, string.Empty);
            building.Progress = Part(parts, 5, string.Empty);
            building.ImageUrl = FindWeaponImageUrl(parts);
            return building;
        }

        private ForpostData ParseForpost(string raw, string sourceUrl)
        {
            if (string.IsNullOrEmpty(raw) || raw.Trim().Length == 0)
            {
                return null;
            }

            var data = new ForpostData();
            data.SourceUrl = sourceUrl;
            string normalized = Regex.Replace(raw.Replace("\r", string.Empty), "(?i)<br\\s*/?>", "\n");
            normalized = Regex.Replace(normalized, "(?i)</p>", "\n");
            normalized = Regex.Replace(normalized, "(?i)</div>", "\n");
            normalized = Regex.Replace(normalized, "<[^>]+>", string.Empty).Replace("&nbsp;", " ");
            foreach (string line in normalized.Split('\n'))
            {
                string trimmed = line.Trim();
                if (trimmed.Length == 0)
                {
                    continue;
                }

                ForpostBuilding building = ParseForpostBuilding(trimmed);
                if (building != null)
                {
                    data.Buildings.Add(building);
                }
            }

            return data;
        }

        private ForpostBuilding ParseForpostBuilding(string raw)
        {
            string[] parts = raw.Split(new[] {','}, 3);
            if (parts.Length < 2)
            {
                return null;
            }

            var building = new ForpostBuilding();
            building.Code = parts[0].Trim();
            building.Name = parts[1].Trim().Length == 0 ? "Здание " + building.Code : parts[1].Trim();
            if (parts.Length < 3 || parts[2].Trim().Length == 0)
            {
                building.Broken = false;
                return building;
            }

            building.Broken = true;
            string[] resources = parts[2].Split('@');
            foreach (string resourceRaw in resources)
            {
                ForpostRepairResource resource = ParseForpostRepairResource(resourceRaw);
                if (resource != null)
                {
                    building.Resources.Add(resource);
                }
            }

            return building;
        }

        private ForpostRepairResource ParseForpostRepairResource(string raw)
        {
            if (string.IsNullOrEmpty(raw) || raw.Trim().Length == 0)
            {
                return null;
            }

            string[] parts = raw.Trim().Split('|');
            if (parts.Length < 3)
            {
                return null;
            }

            var resource = new ForpostRepairResource();
            resource.ResourceId = parts[0].Trim();
            resource.Required = ParseInt(parts[1]);
            resource.Inserted = ParseInt(parts[2]);
            resource.Remaining = Math.Max(0, resource.Required - resource.Inserted);
            return resource;
        }

        private void Render()
        {
            if (_browser == null || _browser.IsDisposed)
            {
                return;
            }

            var builder = new StringBuilder();
            AppendHtmlStart(builder);
            AppendTabs(builder);
            builder.Append("<div class='status'>").Append(Html(_mode == "ratings" ? _professionRatingStatus : _status)).Append("</div>");
            if (_mode == "ratings")
            {
                AppendProfessionRatings(builder);
            }
            else if (_mode == "buildings")
            {
                AppendForpostBuildings(builder);
            }
            else if (_mode == "tables")
            {
                AppendTables(builder);
            }
            else
            {
                AppendCityHall(builder);
            }

            builder.Append("</body></html>");
            _browser.DocumentText = builder.ToString();
        }

        private static void AppendHtmlStart(StringBuilder builder)
        {
            builder.Append("<!DOCTYPE html><html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>")
                .Append("<title>Постройки и Таблицы</title>")
                .Append("<style>")
                .Append("body{font-family:Segoe UI,Tahoma,Arial,sans-serif;background:#ebf1ff;color:#1e232d;margin:0;padding:14px;font-size:14px;}")
                .Append("a{color:#2a3992;text-decoration:none}.tabs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px}")
                .Append(".tab,.button{display:inline-block;padding:10px 16px;border-radius:22px;border:1px solid #b7c3e5;background:#f7f9ff;font-weight:700;color:#2a3992}")
                .Append(".active,.primary{background:#4a5bca;border-color:#2a3992;color:white}.status,.card{background:white;border:1px solid #d8e1f5;border-radius:18px;padding:14px 16px;margin:8px 0 12px;box-shadow:0 2px 8px rgba(40,55,100,.08)}")
                .Append(".hero{background:#203060;color:white;border-color:#203060}.muted{color:#5d6678}.item{display:flex;gap:14px;align-items:center}.imagebox{width:76px;min-width:76px;height:76px;border-radius:16px;background:#f0f4ff;border:1px solid #d6e0f8;display:flex;align-items:center;justify-content:center}.imagebox img{max-width:62px;max-height:62px}")
                .Append(".title{font-size:16px;font-weight:700}.bigtitle{font-size:20px;font-weight:700}.green{color:#1f8e44}.red{color:#c62828}.field{background:#f8faff;border:1px solid #e2e9f9;border-radius:14px;padding:10px 12px;margin:4px 0 7px}.sectionbar{white-space:normal;margin:6px 0 12px}.sectionbar a{margin:0 6px 8px 0}.search{display:flex;gap:8px;margin:8px 0 12px}.search input{flex:1;border:1px solid #cbd6f1;border-radius:18px;padding:10px 14px;font-size:14px}.linkcard{border-color:#7685dc;cursor:pointer}.resource{display:flex;gap:10px;align-items:center}.resource img{max-width:40px;max-height:40px}.badge{padding:4px 10px;border-radius:14px;font-weight:700;float:right}")
                .Append(".ratingrow{display:flex;gap:12px;align-items:center}.ratingrank{width:52px;min-width:52px;text-align:center;font-weight:800;font-size:18px;color:#203060}.ratingicons{width:48px;min-width:48px}.ratingicons img{max-width:16px;max-height:16px;margin-right:4px}.ratingname{flex:1}.ratingpoints{min-width:96px;text-align:right;font-weight:800;color:#203060}.smallinfo{width:13px;height:13px;margin-left:6px;vertical-align:middle}")
                .Append("</style>")
                .Append("<script>function q(){var e=document.getElementById('q');location.href='anclient://tables?search='+encodeURIComponent(e?e.value:'');return false;}</script>")
                .Append("</head><body>");
        }

        private void AppendTabs(StringBuilder builder)
        {
            builder.Append("<div class='tabs'>")
                .Append(TabLink("Рейтинги Проф", "anclient://ratings", _mode == "ratings"))
                .Append(TabLink("Ратуша", "anclient://forpost?tab=cityhall", _mode == "cityhall"))
                .Append(TabLink("Здания", "anclient://forpost?tab=buildings", _mode == "buildings"))
                .Append(TabLink("Таблицы", "anclient://tables", _mode == "tables"))
                .Append("<a class='tab' href='anclient://refresh'>Обновить</a>")
                .Append("</div>");
        }

        private static string TabLink(string title, string href, bool active)
        {
            return "<a class='tab" + (active ? " active" : string.Empty) + "' href='" + href + "'>" + Html(title) + "</a>";
        }

        private void AppendProfessionRatings(StringBuilder builder)
        {
            builder.Append("<div class='sectionbar'>");
            foreach (ProfessionRatingRepository.Category category in ProfessionRatingRepository.GetCategories())
            {
                builder.Append("<a class='tab").Append(category.Id == _selectedProfessionRatingId ? " active" : string.Empty)
                    .Append("' href='anclient://ratings?category=").Append(category.Id).Append("'>")
                    .Append(Html(category.Title)).Append("</a>");
            }

            builder.Append("</div>")
                .Append("<div class='tabs'><a class='button primary' href='anclient://ratings?category=").Append(_selectedProfessionRatingId).Append("&refresh=1'>Обновить рейтинг</a></div>");

            ProfessionRatingRepository.Category selectedCategory = ProfessionRatingRepository.FindCategory(_selectedProfessionRatingId);
            string title = selectedCategory == null ? "Рейтинги Проф" : selectedCategory.Title;
            if (_selectedProfessionRating == null || _selectedProfessionRating.Category.Id != _selectedProfessionRatingId)
            {
                AppendCard(builder, title, "Данные weekly_" + _selectedProfessionRatingId + ".txt пока загружаются или недоступны.");
                return;
            }

            builder.Append("<div class='card hero'><div class='bigtitle'>").Append(Html(title)).Append("</div><div>")
                .Append("Записей: ").Append(_selectedProfessionRating.Entries.Count)
                .Append(" | Загружено: ").Append(Html(_selectedProfessionRating.LoadedAt.ToString("dd.MM.yyyy HH:mm:ss", ANClient.AppVars.Culture)))
                .Append(" | Источник: ").Append(Html(_selectedProfessionRating.SourceUrl)).Append("</div></div>");
            if (_selectedProfessionRating.Entries.Count == 0)
            {
                AppendCard(builder, "Список пуст", "В weekly-файле нет строк рейтинга.");
                return;
            }

            foreach (ProfessionRatingRepository.RatingEntry entry in _selectedProfessionRating.Entries)
            {
                AppendProfessionRatingRow(builder, entry);
            }
        }

        private static void AppendProfessionRatingRow(StringBuilder builder, ProfessionRatingRepository.RatingEntry entry)
        {
            string pinfoHref = "anclient://pinfo?nick=" + Url(entry.Nick);
            string totemIcon = ProfessionRatingRepository.BuildTotemIconUrl(entry.ClanTotem);
            string clanIcon = ProfessionRatingRepository.BuildClanIconUrl(entry.ClanIco);
            builder.Append("<a class='card ratingrow linkcard' href='").Append(pinfoHref).Append("'>")
                .Append("<div class='ratingrank'>#").Append(entry.Rank).Append("</div>")
                .Append("<div class='ratingicons'>");
            if (totemIcon.Length > 0)
            {
                builder.Append("<img src='").Append(HtmlAttr(totemIcon)).Append("'>");
            }

            if (clanIcon.Length > 0)
            {
                builder.Append("<img src='").Append(HtmlAttr(clanIcon)).Append("'>");
            }

            builder.Append("</div><div class='ratingname'><div class='title'>")
                .Append(Html(entry.Nick)).Append(" [").Append(entry.Level).Append("]")
                .Append("<img class='smallinfo' src='").Append(HtmlAttr(ProfessionRatingRepository.GetInfoIconUrl())).Append("'>")
                .Append("</div><div class='muted'>Открыть информацию о персонаже</div></div>")
                .Append("<div class='ratingpoints'>").Append(entry.Rate.ToString(CultureInfo.InvariantCulture)).Append("</div></a>");
        }

        private void AppendCityHall(StringBuilder builder)
        {
            builder.Append("<div class='tabs'>")
                .Append(TabLink("Форпост", "anclient://forpost?tab=cityhall&city=1", _selectedCityHallId == 1))
                .Append(TabLink("Октал", "anclient://forpost?tab=cityhall&city=2", _selectedCityHallId == 2))
                .Append("</div>");
            CityHallData data = _selectedCityHallId == 1 ? _cityHall1 : _cityHall2;
            if (data == null)
            {
                AppendCard(builder, "Ратуша", "Данные cityhall_1.txt/cityhall_2.txt пока недоступны.");
                return;
            }

            var header = new StringBuilder();
            for (int i = 0; i < data.HeaderFields.Count; i++)
            {
                string value = data.HeaderFields[i] == null ? string.Empty : data.HeaderFields[i].Trim();
                if (value.Length > 0)
                {
                    header.Append(Html(CityHallHeaderLabel(i))).Append(": ").Append(Html(value)).Append("<br>");
                }
            }

            if (!string.IsNullOrEmpty(data.Footer))
            {
                header.Append("<br>Дополнительно: ").Append(Html(data.Footer));
            }

            builder.Append("<div class='card'><div class='bigtitle'>").Append(Html(data.Title)).Append("</div><div class='muted'>")
                .Append(header.Length == 0 ? "Общие сведения отсутствуют" : header.ToString()).Append("</div></div>");
            builder.Append("<h2>Строения</h2>");
            if (data.Buildings.Count == 0)
            {
                AppendCard(builder, "Список пуст", "В ответе нет записей строений.");
                return;
            }

            List<CityBuilding> buildings = SortedCityHallBuildings(data.Buildings);
            foreach (CityBuilding building in buildings)
            {
                AppendCityBuilding(builder, building);
            }
        }

        private void AppendCityBuilding(StringBuilder builder, CityBuilding building)
        {
            string recipeImageFile = FindRecipeImageFile(building);
            bool hasRecipe = recipeImageFile.Length > 0;
            string href = hasRecipe ? " href='anclient://item?image=" + Url(recipeImageFile) + "&name=" + Url(building.Name) + "'" : string.Empty;
            builder.Append("<a class='card item").Append(hasRecipe ? " linkcard" : string.Empty).Append("'").Append(href).Append(">");
            if (!string.IsNullOrEmpty(building.ImageUrl))
            {
                builder.Append("<div class='imagebox'><img src='").Append(HtmlAttr(building.ImageUrl)).Append("'></div>");
            }

            builder.Append("<div><div class='title ").Append(BuildingTitleClass(building)).Append("'>").Append(Html(building.Name)).Append("</div>")
                .Append("<div class='muted'>Состояние: ").Append(Html(EmptyDash(building.Level))).Append("/").Append(Html(EmptyDash(building.Progress))).Append("<br>");
            if (hasRecipe)
            {
                builder.Append("Рецепт: есть в разделе Таблицы<br>");
            }

            builder.Append("Исходная запись: ").Append(Html(building.Raw)).Append("</div>");
            if (hasRecipe)
            {
                builder.Append("<div style='margin-top:7px;font-weight:700'>Открыть рецепт в Таблицах</div>");
            }

            builder.Append("</div></a>");
        }

        private void AppendForpostBuildings(StringBuilder builder)
        {
            if (_forpostData == null || _forpostData.Buildings.Count == 0)
            {
                builder.Append("<div class='card hero'><div class='bigtitle'>Здания</div><div>API форпоста недоступен. Нажмите обновить; при HTTP 535/536 клиент пробует скрытый WebBrowser с текущими cookie.</div></div>");
                return;
            }

            builder.Append("<div class='card hero'><div class='bigtitle'>Здания форпоста</div><div>Источник: ").Append(Html(_forpostData.SourceUrl)).Append("</div></div>");
            foreach (ForpostBuilding building in _forpostData.Buildings)
            {
                builder.Append("<div class='card'><span class='badge' style='background:")
                    .Append(building.Broken ? "#ffebee;color:#b71c1c;border:1px solid #ffcdd2" : "#e8f5e9;color:#1b5e20;border:1px solid #c8e6c9")
                    .Append("'>").Append(building.Broken ? "Поломано" : "Целое").Append("</span>")
                    .Append("<div class='title ").Append(building.Broken ? "red" : "green").Append("'>").Append(Html(building.Name)).Append("</div>")
                    .Append("<div class='muted'>Код: ").Append(Html(EmptyDash(building.Code))).Append("</div>");
                if (building.Broken)
                {
                    if (building.Resources.Count == 0)
                    {
                        builder.Append("<div class='field'>Ресурсы ремонта не указаны</div>");
                    }
                    else
                    {
                        foreach (ForpostRepairResource resource in building.Resources)
                        {
                            builder.Append("<div class='field'>Ресурс #").Append(Html(EmptyDash(resource.ResourceId)))
                                .Append(": нужно ").Append(resource.Required)
                                .Append(", вложено ").Append(resource.Inserted)
                                .Append(", осталось ").Append(resource.Remaining).Append("</div>");
                        }
                    }
                }

                builder.Append("</div>");
            }
        }

        private void AppendTables(StringBuilder builder)
        {
            if (_recipeDatabase == null)
            {
                AppendCard(builder, "Таблицы", "База рецептов не загружена.");
                return;
            }

            if (string.IsNullOrEmpty(_selectedSectionKey) || _recipeDatabase.FindSection(_selectedSectionKey) == null)
            {
                _selectedSectionKey = _recipeDatabase.GetFirstSectionKey();
            }

            if (_selectedItem != null)
            {
                AppendItemDetail(builder, _selectedItem);
                return;
            }

            builder.Append("<div class='sectionbar'>");
            foreach (RecipeDatabase.RecipeSection section in _recipeDatabase.GetSections())
            {
                builder.Append("<a class='tab").Append(section.Key.Equals(_selectedSectionKey, StringComparison.OrdinalIgnoreCase) ? " active" : string.Empty)
                    .Append("' href='anclient://tables?section=").Append(Url(section.Key)).Append("'>")
                    .Append(Html(section.Title)).Append(" (").Append(section.Items.Count).Append(")</a>");
            }

            builder.Append("</div>")
                .Append("<form class='search' onsubmit='return q();'><input id='q' value='").Append(HtmlAttr(_searchQuery)).Append("' placeholder='Поиск по названию, gif или ресурсам'><button class='button primary' type='submit'>Поиск</button></form>");

            RecipeDatabase.RecipeSection selectedSection = _recipeDatabase.FindSection(_selectedSectionKey);
            List<RecipeDatabase.RecipeItem> baseItems = selectedSection == null ? new List<RecipeDatabase.RecipeItem>() : new List<RecipeDatabase.RecipeItem>(selectedSection.Items);
            List<RecipeDatabase.RecipeItem> items = FilterItems(baseItems);
            string title = selectedSection == null ? "Раздел" : selectedSection.Title;
            builder.Append("<div class='card hero'><div class='bigtitle'>").Append(Html(title)).Append("</div><div>")
                .Append(items.Count).Append(" из ").Append(baseItems.Count).Append(" | база: ")
                .Append(Html(ShortPath(_recipeDatabase.ConsolidatedFilePath))).Append("</div></div>");
            if (items.Count == 0)
            {
                AppendCard(builder, "Ничего не найдено", "Измените раздел или строку поиска.");
                return;
            }

            foreach (RecipeDatabase.RecipeItem item in items)
            {
                AppendItemCard(builder, item);
            }
        }

        private void AppendItemCard(StringBuilder builder, RecipeDatabase.RecipeItem item)
        {
            builder.Append("<a class='card item linkcard' href='anclient://item?image=").Append(Url(item.ItemImageFile)).Append("&name=").Append(Url(item.Name)).Append("'>")
                .Append("<div class='imagebox'><img src='").Append(HtmlAttr(item.ItemImageUrl)).Append("'></div>")
                .Append("<div><div class='title'>").Append(Html(item.Name)).Append("</div>")
                .Append("<div class='muted'>").Append(Html(item.SectionTitle)).Append(" | ").Append(Html(item.ItemImageFile)).Append("</div>")
                .Append("<div style='margin-top:5px'>").Append(Html(Summary(item))).Append("</div></div></a>");
        }

        private void AppendItemDetail(StringBuilder builder, RecipeDatabase.RecipeItem item)
        {
            builder.Append("<div class='tabs'><a class='tab' href='anclient://back'>Назад к списку</a></div>")
                .Append("<div class='card hero item'><div class='imagebox'><img src='").Append(HtmlAttr(item.ItemImageUrl)).Append("'></div><div>")
                .Append("<div class='bigtitle'>").Append(Html(item.Name)).Append("</div>")
                .Append("<div>").Append(Html(item.SectionTitle)).Append("<br>").Append(Html(item.ItemImageFile)).Append("<br>Источник: ").Append(Html(item.SourceAsset)).Append("</div></div></div>")
                .Append("<h2>Ресурсы и инструменты</h2>");
            if (item.Resources.Count == 0)
            {
                AppendCard(builder, "Ресурсы", "В исходной строке ресурсы не распознаны как изображения.");
            }
            else
            {
                foreach (RecipeDatabase.RecipeResource resource in item.Resources)
                {
                    AppendResourceRow(builder, resource);
                }
            }

            builder.Append("<h2>Все поля исходной таблицы</h2>");
            foreach (RecipeDatabase.RecipeField field in item.Fields)
            {
                builder.Append("<div class='field'><b>").Append(Html(field.Name)).Append("</b><br>").Append(Html(field.Value)).Append("</div>");
            }
        }

        private void AppendResourceRow(StringBuilder builder, RecipeDatabase.RecipeResource resource)
        {
            RecipeDatabase.RecipeItem linkedItem = FindLinkedResourceItem(resource);
            bool linked = linkedItem != null;
            string label = string.IsNullOrEmpty(resource.Label) ? resource.ImageFile : resource.Label;
            if (linked)
            {
                builder.Append("<a class='card resource linkcard' href='anclient://item?image=").Append(Url(resource.ImageFile)).Append("&name=").Append(Url(label)).Append("&push=1'>");
            }
            else
            {
                builder.Append("<div class='card resource'>");
            }

            builder.Append("<img src='").Append(HtmlAttr(resource.ImageUrl)).Append("'><div><div>").Append(Html(label)).Append("</div>");
            if (linked)
            {
                builder.Append("<div style='font-weight:700;margin-top:3px'>Открыть: ").Append(Html(linkedItem.Name)).Append("</div>");
            }

            builder.Append("</div>").Append(linked ? "</a>" : "</div>");
        }

        private RecipeDatabase.RecipeItem FindLinkedResourceItem(RecipeDatabase.RecipeResource resource)
        {
            if (_recipeDatabase == null || resource == null)
            {
                return null;
            }

            string resourceUrl = resource.ImageUrl == null ? string.Empty : resource.ImageUrl.ToLowerInvariant();
            if (resourceUrl.IndexOf("/tools/", StringComparison.Ordinal) >= 0 || resourceUrl.IndexOf("tools/", StringComparison.Ordinal) >= 0)
            {
                return null;
            }

            List<RecipeDatabase.RecipeItem> matches = _recipeDatabase.FindByImageFile(resource.ImageFile);
            if (matches.Count == 0)
            {
                return null;
            }

            RecipeDatabase.RecipeItem item = ChooseItemByName(matches, resource.Label);
            return item ?? matches[0];
        }

        private List<RecipeDatabase.RecipeItem> FilterItems(List<RecipeDatabase.RecipeItem> items)
        {
            string query = _searchQuery == null ? string.Empty : _searchQuery.Trim().ToLowerInvariant();
            if (query.Length == 0)
            {
                return items;
            }

            var result = new List<RecipeDatabase.RecipeItem>();
            foreach (RecipeDatabase.RecipeItem item in items)
            {
                if (Matches(item, query))
                {
                    result.Add(item);
                }
            }

            return result;
        }

        private static bool Matches(RecipeDatabase.RecipeItem item, string query)
        {
            if (Contains(item.Name, query) || Contains(item.ItemImageFile, query) || Contains(item.SectionTitle, query))
            {
                return true;
            }

            foreach (RecipeDatabase.RecipeField field in item.Fields)
            {
                if (Contains(field.Name, query) || Contains(field.Value, query))
                {
                    return true;
                }
            }

            foreach (RecipeDatabase.RecipeResource resource in item.Resources)
            {
                if (Contains(resource.ImageFile, query) || Contains(resource.Label, query))
                {
                    return true;
                }
            }

            return false;
        }

        private static bool Contains(string value, string query)
        {
            return value != null && value.ToLowerInvariant().IndexOf(query, StringComparison.Ordinal) >= 0;
        }

        private static RecipeDatabase.RecipeItem ChooseItemByName(List<RecipeDatabase.RecipeItem> matches, string requestedNameRaw)
        {
            if (matches.Count == 1)
            {
                return matches[0];
            }

            string requestedName = NormalizeForCompare(requestedNameRaw);
            if (requestedName.Length > 0)
            {
                foreach (RecipeDatabase.RecipeItem item in matches)
                {
                    if (NormalizeForCompare(item.Name).Equals(requestedName, StringComparison.Ordinal))
                    {
                        return item;
                    }
                }

                foreach (RecipeDatabase.RecipeItem item in matches)
                {
                    string itemName = NormalizeForCompare(item.Name);
                    if (itemName.IndexOf(requestedName, StringComparison.Ordinal) >= 0 || requestedName.IndexOf(itemName, StringComparison.Ordinal) >= 0)
                    {
                        return item;
                    }
                }

                string requestedKey = NormalizeNameKey(requestedNameRaw);
                if (requestedKey.Length > 0)
                {
                    foreach (RecipeDatabase.RecipeItem item in matches)
                    {
                        string itemKey = NormalizeNameKey(item.Name);
                        if (itemKey.Equals(requestedKey, StringComparison.Ordinal)
                            || itemKey.IndexOf(requestedKey, StringComparison.Ordinal) >= 0
                            || requestedKey.IndexOf(itemKey, StringComparison.Ordinal) >= 0)
                        {
                            return item;
                        }
                    }
                }
            }

            return null;
        }

        private static string NormalizeForCompare(string value)
        {
            if (value == null)
            {
                return string.Empty;
            }

            return Regex.Replace(value.Replace('\u00A0', ' '), "\\s+", " ").Trim().ToLowerInvariant();
        }

        private static string NormalizeNameKey(string value)
        {
            string normalized = NormalizeForCompare(value);
            int colonIndex = normalized.IndexOf(':');
            if (colonIndex > 0)
            {
                normalized = normalized.Substring(0, colonIndex);
            }

            normalized = Regex.Replace(normalized, "\\([^)]*\\)", " ");
            normalized = Regex.Replace(normalized, "[0-9.,:;\\[\\]{}<>/\\\\|+=_-]+", " ");
            return Regex.Replace(normalized, "\\s+", " ").Trim();
        }

        private static string Summary(RecipeDatabase.RecipeItem item)
        {
            var builder = new StringBuilder();
            int added = 0;
            foreach (RecipeDatabase.RecipeField field in item.Fields)
            {
                string key = field.Name.ToLowerInvariant();
                if (key.IndexOf("умение", StringComparison.Ordinal) >= 0
                    || key.IndexOf("уров", StringComparison.Ordinal) >= 0
                    || key.IndexOf("гос", StringComparison.Ordinal) >= 0
                    || key.IndexOf("время", StringComparison.Ordinal) >= 0
                    || key.IndexOf("эффект", StringComparison.Ordinal) >= 0
                    || key.IndexOf("долговеч", StringComparison.Ordinal) >= 0)
                {
                    if (builder.Length > 0)
                    {
                        builder.Append("; ");
                    }

                    builder.Append(field.Name).Append(": ").Append(field.Value);
                    added++;
                    if (added >= 2)
                    {
                        break;
                    }
                }
            }

            if (builder.Length == 0)
            {
                builder.Append("Ресурсов/инструментов: ").Append(item.Resources.Count);
            }

            return builder.ToString();
        }

        private string FindRecipeImageFile(CityBuilding building)
        {
            if (building == null || _recipeDatabase == null)
            {
                return string.Empty;
            }

            string imageFile = RecipeDatabase.NormalizeImageFile(building.ImageUrl);
            return _recipeDatabase.HasImageFile(imageFile) ? imageFile : string.Empty;
        }

        private static List<CityBuilding> SortedCityHallBuildings(List<CityBuilding> buildings)
        {
            var sorted = new List<CityBuilding>(buildings);
            sorted.Sort(delegate(CityBuilding left, CityBuilding right)
                {
                    return CityHallSortWeight(left).CompareTo(CityHallSortWeight(right));
                });
            return sorted;
        }

        private static int CityHallSortWeight(CityBuilding building)
        {
            float level;
            float progress;
            if (!TryParseFloat(building.Level, out level) || !TryParseFloat(building.Progress, out progress))
            {
                return 2;
            }

            int compare = level.CompareTo(progress);
            if (compare < 0)
            {
                return 0;
            }

            return compare == 0 ? 1 : 2;
        }

        private static string BuildingTitleClass(CityBuilding building)
        {
            float level;
            float progress;
            if (!TryParseFloat(building.Level, out level) || !TryParseFloat(building.Progress, out progress))
            {
                return string.Empty;
            }

            int compare = level.CompareTo(progress);
            if (compare == 0)
            {
                return "green";
            }

            return compare < 0 ? "red" : string.Empty;
        }

        private static bool TryParseFloat(string value, out float result)
        {
            return float.TryParse((value ?? string.Empty).Trim().Replace(',', '.'), System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out result);
        }

        private static int ParseInt(string value)
        {
            int result;
            return int.TryParse(value == null ? "0" : value.Trim(), out result) ? result : 0;
        }

        private static string Part(string[] parts, int index, string fallback)
        {
            if (parts == null || index < 0 || index >= parts.Length || parts[index].Trim().Length == 0)
            {
                return fallback;
            }

            return parts[index].Trim();
        }

        private static string FindWeaponImageUrl(string[] parts)
        {
            if (parts == null)
            {
                return string.Empty;
            }

            foreach (string part in parts)
            {
                string value = part == null ? string.Empty : part.Trim();
                int gifIndex = value.ToLowerInvariant().IndexOf(".gif", StringComparison.Ordinal);
                if (gifIndex < 0)
                {
                    continue;
                }

                int start = gifIndex;
                while (start > 0)
                {
                    char ch = value[start - 1];
                    if (char.IsLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '/')
                    {
                        start--;
                        continue;
                    }

                    break;
                }

                string fileName = value.Substring(start, gifIndex + 4 - start);
                int slashIndex = fileName.LastIndexOf('/');
                if (slashIndex >= 0 && slashIndex + 1 < fileName.Length)
                {
                    fileName = fileName.Substring(slashIndex + 1);
                }

                if (fileName.Length > 0)
                {
                    return ImageWeaponBaseUrl + fileName;
                }
            }

            return string.Empty;
        }

        private static string CityHallHeaderLabel(int index)
        {
            switch (index)
            {
                case 0:
                    return "Город";
                case 1:
                    return "Начало";
                case 2:
                    return "Конец";
                default:
                    return "Поле " + index;
            }
        }

        private static string EmptyDash(string value)
        {
            return string.IsNullOrEmpty(value) || value.Trim().Length == 0 ? "-" : value.Trim();
        }

        private static void AppendCard(StringBuilder builder, string title, string body)
        {
            builder.Append("<div class='card'><div class='title'>").Append(Html(title)).Append("</div><div class='muted'>").Append(Html(body)).Append("</div></div>");
        }

        private static string Html(string value)
        {
            return HttpUtility.HtmlEncode(value ?? string.Empty);
        }

        private static string HtmlAttr(string value)
        {
            return HttpUtility.HtmlAttributeEncode(value ?? string.Empty);
        }

        private static string Url(string value)
        {
            return HttpUtility.UrlEncode(value ?? string.Empty, Encoding.UTF8);
        }

        private void SafeBeginInvoke(MethodInvoker action)
        {
            if (_browser == null || _browser.IsDisposed)
            {
                return;
            }

            if (_browser.InvokeRequired)
            {
                _browser.BeginInvoke(action);
            }
            else
            {
                action();
            }
        }

        private sealed class CityHallData
        {
            internal string Title;
            internal string Footer;
            internal readonly List<string> HeaderFields = new List<string>();
            internal readonly List<CityBuilding> Buildings = new List<CityBuilding>();
        }

        private sealed class CityBuilding
        {
            internal string Name;
            internal string Level;
            internal string Progress;
            internal string ImageUrl;
            internal string Raw;
        }

        private sealed class ForpostData
        {
            internal string SourceUrl;
            internal readonly List<ForpostBuilding> Buildings = new List<ForpostBuilding>();
        }

        private sealed class ForpostBuilding
        {
            internal string Code;
            internal string Name;
            internal bool Broken;
            internal readonly List<ForpostRepairResource> Resources = new List<ForpostRepairResource>();
        }

        private sealed class ForpostRepairResource
        {
            internal string ResourceId;
            internal int Required;
            internal int Inserted;
            internal int Remaining;
        }

        private sealed class LoadProfessionRatingState
        {
            internal LoadProfessionRatingState(int categoryId, bool forceRefresh)
            {
                CategoryId = categoryId;
                ForceRefresh = forceRefresh;
            }

            internal int CategoryId { get; private set; }

            internal bool ForceRefresh { get; private set; }
        }
    }
}
