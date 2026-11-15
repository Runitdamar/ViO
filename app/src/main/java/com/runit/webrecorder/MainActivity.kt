package com.runit.webrecorder

import android.Manifest
import java.util.concurrent.atomic.AtomicBoolean
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.os.SystemClock
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.view.Gravity
import android.view.WindowManager
import android.webkit.URLUtil
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private enum class Screen { HOME, BROWSE, TABS, DOWNLOADS, SETTINGS, PLACEHOLDER }

    private data class Recent(val title: String, val url: String, val time: Long)

    // Screens
    private lateinit var homeView: View
    private lateinit var browseView: View
    private lateinit var tabsView: View
    private lateinit var downloadsView: View
    private lateinit var placeholderView: View
    private lateinit var settingsView: View
    private lateinit var placeholderTitle: TextView
    private lateinit var placeholderText: TextView

    // Home
    private lateinit var greeting: TextView
    private lateinit var homeSubtitle: TextView
    private lateinit var homeSearch: EditText
    private lateinit var recentSection: View
    private lateinit var recentList: LinearLayout
    private lateinit var seeAll: TextView
    private lateinit var tileRec: View
    private lateinit var tileStop: View
    private lateinit var lblRec: TextView
    private lateinit var lblDownload: TextView

    // Browse
    private lateinit var webContainer: FrameLayout
    private lateinit var urlBar: EditText
    private lateinit var loadingBar: ProgressBar
    private lateinit var privateBadge: TextView
    private lateinit var tabCount: TextView
    private lateinit var btnTools: ImageView
    private lateinit var btnShield: ImageView

    // Settings switches
    private lateinit var swAds: SwitchCompat
    private lateinit var swHttps: SwitchCompat
    private lateinit var swCookies: SwitchCompat
    private lateinit var swFp: SwitchCompat
    private lateinit var swLockApp: SwitchCompat
    private lateinit var swLockPrivate: SwitchCompat

    // Fullscreen video
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fsContainer: FrameLayout? = null
    private var prevOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var prevUiFlags = 0

    // Lock state
    private var lockView: View? = null
    private var authRunning = false
    private var suppressLock = false
    private var stoppedAt = 0L
    private var privateUnlocked = false

    // Detect card (popup when a video plays)
    private lateinit var detectCard: View
    private lateinit var dcThumb: ImageView
    private lateinit var dcTitle: TextView
    private lateinit var dcMeta: TextView
    private lateinit var dcAction: TextView
    private lateinit var dcRecord: TextView
    private lateinit var dcStatus: TextView
    private lateinit var dcProgress: ProgressBar

    // Tabs screen
    private lateinit var tabsList: LinearLayout
    private lateinit var chips: List<TextView>

    // Download screen
    private lateinit var dlList: LinearLayout
    private lateinit var dlCount: TextView
    private lateinit var dlEmpty: TextView
    private lateinit var btnDlSelected: TextView
    private lateinit var dlTabFound: TextView
    private lateinit var dlTabSaved: TextView
    private lateinit var dlFoundHeader: View
    private lateinit var dlFoundScroll: View
    private lateinit var libScroll: View
    private lateinit var libList: LinearLayout
    private lateinit var libEmpty: TextView

    // Saved videos (library)
    private var showSaved = false
    private var askedMedia = false
    private var pendingDelete: LibItem? = null
    private var pendingMove = false
    private val libThumbs = LruCache<String, Bitmap>(150)
    private val thumbMiss = HashSet<String>()
    private val thumbExec = Executors.newFixedThreadPool(2)
    private var lastDone = 0

    // Library 2.0
    private enum class LibView(val label: String) {
        ALL("All"), VIDEO("Video"), AUDIO("Audio"), DOC("Docs"), IMAGE("Images"),
        OTHER("Other"), PLAYLISTS("Playlists"), VAULT("Vault")
    }

    private enum class SortMode(val label: String) { NEWEST("Newest"), NAME("Name"), SIZE("Largest") }

    private lateinit var dlFoundFrame: View
    private lateinit var libPanel: View
    private lateinit var libSearch: EditText
    private lateinit var libSort: TextView
    private lateinit var libCats: LinearLayout
    private lateinit var libStorageCard: View
    private lateinit var libStorageText: TextView
    private lateinit var libStorageBar: StorageBar
    private lateinit var libLegend: TextView
    private lateinit var libActions: View
    private lateinit var libActionInfo: TextView
    private lateinit var libPlayAll: TextView
    private lateinit var libShuffle: TextView
    private lateinit var libNew: TextView
    private lateinit var dlQueueBar: View
    private lateinit var dlQueueInfo: TextView
    private lateinit var dlPauseAll: TextView
    private lateinit var dlResumeAll: TextView
    private val libChips = ArrayList<TextView>()
    private var libView = LibView.ALL
    private var sortMode = SortMode.NEWEST
    private var libQuery = ""
    private var allLib: List<LibItem> = emptyList()
    private var vaultBytes = 0L
    private var freeBytes = 0L
    private var openPlaylist: String? = null
    private var vaultUnlocked = false
    private var movingToVault = false

    // Bottom nav
    private lateinit var navIcons: List<ImageView>
    private lateinit var navTexts: List<TextView>

    // Tabs
    private val tabs = ArrayList<Tab>()
    private var currentTab: Tab? = null
    private var nextTabId = 1
    private val itemIds = AtomicInteger(1)
    private var tabFilter = 0 // 0 All, 1 Normal, 2 Private, 3 Recent

    private var screen = Screen.HOME
    private var returnScreen = Screen.HOME
    private var homePrivate = false
    private var showAllRecent = false

    // Download screen rows
    private val rowViews = HashMap<Int, View>()
    private var shownIds: List<Int> = emptyList()

    // Detect card state
    private var detectItem: VideoItem? = null
    private var detectDoneHandled = false
    private var lastRec = false
    private var askedNotif = false
    private val shownKeys = HashSet<String>()
    private val hideCardRunnable = Runnable { detectCard.visibility = View.GONE }
    private val uiPending = AtomicBoolean(false)

    private val uiHandler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            updateUi()
            uiHandler.postDelayed(this, 500)
        }
    }

    // Result of the "Start recording?" system popup
    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        suppressLock = false
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val svc = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_START
                putExtra(RecorderService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(RecorderService.EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(this, svc)
        } else {
            toast("Recording cancelled")
        }
    }

    // Mic + notification permissions, then continue either way
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        suppressLock = false
        askCapture()
    }

    private val mediaPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        suppressLock = false
        if (showSaved) renderLibrary()
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        suppressLock = false
        if (pendingMove) {
            pendingMove = false
            toast(
                if (result.resultCode == RESULT_OK) "Moved to Vault"
                else "Copied to Vault, but the original is still in your library"
            )
        }
        renderLibrary()
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { suppressLock = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        Playlists.init(this)
        Shields.load(this)
        DownloadCenter.applyParallelism()
        val appCtx = applicationContext
        Thread {
            Vault.cleanOrphans(appCtx)
            Vault.clearTemp(appCtx)
        }.start()
        setContentView(R.layout.activity_main)

        bindViews()
        setupClicks()

        DownloadCenter.cleanTemp(this)
        DownloadCenter.onEvent = { msg ->
            uiHandler.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
        DownloadCenter.onChange = {
            if (uiPending.compareAndSet(false, true)) {
                uiHandler.post {
                    uiPending.set(false)
                    updateUi()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    exitFullscreen()
                    return
                }
                when (screen) {
                    Screen.BROWSE -> {
                        val wv = currentTab?.webView
                        if (wv != null && wv.canGoBack()) wv.goBack() else goHome()
                    }
                    Screen.TABS -> goHome()
                    Screen.DOWNLOADS -> {
                        if (showSaved && libView == LibView.PLAYLISTS && openPlaylist != null) {
                            openPlaylist = null
                            bindLibrary()
                        } else {
                            leaveDownloads()
                        }
                    }
                    Screen.SETTINGS -> goHome()
                    Screen.PLACEHOLDER -> goHome()
                    Screen.HOME -> finish()
                }
            }
        })

        showScreen(Screen.HOME, 0)
        if (Settings.lockApp) showLock()
    }

    // ---------- Setup ----------

    private fun bindViews() {
        homeView = findViewById(R.id.homeView)
        browseView = findViewById(R.id.browseView)
        tabsView = findViewById(R.id.tabsView)
        downloadsView = findViewById(R.id.downloadsView)
        placeholderView = findViewById(R.id.placeholderView)
        settingsView = findViewById(R.id.settingsView)
        placeholderTitle = findViewById(R.id.placeholderTitle)
        placeholderText = findViewById(R.id.placeholderText)

        greeting = findViewById(R.id.greeting)
        homeSubtitle = findViewById(R.id.homeSubtitle)
        homeSearch = findViewById(R.id.homeSearch)
        recentSection = findViewById(R.id.recentSection)
        recentList = findViewById(R.id.recentList)
        seeAll = findViewById(R.id.seeAll)
        tileRec = findViewById(R.id.tileRec)
        tileStop = findViewById(R.id.tileStop)
        lblRec = findViewById(R.id.lblRec)
        lblDownload = findViewById(R.id.lblDownload)

        webContainer = findViewById(R.id.webContainer)
        urlBar = findViewById(R.id.urlBar)
        loadingBar = findViewById(R.id.loadingBar)
        privateBadge = findViewById(R.id.privateBadge)
        tabCount = findViewById(R.id.tabCount)
        btnTools = findViewById(R.id.btnTools)
        btnShield = findViewById(R.id.btnShield)
        swAds = findViewById(R.id.swAds)
        swHttps = findViewById(R.id.swHttps)
        swCookies = findViewById(R.id.swCookies)
        swFp = findViewById(R.id.swFp)
        swLockApp = findViewById(R.id.swLockApp)
        swLockPrivate = findViewById(R.id.swLockPrivate)

        detectCard = findViewById(R.id.detectCard)
        dcThumb = findViewById(R.id.dcThumb)
        dcTitle = findViewById(R.id.dcTitle)
        dcMeta = findViewById(R.id.dcMeta)
        dcAction = findViewById(R.id.dcAction)
        dcRecord = findViewById(R.id.dcRecord)
        dcStatus = findViewById(R.id.dcStatus)
        dcProgress = findViewById(R.id.dcProgress)

        tabsList = findViewById(R.id.tabsList)
        chips = listOf(
            findViewById<TextView>(R.id.chipAll),
            findViewById<TextView>(R.id.chipNormal),
            findViewById<TextView>(R.id.chipPrivate),
            findViewById<TextView>(R.id.chipRecent)
        )

        dlList = findViewById(R.id.dlList)
        dlCount = findViewById(R.id.dlCount)
        dlEmpty = findViewById(R.id.dlEmpty)
        btnDlSelected = findViewById(R.id.btnDlSelected)
        dlTabFound = findViewById(R.id.dlTabFound)
        dlTabSaved = findViewById(R.id.dlTabSaved)
        dlFoundHeader = findViewById(R.id.dlFoundHeader)
        dlFoundScroll = findViewById(R.id.dlFoundScroll)
        libScroll = findViewById(R.id.libScroll)
        libList = findViewById(R.id.libList)
        libEmpty = findViewById(R.id.libEmpty)
        dlFoundFrame = findViewById(R.id.dlFoundFrame)
        libPanel = findViewById(R.id.libPanel)
        libSearch = findViewById(R.id.libSearch)
        libSort = findViewById(R.id.libSort)
        libCats = findViewById(R.id.libCats)
        libStorageCard = findViewById(R.id.libStorageCard)
        libStorageText = findViewById(R.id.libStorageText)
        libStorageBar = findViewById(R.id.libStorageBar)
        libLegend = findViewById(R.id.libLegend)
        libActions = findViewById(R.id.libActions)
        libActionInfo = findViewById(R.id.libActionInfo)
        libPlayAll = findViewById(R.id.libPlayAll)
        libShuffle = findViewById(R.id.libShuffle)
        libNew = findViewById(R.id.libNew)
        dlQueueBar = findViewById(R.id.dlQueueBar)
        dlQueueInfo = findViewById(R.id.dlQueueInfo)
        dlPauseAll = findViewById(R.id.dlPauseAll)
        dlResumeAll = findViewById(R.id.dlResumeAll)

        navIcons = listOf(
            findViewById<ImageView>(R.id.iconHome),
            findViewById<ImageView>(R.id.iconTabs),
            findViewById<ImageView>(R.id.iconBookmarks),
            findViewById<ImageView>(R.id.iconMore)
        )
        navTexts = listOf(
            findViewById<TextView>(R.id.textHome),
            findViewById<TextView>(R.id.textTabs),
            findViewById<TextView>(R.id.textBookmarks),
            findViewById<TextView>(R.id.textMore)
        )
    }

    private fun setupClicks() {
        bindGo(homeSearch)
        bindGo(urlBar)

        // Home tiles
        findViewById<View>(R.id.tileGo).setOnClickListener {
            if (homeSearch.text.toString().trim().isEmpty()) {
                homeSearch.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(homeSearch, InputMethodManager.SHOW_IMPLICIT)
            } else {
                submit(homeSearch)
            }
        }
        tileRec.setOnClickListener { startManual() }
        tileStop.setOnClickListener { stopManual() }
        findViewById<View>(R.id.tileDownload).setOnClickListener { openDownloads() }

        // Browse
        btnTools.setOnClickListener { showToolsCard() }
        btnShield.setOnClickListener { showShieldDialog() }
        tabCount.setOnClickListener { showScreen(Screen.TABS, 1) }

        // Recent
        seeAll.setOnClickListener {
            showAllRecent = !showAllRecent
            renderRecent()
        }

        // Detect card
        dcAction.setOnClickListener { onDetectAction() }
        dcRecord.setOnClickListener { onCardRecord() }
        findViewById<View>(R.id.dcClose).setOnClickListener { hideDetectCard() }
        detectCard.setOnClickListener { openDownloads() }

        // Tabs screen
        findViewById<View>(R.id.tabsBack).setOnClickListener { goHome() }
        findViewById<View>(R.id.tabsEdit).setOnClickListener { showCloseTabsDialog() }
        for (i in chips.indices) {
            chips[i].setOnClickListener {
                tabFilter = i
                renderTabs()
            }
        }
        findViewById<View>(R.id.btnNewTab).setOnClickListener { newTab(false) }
        findViewById<View>(R.id.btnNewPrivate).setOnClickListener { newTab(true) }

        // Download screen
        findViewById<View>(R.id.dlBack).setOnClickListener { leaveDownloads() }
        findViewById<View>(R.id.dlSelectAll).setOnClickListener { selectAllVideos() }
        btnDlSelected.setOnClickListener { downloadSelected() }
        dlTabFound.setOnClickListener { setDlTab(false) }
        dlTabSaved.setOnClickListener { setDlTab(true) }
        dlPauseAll.setOnClickListener {
            DownloadCenter.pauseAll()
            updateUi()
        }
        dlResumeAll.setOnClickListener {
            if (DownloadCenter.pausedCount() > 0) DownloadCenter.resumeAll(this)
            else DownloadCenter.retryFailed(this)
            updateUi()
        }
        libSort.setOnClickListener { showSortDialog() }
        libStorageCard.setOnClickListener { showStorageDialog() }
        libSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                if (q != libQuery) {
                    libQuery = q
                    bindLibrary()
                }
            }
        })
        libSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // Settings: downloads + storage
        findViewById<View>(R.id.rowMaxDl).setOnClickListener {
            Settings.maxDownloads = if (Settings.maxDownloads >= 3) 1 else Settings.maxDownloads + 1
            DownloadCenter.applyParallelism()
            bindSettings()
        }
        findViewById<View>(R.id.rowClearTemp).setOnClickListener { clearTempFiles() }

        // Bottom nav
        findViewById<View>(R.id.navHome).setOnClickListener { goHome() }
        findViewById<View>(R.id.navTabs).setOnClickListener { showScreen(Screen.TABS, 1) }
        findViewById<View>(R.id.navBookmarks).setOnClickListener {
            showPlaceholder("Bookmarks", "Coming soon", 2)
        }
        findViewById<View>(R.id.navMore).setOnClickListener {
            showScreen(Screen.SETTINGS, 3)
        }
    }

    private fun bindGo(edit: EditText) {
        edit.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                submit(edit)
                true
            } else {
                false
            }
        }
    }

    // ---------- Screens ----------

    private fun showScreen(s: Screen, nav: Int) {
        screen = s
        homeView.visibility = if (s == Screen.HOME) View.VISIBLE else View.GONE
        browseView.visibility = if (s == Screen.BROWSE) View.VISIBLE else View.GONE
        tabsView.visibility = if (s == Screen.TABS) View.VISIBLE else View.GONE
        downloadsView.visibility = if (s == Screen.DOWNLOADS) View.VISIBLE else View.GONE
        placeholderView.visibility = if (s == Screen.PLACEHOLDER) View.VISIBLE else View.GONE
        settingsView.visibility = if (s == Screen.SETTINGS) View.VISIBLE else View.GONE

        for (i in navIcons.indices) {
            val c = color(if (i == nav) R.color.vio_accent else R.color.vio_subtext)
            navIcons[i].setColorFilter(c)
            navTexts[i].setTextColor(c)
        }

        if (s != Screen.BROWSE) hideDetectCard()
        if (s != Screen.HOME) hideKeyboard()
        if (s != Screen.DOWNLOADS) lockVault(true)

        when (s) {
            Screen.HOME -> {
                applyHomeMode()
                renderRecent()
            }
            Screen.BROWSE -> applyPrivateStyle()
            Screen.TABS -> renderTabs()
            Screen.DOWNLOADS -> {
                applyDlTab()
                renderDownloads()
                if (showSaved) renderLibrary()
            }
            Screen.SETTINGS -> bindSettings()
            Screen.PLACEHOLDER -> {}
        }
        updateUi()
    }

    private fun goHome() {
        homePrivate = currentTab?.isPrivate == true
        showScreen(Screen.HOME, 0)
    }

    private fun showPlaceholder(title: String, text: String, nav: Int) {
        placeholderTitle.text = title
        placeholderText.text = text
        showScreen(Screen.PLACEHOLDER, nav)
    }

    private fun applyHomeMode() {
        if (homePrivate) {
            greeting.text = "Private browsing."
            greeting.setTextColor(color(R.color.vio_accent))
            homeSubtitle.text = "Pages you visit here won't be saved to Recent"
            recentSection.visibility = View.GONE
        } else {
            greeting.text = greetingText()
            greeting.setTextColor(color(R.color.vio_text))
            homeSubtitle.text = "Search the web or type a URL"
            recentSection.visibility = View.VISIBLE
        }
    }

    private fun applyPrivateStyle() {
        privateBadge.visibility = if (currentTab?.isPrivate == true) View.VISIBLE else View.GONE
    }

    private fun greetingText(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning."
            hour < 17 -> "Good afternoon."
            else -> "Good evening."
        }
    }

    // ---------- Tabs ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(isPrivate: Boolean): Tab {
        val id = nextTabId++
        val wv = WebView(this)

        val st = wv.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.mediaPlaybackRequiresUserGesture = false
        st.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        st.setSupportZoom(true)
        st.builtInZoomControls = true
        st.displayZoomControls = false
        if (isPrivate) {
            st.cacheMode = WebSettings.LOAD_NO_CACHE
        }

        if (Settings.fingerprintProtection) st.userAgentString = Shields.reduceUa(st.userAgentString)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !Settings.blockThirdPartyCookies)

        val sniffer = VideoSniffer(id) { itemIds.getAndIncrement() }
        sniffer.userAgent = st.userAgentString
        sniffer.onChange = { uiHandler.post { updateUi() } }

        val tab = Tab(id, isPrivate, wv, sniffer)

        // Video info / play events from the page (arrive on a background thread)
        wv.addJavascriptInterface(
            WebBridge(
                { info, playing -> uiHandler.post { onVideoInfo(tab, info, playing) } },
                { }
            ),
            "WRBridge"
        )

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null) {
                    tab.url = url
                    sniffer.clear()
                    sniffer.pageUrl = url
                    tab.blocked.set(0)
                    clearShown(tab)
                    if (tab === currentTab && !urlBar.hasFocus()) urlBar.setText(url)
                }
                if (tab === currentTab) {
                    loadingBar.visibility = View.VISIBLE
                }
                view?.evaluateJavascript(VideoSniffer.HOOK_JS, null)
                injectShields(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val req = request ?: return false
                if (!req.isForMainFrame) return false
                val orig = req.url.toString()
                if (tab.skipUpgradeUrl == orig) return false
                val https = Shields.upgrade(orig) ?: return false
                tab.httpFallback = orig
                view?.loadUrl(https)
                return true
            }

            // If an upgraded https page fails, go back to the original http address once
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                val req = request
                if (req != null && req.isForMainFrame) {
                    val orig = tab.httpFallback
                    if (orig != null && req.url.toString().startsWith("https://")) {
                        tab.httpFallback = null
                        tab.skipUpgradeUrl = orig
                        view?.loadUrl(orig)
                        return
                    }
                }
                super.onReceivedError(view, request, error)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (tab === currentTab) loadingBar.visibility = View.GONE
                view?.evaluateJavascript(VideoSniffer.HOOK_JS, null)
                injectShields(view, url)
                tab.httpFallback = null
                tab.title = view?.title ?: ""
                if (url != null && !tab.isPrivate) addRecent(view?.title, url)
            }

            // Sees every request the page makes, looks for video files
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request != null) {
                    val pageHost = hostOf(tab.url)
                    if (Shields.shouldBlock(request.url.host, pageHost, request.isForMainFrame)) {
                        tab.blocked.incrementAndGet()
                        return Shields.emptyResponse()
                    }
                    sniffer.checkRequest(request.url.toString())
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (tab === currentTab) loadingBar.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                tab.title = title ?: ""
            }

            // The video's fullscreen button
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                enterFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }

        // Any file download the page starts (PDF, zip, mp3 ...) goes through Vio's own downloader
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            uiHandler.post { onPageDownload(tab, url, userAgent, contentDisposition, mimeType, contentLength) }
        }

        wv.visibility = View.GONE
        // Index 0 keeps the detect card above every WebView
        webContainer.addView(
            wv,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        tabs.add(tab)
        installDocScripts(tab)
        return tab
    }

    // ---------- File downloads from pages ----------

    private fun onPageDownload(
        tab: Tab,
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        if (url.isNullOrEmpty()) return
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            toast(
                if (lower.startsWith("blob:") || lower.startsWith("data:")) "This page makes the file itself, so Vio can't download it directly"
                else "Vio can't download this kind of link"
            )
            return
        }
        if (DownloadCenter.hasActiveUrl(url)) {
            toast("Already downloading this file")
            return
        }
        var name = DownloadUtil.parseFileName(contentDisposition)
        if (name.isNullOrEmpty()) name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        if (name.isNullOrEmpty()) name = "file"
        val fileName: String = name
        val cat = Library.categoryFor(fileName, mimeType)
        val host = hostOf(url)
        val size = if (contentLength > 0) formatSize(contentLength) + "  •  " else ""
        AlertDialog.Builder(this)
            .setTitle("Download file?")
            .setMessage(fileName + "\n" + size + cat.label + "  •  " + host)
            .setPositiveButton("Download") { _, _ ->
                val item = VideoItem(
                    itemIds.getAndIncrement(),
                    url,
                    tab.url.ifEmpty { null },
                    userAgent ?: tab.webView.settings.userAgentString,
                    tab.id,
                    host,
                    fileName.substringBeforeLast('.')
                )
                item.generic = true
                item.kind = VideoItem.Kind.FILE
                item.cat = cat
                item.serverFileName = fileName
                item.mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
                if (contentLength > 0) item.sizeBytes = contentLength
                askNotificationOnce()
                DownloadCenter.enqueue(this, item)
                toast("Downloading $fileName")
                updateUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Fullscreen video ----------

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback?) {
        val box = FrameLayout(this)
        box.setBackgroundColor(0xFF000000.toInt())
        box.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addContentView(
            box,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        customView = view
        customCallback = callback
        fsContainer = box

        prevOrientation = requestedOrientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        @Suppress("DEPRECATION")
        run {
            prevUiFlags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun exitFullscreen() {
        val box = fsContainer ?: return
        (box.parent as? ViewGroup)?.removeView(box)
        fsContainer = null
        customView = null
        val cb = customCallback
        customCallback = null
        cb?.onCustomViewHidden()
        requestedOrientation = prevOrientation
        @Suppress("DEPRECATION")
        run { window.decorView.systemUiVisibility = prevUiFlags }
    }

    // ---------- Scripts that run before a page's own scripts ----------

    private fun installDocScripts(tab: Tab) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        try {
            for (h in tab.scripts) h.remove()
            tab.scripts.clear()
            val script = Shields.docStartScript(Settings.shieldsOffAll()) ?: return
            tab.scripts.add(WebViewCompat.addDocumentStartJavaScript(tab.webView, script, setOf("*")))
        } catch (_: Exception) {
        }
    }

    private fun reinstallDocScripts() {
        for (t in tabs) installDocScripts(t)
    }

    private fun switchTo(tab: Tab) {
        currentTab = tab
        tab.lastUsed = System.currentTimeMillis()
        for (t in tabs) {
            t.webView.visibility = if (t === tab) View.VISIBLE else View.GONE
        }
        urlBar.setText(tab.url)
        loadingBar.visibility = View.GONE
        hideDetectCard()
        applyPrivateStyle()
        updateUi()
    }

    private fun closeTab(tab: Tab) {
        if (!tabs.remove(tab)) return
        try {
            tab.webView.stopLoading()
        } catch (_: Exception) {
        }
        if (tab.isPrivate) {
            try {
                tab.webView.clearCache(true)
            } catch (_: Exception) {
            }
        }
        webContainer.removeView(tab.webView)
        tab.webView.destroy()
        if (currentTab === tab) {
            currentTab = tabs.maxByOrNull { it.lastUsed }
            hideDetectCard()
        }
    }

    private fun closeMany(match: (Tab) -> Boolean) {
        val victims = tabs.filter { match(it) }
        for (t in victims) closeTab(t)
    }

    private fun newTab(isPrivate: Boolean) {
        if (isPrivate) {
            requirePrivate { newTabNow(true) }
        } else {
            newTabNow(false)
        }
    }

    private fun newTabNow(isPrivate: Boolean) {
        val t = createTab(isPrivate)
        homePrivate = isPrivate
        switchTo(t)
        showScreen(Screen.HOME, 0)
    }

    private fun openTab(tab: Tab) {
        if (tab.isPrivate) {
            requirePrivate { openTabNow(tab) }
        } else {
            openTabNow(tab)
        }
    }

    private fun openTabNow(tab: Tab) {
        switchTo(tab)
        if (tab.isBlank) {
            homePrivate = tab.isPrivate
            showScreen(Screen.HOME, 0)
        } else {
            showScreen(Screen.BROWSE, 1)
        }
    }

    private fun showCloseTabsDialog() {
        val options = arrayOf("Close all tabs", "Close private tabs", "Close normal tabs")
        AlertDialog.Builder(this)
            .setTitle("Close tabs")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> closeMany { true }
                    1 -> closeMany { it.isPrivate }
                    else -> closeMany { !it.isPrivate }
                }
                renderTabs()
                updateUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderTabs() {
        for (i in chips.indices) {
            val on = i == tabFilter
            chips[i].setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
            chips[i].setTextColor(color(if (on) R.color.vio_surface else R.color.vio_text))
        }

        tabsList.removeAllViews()
        val sorted = tabs.sortedByDescending { it.lastUsed }
        val list = when (tabFilter) {
            1 -> sorted.filter { !it.isPrivate }
            2 -> sorted.filter { it.isPrivate }
            3 -> sorted.take(5)
            else -> sorted
        }

        if (list.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No tabs here yet"
            tv.setTextColor(color(R.color.vio_subtext))
            tv.textSize = 14f
            tv.setPadding(0, dp(24), 0, dp(24))
            tabsList.addView(tv)
        }

        for (tab in list) {
            val row = layoutInflater.inflate(R.layout.item_tab, tabsList, false)
            val masked = tab.isPrivate && Settings.lockPrivate && !privateUnlocked
            val title = when {
                masked -> "Private tab"
                tab.title.isNotBlank() -> tab.title
                tab.isBlank -> "New tab"
                else -> hostOf(tab.url)
            }
            val sub = when {
                masked -> "Locked"
                tab.isBlank && tab.isPrivate -> "Private tab"
                tab.isBlank -> "Start page"
                tab.isPrivate -> "Private • " + hostOf(tab.url)
                else -> hostOf(tab.url)
            }
            row.findViewById<TextView>(R.id.tabTitle).text = title
            row.findViewById<TextView>(R.id.tabHost).text = sub

            val letter = row.findViewById<TextView>(R.id.tabLetter)
            val lock = row.findViewById<ImageView>(R.id.tabLock)
            val avatar = row.findViewById<View>(R.id.tabAvatar)
            if (tab.isPrivate) {
                avatar.setBackgroundResource(R.drawable.bg_avatar_dark)
                letter.visibility = View.GONE
                lock.visibility = View.VISIBLE
            } else {
                letter.text = (if (tab.isBlank) "+" else hostOf(tab.url).take(1).uppercase())
            }

            row.setBackgroundResource(
                if (tab === currentTab) R.drawable.bg_card_current else R.drawable.bg_card
            )
            row.setOnClickListener { openTab(tab) }
            row.findViewById<View>(R.id.tabClose).setOnClickListener {
                closeTab(tab)
                renderTabs()
                updateUi()
            }
            tabsList.addView(row)
        }
    }

    // ---------- Navigation / URL ----------

    private fun submit(edit: EditText) {
        val input = edit.text.toString().trim()
        if (input.isEmpty()) return
        val url = resolveUrl(input)
        if (edit === homeSearch) {
            edit.setText("")
            openFromHome(url)
        } else {
            val tab = currentTab
            if (tab != null) openInTab(tab, url)
        }
    }

    private fun resolveUrl(input: String): String = when {
        input.startsWith("http://") || input.startsWith("https://") -> input
        input.contains(" ") || !input.contains(".") ->
            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(input, "UTF-8")
        else -> "https://$input"
    }

    // Uses the current tab if it is still empty, otherwise opens a new one
    private fun openFromHome(url: String) {
        val cur = currentTab
        val tab = if (cur != null && cur.isBlank && cur.isPrivate == homePrivate) {
            cur
        } else {
            createTab(homePrivate)
        }
        openInTab(tab, url)
    }

    private fun openInTab(tab: Tab, typed: String) {
        var url = typed
        val upgraded = if (tab.skipUpgradeUrl == typed) null else Shields.upgrade(typed)
        if (upgraded != null) {
            tab.httpFallback = typed
            url = upgraded
        }
        tab.url = url
        switchTo(tab)
        showScreen(Screen.BROWSE, 1)
        tab.webView.loadUrl(url)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val v = currentFocus ?: window.decorView
        imm.hideSoftInputFromWindow(v.windowToken, 0)
        currentFocus?.clearFocus()
    }

    // ---------- Recent ----------

    private fun loadRecent(): MutableList<Recent> {
        val list = ArrayList<Recent>()
        try {
            val raw = getSharedPreferences("vio", MODE_PRIVATE).getString("recent", "[]") ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Recent(o.optString("t"), o.optString("u"), o.optLong("ts")))
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun saveRecent(list: List<Recent>) {
        val arr = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("t", r.title)
            o.put("u", r.url)
            o.put("ts", r.time)
            arr.put(o)
        }
        getSharedPreferences("vio", MODE_PRIVATE).edit().putString("recent", arr.toString()).apply()
    }

    private fun addRecent(title: String?, url: String) {
        if (!url.startsWith("http")) return
        val list = loadRecent()
        list.removeAll { it.url == url }
        val name = if (title.isNullOrBlank()) hostOf(url) else title
        list.add(0, Recent(name, url, System.currentTimeMillis()))
        while (list.size > 30) list.removeAt(list.size - 1)
        saveRecent(list)
    }

    private fun renderRecent() {
        recentList.removeAllViews()
        val all = loadRecent()
        val shown = if (showAllRecent) all else all.take(5)

        if (shown.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Pages you visit will show up here"
            tv.setTextColor(color(R.color.vio_subtext))
            tv.textSize = 13f
            tv.setPadding(0, dp(12), 0, dp(12))
            recentList.addView(tv)
        }

        for (r in shown) {
            val row = layoutInflater.inflate(R.layout.item_recent, recentList, false)
            val host = hostOf(r.url)
            row.findViewById<TextView>(R.id.recentLetter).text = host.take(1).uppercase()
            row.findViewById<TextView>(R.id.recentTitle).text = r.title
            row.findViewById<TextView>(R.id.recentHost).text = host
            row.findViewById<TextView>(R.id.recentTime).text = timeAgo(r.time)
            row.setOnClickListener { openFromHome(r.url) }
            row.findViewById<View>(R.id.recentMore).setOnClickListener {
                val list = loadRecent()
                list.removeAll { entry -> entry.url == r.url }
                saveRecent(list)
                renderRecent()
            }
            recentList.addView(row)
        }

        seeAll.visibility = if (all.size > 5) View.VISIBLE else View.GONE
        seeAll.text = if (showAllRecent) "Show less" else "See all"
    }

    private fun hostOf(url: String): String {
        val host = try {
            Uri.parse(url).host
        } catch (_: Exception) {
            null
        }
        return (host ?: url).removePrefix("www.")
    }

    private fun timeAgo(time: Long): String {
        val diff = System.currentTimeMillis() - time
        val min = diff / 60000
        return when {
            min < 1 -> "now"
            min < 60 -> "${min}m"
            min < 1440 -> "${min / 60}h"
            else -> "${min / 1440}d"
        }
    }

    // ---------- Video popup card ----------

    private fun onVideoInfo(tab: Tab, info: PageInfo, playing: Boolean) {
        val item = tab.sniffer.applyPageInfo(info)
        if (!playing || tab !== currentTab || screen != Screen.BROWSE) return
        if (RecorderService.isRecording) return // keep the card out of the recording

        if (item != null) {
            showDetectCard(tab, item)
        } else {
            // The network request for the video may arrive a moment after "playing"
            uiHandler.postDelayed({
                if (tab === currentTab && screen == Screen.BROWSE && !RecorderService.isRecording) {
                    showDetectCard(tab, tab.sniffer.latest())
                }
            }, 1500)
        }
    }

    private fun clearShown(tab: Tab) {
        val prefix = "${tab.id}|"
        shownKeys.removeAll { it.startsWith(prefix) }
    }

    private fun showDetectCard(tab: Tab, item: VideoItem?) {
        val key = "${tab.id}|${item?.url ?: "none"}"
        if (!shownKeys.add(key)) return
        openCard(tab, item)
    }

    private fun openCard(tab: Tab, item: VideoItem?) {
        detectItem = item
        detectDoneHandled = false
        if (item != null) {
            tab.sniffer.loadDetails(item)
            MetadataFetcher.loadPoster(item) { uiHandler.post { updateUi() } }
        }
        detectCard.visibility = View.VISIBLE
        bindDetectCard()
        scheduleHide(15000)
    }

    /** Tools icon in the URL bar: brings the card back (also while recording). */
    private fun showToolsCard() {
        val tab = currentTab ?: return
        if (screen != Screen.BROWSE) return
        val item = detectItem?.takeIf { !it.hidden || it.isActive() } ?: tab.sniffer.latest()
        openCard(tab, item)
    }

    private fun scheduleHide(ms: Long) {
        uiHandler.removeCallbacks(hideCardRunnable)
        uiHandler.postDelayed(hideCardRunnable, ms)
    }

    private fun hideDetectCard() {
        uiHandler.removeCallbacks(hideCardRunnable)
        detectCard.visibility = View.GONE
    }

    private fun bindDetectCard() {
        val rec = RecorderService.isRecording
        val saving = RecorderService.isSaving
        dcRecord.text = when {
            saving -> "Saving..."
            rec -> "Stop"
            else -> "Record"
        }
        dcRecord.setBackgroundResource(if (rec) R.drawable.bg_btn_red else R.drawable.bg_btn_dark)

        val item = detectItem?.takeIf { !it.hidden || it.isActive() }
        if (item == null) {
            dcTitle.text = "No video found yet"
            dcMeta.text = "Play a video, or record the screen"
            dcStatus.visibility = View.GONE
            dcProgress.visibility = View.GONE
            dcAction.visibility = View.GONE
            applyThumb(dcThumb, null)
            return
        }
        dcAction.visibility = View.VISIBLE
        dcTitle.text = item.title
        dcMeta.text = item.metaLine()
        applyThumb(dcThumb, item.thumb)

        dcAction.alpha = 1f
        dcStatus.setTextColor(color(R.color.vio_accent))
        when (item.state) {
            VideoItem.State.IDLE -> {
                dcProgress.visibility = View.GONE
                val prob = item.problem
                if (prob != null) {
                    dcStatus.visibility = View.VISIBLE
                    dcStatus.text = prob
                    dcStatus.setTextColor(color(R.color.vio_red))
                    dcAction.alpha = 0.5f
                } else {
                    dcStatus.visibility = View.GONE
                }
                dcAction.text = "Download"
            }
            VideoItem.State.FAILED -> {
                dcProgress.visibility = View.GONE
                dcStatus.visibility = View.VISIBLE
                dcStatus.text = item.statusLine()
                dcStatus.setTextColor(color(R.color.vio_red))
                dcAction.text = "Retry"
            }
            VideoItem.State.QUEUED -> {
                showProgress(item)
                dcAction.text = "Cancel"
                uiHandler.removeCallbacks(hideCardRunnable)
            }
            VideoItem.State.DOWNLOADING -> {
                showProgress(item)
                dcAction.text = "Pause"
                uiHandler.removeCallbacks(hideCardRunnable)
            }
            VideoItem.State.PAUSED -> {
                showProgress(item)
                dcAction.text = "Resume"
                uiHandler.removeCallbacks(hideCardRunnable)
            }
            VideoItem.State.DONE -> {
                dcProgress.visibility = View.GONE
                dcStatus.visibility = View.VISIBLE
                dcStatus.text = "Saved to Movies/WebRecorder"
                dcStatus.setTextColor(color(R.color.vio_green))
                dcAction.text = "Open list"
                if (!detectDoneHandled) {
                    detectDoneHandled = true
                    scheduleHide(4000)
                }
            }
        }
    }

    private fun showProgress(item: VideoItem) {
        val p = item.progress
        dcProgress.visibility = View.VISIBLE
        dcProgress.isIndeterminate = p < 0 || item.state == VideoItem.State.QUEUED
        if (p in 0..100) dcProgress.progress = p
        dcStatus.visibility = View.VISIBLE
        dcStatus.text = item.statusLine()
    }

    private fun onDetectAction() {
        val item = detectItem ?: return
        when (item.state) {
            VideoItem.State.IDLE, VideoItem.State.FAILED -> startDownload(item)
            VideoItem.State.DOWNLOADING -> DownloadCenter.pause(item)
            VideoItem.State.QUEUED -> DownloadCenter.cancel(this, item)
            VideoItem.State.PAUSED -> DownloadCenter.resume(this, item)
            VideoItem.State.DONE -> openDownloads()
        }
        bindDetectCard()
    }

    private fun onCardRecord() {
        if (RecorderService.isRecording) stopManual() else startManual()
    }

    /** Starts one download; HLS streams with several qualities ask which one first. */
    private fun startDownload(item: VideoItem) {
        if (item.problem != null) {
            toast(item.problem ?: "Can't download this")
            return
        }
        askNotificationOnce()
        val vs = item.variants
        if (item.kind == VideoItem.Kind.HLS && vs.size > 1) {
            val labels = vs.map { v ->
                val br = DownloadUtil.formatBitrate(v.bandwidth)
                if (br.isEmpty()) v.label else v.label + "  •  " + br
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Choose quality")
                .setItems(labels) { _, which ->
                    DownloadCenter.enqueue(this, item, vs[which])
                    toast("Download started")
                    updateUi()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            DownloadCenter.enqueue(this, item)
            toast("Download started")
            updateUi()
        }
    }

    private fun askNotificationOnce() {
        if (askedNotif || Build.VERSION.SDK_INT < 33) return
        askedNotif = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            suppressLock = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---------- Download screen ----------

    private fun openDownloads() {
        returnScreen = if (screen == Screen.BROWSE) Screen.BROWSE else Screen.HOME
        showScreen(Screen.DOWNLOADS, -1)
    }

    private fun leaveDownloads() {
        if (returnScreen == Screen.BROWSE && currentTab != null) {
            showScreen(Screen.BROWSE, 1)
        } else {
            goHome()
        }
    }

    /** This tab's videos plus every download started in this run; active ones first. */
    private fun currentItems(): List<VideoItem> {
        val map = LinkedHashMap<Int, VideoItem>()
        val tab = currentTab
        if (tab != null) for (v in tab.sniffer.list()) map[v.id] = v
        for (v in DownloadCenter.all()) map[v.id] = v
        fun rank(v: VideoItem): Int = when {
            v.isActive() -> 0
            v.state == VideoItem.State.IDLE -> 1
            else -> 2
        }
        return map.values.sortedWith(compareBy<VideoItem>({ rank(it) }, { -it.id }))
    }

    private fun renderDownloads() {
        val items = currentItems()
        if (!showSaved) dlCount.text = "${items.size}"
        dlEmpty.visibility = if (!showSaved && items.isEmpty()) View.VISIBLE else View.GONE

        val ids = items.map { it.id }
        if (ids != shownIds) {
            dlList.removeAllViews()
            rowViews.clear()
            for (item in items) {
                val row = layoutInflater.inflate(R.layout.item_video, dlList, false)
                row.setOnClickListener { toggleSelect(item) }
                dlList.addView(row)
                rowViews[item.id] = row
                MetadataFetcher.loadDetails(item) { uiHandler.post { updateUi() } }
                MetadataFetcher.loadPoster(item) { uiHandler.post { updateUi() } }
            }
            shownIds = ids
        }

        for (item in items) {
            val row = rowViews[item.id]
            if (row != null) bindRow(row, item)
        }

        val doneNow = items.count { it.state == VideoItem.State.DONE }
        if (doneNow != lastDone) {
            lastDone = doneNow
            if (showSaved) renderLibrary()
        }
        if (!showSaved) updateQueueBar()

        val n = items.count { it.selected && it.isSelectable() }
        btnDlSelected.text = "Download Selected ($n)"
        btnDlSelected.alpha = if (n > 0) 1f else 0.5f
    }

    private fun updateQueueBar() {
        val active = DownloadCenter.activeCount()
        val paused = DownloadCenter.pausedCount()
        val failed = DownloadCenter.failedCount()
        if (active + paused + failed == 0) {
            dlQueueBar.visibility = View.GONE
            return
        }
        dlQueueBar.visibility = View.VISIBLE
        val parts = ArrayList<String>()
        if (active > 0) parts.add("$active active")
        if (paused > 0) parts.add("$paused paused")
        if (failed > 0) parts.add("$failed failed")
        dlQueueInfo.text = parts.joinToString(" • ") + "  (max " + Settings.maxDownloads + " at once)"
        dlPauseAll.visibility = if (active > 0) View.VISIBLE else View.GONE
        dlResumeAll.visibility = if (paused > 0 || failed > 0) View.VISIBLE else View.GONE
        dlResumeAll.text = if (paused > 0) "Resume all" else "Retry failed"
    }

    private fun bindRow(row: View, item: VideoItem) {
        applyThumb(
            row.findViewById<ImageView>(R.id.vThumb),
            item.thumb,
            if (item.generic) iconFor(item.cat) else R.drawable.ic_video
        )
        row.findViewById<TextView>(R.id.vTitle).text = item.title
        row.findViewById<TextView>(R.id.vMeta).text = item.metaLine()
        row.findViewById<TextView>(R.id.vType).text = item.typeLine()

        val progress = row.findViewById<ProgressBar>(R.id.vProgress)
        val status = row.findViewById<TextView>(R.id.vStatus)
        val check = row.findViewById<CheckBox>(R.id.vCheck)
        val done = row.findViewById<ImageView>(R.id.vDone)
        val action = row.findViewById<ImageView>(R.id.vAction)
        val cancel = row.findViewById<ImageView>(R.id.vCancel)
        val play = row.findViewById<ImageView>(R.id.vPlay)
        play.visibility = if (!item.generic && item.problem == null && item.state != VideoItem.State.DONE) View.VISIBLE else View.GONE
        play.setOnClickListener { playItem(item) }

        status.setTextColor(color(R.color.vio_accent))
        action.visibility = View.GONE
        cancel.visibility = View.GONE
        done.visibility = View.GONE
        check.visibility = View.GONE
        progress.visibility = View.GONE
        status.visibility = View.GONE

        when (item.state) {
            VideoItem.State.IDLE -> {
                val prob = item.problem
                if (prob != null) {
                    status.visibility = View.VISIBLE
                    status.text = prob
                    status.setTextColor(color(R.color.vio_red))
                } else {
                    check.visibility = View.VISIBLE
                    check.isChecked = item.selected
                }
            }
            VideoItem.State.QUEUED, VideoItem.State.DOWNLOADING, VideoItem.State.PAUSED -> {
                val p = item.progress
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = p < 0 || item.state == VideoItem.State.QUEUED
                if (p in 0..100) progress.progress = p
                status.visibility = View.VISIBLE
                status.text = item.statusLine()
                cancel.visibility = View.VISIBLE
                cancel.setOnClickListener { DownloadCenter.cancel(this, item); updateUi() }
                if (item.state != VideoItem.State.QUEUED) {
                    action.visibility = View.VISIBLE
                    if (item.state == VideoItem.State.PAUSED) {
                        action.setImageResource(R.drawable.ic_play)
                        action.setOnClickListener { DownloadCenter.resume(this, item); updateUi() }
                    } else {
                        action.setImageResource(R.drawable.ic_pause)
                        action.setOnClickListener { DownloadCenter.pause(item); updateUi() }
                    }
                }
            }
            VideoItem.State.DONE -> {
                status.visibility = View.VISIBLE
                status.text = "Saved to " + Library.folder(if (item.generic) item.cat else Cat.VIDEO)
                status.setTextColor(color(R.color.vio_green))
                done.visibility = View.VISIBLE
            }
            VideoItem.State.FAILED -> {
                status.visibility = View.VISIBLE
                status.text = item.statusLine() + ". Tap to retry"
                status.setTextColor(color(R.color.vio_red))
                check.visibility = View.VISIBLE
                check.isChecked = item.selected
                cancel.visibility = View.VISIBLE
                cancel.setOnClickListener { DownloadCenter.cancel(this, item); updateUi() }
            }
        }
    }

    private fun applyThumb(view: ImageView, bmp: Bitmap?, ph: Int = R.drawable.ic_video) {
        if (bmp != null) {
            if (view.tag !== bmp) {
                view.clearColorFilter()
                view.setPadding(0, 0, 0, 0)
                view.scaleType = ImageView.ScaleType.CENTER_CROP
                view.setImageBitmap(bmp)
                view.tag = bmp
            }
        } else if (view.tag != "ph$ph") {
            val p = dp(16)
            view.scaleType = ImageView.ScaleType.CENTER_INSIDE
            view.setPadding(p, p, p, p)
            view.setImageResource(ph)
            view.setColorFilter(color(R.color.vio_subtext))
            view.tag = "ph$ph"
        }
    }

    private fun toggleSelect(item: VideoItem) {
        if (!item.isSelectable()) return
        item.selected = !item.selected
        renderDownloads()
    }

    private fun selectAllVideos() {
        val items = currentItems().filter { it.isSelectable() }
        val allOn = items.isNotEmpty() && items.all { it.selected }
        for (item in items) item.selected = !allOn
        renderDownloads()
    }

    private fun downloadSelected() {
        val chosen = currentItems().filter { it.selected && it.isSelectable() }
        if (chosen.isEmpty()) {
            toast("Select at least one video")
            return
        }
        askNotificationOnce()
        for (item in chosen) {
            item.selected = false
            DownloadCenter.enqueue(this, item)
        }
        toast(if (chosen.size == 1) "Download started" else "Downloading ${chosen.size} videos")
        renderDownloads()
    }

    // ---------- Recording ----------

    private fun startManual() {
        if (RecorderService.isSaving) {
            toast("Saving, please wait")
            return
        }
        if (RecorderService.isRecording) {
            toast("Already recording")
            return
        }
        startFlow()
    }

    private fun stopManual() {
        if (!RecorderService.isRecording) {
            toast("Not recording")
            return
        }
        val svc = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_STOP
        }
        startService(svc)
    }

    // Checks permissions, then shows the system capture popup
    private fun startFlow() {
        val missing = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missing.isEmpty()) {
            askCapture()
        } else {
            suppressLock = true
            permLauncher.launch(missing.toTypedArray())
        }
    }

    private fun askCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        suppressLock = true
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // ---------- UI sync ----------

    private fun updateUi() {
        val rec = RecorderService.isRecording
        val saving = RecorderService.isSaving
        val items = currentTab?.sniffer?.list() ?: emptyList()
        val active = DownloadCenter.all().filter { it.isBusy() }
        val dlText = if (active.isNotEmpty()) {
            val p = active[0].progress
            if (p in 0..100) "$p%" else "..."
        } else {
            "${items.size}"
        }

        // Home tiles
        lblRec.text = if (saving) "Saving" else "Rec"
        tileRec.setBackgroundResource(if (rec) R.drawable.bg_tile_rec else R.drawable.bg_tile)
        tileStop.alpha = if (rec) 1f else 0.4f
        lblDownload.text = "Download\n$dlText"

        // URL bar tools icon: red while recording
        btnTools.setColorFilter(color(if (rec) R.color.vio_red else R.color.vio_accent))
        tabCount.text = "${tabs.size}"
        val shieldHost = currentTab?.let { hostOf(it.url) } ?: ""
        val shieldOn = Settings.blockAds && !Settings.shieldsOffFor(shieldHost)
        btnShield.setColorFilter(color(if (shieldOn) R.color.vio_accent else R.color.vio_subtext))

        // Recording just started: tuck the card away so it isn't in the video
        if (rec && !lastRec && detectCard.visibility == View.VISIBLE) scheduleHide(2500)
        lastRec = rec

        if (screen == Screen.DOWNLOADS) renderDownloads()
        if (detectCard.visibility == View.VISIBLE) bindDetectCard()
    }

    // ---------- Player + Saved library ----------

    private fun playItem(item: VideoItem) {
        val url = item.finalUrl ?: item.url
        PlayerActivity.start(
            this,
            url,
            item.title,
            item.kind == VideoItem.Kind.HLS,
            item.userAgent,
            item.referer,
            Http.cookieFor(url)
        )
    }

    private fun setDlTab(saved: Boolean) {
        showSaved = saved
        applyDlTab()
        if (saved) {
            askMediaPermissionOnce()
            renderLibrary()
        } else {
            lockVault(true)
            renderDownloads()
        }
    }

    private fun applyDlTab() {
        dlTabFound.setBackgroundResource(if (!showSaved) R.drawable.bg_chip_on else R.drawable.bg_chip)
        dlTabFound.setTextColor(color(if (!showSaved) R.color.vio_surface else R.color.vio_text))
        dlTabSaved.setBackgroundResource(if (showSaved) R.drawable.bg_chip_on else R.drawable.bg_chip)
        dlTabSaved.setTextColor(color(if (showSaved) R.color.vio_surface else R.color.vio_text))
        dlFoundHeader.visibility = if (showSaved) View.GONE else View.VISIBLE
        dlFoundFrame.visibility = if (showSaved) View.GONE else View.VISIBLE
        libPanel.visibility = if (showSaved) View.VISIBLE else View.GONE
        btnDlSelected.visibility = if (showSaved) View.GONE else View.VISIBLE
        if (showSaved) {
            dlEmpty.visibility = View.GONE
            dlQueueBar.visibility = View.GONE
        }
        libEmpty.visibility = View.GONE
        buildCatChips()
        libSort.text = sortMode.label
    }

    /** Files saved before a reinstall are only visible with media permission. */
    private fun askMediaPermissionOnce() {
        if (askedMedia || Build.VERSION.SDK_INT < 29) return
        askedMedia = true
        val wanted = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            suppressLock = true
            mediaPermLauncher.launch(missing.toTypedArray())
        }
    }

    private fun sizeText(bytes: Long): String = if (bytes <= 0) "0 KB" else formatSize(bytes)

    // ---------- Library: loading ----------

    private fun renderLibrary() {
        val app = applicationContext
        Thread {
            val items = Library.list(app)
            val vb = try {
                Vault.totalBytes(app)
            } catch (_: Exception) {
                0L
            }
            val free = Library.freeBytes()
            uiHandler.post {
                allLib = items
                vaultBytes = vb
                freeBytes = free
                bindLibrary()
            }
        }.start()
    }

    private fun bindLibrary() {
        if (!showSaved || screen != Screen.DOWNLOADS) return
        buildCatChips()
        updateCatChips()
        updateStorage()
        libList.removeAllViews()
        libEmpty.visibility = View.GONE
        libActions.visibility = View.GONE
        applySecure()
        when (libView) {
            LibView.PLAYLISTS -> bindPlaylists()
            LibView.VAULT -> bindVault()
            else -> bindFiles()
        }
    }

    // ---------- Library: chips, search, sort, storage ----------

    private fun buildCatChips() {
        if (libChips.isNotEmpty()) return
        for (v in LibView.values()) {
            val tv = TextView(this)
            tv.textSize = 13f
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(14), 0, dp(14), 0)
            tv.isClickable = true
            tv.isFocusable = true
            tv.setOnClickListener { selectLibView(v) }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
            lp.marginEnd = dp(6)
            libCats.addView(tv, lp)
            libChips.add(tv)
        }
    }

    private fun updateCatChips() {
        val counts = IntArray(Cat.values().size)
        for (li in allLib) counts[li.cat.ordinal]++
        val views = LibView.values()
        for (i in views.indices) {
            val v = views[i]
            val on = v == libView
            val n = when (v) {
                LibView.ALL -> allLib.size
                LibView.VIDEO -> counts[Cat.VIDEO.ordinal]
                LibView.AUDIO -> counts[Cat.AUDIO.ordinal]
                LibView.DOC -> counts[Cat.DOC.ordinal]
                LibView.IMAGE -> counts[Cat.IMAGE.ordinal]
                LibView.OTHER -> counts[Cat.OTHER.ordinal]
                else -> 0
            }
            val tv = libChips[i]
            tv.text = if (n > 0) v.label + " " + n else v.label
            tv.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
            tv.setTextColor(color(if (on) R.color.vio_surface else R.color.vio_text))
        }
    }

    private fun selectLibView(v: LibView) {
        if (v == libView) {
            if (v == LibView.PLAYLISTS && openPlaylist != null) {
                openPlaylist = null
                bindLibrary()
            }
            return
        }
        if (libView == LibView.VAULT) lockVault(false)
        libView = v
        openPlaylist = null
        bindLibrary()
        if (v == LibView.VAULT) requireVault { bindLibrary() }
    }

    private fun showSortDialog() {
        val modes = SortMode.values()
        AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setItems(modes.map { it.label }.toTypedArray()) { _, which ->
                sortMode = modes[which]
                libSort.text = sortMode.label
                bindLibrary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val catColors = intArrayOf(
        0xFF4B42F0.toInt(), // video
        0xFF16A34A.toInt(), // audio
        0xFFF59E0B.toInt(), // docs
        0xFFEC4899.toInt(), // images
        0xFF8A8A93.toInt(), // other
        0xFF111114.toInt()  // vault
    )

    private fun categorySizes(): LongArray {
        val sums = LongArray(6)
        for (li in allLib) sums[li.cat.ordinal] += li.size
        sums[5] = vaultBytes
        return sums
    }

    private fun updateStorage() {
        val sums = categorySizes()
        val total = sums.sum()
        libStorageText.text = "Vio files: " + sizeText(total) + "  •  " + sizeText(freeBytes) + " free on phone"
        libStorageBar.set(sums, catColors, freeBytes)
        val names = arrayOf("Video", "Audio", "Docs", "Images", "Other", "Vault")
        val sb = SpannableStringBuilder()
        for (i in sums.indices) {
            if (sums[i] <= 0) continue
            val start = sb.length
            sb.append("●")
            sb.setSpan(ForegroundColorSpan(catColors[i]), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" " + names[i] + " " + formatSize(sums[i]) + "    ")
        }
        libLegend.text = sb
        libLegend.visibility = if (sb.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showStorageDialog() {
        val sums = categorySizes()
        val names = arrayOf("Video", "Audio", "Docs", "Images", "Other", "Vault")
        val sb = StringBuilder()
        for (i in sums.indices) sb.append(names[i]).append(": ").append(sizeText(sums[i])).append('\n')
        sb.append('\n').append("Free on phone: ").append(sizeText(freeBytes))
        sb.append('\n').append("Temporary files: ").append(sizeText(tempBytes()))
        AlertDialog.Builder(this)
            .setTitle("Storage")
            .setMessage(sb.toString())
            .setPositiveButton("Largest files first") { _, _ ->
                if (libView == LibView.VAULT) lockVault(false)
                libView = LibView.ALL
                openPlaylist = null
                sortMode = SortMode.SIZE
                libSort.text = sortMode.label
                bindLibrary()
            }
            .setNeutralButton("Clear temp files") { _, _ -> clearTempFiles() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun tempBytes(): Long {
        var n = 0L
        try {
            DownloadCenter.tempDir(this).listFiles()?.forEach { n += it.length() }
            Vault.tempDir(this).listFiles()?.forEach { n += it.length() }
        } catch (_: Exception) {
        }
        return n
    }

    private fun clearTempFiles() {
        if (DownloadCenter.activeCount() + DownloadCenter.pausedCount() > 0) {
            toast("Finish or cancel your downloads first")
            return
        }
        var freed = 0L
        try {
            for (dir in listOf(DownloadCenter.tempDir(this), Vault.tempDir(this))) {
                dir.listFiles()?.forEach {
                    freed += it.length()
                    it.delete()
                }
            }
        } catch (_: Exception) {
        }
        toast("Cleared " + sizeText(freed))
        if (screen == Screen.SETTINGS) bindSettings()
        if (showSaved && screen == Screen.DOWNLOADS) updateStorage()
    }

    // ---------- Library: files ----------

    private fun iconFor(cat: Cat): Int = when (cat) {
        Cat.VIDEO -> R.drawable.ic_video
        Cat.AUDIO -> R.drawable.ic_audio
        Cat.DOC -> R.drawable.ic_doc
        Cat.IMAGE -> R.drawable.ic_image
        Cat.OTHER -> R.drawable.ic_file
    }

    private fun filteredFiles(): List<LibItem> {
        var l: List<LibItem> = allLib
        val cat = when (libView) {
            LibView.VIDEO -> Cat.VIDEO
            LibView.AUDIO -> Cat.AUDIO
            LibView.DOC -> Cat.DOC
            LibView.IMAGE -> Cat.IMAGE
            LibView.OTHER -> Cat.OTHER
            else -> null
        }
        if (cat != null) l = l.filter { it.cat == cat }
        val q = libQuery.trim()
        if (q.isNotEmpty()) l = l.filter { it.name.contains(q, true) }
        return when (sortMode) {
            SortMode.NEWEST -> l.sortedByDescending { it.dateSec }
            SortMode.NAME -> l.sortedBy { it.name.lowercase() }
            SortMode.SIZE -> l.sortedByDescending { it.size }
        }
    }

    private fun emptyText(): String {
        if (libQuery.trim().isNotEmpty()) return "No files match your search."
        return when (libView) {
            LibView.VIDEO -> "No videos yet.\nDownloads and recordings show up here."
            LibView.AUDIO -> "No audio yet.\nDownload an mp3 or other sound file and it shows up here."
            LibView.DOC -> "No documents yet.\nPDFs, Word files and more show up here."
            LibView.IMAGE -> "No images yet."
            LibView.OTHER -> "Nothing here.\nZips, apps and other files show up here."
            LibView.PLAYLISTS -> "No playlists yet.\nTap + New to make one."
            else -> "Nothing saved yet.\nDownloads and recordings show up here."
        }
    }

    private fun bindFiles() {
        val items = filteredFiles()
        dlCount.text = "${items.size}"
        libEmpty.text = emptyText()
        libEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if ((libView == LibView.AUDIO || libView == LibView.VIDEO) && items.isNotEmpty()) {
            libActions.visibility = View.VISIBLE
            libActionInfo.text = "${items.size} " + (if (libView == LibView.AUDIO) "tracks" else "videos")
            libPlayAll.visibility = View.VISIBLE
            libPlayAll.text = "Play all"
            libPlayAll.setOnClickListener { playQueue(items, false) }
            libShuffle.visibility = View.VISIBLE
            libShuffle.setOnClickListener { playQueue(items, true) }
            libNew.visibility = View.GONE
        }
        for (li in items) libList.addView(fileRow(li, items, null))
    }

    private fun fileMeta(li: LibItem): String {
        val parts = ArrayList<String>()
        if (li.size > 0) parts.add(formatSize(li.size))
        if (li.durationMs > 0) parts.add(formatDuration(li.durationMs))
        val e = li.ext.uppercase()
        if (e.isNotEmpty()) parts.add(e)
        return parts.joinToString(" • ")
    }

    private fun fileRow(li: LibItem, list: List<LibItem>, plIndex: Int?): View {
        val row = layoutInflater.inflate(R.layout.item_library, libList, false)
        row.findViewById<TextView>(R.id.lTitle).text = li.title
        row.findViewById<TextView>(R.id.lMeta).text = fileMeta(li)
        bindLibThumb(row.findViewById(R.id.lThumb), li)
        row.setOnClickListener { openFile(li, list) }
        row.findViewById<View>(R.id.lMore).setOnClickListener { showLibMenu(li, list, plIndex) }
        return row
    }

    private fun bindLibThumb(thumb: ImageView, li: LibItem) {
        val key = li.uri.toString()
        val ph = iconFor(li.cat)
        val cached = libThumbs.get(key)
        applyThumb(thumb, cached, ph)
        if (cached != null || key in thumbMiss) return
        if (li.cat != Cat.VIDEO && li.cat != Cat.IMAGE && li.cat != Cat.AUDIO) return
        val app = applicationContext
        thumbExec.execute {
            val bmp = Library.thumb(app, li)
            uiHandler.post {
                if (bmp != null) {
                    libThumbs.put(key, bmp)
                    applyThumb(thumb, bmp, ph)
                } else {
                    thumbMiss.add(key)
                }
            }
        }
    }

    private fun openFile(li: LibItem, list: List<LibItem>) {
        when (li.cat) {
            Cat.VIDEO, Cat.AUDIO -> {
                val q = list.filter { it.cat == li.cat }
                val idx = q.indexOfFirst { it.uri == li.uri }.coerceAtLeast(0)
                PlayerActivity.startList(
                    this, q.map { it.uri.toString() }, q.map { it.title }, idx, li.cat == Cat.AUDIO
                )
            }
            Cat.IMAGE -> {
                val q = list.filter { it.cat == Cat.IMAGE }
                val idx = q.indexOfFirst { it.uri == li.uri }.coerceAtLeast(0)
                ImageViewerActivity.start(
                    this, q.map { it.uri.toString() }, q.map { it.name }, idx, false
                )
            }
            Cat.DOC -> {
                if (li.ext == "pdf") PdfViewerActivity.start(this, li.uri.toString(), li.name)
                else openExternal(li)
            }
            Cat.OTHER -> openExternal(li)
        }
    }

    private fun openExternal(li: LibItem) {
        if (li.path != null) {
            toast("Opening this file needs Android 10 or newer")
            return
        }
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(li.uri, li.mime ?: Library.mimeFor(li.name, li.cat))
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(i)
        } catch (_: Exception) {
            toast("No app on this phone can open this file")
        }
    }

    private fun playQueue(items: List<LibItem>, shuffle: Boolean) {
        val q = items.filter { it.cat == Cat.AUDIO || it.cat == Cat.VIDEO }
        if (q.isEmpty()) {
            toast("Nothing to play")
            return
        }
        val audio = q.all { it.cat == Cat.AUDIO }
        val start = if (shuffle) (0 until q.size).random() else 0
        PlayerActivity.startList(
            this, q.map { it.uri.toString() }, q.map { it.title }, start, audio, false, shuffle
        )
    }

    private fun showLibMenu(li: LibItem, list: List<LibItem>, plIndex: Int?) {
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        labels.add("Open")
        acts.add { openFile(li, list) }
        labels.add("Share")
        acts.add { shareLib(li) }
        val pl = openPlaylist
        if (plIndex != null && pl != null) {
            labels.add("Remove from playlist")
            acts.add {
                Playlists.removeAt(pl, plIndex)
                bindLibrary()
            }
        } else if (li.cat == Cat.AUDIO || li.cat == Cat.VIDEO) {
            labels.add("Add to playlist")
            acts.add { addToPlaylist(li) }
        }
        labels.add("Move to Vault")
        acts.add { confirmMoveToVault(li) }
        labels.add("Delete")
        acts.add { confirmDelete(li) }
        AlertDialog.Builder(this)
            .setTitle(li.title)
            .setItems(labels.toTypedArray()) { _, which -> acts[which]() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareLib(li: LibItem) {
        if (li.path != null) {
            toast("Sharing needs Android 10 or newer")
            return
        }
        val send = Intent(Intent.ACTION_SEND)
        send.type = li.mime ?: Library.mimeFor(li.name, li.cat)
        send.putExtra(Intent.EXTRA_STREAM, li.uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share file"))
    }

    private fun confirmDelete(li: LibItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete this file?")
            .setMessage(li.name)
            .setPositiveButton("Delete") { _, _ -> deleteLib(li) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteLib(li: LibItem) {
        if (Library.delete(this, li)) {
            toast("Deleted")
            renderLibrary()
            return
        }
        // Files from an earlier install need the user's OK (Android 11+)
        if (Build.VERSION.SDK_INT >= 30 && li.path == null) {
            try {
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(li.uri))
                pendingDelete = li
                suppressLock = true
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                return
            } catch (_: Exception) {
            }
        }
        toast("Couldn't delete this file")
    }

    // ---------- Library: playlists ----------

    private fun askNewPlaylist(onCreated: (String) -> Unit) {
        val input = EditText(this)
        input.hint = "Playlist name"
        input.maxLines = 1
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(this)
            .setTitle("New playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isEmpty()) {
                    toast("Type a name first")
                } else if (Playlists.create(n)) {
                    onCreated(n)
                } else {
                    toast("A playlist with that name already exists")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addToPlaylist(li: LibItem) {
        val lists = Playlists.all()
        val names = ArrayList<String>()
        for (p in lists) names.add(p.name)
        names.add("+ New playlist")
        AlertDialog.Builder(this)
            .setTitle("Add to playlist")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == lists.size) {
                    askNewPlaylist { n ->
                        Playlists.addTo(n, li.uri.toString(), li.title)
                        toast("Added to $n")
                        bindLibrary()
                    }
                } else {
                    val ok = Playlists.addTo(lists[which].name, li.uri.toString(), li.title)
                    toast(if (ok) "Added to ${lists[which].name}" else "Already in this playlist")
                    bindLibrary()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun bindPlaylists() {
        val open = openPlaylist
        if (open == null) {
            val lists = Playlists.all()
            dlCount.text = "${lists.size}"
            libActions.visibility = View.VISIBLE
            libActionInfo.text = "Your playlists"
            libPlayAll.visibility = View.GONE
            libShuffle.visibility = View.GONE
            libNew.visibility = View.VISIBLE
            libNew.setOnClickListener { askNewPlaylist { bindLibrary() } }
            libEmpty.text = emptyText()
            libEmpty.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
            for (p in lists) libList.addView(playlistRow(p))
            return
        }
        val pl = Playlists.find(open)
        if (pl == null) {
            openPlaylist = null
            bindPlaylists()
            return
        }
        val byUri = HashMap<String, LibItem>()
        for (li in allLib) byUri[li.uri.toString()] = li
        val resolved = ArrayList<Pair<Int, LibItem>>()
        for (i in pl.items.indices) {
            val li = byUri[pl.items[i].uri]
            if (li != null) resolved.add(Pair(i, li))
        }
        val q = libQuery.trim()
        val shown = if (q.isEmpty()) resolved else resolved.filter { it.second.name.contains(q, true) }
        val files = shown.map { it.second }
        dlCount.text = "${shown.size}"
        libActions.visibility = View.VISIBLE
        libActionInfo.text = pl.name
        libPlayAll.visibility = View.VISIBLE
        libPlayAll.text = "Play all"
        libPlayAll.setOnClickListener { playQueue(files, false) }
        libShuffle.visibility = View.VISIBLE
        libShuffle.setOnClickListener { playQueue(files, true) }
        libNew.visibility = View.GONE
        libEmpty.text = if (resolved.isEmpty()) {
            "This playlist is empty.\nOpen the ⋮ menu on a song or video and choose Add to playlist."
        } else {
            emptyText()
        }
        libEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        for (pair in shown) libList.addView(fileRow(pair.second, files, pair.first))
    }

    private fun playlistRow(p: Playlist): View {
        val row = layoutInflater.inflate(R.layout.item_library, libList, false)
        row.findViewById<TextView>(R.id.lTitle).text = p.name
        val have = HashSet<String>()
        for (li in allLib) have.add(li.uri.toString())
        val n = p.items.count { it.uri in have }
        row.findViewById<TextView>(R.id.lMeta).text = if (n == 1) "1 item" else "$n items"
        applyThumb(row.findViewById(R.id.lThumb), null, R.drawable.ic_audio)
        row.setOnClickListener {
            openPlaylist = p.name
            bindLibrary()
        }
        row.findViewById<View>(R.id.lMore).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(p.name)
                .setItems(arrayOf("Open", "Rename", "Delete playlist")) { _, which ->
                    when (which) {
                        0 -> {
                            openPlaylist = p.name
                            bindLibrary()
                        }
                        1 -> renamePlaylist(p.name)
                        else -> AlertDialog.Builder(this)
                            .setTitle("Delete playlist?")
                            .setMessage("Your files stay on the phone. Only the playlist is removed.")
                            .setPositiveButton("Delete") { _, _ ->
                                Playlists.delete(p.name)
                                bindLibrary()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        return row
    }

    private fun renamePlaylist(old: String) {
        val input = EditText(this)
        input.setText(old)
        input.maxLines = 1
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(this)
            .setTitle("Rename playlist")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                if (Playlists.rename(old, input.text.toString())) bindLibrary()
                else toast("Pick a different name")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Vault ----------

    /** Runs [action] right away if the Vault is open, otherwise after fingerprint / PIN. */
    private fun requireVault(action: () -> Unit) {
        if (vaultUnlocked) {
            action()
            return
        }
        if (!AppLock.available(this) || !Vault.isAvailable()) {
            toast("Set a screen lock or fingerprint on your phone to use the Vault")
            return
        }
        if (authRunning) return
        authRunning = true
        suppressLock = true
        AppLock.authenticate(
            this,
            "Open Vault",
            {
                authRunning = false
                suppressLock = false
                vaultUnlocked = true
                action()
            },
            {
                authRunning = false
                suppressLock = false
            }
        )
    }

    /** Closes the Vault, wipes decrypted previews, and (optionally) leaves the Vault tab. */
    private fun lockVault(leaveTab: Boolean) {
        vaultUnlocked = false
        Vault.clearTemp(applicationContext)
        if (leaveTab && libView == LibView.VAULT) libView = LibView.ALL
        applySecure()
        if (!leaveTab && showSaved && screen == Screen.DOWNLOADS && libView == LibView.VAULT) bindLibrary()
    }

    /** Hide the screen from screenshots / recents while vault files are listed. */
    private fun applySecure() {
        val secure = showSaved && screen == Screen.DOWNLOADS && libView == LibView.VAULT && vaultUnlocked
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun bindVault() {
        if (!vaultUnlocked) {
            dlCount.text = ""
            val card = TextView(this)
            card.setBackgroundResource(R.drawable.bg_card)
            card.setPadding(dp(16), dp(24), dp(16), dp(24))
            card.gravity = Gravity.CENTER
            card.setLineSpacing(0f, 1.2f)
            card.text = "Vault is locked\nTap to unlock with fingerprint, face or phone PIN"
            card.setTextColor(color(R.color.vio_text))
            card.textSize = 14f
            card.setOnClickListener { requireVault { bindLibrary() } }
            libList.addView(card)
            return
        }
        val app = applicationContext
        var entries: List<VaultEntry> = try {
            Vault.entries(app)
        } catch (_: Exception) {
            emptyList()
        }
        val q = libQuery.trim()
        if (q.isNotEmpty()) entries = entries.filter { it.name.contains(q, true) }
        entries = when (sortMode) {
            SortMode.NEWEST -> entries.sortedByDescending { it.addedAt }
            SortMode.NAME -> entries.sortedBy { it.name.lowercase() }
            SortMode.SIZE -> entries.sortedByDescending { it.size }
        }
        dlCount.text = "${entries.size}"
        libActions.visibility = View.VISIBLE
        libActionInfo.text = "${entries.size} private files • encrypted"
        libPlayAll.visibility = View.VISIBLE
        libPlayAll.text = "Lock now"
        libPlayAll.setOnClickListener { lockVault(false) }
        libShuffle.visibility = View.GONE
        libNew.visibility = View.GONE
        libEmpty.text = if (q.isNotEmpty()) "No files match your search." else
            "Your Vault is empty.\nOpen the ⋮ menu on a file and choose Move to Vault."
        libEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        for (e in entries) libList.addView(vaultRow(e, entries))
    }

    private fun vaultRow(e: VaultEntry, list: List<VaultEntry>): View {
        val row = layoutInflater.inflate(R.layout.item_library, libList, false)
        row.findViewById<TextView>(R.id.lTitle).text = e.title
        val parts = ArrayList<String>()
        if (e.size > 0) parts.add(formatSize(e.size))
        if (e.ext.isNotEmpty()) parts.add(e.ext.uppercase())
        parts.add(e.cat.label)
        row.findViewById<TextView>(R.id.lMeta).text = parts.joinToString(" • ")
        applyThumb(row.findViewById(R.id.lThumb), null, iconFor(e.cat))
        row.setOnClickListener { openVault(e, list) }
        row.findViewById<View>(R.id.lMore).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(e.title)
                .setItems(arrayOf("Open", "Restore to library", "Delete permanently")) { _, which ->
                    when (which) {
                        0 -> openVault(e, list)
                        1 -> restoreFromVault(e)
                        else -> AlertDialog.Builder(this)
                            .setTitle("Delete from Vault?")
                            .setMessage("This can't be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                Vault.remove(applicationContext, e.id)
                                renderLibrary()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        return row
    }

    private fun openVault(e: VaultEntry, list: List<VaultEntry>) {
        when (e.cat) {
            Cat.VIDEO, Cat.AUDIO -> {
                val q = list.filter { it.cat == e.cat }
                val idx = q.indexOfFirst { it.id == e.id }.coerceAtLeast(0)
                PlayerActivity.startList(
                    this, q.map { "vio-vault://" + it.id }, q.map { it.title }, idx, e.cat == Cat.AUDIO, true
                )
            }
            Cat.IMAGE -> {
                val q = list.filter { it.cat == Cat.IMAGE }
                val idx = q.indexOfFirst { it.id == e.id }.coerceAtLeast(0)
                ImageViewerActivity.start(this, q.map { "vio-vault://" + it.id }, q.map { it.name }, idx, true)
            }
            Cat.DOC -> {
                if (e.ext == "pdf") PdfViewerActivity.startVault(this, e.id, e.name) else cantOpenInVault(e)
            }
            Cat.OTHER -> cantOpenInVault(e)
        }
    }

    private fun cantOpenInVault(e: VaultEntry) {
        AlertDialog.Builder(this)
            .setTitle(e.title)
            .setMessage("This kind of file can't be opened inside the Vault. Restore it to your library to open it with another app.")
            .setPositiveButton("Restore") { _, _ -> restoreFromVault(e) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreFromVault(e: VaultEntry) {
        toast("Restoring...")
        val app = applicationContext
        Thread {
            var saved: String? = null
            var err = ""
            try {
                saved = Vault.restore(app, e)
            } catch (ex: Exception) {
                err = ex.message ?: "error"
            }
            uiHandler.post {
                toast(if (saved != null) "Restored to " + Library.folder(e.cat) else "Couldn't restore: $err")
                renderLibrary()
            }
        }.start()
    }

    private fun confirmMoveToVault(li: LibItem) {
        AlertDialog.Builder(this)
            .setTitle("Move to Vault?")
            .setMessage("The file is encrypted and hidden from your gallery and other apps. Opening the Vault needs your fingerprint, face or phone PIN.")
            .setPositiveButton("Move") { _, _ -> requireVault { moveToVault(li) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun moveToVault(li: LibItem) {
        if (movingToVault) {
            toast("Still moving another file")
            return
        }
        movingToVault = true
        toast("Encrypting... keep Vio open")
        val app = applicationContext
        Thread {
            var ok = false
            var err = ""
            try {
                val stream = app.contentResolver.openInputStream(li.uri) ?: throw java.io.IOException("Can't open file")
                val mime = li.mime ?: Library.mimeFor(li.name, li.cat)
                stream.use { Vault.add(app, it, li.name, mime, li.cat) }
                ok = true
            } catch (ex: Exception) {
                err = ex.message ?: "error"
            }
            uiHandler.post {
                movingToVault = false
                if (ok) removeOriginal(li) else toast("Couldn't move to Vault: $err")
            }
        }.start()
    }

    /** After the encrypted copy is safe, remove the plain file from the library. */
    private fun removeOriginal(li: LibItem) {
        if (Library.delete(this, li)) {
            toast("Moved to Vault")
            renderLibrary()
            return
        }
        if (Build.VERSION.SDK_INT >= 30 && li.path == null) {
            try {
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(li.uri))
                pendingMove = true
                suppressLock = true
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                return
            } catch (_: Exception) {
            }
        }
        toast("Copied to Vault, but the original couldn't be removed from your library")
        renderLibrary()
    }

    // ---------- Shields ----------

    private fun injectShields(view: WebView?, url: String?) {
        if (view == null || url == null) return
        if (Settings.shieldsOffFor(hostOf(url))) return
        if (Settings.blockAds) view.evaluateJavascript(Shields.COSMETIC_JS, null)
        if (Settings.fingerprintProtection) view.evaluateJavascript(Shields.FINGERPRINT_JS, null)
    }

    private fun showShieldDialog() {
        val tab = currentTab ?: return
        val host = hostOf(tab.url)
        if (host.isEmpty()) return
        val off = Settings.shieldsOffFor(host)
        val msg = when {
            !Settings.blockAds -> "Ad and tracker blocking is off in Settings."
            off -> "Shields are OFF for $host."
            else -> "Shields are ON for $host.\n\nBlocked on this page: ${tab.blocked.get()}"
        }
        val b = AlertDialog.Builder(this).setTitle("Shields").setMessage(msg)
        if (Settings.blockAds) {
            b.setPositiveButton(if (off) "Turn on" else "Turn off for this site") { _, _ ->
                Settings.setShieldsOff(host, !off)
                reinstallDocScripts()
                tab.webView.reload()
                updateUi()
            }
        }
        b.setNegativeButton("Close", null).show()
    }

    private fun applyCookiePolicy() {
        for (t in tabs) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(t.webView, !Settings.blockThirdPartyCookies)
        }
    }

    // ---------- Settings screen ----------

    private fun bindSettings() {
        swAds.setOnCheckedChangeListener(null)
        swHttps.setOnCheckedChangeListener(null)
        swCookies.setOnCheckedChangeListener(null)
        swFp.setOnCheckedChangeListener(null)
        swLockApp.setOnCheckedChangeListener(null)
        swLockPrivate.setOnCheckedChangeListener(null)

        swAds.isChecked = Settings.blockAds
        swHttps.isChecked = Settings.httpsUpgrade
        swCookies.isChecked = Settings.blockThirdPartyCookies
        swFp.isChecked = Settings.fingerprintProtection
        swLockApp.isChecked = Settings.lockApp
        swLockPrivate.isChecked = Settings.lockPrivate

        swAds.setOnCheckedChangeListener { _, c ->
            Settings.blockAds = c
            reinstallDocScripts()
        }
        swHttps.setOnCheckedChangeListener { _, c -> Settings.httpsUpgrade = c }
        swCookies.setOnCheckedChangeListener { _, c ->
            Settings.blockThirdPartyCookies = c
            applyCookiePolicy()
        }
        swFp.setOnCheckedChangeListener { _, c ->
            Settings.fingerprintProtection = c
            reinstallDocScripts()
        }
        findViewById<TextView>(R.id.rowMaxDlSub).text =
            "${Settings.maxDownloads} at a time  •  tap to change"
        findViewById<TextView>(R.id.rowClearTempSub).text =
            sizeText(tempBytes()) + " of leftover downloads and vault previews"

        swLockApp.setOnCheckedChangeListener { _, c ->
            changeLock(c) { Settings.lockApp = c }
        }
        swLockPrivate.setOnCheckedChangeListener { _, c ->
            changeLock(c) { Settings.lockPrivate = c }
        }
    }

    /** Changing a lock setting needs the phone's fingerprint / PIN first. */
    private fun changeLock(turnOn: Boolean, apply: () -> Unit) {
        if (!AppLock.available(this)) {
            toast("Set a screen lock or fingerprint on your phone first")
            bindSettings()
            return
        }
        suppressLock = true
        authRunning = true
        AppLock.authenticate(
            this,
            if (turnOn) "Turn on lock" else "Turn off lock",
            {
                authRunning = false
                suppressLock = false
                apply()
                bindSettings()
            },
            {
                authRunning = false
                suppressLock = false
                bindSettings()
            }
        )
    }

    // ---------- Lock ----------

    private fun showLock() {
        val existing = lockView
        val v: View
        if (existing == null) {
            v = layoutInflater.inflate(R.layout.view_lock, null)
            addContentView(
                v,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            v.findViewById<View>(R.id.lockUnlock).setOnClickListener { promptUnlock() }
            lockView = v
        } else {
            v = existing
        }
        v.visibility = View.VISIBLE
        hideKeyboard()
        promptUnlock()
    }

    private fun promptUnlock() {
        if (authRunning) return
        if (!AppLock.available(this)) {
            lockView?.visibility = View.GONE
            toast("No screen lock set on this phone, so Vio can't lock")
            return
        }
        authRunning = true
        suppressLock = true
        AppLock.authenticate(
            this,
            "Unlock Vio",
            {
                authRunning = false
                suppressLock = false
                privateUnlocked = true
                lockView?.visibility = View.GONE
            },
            {
                authRunning = false
                suppressLock = false
            }
        )
    }

    /** Runs [action] right away, or after the lock prompt if private tabs are locked. */
    private fun requirePrivate(action: () -> Unit) {
        if (!Settings.lockPrivate || privateUnlocked) {
            action()
            return
        }
        if (!AppLock.available(this)) {
            action()
            return
        }
        if (authRunning) return
        authRunning = true
        suppressLock = true
        AppLock.authenticate(
            this,
            "Open private tabs",
            {
                authRunning = false
                suppressLock = false
                privateUnlocked = true
                action()
            },
            {
                authRunning = false
                suppressLock = false
            }
        )
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        uiHandler.post(syncRunnable)
    }

    override fun onStop() {
        stoppedAt = if (suppressLock || authRunning) 0L else SystemClock.elapsedRealtime()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        val t = stoppedAt
        stoppedAt = 0L
        if (t > 0 && SystemClock.elapsedRealtime() - t > 15000) {
            privateUnlocked = false
            if (vaultUnlocked) lockVault(false)
            if (Settings.lockApp) {
                showLock()
            } else if (Settings.lockPrivate && screen == Screen.BROWSE && currentTab?.isPrivate == true) {
                goHome()
            }
        }
    }

    override fun onPause() {
        uiHandler.removeCallbacks(syncRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        DownloadCenter.onEvent = null
        DownloadCenter.onChange = null
        for (t in tabs) {
            try {
                t.webView.destroy()
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }
}
