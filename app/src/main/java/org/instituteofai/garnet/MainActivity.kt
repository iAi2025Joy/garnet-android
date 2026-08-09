
package org.instituteofai.garnet

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.instituteofai.garnet.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Held onto between onPermissionRequest (the WebView asking, on
    // behalf of the page's own getUserMedia() call, for mic access) and
    // the actual Android runtime permission result -- a real two-step
    // dance Android requires: the OS-level RECORD_AUDIO permission has
    // to be granted to the APP first, and only then can the WebView, in
    // turn, grant the specific in-page request. Getting this wrong is
    // the single most common way a WebView-wrapped Live Chat feature
    // silently fails to access the microphone at all.
    private var pendingPermissionRequest: PermissionRequest? = null

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingPermissionRequest
        pendingPermissionRequest = null
        if (request == null) return@registerForActivityResult
        if (granted) {
            request.grant(request.resources)
        } else {
            request.deny()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback == null) return@registerForActivityResult
        val data = result.data
        val uris: Array<Uri> = when {
            result.resultCode != RESULT_OK || data == null -> emptyArray()
            data.clipData != null -> {
                val clipData = data.clipData!!
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> emptyArray()
        }
        callback.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupSwipeRefresh()
        binding.retryButton.setOnClickListener { loadGarnet() }

        if (hasInternetConnection()) {
            loadGarnet()
        } else {
            showOfflineState()
        }
    }

    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // required for localStorage -- every saved preference in the app (Voice Language, Speaking Language, gender preference, etc.) depends on this
        settings.mediaPlaybackRequiresUserGesture = false // without this, ElevenLabs/TTS audio playback would silently fail to autoplay inside the WebView, even though it works fine in a normal mobile browser tab
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        settings.userAgentString = settings.userAgentString + " GarnetAndroidApp/1.0"

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefresh.isRefreshing = false
                hideSplash()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Only treat a failure of the TOP-LEVEL page load as
                // "offline" -- a failed sub-resource (an ad blocker
                // list, an analytics ping, etc.) shouldn't take over the
                // whole screen.
                if (request?.isForMainFrame == true) {
                    binding.swipeRefresh.isRefreshing = false
                    showOfflineState()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // This is the critical piece that makes Live Chat's
            // microphone access actually work inside the WebView. When
            // the page calls navigator.mediaDevices.getUserMedia() for
            // audio, the WebView routes that request here rather than
            // handling it automatically the way a full browser app
            // would. Granting request.resources blindly without first
            // confirming Android's own RECORD_AUDIO permission is held
            // would let the WebView claim success while the OS quietly
            // blocks the actual audio -- so the real Android runtime
            // permission is checked/requested FIRST, and the WebView
            // request is only granted once that's confirmed.
            override fun onPermissionRequest(request: PermissionRequest) {
                val wantsAudio = request.resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                if (!wantsAudio) {
                    request.deny()
                    return
                }
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    request.grant(request.resources)
                } else {
                    pendingPermissionRequest = request
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            // File upload support -- lets the site's own upload buttons
            // (attaching an image or document to a chat message) open
            // Android's native file/photo picker, rather than silently
            // doing nothing the way a bare WebView does by default.
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                    ?: android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        // Downloads (e.g. a chat export/download button on the site) --
        // a bare WebView has no built-in download handling at all, so
        // without this, tapping a download link would silently do
        // nothing. Handed off to Android's own DownloadManager, which
        // shows a real system notification/progress and saves to the
        // device's normal Downloads folder.
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)
            } catch (e: Exception) {
                // Fails closed/silently rather than crashing the app over a non-critical download failure
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { loadGarnet() }
        binding.swipeRefresh.setColorSchemeResources(R.color.garnet_accent)
    }

    private fun loadGarnet() {
        showSplash()
        binding.webView.loadUrl(getString(R.string.garnet_url))
    }

    private fun showSplash() {
        binding.offlineState.visibility = View.GONE
        binding.splashProgress.visibility = View.VISIBLE
        binding.splashOverlay.visibility = View.VISIBLE
    }

    private fun hideSplash() {
        binding.splashOverlay.visibility = View.GONE
    }

    private fun showOfflineState() {
        binding.splashProgress.visibility = View.GONE
        binding.offlineState.visibility = View.VISIBLE
        binding.splashOverlay.visibility = View.VISIBLE
    }

    private fun hasInternetConnection(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Real back-button behavior for a WebView-based app: go back through
    // the page's own navigation history first (e.g. closing a modal,
    // returning from Settings within the site), and only exit the app
    // once there's nowhere left to go back to -- rather than the
    // default behavior of exiting immediately from anywhere.
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
