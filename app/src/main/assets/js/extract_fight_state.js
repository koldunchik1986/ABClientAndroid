(function() {
    if (window.AndroidBridge && window.AndroidBridge.processFightHtml) {
        window.AndroidBridge.processFightHtml(document.documentElement.innerHTML);
    }
})();
