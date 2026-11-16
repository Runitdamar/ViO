package com.runit.webrecorder

import android.Manifest
import java.util.concurrent.atomic.AtomicBoolean
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.os.SystemClock
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.URLUtil
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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

    // Browsing features
    private lateinit var urlRow: View
    private lateinit var btnSite: ImageView
    private lateinit var btnMenu: ImageView
    private lateinit var bottomNav: View
    private lateinit var navDivider: View
    private lateinit var fsExit: View
    private lateinit var suggestCard: View
    private lateinit var suggestList: LinearLayout
    private lateinit var tabsSearch: EditText
    private lateinit var tabsTitle: TextView
    private lateinit var shortcutsSection: View
    private lateinit var shortcutsGrid: LinearLayout
    private lateinit var swSuggest: SwitchCompat
    private lateinit var swSwipe: SwitchCompat
    private lateinit var swPull: SwitchCompat
    private lateinit var swShortcuts: SwitchCompat
    private lateinit var swRecentHome: SwitchCompat
    private var tabQuery = ""
    private lateinit var wallpaperView: ImageView
    private lateinit var wallpaperScrim: View
    private lateinit var homeContent: LinearLayout
    private lateinit var homeBrandText: TextView
    private lateinit var homeSearchRow: View
    private lateinit var homeTiles: View
    private lateinit var shortcutsHeader: TextView
    private lateinit var recentHeader: TextView
    private var appliedDark = false
    private var navIndex = 0
    private var barActive = false
    private var browseFs = false
    private var browseFsFlags = 0
    private var sugSeq = 0
    private var sugAnchor: EditText? = null
    private val sugExec = Executors.newSingleThreadExecutor()
    private var pendingSiteAction: ((Map<String, Boolean>) -> Unit)? = null
    private var swipeActive = false
    private var swipeDone = false
    private var swipeFromLeft = false
    private var swipeX = 0f
    private var swipeY = 0f

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

    // Camera / microphone / location the phone must allow before a site can use them
    private val sitePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        suppressLock = false
        val cb = pendingSiteAction
        pendingSiteAction = null
        cb?.invoke(result)
    }

    private val wallpaperPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        suppressLock = false
        if (uri != null) {
            val appCtx = applicationContext
            Thread {
                val ok = Wallpaper.savePhoto(appCtx, uri)
                uiHandler.post {
                    if (ok) {
                        Settings.wallpaperKind = 2
                        bindSettings()
                        toast("Wallpaper set. See it on the start page")
                    } else {
                        toast("Couldn't use that picture")
                    }
                }
            }.start()
        }
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { res ->
        suppressLock = false
        val text = res?.contents
        if (text.isNullOrBlank()) toast("Nothing scanned") else onScanned(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        SearchEngines.init(this)
        SitePermissions.init(this)
        StartPage.init(this)
        TabStore.init(this)
        Playlists.init(this)
        Shields.load(this)
        DownloadCenter.applyParallelism()
        val appCtx = applicationContext
        Thread {
            Vault.cleanOrphans(appCtx)
            Vault.clearTemp(appCtx)
        }.start()
        VioTheme.apply(this)
        appliedDark = VioTheme.isDark(this)
        setContentView(R.layout.activity_main)

        bindViews()
        setupClicks()
        VioTheme.applyBars(this)
        applyToolbarLayout()
        applyToolbarButtons()

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
                if (browseFs) {
                    setBrowseFullscreen(false)
                    return
                }
                if (suggestCard.visibility == View.VISIBLE) {
                    hideSuggestions()
                    hideKeyboard()
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

        restorePinned()
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
        btnSite = findViewById(R.id.btnSite)
        btnMenu = findViewById(R.id.btnMenu)
        urlRow = findViewById(R.id.urlRow)
        bottomNav = findViewById(R.id.bottomNav)
        navDivider = findViewById(R.id.navDivider)
        fsExit = findViewById(R.id.fsExit)
        suggestCard = findViewById(R.id.suggestCard)
        suggestList = findViewById(R.id.suggestList)
        shortcutsSection = findViewById(R.id.shortcutsSection)
        shortcutsGrid = findViewById(R.id.shortcutsGrid)
        wallpaperView = findViewById(R.id.wallpaperView)
        wallpaperScrim = findViewById(R.id.wallpaperScrim)
        homeContent = findViewById(R.id.homeContent)
        homeBrandText = findViewById(R.id.homeBrandText)
        homeSearchRow = findViewById(R.id.homeSearchRow)
        homeTiles = findViewById(R.id.homeTiles)
        shortcutsHeader = findViewById(R.id.shortcutsHeader)
        recentHeader = findViewById(R.id.recentHeader)
        swSuggest = findViewById(R.id.swSuggest)
        swSwipe = findViewById(R.id.swSwipe)
        swPull = findViewById(R.id.swPull)
        swShortcuts = findViewById(R.id.swShortcuts)
        swRecentHome = findViewById(R.id.swRecentHome)
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
        tabsSearch = findViewById(R.id.tabsSearch)
        tabsTitle = findViewById(R.id.tabsTitle)
        chips = listOf(
            findViewById<TextView>(R.id.chipAll),
            findViewById<TextView>(R.id.chipNormal),
            findViewById<TextView>(R.id.chipPrivate),
            findViewById<TextView>(R.id.chipRecent),
            findViewById<TextView>(R.id.chipClosed)
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
        setupSuggestions()
        findViewById<View>(R.id.btnQrHome).setOnClickListener { startScan() }
        btnSite.setOnClickListener { showSiteDialog() }
        btnMenu.setOnClickListener { showBrowseMenu() }
        fsExit.setOnClickListener { setBrowseFullscreen(false) }
        findViewById<View>(R.id.rowEngine).setOnClickListener { showEngineDialog() }
        findViewById<View>(R.id.rowGreetName).setOnClickListener { askGreetName() }
        findViewById<View>(R.id.rowPerms).setOnClickListener { showPermManager() }
        findViewById<View>(R.id.rowTheme).setOnClickListener { showThemeDialog() }
        findViewById<View>(R.id.rowAccent).setOnClickListener { showAccentDialog() }
        findViewById<View>(R.id.rowWallpaper).setOnClickListener { showWallpaperDialog() }
        findViewById<View>(R.id.rowHomeLayout).setOnClickListener { showHomeLayoutDialog() }
        findViewById<View>(R.id.rowToolbar).setOnClickListener { showToolbarDialog() }
        findViewById<View>(R.id.rowGestures).setOnClickListener { showGesturesDialog() }
        findViewById<View>(R.id.rowTextSize).setOnClickListener { showTextSizeDialog() }
        findViewById<View>(R.id.rowFonts).setOnClickListener { showFontsDialog() }
        tabCount.setOnLongClickListener {
            runGesture(Gestures.get(GSlot.TABS_HOLD))
            true
        }
        tabsSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                if (q != tabQuery) {
                    tabQuery = q
                    renderTabs()
                }
            }
        })

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
        navIndex = nav
        updateBars()
        homeView.visibility = if (s == Screen.HOME) View.VISIBLE else View.GONE
        browseView.visibility = if (s == Screen.BROWSE) View.VISIBLE else View.GONE
        tabsView.visibility = if (s == Screen.TABS) View.VISIBLE else View.GONE
        downloadsView.visibility = if (s == Screen.DOWNLOADS) View.VISIBLE else View.GONE
        placeholderView.visibility = if (s == Screen.PLACEHOLDER) View.VISIBLE else View.GONE
        settingsView.visibility = if (s == Screen.SETTINGS) View.VISIBLE else View.GONE

        for (i in navIcons.indices) {
            val c = color(if (i == nav) R.attr.vioAccent else R.attr.vioSubtext)
            navIcons[i].setColorFilter(c)
            navTexts[i].setTextColor(c)
        }

        if (s != Screen.BROWSE) {
            hideDetectCard()
            setBrowseFullscreen(false)
        }
        hideSuggestions()
        if (s != Screen.HOME) hideKeyboard()
        if (s != Screen.DOWNLOADS) lockVault(true)

        when (s) {
            Screen.HOME -> {
                applyHomeMode()
                applyHomeLayout()
                renderShortcuts()
                renderRecent()
            }
            Screen.BROWSE -> applyPrivateStyle()
            Screen.TABS -> {
                if (tabQuery.isNotEmpty()) {
                    tabQuery = ""
                    tabsSearch.setText("")
                }
                renderTabs()
            }
            Screen.DOWNLOADS -> {
                applyDlTab()
                renderDownloads()
                if (showSaved) renderLibrary()
            }
            Screen.SETTINGS -> bindSettings()
            Screen.PLACEHOLDER -> {}
        }
        applyWallpaper()
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
            greeting.setTextColor(color(R.attr.vioAccent))
            homeSubtitle.text = "Pages you visit here won't be saved to Recent"
            recentSection.visibility = View.GONE
        } else {
            greeting.text = greetingText()
            greeting.setTextColor(color(R.attr.vioText))
            homeSubtitle.text = "Search the web or type a URL"
            recentSection.visibility = if (Settings.showRecent) View.VISIBLE else View.GONE
        }
    }

    private fun applyPrivateStyle() {
        privateBadge.visibility = if (currentTab?.isPrivate == true) View.VISIBLE else View.GONE
    }

    private fun greetingText(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val base = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val name = Settings.greetName
        return if (name.isEmpty()) "$base." else "$base, $name."
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
        st.setSupportMultipleWindows(true)
        applyPageText(st)
        if (isPrivate) {
            st.cacheMode = WebSettings.LOAD_NO_CACHE
        }

        if (Settings.fingerprintProtection) st.userAgentString = Shields.reduceUa(st.userAgentString)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !Settings.blockThirdPartyCookies)

        val sniffer = VideoSniffer(id) { itemIds.getAndIncrement() }
        sniffer.userAgent = st.userAgentString
        sniffer.onChange = { uiHandler.post { updateUi() } }

        val tab = Tab(id, isPrivate, wv, sniffer)
        tab.mobileUa = st.userAgentString
        tab.mobileWide = st.useWideViewPort
        tab.mobileOverview = st.loadWithOverviewMode

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
                syncDesktop(tab, orig)
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
                tab.refresher?.isRefreshing = false
                if (url != null && !tab.isPrivate) addRecent(view?.title, url)
                if (tab.pinned) persistPinned()
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

            // A link that opens a new window (target=_blank, window.open): becomes a new tab
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (!isUserGesture || resultMsg == null) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val t = createTab(tab.isPrivate)
                t.group = tab.group
                t.openerId = tab.id
                transport.webView = t.webView
                resultMsg.sendToTarget()
                uiHandler.post {
                    switchTo(t)
                    showScreen(Screen.BROWSE, 1)
                }
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                uiHandler.post {
                    val t = tabs.firstOrNull { it.webView === window } ?: return@post
                    val wasCurrent = t === currentTab
                    closeTab(t, false)
                    if (wasCurrent && screen == Screen.BROWSE) {
                        val next = currentTab
                        if (next != null) switchTo(next) else goHome()
                    }
                    updateUi()
                }
            }

            // A site asks for the camera or microphone
            override fun onPermissionRequest(request: PermissionRequest?) {
                val req = request ?: return
                uiHandler.post {
                    val needs = ArrayList<Perm>()
                    for (r in req.resources) {
                        if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE) needs.add(Perm.CAMERA)
                        if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE) needs.add(Perm.MIC)
                    }
                    if (needs.isEmpty()) {
                        try {
                            req.deny()
                        } catch (_: Exception) {
                        }
                        return@post
                    }
                    askSitePermissions(tab, req.origin.toString(), needs) { ok ->
                        try {
                            if (ok) {
                                val granted = ArrayList<String>()
                                if (needs.contains(Perm.CAMERA)) granted.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                if (needs.contains(Perm.MIC)) granted.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                req.grant(granted.toTypedArray())
                            } else {
                                req.deny()
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            // A site asks for your location
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val cb = callback ?: return
                val o = origin ?: return
                askSitePermissions(tab, o, listOf(Perm.LOCATION)) { ok ->
                    try {
                        cb.invoke(o, ok, false)
                    } catch (_: Exception) {
                    }
                }
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
            uiHandler.post {
                var owner = tab
                // A new window that only turned out to be a download: drop it, go back to the page
                if (tab.url.isEmpty() && tab.openerId != 0) {
                    val op = tabs.firstOrNull { it.id == tab.openerId }
                    if (op != null) {
                        owner = op
                        val wasCurrent = tab === currentTab
                        closeTab(tab, false)
                        if (wasCurrent) switchTo(op)
                    }
                }
                onPageDownload(owner, url, userAgent, contentDisposition, mimeType, contentLength)
            }
        }

        // Press and hold on a link or an image
        wv.setOnLongClickListener {
            val hit = wv.hitTestResult
            val extra = hit.extra
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    if (extra != null) showLinkMenu(tab, extra, null)
                    extra != null
                }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val h = Handler(Looper.getMainLooper()) { m ->
                        val link = m.data.getString("url")
                        val src = m.data.getString("src")
                        if (!link.isNullOrEmpty()) {
                            showLinkMenu(tab, link, if (src.isNullOrEmpty()) null else src)
                        } else if (!src.isNullOrEmpty()) {
                            showLinkMenu(tab, null, src)
                        }
                        true
                    }
                    wv.requestFocusNodeHref(h.obtainMessage())
                    true
                }
                WebView.HitTestResult.IMAGE_TYPE -> {
                    if (extra != null) showLinkMenu(tab, null, extra)
                    extra != null
                }
                else -> false
            }
        }

        // The page sits inside a pull-to-refresh layout
        val holder = SwipeRefreshLayout(this)
        holder.addView(
            wv,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        holder.setColorSchemeColors(color(R.attr.vioAccent))
        holder.setProgressBackgroundColorSchemeColor(color(R.attr.vioSurface))
        holder.setOnChildScrollUpCallback { _, _ -> wv.scrollY > 0 }
        holder.setOnRefreshListener {
            wv.reload()
            uiHandler.postDelayed({ holder.isRefreshing = false }, 1500)
        }
        holder.isEnabled = Settings.pullRefresh
        holder.visibility = View.GONE
        tab.holder = holder
        tab.refresher = holder
        // Index 0 keeps the detect card above every page
        webContainer.addView(
            holder,
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

    // ---------- Small dialogs and helpers ----------

    private fun promptText(
        title: String,
        hint: String,
        initial: String,
        allowEmpty: Boolean,
        onOk: (String) -> Unit
    ) {
        val input = EditText(this)
        input.hint = hint
        input.maxLines = 1
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        input.setText(initial)
        input.setSelection(input.text.length)
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(
            input,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotEmpty() || allowEmpty) onOk(v)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Two fields. [onOk] returns an error message to show (and ask again), or null when it worked. */
    private fun promptTwo(
        title: String,
        hint1: String,
        v1: String,
        hint2: String,
        v2: String,
        onOk: (String, String) -> String?
    ) {
        val a = EditText(this)
        a.hint = hint1
        a.maxLines = 1
        a.inputType = android.text.InputType.TYPE_CLASS_TEXT
        a.setText(v1)
        val b = EditText(this)
        b.hint = hint2
        b.maxLines = 1
        b.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        b.setText(v2)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(
            a,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        box.addView(
            b,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val x = a.text.toString().trim()
                val y = b.text.toString().trim()
                val err = onOk(x, y)
                if (err != null) {
                    toast(err)
                    promptTwo(title, hint1, x, hint2, y, onOk)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyText(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Vio", text))
        toast("Copied")
    }

    private fun shareText(text: String) {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, text)
        try {
            startActivity(Intent.createChooser(i, "Share"))
        } catch (_: Exception) {
            toast("Nothing to share with")
        }
    }

    // ---------- Long-press on a link or image ----------

    private fun showLinkMenu(tab: Tab, link: String?, image: String?) {
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        val shown = link ?: image ?: return
        if (link != null) {
            val web = link.startsWith("http://") || link.startsWith("https://")
            if (web) {
                labels.add("Open in new tab")
                acts.add { openLinkTab(tab, link, true) }
                labels.add("Open in background tab")
                acts.add { openLinkTab(tab, link, false) }
                if (!tab.isPrivate) {
                    labels.add("Open in private tab")
                    acts.add {
                        requirePrivate {
                            val t = createTab(true)
                            homePrivate = true
                            openInTab(t, link)
                        }
                    }
                }
            }
            labels.add("Copy link")
            acts.add { copyText(link) }
            if (web) {
                labels.add("Share link")
                acts.add { shareText(link) }
            }
        }
        if (image != null) {
            val web = image.startsWith("http://") || image.startsWith("https://")
            if (web) {
                labels.add("Download image")
                acts.add { onPageDownload(tab, image, tab.mobileUa.ifEmpty { null }, null, null, -1L) }
                if (link == null) {
                    labels.add("Open image in new tab")
                    acts.add { openLinkTab(tab, image, true) }
                }
            }
            labels.add("Copy image address")
            acts.add { copyText(image) }
        }
        AlertDialog.Builder(this)
            .setTitle(if (shown.length > 70) shown.take(67) + "..." else shown)
            .setItems(labels.toTypedArray()) { _, i -> acts[i]() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Address bar suggestions ----------

    private class Sug(val text: String, val sub: String, val local: Boolean, val target: String)

    private val sugRunnable = Runnable { fetchSuggestions() }

    private fun setupSuggestions() {
        urlBar.setSelectAllOnFocus(true)
        for (edit in listOf(homeSearch, urlBar)) {
            edit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onTyping(edit)
                }
            })
            edit.setOnFocusChangeListener { _, has ->
                if (!has) hideSuggestions()
            }
        }
        suggestCard.setOnClickListener { }
    }

    private fun onTyping(edit: EditText) {
        sugSeq++
        val q = edit.text.toString().trim()
        if (!edit.hasFocus() || q.isEmpty()) {
            hideSuggestions()
            return
        }
        sugAnchor = edit
        showSuggestions(q, emptyList())
        uiHandler.removeCallbacks(sugRunnable)
        if (Settings.searchSuggestions) uiHandler.postDelayed(sugRunnable, 220)
    }

    private fun fetchSuggestions() {
        val edit = sugAnchor ?: return
        val q = edit.text.toString().trim()
        if (q.isEmpty() || !Settings.searchSuggestions || !edit.hasFocus()) return
        if (q.startsWith("http://") || q.startsWith("https://")) return
        val seq = sugSeq
        sugExec.execute {
            val res = try {
                SearchEngines.suggestions(q)
            } catch (_: Exception) {
                emptyList<String>()
            }
            uiHandler.post {
                if (seq == sugSeq && edit.hasFocus() && edit.text.toString().trim() == q) {
                    showSuggestions(q, res)
                }
            }
        }
    }

    private fun showSuggestions(q: String, net: List<String>) {
        val edit = sugAnchor ?: return
        val rows = ArrayList<Sug>()
        val looksLikeSite = !q.contains(" ") && (q.contains(".") || q.startsWith("http"))
        rows.add(
            if (looksLikeSite) Sug(q, "Go to site", false, resolveUrl(q))
            else Sug(q, SearchEngines.current().name, false, resolveUrl(q))
        )

        // Pages you already have: open tabs, shortcuts, recent
        val low = q.lowercase()
        val seen = HashSet<String>()
        val local = ArrayList<Sug>()
        for (t in tabs) {
            if (t.isPrivate || !t.url.startsWith("http")) continue
            if (t.title.lowercase().contains(low) || t.url.lowercase().contains(low)) {
                if (seen.add(t.url)) local.add(Sug(t.title.ifBlank { hostOf(t.url) }, hostOf(t.url) + "  •  open tab", true, t.url))
            }
        }
        for (s in StartPage.all()) {
            if (s.title.lowercase().contains(low) || s.url.lowercase().contains(low)) {
                if (seen.add(s.url)) local.add(Sug(s.title, hostOf(s.url) + "  •  shortcut", true, s.url))
            }
        }
        for (r in loadRecent()) {
            if (r.title.lowercase().contains(low) || r.url.lowercase().contains(low)) {
                if (seen.add(r.url)) local.add(Sug(r.title, hostOf(r.url), true, r.url))
            }
        }
        for (s in local.take(3)) rows.add(s)
        for (s in net) {
            if (rows.size >= 9) break
            if (!s.equals(q, ignoreCase = true)) rows.add(Sug(s, "", false, resolveUrl(s)))
        }

        suggestList.removeAllViews()
        for (r in rows) {
            val v = layoutInflater.inflate(R.layout.item_suggest, suggestList, false)
            v.findViewById<TextView>(R.id.sugText).text = r.text
            val sub = v.findViewById<TextView>(R.id.sugSub)
            sub.text = r.sub
            sub.visibility = if (r.sub.isEmpty()) View.GONE else View.VISIBLE
            v.findViewById<ImageView>(R.id.sugIcon).setImageResource(
                if (r.local) R.drawable.ic_history else R.drawable.ic_search_small
            )
            v.setOnClickListener {
                hideSuggestions()
                hideKeyboard()
                go(edit, r.target)
            }
            suggestList.addView(v)
        }
        placeSuggestCard(if (edit === homeSearch) (edit.parent as View) else urlRow)
        suggestCard.visibility = View.VISIBLE
    }

    private fun placeSuggestCard(anchor: View) {
        val frame = suggestCard.parent as View
        val a = IntArray(2)
        val f = IntArray(2)
        anchor.getLocationInWindow(a)
        frame.getLocationInWindow(f)
        val lp = suggestCard.layoutParams as FrameLayout.LayoutParams
        if (anchor === urlRow && Settings.addressBottom) {
            lp.gravity = Gravity.BOTTOM
            lp.topMargin = 0
            lp.bottomMargin = frame.height - (a[1] - f[1]) + dp(6)
        } else {
            lp.gravity = Gravity.TOP
            lp.bottomMargin = 0
            lp.topMargin = a[1] - f[1] + anchor.height + dp(6)
        }
        suggestCard.layoutParams = lp
    }

    private fun hideSuggestions() {
        sugSeq++
        uiHandler.removeCallbacks(sugRunnable)
        if (::suggestCard.isInitialized) suggestCard.visibility = View.GONE
    }

    // ---------- Search engine ----------

    private fun showEngineDialog() {
        val engines = SearchEngines.all()
        val cur = SearchEngines.current().name
        val labels = ArrayList<String>()
        for (e in engines) {
            labels.add((if (e.name == cur) "✓   " else "      ") + e.name + (if (e.custom) "   (custom)" else ""))
        }
        labels.add("+   Add a search engine…")
        val hasCustom = engines.any { it.custom }
        if (hasCustom) labels.add("−   Remove a custom engine…")
        AlertDialog.Builder(this)
            .setTitle("Search engine")
            .setItems(labels.toTypedArray()) { _, i ->
                when {
                    i < engines.size -> {
                        SearchEngines.setCurrent(engines[i].name)
                        bindSettings()
                    }
                    i == engines.size -> showAddEngine()
                    else -> showRemoveEngine()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddEngine() {
        promptTwo(
            "Add search engine",
            "Name",
            "",
            "Search address with %s  (https://site.com/?q=%s)",
            ""
        ) { name, url ->
            val err = SearchEngines.addCustom(name, url)
            if (err == null) {
                SearchEngines.setCurrent(name)
                bindSettings()
                toast("$name is now your search engine")
            }
            err
        }
    }

    private fun showRemoveEngine() {
        val customs = SearchEngines.all().filter { it.custom }
        if (customs.isEmpty()) return
        val names = Array(customs.size) { customs[it].name }
        AlertDialog.Builder(this)
            .setTitle("Remove which engine?")
            .setItems(names) { _, i ->
                SearchEngines.removeCustom(names[i])
                bindSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Start page ----------

    private fun renderShortcuts() {
        val show = Settings.showShortcuts && !homePrivate
        shortcutsSection.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        shortcutsGrid.removeAllViews()
        val items = StartPage.all()
        val cells = ArrayList<View>()
        for ((i, s) in items.withIndex()) {
            val letter = (s.title.ifBlank { hostOf(s.url) }).take(1).uppercase()
            cells.add(
                shortcutTile(
                    s.title.ifBlank { hostOf(s.url) },
                    letter,
                    { openFromHome(s.url) },
                    { showShortcutMenu(i) }
                )
            )
        }
        if (items.size < StartPage.MAX) {
            cells.add(shortcutTile("Add", "+", { showShortcutEditor(-1) }, null))
        }
        val cols = Settings.shortcutCols
        var row: LinearLayout? = null
        for ((i, c) in cells.withIndex()) {
            if (i % cols == 0) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.weightSum = cols.toFloat()
                shortcutsGrid.addView(
                    r,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                row = r
            }
            row?.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rem = cells.size % cols
        if (rem != 0) {
            for (k in rem until cols) {
                row?.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
        }
    }

    private fun shortcutTile(label: String, letter: String, click: () -> Unit, longClick: (() -> Unit)?): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(2), dp(8), dp(2), dp(8))
        box.isClickable = true
        box.isFocusable = true

        val av = TextView(this)
        av.text = letter
        av.gravity = Gravity.CENTER
        av.setTextColor(color(R.attr.vioAccent))
        av.textSize = 18f
        av.typeface = android.graphics.Typeface.DEFAULT_BOLD
        av.setBackgroundResource(R.drawable.bg_avatar)
        box.addView(av, LinearLayout.LayoutParams(dp(48), dp(48)))

        val tv = TextView(this)
        tv.text = label
        tv.maxLines = 1
        tv.ellipsize = android.text.TextUtils.TruncateAt.END
        tv.textSize = 12f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(if (wallpaperOn()) Color.WHITE else color(R.attr.vioText))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        box.addView(tv, lp)

        box.setOnClickListener { click() }
        if (longClick != null) {
            box.setOnLongClickListener {
                longClick()
                true
            }
        }
        return box
    }

    private fun showShortcutMenu(index: Int) {
        val labels = arrayOf("Edit", "Move left", "Move right", "Remove")
        AlertDialog.Builder(this)
            .setItems(labels) { _, i ->
                when (i) {
                    0 -> showShortcutEditor(index)
                    1 -> {
                        StartPage.move(index, -1)
                        renderShortcuts()
                    }
                    2 -> {
                        StartPage.move(index, 1)
                        renderShortcuts()
                    }
                    else -> {
                        StartPage.remove(index)
                        renderShortcuts()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showShortcutEditor(index: Int) {
        val cur = if (index >= 0) StartPage.all().getOrNull(index) else null
        promptTwo(
            if (cur == null) "Add shortcut" else "Edit shortcut",
            "Name",
            cur?.title ?: "",
            "Web address (example.com)",
            cur?.url ?: ""
        ) { name, url ->
            val err = if (cur == null) StartPage.add(name, url) else StartPage.update(index, name, url)
            if (err == null) renderShortcuts()
            err
        }
    }

    private fun askGreetName() {
        promptText("Name in the greeting", "Your name (empty = no name)", Settings.greetName, true) { n ->
            Settings.greetName = n
            bindSettings()
        }
    }

    // ---------- Website permissions ----------

    private fun hasOsPermission(p: Perm): Boolean {
        fun ok(name: String) = ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED
        return when (p) {
            Perm.CAMERA -> ok(Manifest.permission.CAMERA)
            Perm.MIC -> ok(Manifest.permission.RECORD_AUDIO)
            Perm.LOCATION -> ok(Manifest.permission.ACCESS_FINE_LOCATION) || ok(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    /** Asks the phone for camera / microphone / location if Vio doesn't have it yet. */
    private fun ensureOsPermissions(needs: List<Perm>, done: (Boolean) -> Unit) {
        val missing = ArrayList<String>()
        for (p in needs) {
            if (hasOsPermission(p)) continue
            when (p) {
                Perm.CAMERA -> missing.add(Manifest.permission.CAMERA)
                Perm.MIC -> missing.add(Manifest.permission.RECORD_AUDIO)
                Perm.LOCATION -> {
                    missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
        if (missing.isEmpty()) {
            done(true)
            return
        }
        if (pendingSiteAction != null) {
            done(false)
            return
        }
        pendingSiteAction = { _ -> done(needs.all { hasOsPermission(it) }) }
        suppressLock = true
        sitePermLauncher.launch(missing.toTypedArray())
    }

    /** A page wants the camera / microphone / location: uses what you decided for that site, or asks. */
    private fun askSitePermissions(tab: Tab, origin: String, needs: List<Perm>, done: (Boolean) -> Unit) {
        val host = hostOf(origin)
        val priv = tab.isPrivate
        val decisions = needs.map { SitePermissions.get(host, it, priv) }
        if (decisions.any { it == Decision.BLOCK }) {
            done(false)
            return
        }
        if (decisions.all { it == Decision.ALLOW }) {
            ensureOsPermissions(needs, done)
            return
        }
        val what = needs.joinToString(" and ") { it.label.lowercase() }
        val remember = CheckBox(this)
        remember.text = if (priv) "Remember until Vio closes" else "Remember for this site"
        remember.isChecked = true
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(
            remember,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        AlertDialog.Builder(this)
            .setTitle("$host wants to use your $what")
            .setView(box)
            .setPositiveButton("Allow") { _, _ ->
                if (remember.isChecked) for (p in needs) SitePermissions.set(host, p, Decision.ALLOW, priv)
                ensureOsPermissions(needs, done)
            }
            .setNegativeButton("Block") { _, _ ->
                if (remember.isChecked) for (p in needs) SitePermissions.set(host, p, Decision.BLOCK, priv)
                done(false)
            }
            .setOnCancelListener { done(false) }
            .show()
    }

    private fun showSiteDialog() {
        val tab = currentTab ?: return
        val host = hostOf(tab.url)
        if (host.isEmpty()) return
        showSiteSettings(host, tab)
    }

    private fun showSiteSettings(host: String, tab: Tab?) {
        val priv = tab?.isPrivate == true
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        if (tab != null) {
            labels.add("Desktop site:   " + if (tab.desktop) "On" else "Off")
            acts.add {
                toggleDesktop(tab)
                showSiteSettings(host, tab)
            }
        }
        for (p in Perm.values()) {
            val d = SitePermissions.get(host, p, priv)
            labels.add(p.label + ":   " + d.label)
            acts.add {
                SitePermissions.set(host, p, Decision.values()[(d.ordinal + 1) % Decision.values().size], priv)
                showSiteSettings(host, tab)
            }
        }
        labels.add("Reset this site")
        acts.add {
            SitePermissions.reset(host)
            if (priv) for (p in Perm.values()) SitePermissions.set(host, p, Decision.ASK, true)
            toast("Permissions reset for $host")
            showSiteSettings(host, tab)
        }
        val secure = tab != null && tab.url.startsWith("https://")
        val title = if (tab == null) host else host + (if (secure) "   •   secure" else "   •   not secure")
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, i -> acts[i]() }
            .setNegativeButton(if (tab == null) "Back" else "Close") { _, _ ->
                if (tab == null) showPermManager()
            }
            .show()
    }

    private fun permSummary(host: String): String {
        val parts = ArrayList<String>()
        for (p in Perm.values()) {
            val d = SitePermissions.get(host, p)
            if (d != Decision.ASK) parts.add(p.label + " " + d.label.lowercase())
        }
        return parts.joinToString(", ")
    }

    private fun showPermManager() {
        val hosts = SitePermissions.savedHosts()
        if (hosts.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Website permissions")
                .setMessage(
                    "No saved choices yet.\n\nWhen a site asks for your camera, microphone or location " +
                        "and you tick \"Remember\", it shows up here. You can also set them from the " +
                        "icon at the left of the address bar."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = Array(hosts.size) { hosts[it] + "\n" + permSummary(hosts[it]) }
        AlertDialog.Builder(this)
            .setTitle("Website permissions")
            .setItems(labels) { _, i -> showSiteSettings(hosts[i], null) }
            .setNeutralButton("Reset all") { _, _ ->
                SitePermissions.resetAll()
                toast("All site permissions reset")
                bindSettings()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ---------- QR scanner ----------

    private fun startScan() {
        val o = ScanOptions()
        o.setPrompt("Point the camera at a QR code")
        o.setBeepEnabled(false)
        o.setOrientationLocked(false)
        suppressLock = true
        try {
            scanLauncher.launch(o)
        } catch (_: Exception) {
            suppressLock = false
            toast("Couldn't open the scanner")
        }
    }

    private fun onScanned(text: String) {
        val t = text.trim()
        val isUrl = t.startsWith("http://") || t.startsWith("https://")
        val looksLikeSite = !isUrl && !t.contains(" ") && !t.contains("\n") && t.contains(".")
        val b = AlertDialog.Builder(this)
            .setTitle("QR code")
            .setMessage(if (t.length > 500) t.take(500) + "..." else t)
        if (isUrl || looksLikeSite) {
            b.setPositiveButton("Open") { _, _ -> openScanned(if (isUrl) t else "https://$t") }
        } else {
            b.setPositiveButton("Search") { _, _ -> openScanned(SearchEngines.searchUrl(t)) }
        }
        b.setNeutralButton("Copy") { _, _ -> copyText(t) }
        b.setNegativeButton("Close", null)
        b.show()
    }

    private fun openScanned(url: String) {
        val priv = if (screen == Screen.HOME) homePrivate else (screen == Screen.BROWSE && currentTab?.isPrivate == true)
        val t = createTab(priv)
        homePrivate = priv
        openInTab(t, url)
    }

    // ---------- Browse menu and full screen ----------

    private fun showBrowseMenu() {
        val tab = currentTab ?: return
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        labels.add("New tab")
        acts.add { newTab(false) }
        labels.add("New private tab")
        acts.add { newTab(true) }
        if (Settings.hideBottomBar) {
            labels.add("Start page")
            acts.add { goHome() }
        }
        val last = TabStore.closed().firstOrNull()
        if (last != null) {
            labels.add("Reopen closed tab")
            acts.add { reopenClosed(last) }
        }
        labels.add(if (tab.desktop) "Mobile site" else "Desktop site")
        acts.add { toggleDesktop(tab) }
        if (!tab.isPrivate) {
            labels.add(if (tab.pinned) "Unpin tab" else "Pin tab")
            acts.add { togglePin(tab) }
        }
        labels.add(if (tab.group == null) "Add tab to group…" else "Move tab to group…")
        acts.add { pickGroup(tab) }
        if (!tab.isPrivate && tab.url.startsWith("http")) {
            labels.add("Add to start page")
            acts.add {
                val err = StartPage.add(tab.title.ifBlank { hostOf(tab.url) }, tab.url)
                toast(err ?: "Added to your start page")
            }
        }
        labels.add("Full screen")
        acts.add { setBrowseFullscreen(true) }
        labels.add("Scan QR code")
        acts.add { startScan() }
        if (Settings.hideBtnShield) {
            labels.add("Shields")
            acts.add { showShieldDialog() }
        }
        if (Settings.hideBtnTools) {
            labels.add("Video and download tools")
            acts.add { showToolsCard() }
        }
        if (tab.url.startsWith("http")) {
            labels.add("Copy link")
            acts.add { copyText(tab.url) }
            labels.add("Share link")
            acts.add { shareText(tab.url) }
            labels.add("Site settings")
            acts.add { showSiteDialog() }
        }
        AlertDialog.Builder(this)
            .setItems(labels.toTypedArray()) { _, i -> acts[i]() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Full screen browsing: hides the address bar, the bottom bar and the phone's own bars. */
    private fun setBrowseFullscreen(on: Boolean) {
        if (browseFs == on) return
        browseFs = on
        urlRow.visibility = if (on) View.GONE else View.VISIBLE
        updateBars()
        fsExit.visibility = if (on) View.VISIBLE else View.GONE
        hideSuggestions()
        @Suppress("DEPRECATION")
        run {
            if (on) {
                browseFsFlags = window.decorView.systemUiVisibility
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                window.decorView.systemUiVisibility = browseFsFlags
            }
        }
    }

    // ---------- Swipes: screen edges and address bar ----------

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            swipeActive = false
            swipeDone = false
            barActive = false
        }
        if (screen == Screen.BROWSE && customView == null) {
            val density = resources.displayMetrics.density
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val loc = IntArray(2)
                    webContainer.getLocationOnScreen(loc)
                    val inPage = ev.rawY >= loc[1] && ev.rawY <= loc[1] + webContainer.height
                    val width = resources.displayMetrics.widthPixels
                    val edge = 26 * density
                    if (Settings.swipeNav && inPage && (ev.rawX <= edge || ev.rawX >= width - edge)) {
                        swipeActive = true
                        swipeFromLeft = ev.rawX <= edge
                        swipeX = ev.rawX
                        swipeY = ev.rawY
                    } else if (urlRow.visibility == View.VISIBLE && !urlBar.hasFocus()) {
                        urlRow.getLocationOnScreen(loc)
                        val inBar = ev.rawY >= loc[1] && ev.rawY <= loc[1] + urlRow.height &&
                            ev.rawX >= loc[0] && ev.rawX <= loc[0] + urlRow.width
                        if (inBar) {
                            barActive = true
                            swipeX = ev.rawX
                            swipeY = ev.rawY
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - swipeX
                    val dy = ev.rawY - swipeY
                    if (swipeActive && !swipeDone) {
                        val good = if (swipeFromLeft) dx > 72 * density else -dx > 72 * density
                        if (good && Math.abs(dy) < Math.abs(dx) * 0.6f) {
                            val action = Gestures.get(if (swipeFromLeft) GSlot.EDGE_LEFT else GSlot.EDGE_RIGHT)
                            swipeActive = false
                            if (action != GAction.NONE) return fireSwipe(ev, action)
                        } else if (Math.abs(dy) > 40 * density && Math.abs(dy) > Math.abs(dx)) {
                            swipeActive = false
                        }
                    } else if (barActive && !swipeDone) {
                        val slot = when {
                            Math.abs(dx) > 60 * density && Math.abs(dy) < Math.abs(dx) * 0.6f ->
                                if (dx < 0) GSlot.BAR_LEFT else GSlot.BAR_RIGHT
                            Math.abs(dy) > 44 * density && Math.abs(dx) < Math.abs(dy) * 0.6f ->
                                if (dy < 0) GSlot.BAR_UP else GSlot.BAR_DOWN
                            else -> null
                        }
                        if (slot != null) {
                            val action = Gestures.get(slot)
                            barActive = false
                            // A gesture set to "Do nothing" leaves the bar behaving as usual
                            if (action != GAction.NONE) return fireSwipe(ev, action)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    swipeActive = false
                    barActive = false
                    if (swipeDone) {
                        swipeDone = false
                        return true
                    }
                }
            }
            if (swipeDone) return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Stops the page / bar from seeing the rest of this touch, then runs the action. */
    private fun fireSwipe(ev: MotionEvent, action: GAction): Boolean {
        swipeDone = true
        swipeActive = false
        barActive = false
        val cancel = MotionEvent.obtain(ev)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        if (action != GAction.NONE) {
            webContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            runGesture(action)
        }
        return true
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
        hideSuggestions()
        for (t in tabs) {
            t.holder?.visibility = if (t === tab) View.VISIBLE else View.GONE
        }
        tab.refresher?.isEnabled = Settings.pullRefresh
        val pending = tab.pendingLoad
        if (pending != null) {
            tab.pendingLoad = null
            syncDesktop(tab, pending)
            tab.webView.loadUrl(pending)
        }
        urlBar.setText(tab.url)
        loadingBar.visibility = View.GONE
        hideDetectCard()
        applyPrivateStyle()
        updateUi()
    }

    private fun closeTab(tab: Tab, remember: Boolean = true) {
        if (!tabs.remove(tab)) return
        if (remember && !tab.isPrivate && tab.url.startsWith("http")) {
            TabStore.pushClosed(tab.title.ifBlank { hostOf(tab.url) }, tab.url)
        }
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
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        val holder = tab.holder
        if (holder != null) webContainer.removeView(holder)
        tab.webView.destroy()
        if (tab.pinned) persistPinned()
        if (currentTab === tab) {
            currentTab = tabs.maxByOrNull { it.lastUsed }
            hideDetectCard()
        }
    }

    private fun closeMany(match: (Tab) -> Boolean) {
        val victims = tabs.filter { match(it) }
        for (t in victims) closeTab(t)
    }

    // ---------- Pinned tabs (kept for the next launch) ----------

    private fun persistPinned() {
        val list = ArrayList<SavedTab>()
        for (t in tabs.filter { it.pinned && !it.isPrivate && it.url.startsWith("http") }.sortedBy { it.id }) {
            list.add(SavedTab(t.title, t.url, t.group))
        }
        TabStore.savePinned(list)
    }

    /** Brings back last session's pinned tabs. Their pages load when you open them. */
    private fun restorePinned() {
        for (s in TabStore.pinned()) {
            val t = createTab(false)
            t.pinned = true
            t.group = s.group
            t.title = s.title
            t.url = s.url
            t.pendingLoad = s.url
            t.lastUsed = 0L
        }
    }

    private fun togglePin(tab: Tab) {
        if (tab.isPrivate) {
            toast("Private tabs can't be pinned")
            return
        }
        tab.pinned = !tab.pinned
        persistPinned()
        toast(if (tab.pinned) "Tab pinned" else "Tab unpinned")
    }

    private fun reopenClosed(c: ClosedTab) {
        TabStore.removeClosed(c.url)
        val t = createTab(false)
        openInTab(t, c.url)
    }

    // ---------- Desktop site ----------

    private fun desktopUa(mobile: String): String {
        val ver = Regex("Chrome/([0-9.]+)").find(mobile)?.groupValues?.get(1) ?: "120.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$ver Safari/537.36"
    }

    private fun applyDesktop(tab: Tab, on: Boolean) {
        if (tab.desktop == on) return
        tab.desktop = on
        val st = tab.webView.settings
        st.userAgentString = if (on) desktopUa(tab.mobileUa) else tab.mobileUa
        st.useWideViewPort = if (on) true else tab.mobileWide
        st.loadWithOverviewMode = if (on) true else tab.mobileOverview
        tab.sniffer.userAgent = st.userAgentString
    }

    /** Before a page loads: desktop or mobile, depending on what you chose for that site. */
    private fun syncDesktop(tab: Tab, url: String) {
        val host = hostOf(url)
        if (host.isEmpty()) return
        val saved = Settings.isDesktop(host)
        // A private tab keeps its own choice while you browse in it
        applyDesktop(tab, if (tab.isPrivate) (tab.desktop || saved) else saved)
    }

    private fun toggleDesktop(tab: Tab) {
        val on = !tab.desktop
        val host = hostOf(tab.url)
        if (!tab.isPrivate) Settings.setDesktop(host, on)
        applyDesktop(tab, on)
        if (tab.pendingLoad == null && tab.url.isNotEmpty()) tab.webView.reload()
        toast(if (on) "Desktop site" else "Mobile site")
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
        val options = arrayOf(
            "Close all tabs",
            "Close private tabs",
            "Close normal tabs",
            "Clear recently closed list"
        )
        AlertDialog.Builder(this)
            .setTitle("Close tabs (pinned tabs stay)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> closeMany { !it.pinned }
                    1 -> closeMany { it.isPrivate }
                    2 -> closeMany { !it.isPrivate && !it.pinned }
                    else -> TabStore.clearClosed()
                }
                renderTabs()
                updateUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun matchesQuery(tab: Tab, q: String): Boolean {
        // A locked private tab can't be found by what it shows
        if (tab.isPrivate && Settings.lockPrivate && !privateUnlocked) return false
        return tab.title.lowercase().contains(q) ||
            tab.url.lowercase().contains(q) ||
            (tab.group ?: "").lowercase().contains(q)
    }

    private fun sectionHeader(text: String, onClick: (() -> Unit)?): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(color(R.attr.vioAccent))
        tv.textSize = 12f
        tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tv.setPadding(dp(4), dp(10), dp(4), dp(8))
        if (onClick != null) {
            tv.isClickable = true
            tv.isFocusable = true
            tv.setOnClickListener { onClick() }
        }
        return tv
    }

    private fun emptyNote(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(color(R.attr.vioSubtext))
        tv.textSize = 14f
        tv.setPadding(0, dp(24), 0, dp(24))
        return tv
    }

    private fun renderTabs() {
        for (i in chips.indices) {
            val on = i == tabFilter
            chips[i].setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
            chips[i].setTextColor(color(if (on) R.attr.vioSurface else R.attr.vioText))
        }

        tabsList.removeAllViews()
        tabsTitle.text = if (tabs.isEmpty()) "Tabs" else "Tabs (${tabs.size})"

        if (tabFilter == 4) {
            renderClosedTabs()
            return
        }

        val q = tabQuery.trim().lowercase()
        val sorted = tabs.sortedByDescending { it.lastUsed }
        var list = when (tabFilter) {
            1 -> sorted.filter { !it.isPrivate }
            2 -> sorted.filter { it.isPrivate }
            3 -> sorted.take(5)
            else -> sorted
        }
        if (q.isNotEmpty()) list = list.filter { matchesQuery(it, q) }

        if (list.isEmpty()) {
            tabsList.addView(emptyNote(if (q.isNotEmpty()) "No tabs match \"" + tabQuery.trim() + "\"" else "No tabs here yet"))
            return
        }

        // Searching or "Recent": one flat list. Otherwise: Pinned, groups, then the rest.
        if (q.isNotEmpty() || tabFilter == 3) {
            for (tab in list) tabsList.addView(tabRow(tab, true))
            return
        }

        val pinned = list.filter { it.pinned }.sortedBy { it.id }
        val rest = list.filter { !it.pinned }

        if (pinned.isNotEmpty()) {
            tabsList.addView(sectionHeader("PINNED", null))
            for (tab in pinned) tabsList.addView(tabRow(tab, false))
        }

        val groups = LinkedHashMap<String, MutableList<Tab>>()
        for (t in rest) {
            val g = t.group ?: continue
            groups.getOrPut((if (t.isPrivate) "p:" else "n:") + g) { ArrayList() }.add(t)
        }
        for ((_, members) in groups) {
            val first = members[0]
            val name = first.group ?: continue
            val priv = first.isPrivate
            val label = name + (if (priv) "  •  Private" else "") + "  •  " + members.size + "   ⋯"
            tabsList.addView(sectionHeader(label.uppercase()) { showGroupMenu(name, priv) })
            for (tab in members) tabsList.addView(tabRow(tab, false))
        }

        val loose = rest.filter { it.group == null }
        if (loose.isNotEmpty()) {
            if (pinned.isNotEmpty() || groups.isNotEmpty()) tabsList.addView(sectionHeader("TABS", null))
            for (tab in loose) tabsList.addView(tabRow(tab, false))
        }
    }

    private fun tabRow(tab: Tab, flat: Boolean): View {
        val row = layoutInflater.inflate(R.layout.item_tab, tabsList, false)
        val masked = tab.isPrivate && Settings.lockPrivate && !privateUnlocked
        val title = when {
            masked -> "Private tab"
            tab.title.isNotBlank() -> tab.title
            tab.isBlank -> "New tab"
            else -> hostOf(tab.url)
        }
        var sub = when {
            masked -> "Locked"
            tab.isBlank && tab.isPrivate -> "Private tab"
            tab.isBlank -> "Start page"
            tab.isPrivate -> "Private • " + hostOf(tab.url)
            else -> hostOf(tab.url)
        }
        val g = tab.group
        if (flat && g != null && !masked) sub = "$g • $sub"
        row.findViewById<TextView>(R.id.tabTitle).text = title
        row.findViewById<TextView>(R.id.tabHost).text = sub
        row.findViewById<View>(R.id.tabPin).visibility = if (tab.pinned) View.VISIBLE else View.GONE

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
        row.setOnLongClickListener {
            showTabMenu(tab)
            true
        }
        row.findViewById<View>(R.id.tabClose).setOnClickListener {
            closeTab(tab)
            renderTabs()
            updateUi()
        }
        return row
    }

    private fun renderClosedTabs() {
        val q = tabQuery.trim().lowercase()
        val all = TabStore.closed().filter {
            q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
        if (all.isEmpty()) {
            tabsList.addView(
                emptyNote(if (q.isNotEmpty()) "No closed tabs match \"" + tabQuery.trim() + "\"" else "Tabs you close show up here")
            )
            return
        }
        tabsList.addView(sectionHeader("TAP TO REOPEN   •   CLEAR LIST") {
            TabStore.clearClosed()
            renderTabs()
        })
        for (c in all) {
            val row = layoutInflater.inflate(R.layout.item_tab, tabsList, false)
            row.findViewById<TextView>(R.id.tabTitle).text = c.title
            row.findViewById<TextView>(R.id.tabHost).text = hostOf(c.url) + "  •  " + timeAgo(c.time)
            row.findViewById<TextView>(R.id.tabLetter).text = hostOf(c.url).take(1).uppercase()
            row.setBackgroundResource(R.drawable.bg_card)
            row.setOnClickListener { reopenClosed(c) }
            row.findViewById<View>(R.id.tabClose).setOnClickListener {
                TabStore.removeClosed(c.url)
                renderTabs()
            }
            tabsList.addView(row)
        }
    }

    // ---------- Tab menu, groups ----------

    private fun showTabMenu(tab: Tab) {
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        if (!tab.isPrivate) {
            labels.add(if (tab.pinned) "Unpin tab" else "Pin tab")
            acts.add {
                togglePin(tab)
                renderTabs()
            }
        }
        labels.add(if (tab.group == null) "Add to group…" else "Move to group…")
        acts.add { pickGroup(tab) }
        if (tab.group != null) {
            labels.add("Remove from group")
            acts.add {
                setGroup(tab, null)
            }
        }
        labels.add("Close other tabs")
        acts.add {
            closeMany { it !== tab && !it.pinned }
            renderTabs()
            updateUi()
        }
        labels.add("Close tab")
        acts.add {
            closeTab(tab)
            renderTabs()
            updateUi()
        }
        AlertDialog.Builder(this)
            .setItems(labels.toTypedArray()) { _, i -> acts[i]() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setGroup(tab: Tab, name: String?) {
        tab.group = name?.trim()?.take(24)?.ifEmpty { null }
        if (tab.pinned) persistPinned()
        renderTabs()
    }

    private fun pickGroup(tab: Tab) {
        val names = ArrayList<String>()
        for (t in tabs) {
            val g = t.group
            if (g != null && t.isPrivate == tab.isPrivate && !names.contains(g)) names.add(g)
        }
        val labels = ArrayList<String>(names)
        labels.add("+  New group…")
        AlertDialog.Builder(this)
            .setTitle("Tab group")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < names.size) {
                    setGroup(tab, names[i])
                } else {
                    promptText("New group", "Group name", "", false) { setGroup(tab, it) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGroupMenu(name: String, priv: Boolean) {
        val labels = arrayOf("Rename group", "Ungroup tabs", "Close tabs in this group")
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(labels) { _, i ->
                val members = tabs.filter { it.group == name && it.isPrivate == priv }
                when (i) {
                    0 -> promptText("Rename group", "Group name", name, false) { n ->
                        for (t in members) t.group = n.take(24)
                        if (members.any { it.pinned }) persistPinned()
                        renderTabs()
                    }
                    1 -> {
                        for (t in members) t.group = null
                        if (members.any { it.pinned }) persistPinned()
                        renderTabs()
                    }
                    else -> {
                        closeMany { it.group == name && it.isPrivate == priv && !it.pinned }
                        renderTabs()
                        updateUi()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Navigation / URL ----------

    private fun submit(edit: EditText) {
        val input = edit.text.toString().trim()
        if (input.isEmpty()) return
        hideSuggestions()
        go(edit, resolveUrl(input))
    }

    private fun go(edit: EditText, url: String) {
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
        input.startsWith("localhost") -> "http://$input"
        input.contains(" ") || !input.contains(".") -> SearchEngines.searchUrl(input)
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

    /** Sets the tab up for [typed] (https upgrade, desktop or mobile) and returns the address to load. */
    private fun prepareLoad(tab: Tab, typed: String): String {
        var url = typed
        val upgraded = if (tab.skipUpgradeUrl == typed) null else Shields.upgrade(typed)
        if (upgraded != null) {
            tab.httpFallback = typed
            url = upgraded
        }
        tab.pendingLoad = null
        tab.url = url
        syncDesktop(tab, url)
        return url
    }

    private fun openInTab(tab: Tab, typed: String) {
        val url = prepareLoad(tab, typed)
        switchTo(tab)
        showScreen(Screen.BROWSE, 1)
        tab.webView.loadUrl(url)
        hideKeyboard()
    }

    /** A link opened from [from]: in front, or quietly in the background. */
    private fun openLinkTab(from: Tab, link: String, show: Boolean) {
        val t = createTab(from.isPrivate)
        t.group = from.group
        if (show) {
            openInTab(t, link)
        } else {
            t.lastUsed = from.lastUsed - 1
            val url = prepareLoad(t, link)
            t.webView.loadUrl(url)
            updateUi()
            toast("Opened in background")
        }
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
            tv.setTextColor(color(R.attr.vioSubtext))
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
        dcStatus.setTextColor(color(R.attr.vioAccent))
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

        status.setTextColor(color(R.attr.vioAccent))
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
            view.setColorFilter(color(R.attr.vioSubtext))
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
        btnTools.setColorFilter(color(if (rec) R.color.vio_red else R.attr.vioAccent))
        tabCount.text = "${tabs.size}"
        val shieldHost = currentTab?.let { hostOf(it.url) } ?: ""
        val shieldOn = Settings.blockAds && !Settings.shieldsOffFor(shieldHost)
        btnShield.setColorFilter(color(if (shieldOn) R.attr.vioAccent else R.attr.vioSubtext))
        val plainHttp = currentTab?.url?.startsWith("http://") == true
        btnSite.setColorFilter(color(if (plainHttp) R.color.vio_red else R.attr.vioSubtext))

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
        dlTabFound.setTextColor(color(if (!showSaved) R.attr.vioSurface else R.attr.vioText))
        dlTabSaved.setBackgroundResource(if (showSaved) R.drawable.bg_chip_on else R.drawable.bg_chip)
        dlTabSaved.setTextColor(color(if (showSaved) R.attr.vioSurface else R.attr.vioText))
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
            tv.setTextColor(color(if (on) R.attr.vioSurface else R.attr.vioText))
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

    private val catColors: IntArray
        get() = intArrayOf(
            color(R.attr.vioAccent), // video
            0xFF16A34A.toInt(), // audio
            0xFFF59E0B.toInt(), // docs
            0xFFEC4899.toInt(), // images
            0xFF8A8A93.toInt(), // other
            color(R.attr.vioDark) // vault
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
            card.setTextColor(color(R.attr.vioText))
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

    // ---------- Appearance: re-skin without closing tabs ----------

    /** Draws the whole screen again with the current theme, keeping every open tab alive. */
    private fun rebuildUi() {
        if (customView != null) return
        setBrowseFullscreen(false)
        hideSuggestions()
        uiHandler.removeCallbacks(hideCardRunnable)
        VioTheme.apply(this)
        appliedDark = VioTheme.isDark(this)

        // Take the pages out of the old layout so they survive
        for (t in tabs) {
            val h = t.holder
            if (h != null) (h.parent as? ViewGroup)?.removeView(h)
        }
        lockView = null
        libChips.clear()
        rowViews.clear()
        shownIds = emptyList()
        sugAnchor = null

        setContentView(R.layout.activity_main)
        bindViews()
        setupClicks()
        VioTheme.applyBars(this)

        for (t in tabs) {
            val h = t.holder ?: continue
            h.visibility = if (t === currentTab) View.VISIBLE else View.GONE
            webContainer.addView(
                h,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        applyToolbarLayout()
        applyToolbarButtons()
        urlBar.setText(currentTab?.url ?: "")
        showScreen(screen, navIndex)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        VioTheme.applyFontScale(this)
        if (Settings.themeMode == 0 && VioTheme.isDark(this) != appliedDark) rebuildUi()
    }

    private fun showThemeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(VioTheme.THEME_NAMES, Settings.themeMode) { d, which ->
                d.dismiss()
                if (which != Settings.themeMode) {
                    Settings.themeMode = which
                    rebuildUi()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAccentDialog() {
        val dark = VioTheme.isDark(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(8), dp(12), dp(4))
        var dialog: AlertDialog? = null
        var row: LinearLayout? = null
        for (i in VioTheme.ACCENT_NAMES.indices) {
            if (i % 5 == 0) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.gravity = Gravity.CENTER
                box.addView(
                    r,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                row = r
            }
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(dp(2), dp(8), dp(2), dp(8))

            val sw = TextView(this)
            sw.gravity = Gravity.CENTER
            sw.textSize = 20f
            sw.text = if (i == Settings.accent) "✓" else ""
            sw.setTextColor(if (dark) 0xFF0B0B10.toInt() else Color.WHITE)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(if (dark) VioTheme.ACCENT_DARK[i] else VioTheme.ACCENT_LIGHT[i])
            sw.background = g
            cell.addView(sw, LinearLayout.LayoutParams(dp(46), dp(46)))

            val name = TextView(this)
            name.text = VioTheme.ACCENT_NAMES[i]
            name.textSize = 11f
            name.gravity = Gravity.CENTER
            name.setTextColor(color(R.attr.vioSubtext))
            val nlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            nlp.topMargin = dp(4)
            cell.addView(name, nlp)

            cell.setOnClickListener {
                dialog?.dismiss()
                if (Settings.accent != i) {
                    Settings.accent = i
                    rebuildUi()
                }
            }
            row?.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Accent colour")
            .setView(box)
            .setNegativeButton("Close", null)
            .show()
    }

    // ---------- Wallpaper ----------

    private fun wallpaperOn(): Boolean {
        if (screen != Screen.HOME || homePrivate) return false
        return when (Settings.wallpaperKind) {
            1 -> true
            2 -> Wallpaper.hasPhoto(this)
            else -> false
        }
    }

    private fun wallpaperName(): String = when (Settings.wallpaperKind) {
        1 -> Wallpaper.presets[Settings.wallpaperPreset.coerceIn(0, Wallpaper.presets.size - 1)].name
        2 -> if (Wallpaper.hasPhoto(this)) "Your photo" else "None"
        else -> "None"
    }

    private fun applyWallpaper() {
        val on = wallpaperOn()
        var d: android.graphics.drawable.Drawable? = null
        if (on) {
            d = if (Settings.wallpaperKind == 1) {
                Wallpaper.gradient(Wallpaper.presets[Settings.wallpaperPreset.coerceIn(0, Wallpaper.presets.size - 1)])
            } else {
                val b = Wallpaper.photo(this)
                if (b != null) android.graphics.drawable.BitmapDrawable(resources, b) else null
            }
        }
        val show = on && d != null
        wallpaperView.visibility = if (show) View.VISIBLE else View.GONE
        wallpaperScrim.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            wallpaperView.setImageDrawable(d)
            wallpaperScrim.setBackgroundColor(Color.argb(Settings.wallpaperDim * 255 / 100, 0, 0, 0))
        }
        // Text that sits straight on the picture
        val main = if (show) Color.WHITE else color(R.attr.vioText)
        val sub = if (show) 0xCCFFFFFF.toInt() else color(R.attr.vioSubtext)
        homeBrandText.setTextColor(main)
        shortcutsHeader.setTextColor(main)
        recentHeader.setTextColor(main)
        homeSubtitle.setTextColor(sub)
        if (!homePrivate) greeting.setTextColor(main)
        seeAll.setTextColor(if (show) Color.WHITE else color(R.attr.vioAccent))
    }

    private fun showWallpaperDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(8), dp(12), dp(4))
        var dialog: AlertDialog? = null
        var row: LinearLayout? = null
        for ((i, p) in Wallpaper.presets.withIndex()) {
            if (i % 4 == 0) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                box.addView(
                    r,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                row = r
            }
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(dp(3), dp(6), dp(3), dp(6))
            val sw = TextView(this)
            sw.gravity = Gravity.CENTER
            sw.textSize = 20f
            sw.setTextColor(Color.WHITE)
            sw.text = if (Settings.wallpaperKind == 1 && Settings.wallpaperPreset == i) "✓" else ""
            val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(p.c1, p.c2))
            g.cornerRadius = dp(10).toFloat()
            sw.background = g
            cell.addView(sw, LinearLayout.LayoutParams(dp(58), dp(78)))
            val name = TextView(this)
            name.text = p.name
            name.textSize = 11f
            name.setTextColor(color(R.attr.vioSubtext))
            val nlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            nlp.topMargin = dp(4)
            cell.addView(name, nlp)
            cell.setOnClickListener {
                Settings.wallpaperKind = 1
                Settings.wallpaperPreset = i
                dialog?.dismiss()
                bindSettings()
                toast("Wallpaper set. See it on the start page")
            }
            row?.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        fun button(text: String, onClick: (TextView) -> Unit): TextView {
            val tv = TextView(this)
            tv.text = text
            tv.gravity = Gravity.CENTER
            tv.textSize = 14f
            tv.setTextColor(color(R.attr.vioAccent))
            tv.setBackgroundResource(R.drawable.bg_btn_light)
            tv.isClickable = true
            tv.isFocusable = true
            tv.setOnClickListener { onClick(tv) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
            lp.topMargin = dp(8)
            box.addView(tv, lp)
            return tv
        }

        button("Choose a photo") {
            dialog?.dismiss()
            suppressLock = true
            try {
                wallpaperPicker.launch("image/*")
            } catch (_: Exception) {
                suppressLock = false
                toast("No gallery app found")
            }
        }
        val dims = intArrayOf(0, 20, 30, 45, 60)
        button("Darken the picture:  " + Settings.wallpaperDim + "%") { tv ->
            val i = dims.indexOf(Settings.wallpaperDim)
            Settings.wallpaperDim = dims[(i + 1) % dims.size]
            tv.text = "Darken the picture:  " + Settings.wallpaperDim + "%"
        }
        button("No wallpaper") {
            Settings.wallpaperKind = 0
            Wallpaper.deletePhoto(this)
            dialog?.dismiss()
            bindSettings()
        }

        val scroll = ScrollView(this)
        scroll.addView(box)
        dialog = AlertDialog.Builder(this)
            .setTitle("Wallpaper")
            .setView(scroll)
            .setNegativeButton("Close") { _, _ -> bindSettings() }
            .show()
    }

    // ---------- Start page layout ----------

    private val homeKeys = listOf("greeting", "search", "tiles", "shortcuts", "recent")

    private fun homeOrderList(): MutableList<String> {
        val order = ArrayList<String>()
        for (k in Settings.homeOrder.split(",")) if (k in homeKeys && k !in order) order.add(k)
        for (k in homeKeys) if (k !in order) order.add(k)
        return order
    }

    private fun homeVisible(k: String): Boolean = when (k) {
        "greeting" -> Settings.showGreeting
        "tiles" -> Settings.showTiles
        "shortcuts" -> Settings.showShortcuts
        "recent" -> Settings.showRecent
        else -> true
    }

    private fun setHomeVisible(k: String, v: Boolean) {
        when (k) {
            "greeting" -> Settings.showGreeting = v
            "tiles" -> Settings.showTiles = v
            "shortcuts" -> Settings.showShortcuts = v
            "recent" -> Settings.showRecent = v
        }
    }

    private fun homeLabel(k: String): String = when (k) {
        "greeting" -> "Greeting"
        "search" -> "Search bar"
        "tiles" -> "Action tiles"
        "shortcuts" -> "Shortcuts"
        else -> "Recent pages"
    }

    private fun homeLayoutName(): String {
        val order = homeOrderList().joinToString(",")
        val vis = homeKeys.joinToString("") { if (homeVisible(it)) "1" else "0" }
        return when {
            order == "greeting,search,tiles,shortcuts,recent" && vis == "11111" && Settings.shortcutCols == 4 -> "Classic"
            order == "greeting,search,shortcuts,tiles,recent" && vis == "11010" -> "Minimal"
            order == "search,shortcuts,tiles,recent,greeting" && vis == "11111" -> "Quick access"
            else -> "Custom"
        }
    }

    private fun applyHomeLayout() {
        val parent = homeContent
        val order = homeOrderList()
        val groups = mapOf(
            "greeting" to listOf<View>(greeting, homeSubtitle),
            "search" to listOf<View>(homeSearchRow),
            "tiles" to listOf<View>(homeTiles),
            "shortcuts" to listOf<View>(shortcutsSection),
            "recent" to listOf<View>(recentSection)
        )
        val want = ArrayList<View>()
        for (k in order) want.addAll(groups[k] ?: emptyList())
        val have = ArrayList<View>()
        for (i in 1 until parent.childCount) have.add(parent.getChildAt(i))
        if (have != want) {
            for (v in want) parent.removeView(v)
            for (v in want) parent.addView(v)
        }
        val showGreet = Settings.showGreeting || homePrivate
        greeting.visibility = if (showGreet) View.VISIBLE else View.GONE
        homeSubtitle.visibility = if (showGreet) View.VISIBLE else View.GONE
        homeTiles.visibility = if (Settings.showTiles) View.VISIBLE else View.GONE
    }

    private fun showHomeLayoutDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), dp(8))
        scroll.addView(box)

        fun changed(fill: () -> Unit) {
            bindSettings()
            fill()
        }

        fun fill() {
            box.removeAllViews()

            val presets = LinearLayout(this)
            presets.orientation = LinearLayout.HORIZONTAL
            fun preset(name: String, order: String, greet: Boolean, tiles: Boolean, shortcuts: Boolean, recent: Boolean) {
                val tv = TextView(this)
                tv.text = name
                tv.gravity = Gravity.CENTER
                tv.textSize = 13f
                val on = homeLayoutName() == name
                tv.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
                tv.setTextColor(color(if (on) R.attr.vioSurface else R.attr.vioText))
                tv.isClickable = true
                tv.isFocusable = true
                tv.setOnClickListener {
                    Settings.homeOrder = order
                    Settings.showGreeting = greet
                    Settings.showTiles = tiles
                    Settings.showShortcuts = shortcuts
                    Settings.showRecent = recent
                    Settings.shortcutCols = 4
                    changed { fill() }
                }
                val lp = LinearLayout.LayoutParams(0, dp(36), 1f)
                lp.marginEnd = dp(6)
                presets.addView(tv, lp)
            }
            preset("Classic", "greeting,search,tiles,shortcuts,recent", true, true, true, true)
            preset("Minimal", "greeting,search,shortcuts,tiles,recent", true, false, true, false)
            preset("Quick access", "search,shortcuts,tiles,recent,greeting", true, true, true, true)
            box.addView(presets)

            val order = homeOrderList()
            for ((i, k) in order.withIndex()) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL

                val name = TextView(this)
                name.text = homeLabel(k)
                name.textSize = 15f
                name.setTextColor(color(if (homeVisible(k)) R.attr.vioText else R.attr.vioSubtext))
                row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                fun arrow(text: String, delta: Int, enabled: Boolean) {
                    val a = TextView(this)
                    a.text = text
                    a.gravity = Gravity.CENTER
                    a.textSize = 16f
                    a.setTextColor(color(if (enabled) R.attr.vioAccent else R.attr.vioBorder))
                    if (enabled) {
                        a.isClickable = true
                        a.isFocusable = true
                        a.setOnClickListener {
                            val list = homeOrderList()
                            val idx = list.indexOf(k)
                            val to = idx + delta
                            if (idx >= 0 && to in list.indices) {
                                list.removeAt(idx)
                                list.add(to, k)
                                Settings.homeOrder = list.joinToString(",")
                                changed { fill() }
                            }
                        }
                    }
                    row.addView(a, LinearLayout.LayoutParams(dp(40), dp(44)))
                }
                arrow("▲", -1, i > 0)
                arrow("▼", 1, i < order.size - 1)

                if (k == "search") {
                    row.addView(View(this), LinearLayout.LayoutParams(dp(48), dp(1)))
                } else {
                    val sw = SwitchCompat(this)
                    sw.isChecked = homeVisible(k)
                    sw.setOnCheckedChangeListener { _, c ->
                        setHomeVisible(k, c)
                        changed { fill() }
                    }
                    row.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
                }
                box.addView(
                    row,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }

            val cols = TextView(this)
            cols.text = "Shortcuts per row:  " + Settings.shortcutCols + "   (tap to change)"
            cols.textSize = 14f
            cols.setTextColor(color(R.attr.vioAccent))
            cols.setPadding(0, dp(14), 0, dp(6))
            cols.isClickable = true
            cols.setOnClickListener {
                Settings.shortcutCols = if (Settings.shortcutCols >= 5) 3 else Settings.shortcutCols + 1
                changed { fill() }
            }
            box.addView(cols)
        }
        fill()
        AlertDialog.Builder(this)
            .setTitle("Home page layout")
            .setView(scroll)
            .setPositiveButton("Done") { _, _ -> bindSettings() }
            .show()
    }

    // ---------- Toolbar ----------

    /** Address bar at the top or the bottom of the page. */
    private fun applyToolbarLayout() {
        val parent = browseView as LinearLayout
        parent.removeView(urlRow)
        parent.removeView(loadingBar)
        parent.removeView(webContainer)
        val lp = urlRow.layoutParams as LinearLayout.LayoutParams
        if (Settings.addressBottom) {
            lp.topMargin = dp(6)
            lp.bottomMargin = dp(8)
            parent.addView(webContainer)
            parent.addView(loadingBar)
            parent.addView(urlRow)
        } else {
            lp.topMargin = dp(8)
            lp.bottomMargin = 0
            parent.addView(urlRow)
            parent.addView(loadingBar)
            parent.addView(webContainer)
        }
        urlRow.layoutParams = lp
    }

    private fun applyToolbarButtons() {
        btnSite.visibility = if (Settings.hideBtnSite) View.GONE else View.VISIBLE
        btnShield.visibility = if (Settings.hideBtnShield) View.GONE else View.VISIBLE
        btnTools.visibility = if (Settings.hideBtnTools) View.GONE else View.VISIBLE
    }

    /** The Home / Tabs / More bar: hidden in full screen, and optionally while a page is open. */
    private fun updateBars() {
        val hide = browseFs || (Settings.hideBottomBar && screen == Screen.BROWSE)
        bottomNav.visibility = if (hide) View.GONE else View.VISIBLE
        navDivider.visibility = if (hide) View.GONE else View.VISIBLE
    }

    private fun toolbarSummary(): String {
        val a = if (Settings.addressBottom) "Address bar at the bottom" else "Address bar on top"
        return if (Settings.hideBottomBar) "$a  •  bottom bar hidden on pages" else a
    }

    private fun showToolbarDialog() {
        val labels = arrayOf(
            "Address bar:   " + (if (Settings.addressBottom) "Bottom" else "Top"),
            "Bottom bar:   " + (if (Settings.hideBottomBar) "Hidden while browsing" else "Always shown"),
            "Buttons in the address bar…"
        )
        AlertDialog.Builder(this)
            .setTitle("Toolbar")
            .setItems(labels) { _, i ->
                when (i) {
                    0 -> {
                        Settings.addressBottom = !Settings.addressBottom
                        applyToolbarLayout()
                        bindSettings()
                        showToolbarDialog()
                    }
                    1 -> {
                        Settings.hideBottomBar = !Settings.hideBottomBar
                        updateBars()
                        bindSettings()
                        showToolbarDialog()
                    }
                    else -> showToolbarButtons()
                }
            }
            .setNegativeButton("Done", null)
            .show()
    }

    private fun showToolbarButtons() {
        val names = arrayOf("Site info (left icon)", "Shields", "Video and download tools")
        val checked = booleanArrayOf(!Settings.hideBtnSite, !Settings.hideBtnShield, !Settings.hideBtnTools)
        AlertDialog.Builder(this)
            .setTitle("Buttons in the address bar")
            .setMultiChoiceItems(names, checked) { _, i, on ->
                when (i) {
                    0 -> Settings.hideBtnSite = !on
                    1 -> Settings.hideBtnShield = !on
                    else -> Settings.hideBtnTools = !on
                }
                applyToolbarButtons()
            }
            .setPositiveButton("Done") { _, _ ->
                bindSettings()
                showToolbarDialog()
            }
            .show()
    }

    // ---------- Gestures ----------

    private fun showGesturesDialog() {
        val slots = GSlot.values()
        val labels = Array(slots.size) { slots[it].label + "\n" + Gestures.get(slots[it]).label }
        AlertDialog.Builder(this)
            .setTitle("Gestures")
            .setItems(labels) { _, i -> pickGestureAction(slots[i]) }
            .setNeutralButton("Reset all") { _, _ ->
                Gestures.resetAll()
                bindSettings()
                showGesturesDialog()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun pickGestureAction(slot: GSlot) {
        val acts = GAction.values()
        AlertDialog.Builder(this)
            .setTitle(slot.label)
            .setSingleChoiceItems(Array(acts.size) { acts[it].label }, Gestures.get(slot).ordinal) { d, which ->
                Gestures.set(slot, acts[which])
                d.dismiss()
                bindSettings()
                showGesturesDialog()
            }
            .setNegativeButton("Back") { _, _ -> showGesturesDialog() }
            .show()
    }

    private fun cycleTab(delta: Int) {
        val cur = currentTab ?: return
        val pool = tabs.filter { it.isPrivate == cur.isPrivate }
        if (pool.size < 2) {
            toast("No other tabs")
            return
        }
        val idx = pool.indexOf(cur)
        val next = pool[((idx + delta) % pool.size + pool.size) % pool.size]
        openTabNow(next)
    }

    private fun runGesture(a: GAction) {
        if (a == GAction.NONE) return
        val tab = currentTab
        val wv = tab?.webView
        when (a) {
            GAction.BACK -> if (wv != null && wv.canGoBack()) wv.goBack()
            GAction.FORWARD -> if (wv != null && wv.canGoForward()) wv.goForward()
            GAction.RELOAD -> wv?.reload()
            GAction.NEXT_TAB -> cycleTab(1)
            GAction.PREV_TAB -> cycleTab(-1)
            GAction.NEW_TAB -> newTab(tab?.isPrivate == true)
            GAction.CLOSE_TAB -> if (tab != null) {
                closeTab(tab)
                val next = currentTab
                if (next != null) openTabNow(next) else goHome()
                updateUi()
            }
            GAction.TOP -> wv?.pageUp(true)
            GAction.BOTTOM -> wv?.pageDown(true)
            GAction.TABS -> showScreen(Screen.TABS, 1)
            GAction.HOME -> goHome()
            GAction.DESKTOP -> if (tab != null && tab.url.isNotEmpty()) toggleDesktop(tab)
            GAction.FULLSCREEN -> if (screen == Screen.BROWSE) setBrowseFullscreen(!browseFs)
            GAction.SCAN -> startScan()
            GAction.NONE -> {}
        }
    }

    // ---------- Text size and fonts ----------

    private fun applyPageText(st: WebSettings) {
        try {
            st.textZoom = Settings.pageTextZoom
            st.standardFontFamily = when (Settings.pageFont) {
                1 -> "serif"
                2 -> "monospace"
                else -> "sans-serif"
            }
        } catch (_: Exception) {
        }
    }

    private fun applyPageText() {
        for (t in tabs) applyPageText(t.webView.settings)
    }

    private fun appTextName(): String {
        val i = VioTheme.TEXT_SCALES.indexOf(Settings.uiTextScale)
        return if (i >= 0) VioTheme.TEXT_SCALE_NAMES[i] else Settings.uiTextScale.toString() + "%"
    }

    private fun pageFontName(): String = when (Settings.pageFont) {
        1 -> "Serif"
        2 -> "Monospace"
        else -> "Default"
    }

    private fun showTextSizeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Text size")
            .setItems(arrayOf("App text size…", "Page text size…")) { _, i ->
                if (i == 0) pickAppTextSize() else pickPageTextSize()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun pickAppTextSize() {
        var cur = VioTheme.TEXT_SCALES.indexOf(Settings.uiTextScale)
        if (cur < 0) cur = 1
        AlertDialog.Builder(this)
            .setTitle("App text size")
            .setSingleChoiceItems(VioTheme.TEXT_SCALE_NAMES, cur) { d, which ->
                d.dismiss()
                if (VioTheme.TEXT_SCALES[which] != Settings.uiTextScale) {
                    Settings.uiTextScale = VioTheme.TEXT_SCALES[which]
                    rebuildUi()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickPageTextSize() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), dp(4))

        val label = TextView(this)
        label.textSize = 15f
        label.setTextColor(color(R.attr.vioText))
        box.addView(label)

        val seek = SeekBar(this)
        seek.max = 20 // 50% .. 250%, steps of 10
        seek.progress = (Settings.pageTextZoom - 50) / 10
        box.addView(
            seek,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val sample = TextView(this)
        sample.text = "The quick brown fox jumps over the lazy dog."
        sample.setTextColor(color(R.attr.vioSubtext))
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp.topMargin = dp(10)
        box.addView(sample, slp)

        fun show(zoom: Int) {
            label.text = "Page text:  $zoom%"
            sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * zoom / 100f)
        }
        show(Settings.pageTextZoom)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val zoom = 50 + progress * 10
                Settings.pageTextZoom = zoom
                show(zoom)
                applyPageText()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        AlertDialog.Builder(this)
            .setTitle("Page text size")
            .setView(box)
            .setPositiveButton("Done") { _, _ -> bindSettings() }
            .setNeutralButton("Reset") { _, _ ->
                Settings.pageTextZoom = 100
                applyPageText()
                bindSettings()
            }
            .show()
    }

    private fun showFontsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Fonts")
            .setItems(arrayOf("App font…", "Page font…  (pages that pick their own font keep it)")) { _, i ->
                if (i == 0) pickAppFont() else pickPageFont()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun pickAppFont() {
        AlertDialog.Builder(this)
            .setTitle("App font")
            .setSingleChoiceItems(VioTheme.FONT_NAMES, Settings.uiFont) { d, which ->
                d.dismiss()
                if (which != Settings.uiFont) {
                    Settings.uiFont = which
                    rebuildUi()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickPageFont() {
        AlertDialog.Builder(this)
            .setTitle("Page font")
            .setSingleChoiceItems(arrayOf("Default", "Serif", "Monospace"), Settings.pageFont) { d, which ->
                Settings.pageFont = which
                applyPageText()
                d.dismiss()
                bindSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Fills the little grey lines under each Appearance row in Settings. */
    private fun bindAppearanceRows() {
        findViewById<TextView>(R.id.rowThemeSub).text = VioTheme.THEME_NAMES[Settings.themeMode]
        findViewById<TextView>(R.id.rowAccentSub).text = VioTheme.ACCENT_NAMES[Settings.accent]
        findViewById<TextView>(R.id.rowWallpaperSub).text = wallpaperName()
        findViewById<TextView>(R.id.rowHomeLayoutSub).text = homeLayoutName()
        findViewById<TextView>(R.id.rowToolbarSub).text = toolbarSummary()
        findViewById<TextView>(R.id.rowGesturesSub).text =
            "Edge swipes, address bar swipes, tab button"
        findViewById<TextView>(R.id.rowTextSizeSub).text =
            "App: " + appTextName() + "  •  Pages: " + Settings.pageTextZoom + "%"
        findViewById<TextView>(R.id.rowFontsSub).text =
            "App: " + VioTheme.FONT_NAMES[Settings.uiFont] + "  •  Pages: " + pageFontName()
    }

    // ---------- Settings screen ----------

    private fun bindSettings() {
        swAds.setOnCheckedChangeListener(null)
        swHttps.setOnCheckedChangeListener(null)
        swCookies.setOnCheckedChangeListener(null)
        swFp.setOnCheckedChangeListener(null)
        swLockApp.setOnCheckedChangeListener(null)
        swLockPrivate.setOnCheckedChangeListener(null)
        swSuggest.setOnCheckedChangeListener(null)
        swSwipe.setOnCheckedChangeListener(null)
        swPull.setOnCheckedChangeListener(null)
        swShortcuts.setOnCheckedChangeListener(null)
        swRecentHome.setOnCheckedChangeListener(null)

        swSuggest.isChecked = Settings.searchSuggestions
        swSwipe.isChecked = Settings.swipeNav
        swPull.isChecked = Settings.pullRefresh
        swShortcuts.isChecked = Settings.showShortcuts
        swRecentHome.isChecked = Settings.showRecent
        swSuggest.setOnCheckedChangeListener { _, c ->
            Settings.searchSuggestions = c
            if (!c) hideSuggestions()
        }
        swSwipe.setOnCheckedChangeListener { _, c -> Settings.swipeNav = c }
        swPull.setOnCheckedChangeListener { _, c ->
            Settings.pullRefresh = c
            for (t in tabs) t.refresher?.isEnabled = c
        }
        swShortcuts.setOnCheckedChangeListener { _, c -> Settings.showShortcuts = c }
        swRecentHome.setOnCheckedChangeListener { _, c -> Settings.showRecent = c }
        findViewById<TextView>(R.id.rowEngineSub).text =
            SearchEngines.current().name + "  •  tap to change"
        findViewById<TextView>(R.id.rowGreetNameSub).text =
            if (Settings.greetName.isEmpty()) "Not set  •  tap to set" else Settings.greetName
        val savedSites = SitePermissions.savedHosts().size
        findViewById<TextView>(R.id.rowPermsSub).text =
            if (savedSites == 0) "Camera, microphone and location, site by site"
            else "$savedSites saved site" + (if (savedSites == 1) "" else "s") + "  •  tap to manage"

        bindAppearanceRows()
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

    /** A colour from the theme (pass R.attr.vioText ...) or a fixed colour resource. */
    private fun color(id: Int): Int {
        if (resources.getResourceTypeName(id) == "attr") return VioTheme.attrColor(this, id)
        return ContextCompat.getColor(this, id)
    }

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
