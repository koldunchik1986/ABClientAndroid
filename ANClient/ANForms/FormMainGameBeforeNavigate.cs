namespace ANClient.ANForms
{
    using System;

    internal sealed partial class FormMain
    {
        private static bool GameBeforeNavigate(string address)
        {
            var request = new Uri(address).PathAndQuery;
            /*
            if (request.StartsWith("/anc-moveto:", StringComparison.OrdinalIgnoreCase))
            {
                MessageBox.Show(
                    "Навигатор в этой сборке пока не работает",
                    HelperVersions.ProductAndVersionString(),
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning);
                return true;
            }
             */ 

            return false;
        }
    }
}
