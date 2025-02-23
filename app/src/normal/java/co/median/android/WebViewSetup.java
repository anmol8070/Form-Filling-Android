package co.median.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Message;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.Map;

import co.median.median_core.AppConfig;
import co.median.median_core.GNLog;
import co.median.median_core.GoNativeWebviewInterface;

/**
 * Created by weiyin on 9/8/15.
 */
public class WebViewSetup {
    private static final String TAG = WebViewSetup.class.getName();

    @SuppressLint("JavascriptInterface")
    public static void setupWebviewForActivity(GoNativeWebviewInterface webview, MainActivity activity) {
        if (!(webview instanceof LeanWebView)) {
            GNLog.getInstance().logError(TAG, "Expected webview to be of class LeanWebView and not " + webview.getClass().getName());
            return;
        }

        LeanWebView wv = (LeanWebView)webview;

        setupWebview(wv, activity);

        UrlNavigation urlNavigation = new UrlNavigation(activity);
        urlNavigation.setCurrentWebviewUrl(webview.getUrl());

        wv.setWebChromeClient(new GoNativeWebChromeClient(activity, urlNavigation));
        wv.setWebViewClient(new GoNativeWebviewClient(activity, urlNavigation));

        FileDownloader fileDownloader = activity.getFileDownloader();
        if (fileDownloader != null) {
            wv.setDownloadListener(fileDownloader);
            fileDownloader.setUrlNavigation(urlNavigation);
        }

        wv.removeJavascriptInterface("median_status_checker");
        wv.addJavascriptInterface(activity.getStatusCheckerBridge(), "median_status_checker");

        wv.removeJavascriptInterface("gonative_file_writer_sharer");
        wv.addJavascriptInterface(activity.getFileWriterSharer().getJavascriptBridge(), "gonative_file_writer_sharer");

        wv.removeJavascriptInterface("JSBridge");
        wv.addJavascriptInterface(new WebViewInterface(activity::handleMessage), "JSBridge");

        ((GoNativeApplication) activity.getApplication()).mBridge.onWebviewSetUp(activity, wv);

        if (activity.getIntent().getBooleanExtra(MainActivity.EXTRA_WEBVIEW_WINDOW_OPEN, false)) {
            // send to other webview
            Message resultMsg = ((GoNativeApplication)activity.getApplication()).getWebviewMessage();
            if (resultMsg != null) {
                WebView.WebViewTransport transport = (WebView.WebViewTransport)resultMsg.obj;
                if (transport != null) {
                    transport.setWebView(wv);
                    resultMsg.sendToTarget();
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("SetJavaScriptEnabled")
    public static void setupWebview(GoNativeWebviewInterface webview, Context context) {
        if (!(webview instanceof LeanWebView)) {
            GNLog.getInstance().logError(TAG, "Expected webview to be of class LeanWebView and not " + webview.getClass().getName());
            return;
        }

        AppConfig appConfig = AppConfig.getInstance(context);

        LeanWebView wv = (LeanWebView)webview;
        WebSettings webSettings = wv.getSettings();

        if (AppConfig.getInstance(context).allowZoom) {
            webSettings.setBuiltInZoomControls(true);
        }
        else {
            webSettings.setBuiltInZoomControls(false);
        }

        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        // font size bug fix, see https://stackoverflow.com/questions/41179357/android-webview-rem-units-scale-way-to-large-for-boxes
        webSettings.setMinimumFontSize(1);
        webSettings.setMinimumLogicalFontSize(1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        }

        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(appConfig.cacheMode.webSettingsCacheMode());

        webSettings.setDatabaseEnabled(true);

        webSettings.setSaveFormData(false);
        webSettings.setSavePassword(false);
        if (!TextUtils.isEmpty(appConfig.userAgent)) {
            webSettings.setUserAgentString(appConfig.userAgent);
        } else {
            // create a userAgent with the device userAgent plus additional string
            webSettings.setUserAgentString(webview.getDefaultUserAgent() + " " + appConfig.userAgentAdd);
        }

        if (appConfig.numWindows == 1) {
            webSettings.setSupportMultipleWindows(false);
        } else {
            webSettings.setSupportMultipleWindows(appConfig.enableWindowOpen);
        }

        webSettings.setGeolocationEnabled(appConfig.permissions.usesGeolocation());
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        if (appConfig.webviewTextZoom > 0) {
            webSettings.setTextZoom(appConfig.webviewTextZoom);
        }
    }

    public static void setupWebviewGlobals(Context context) {
        // WebView debugging
        if(!AppConfig.getInstance(context).geckoViewEnabled) {
            Map<String,Object> installation = Installation.getInfo(context);
            String dist = (String)installation.get("distribution");
            if (dist != null && (dist.equals("debug") || dist.equals("adhoc"))) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
    }

    public static void removeCallbacks(LeanWebView webview) {
        webview.setWebViewClient(null);
        webview.setWebChromeClient(null);
    }
}
