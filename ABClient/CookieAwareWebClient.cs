using System;
using System.Net;

namespace ABClient
{
    public class CookieAwareWebClient : WebClient
    {
        private readonly CookieContainer _cookieContainer = new CookieContainer();

        public void SetCookies(Uri address, string cookieHeader)
        {
            if (address == null || string.IsNullOrEmpty(cookieHeader))
            {
                return;
            }

            var cookieParts = cookieHeader.Split(new[] { ';' }, StringSplitOptions.RemoveEmptyEntries);
            foreach (var cookiePart in cookieParts)
            {
                var pair = cookiePart.Trim();
                var separator = pair.IndexOf('=');
                if (separator <= 0)
                {
                    continue;
                }

                var name = pair.Substring(0, separator).Trim();
                var value = pair.Substring(separator + 1).Trim();
                if (string.IsNullOrEmpty(name))
                {
                    continue;
                }

                try
                {
                    _cookieContainer.Add(address, new Cookie(name, value));
                }
                catch (CookieException)
                {
                }
            }
        }

        protected override WebRequest GetWebRequest(Uri address)
        {
            var basewr = base.GetWebRequest(address);
            var request = basewr as HttpWebRequest;
            if (request != null)
            {
                var wr = request;
                wr.CookieContainer = _cookieContainer;
            }

            return basewr;
        }

        protected override WebResponse GetWebResponse(WebRequest request)
        {
            WebResponse basewr = null;
            try
            {
                basewr = base.GetWebResponse(request);
                var responce = basewr as HttpWebResponse;
                if (responce != null && responce.Cookies != null)
                {
                    _cookieContainer.Add(responce.Cookies);
                }
            }
            catch (WebException ex)
            {
                if (ex.Response == null)
                {
                    throw;
                }

                basewr = ex.Response;
                var responce = basewr as HttpWebResponse;
                if (responce != null && responce.Cookies != null)
                {
                    _cookieContainer.Add(responce.Cookies);
                }
            }

            return basewr;
        }
    }
}
