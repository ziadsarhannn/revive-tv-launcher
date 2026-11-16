package com.revivecreates.tvlauncher

import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COLS = 5

/** Sizes are written in pixels of a 1920 x 1080 design, then scaled to the real screen. */
class Px(private val unit: Dp, private val density: Density) {
    fun d(v: Number): Dp = unit * v.toFloat()
    fun s(v: Number): TextUnit = with(density) { (unit * v.toFloat()).toSp() }
    fun f(v: Number): Float = with(density) { (unit * v.toFloat()).toPx() }
}

private data class HeroText(val app: TvApp?, val item: WhatsNew.Item?, val index: Int, val count: Int)

private class InputRow(val title: String, val sub: String, val run: (() -> Unit)?)

private class CcItem(val name: String, val icon: ImageVector, val desc: String, val run: () -> Unit)

private val menuOptions = listOf("Open", "Move left", "Move right", "Move to front", "App info", "Uninstall")

@Composable
private fun Label(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    spacing: Float = 0f,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = size, fontFamily = Outfit, fontWeight = weight,
            textAlign = align, letterSpacing = spacing.em,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun LauncherApp(repo: AppRepository, homeSignal: Int, resumeSignal: Int, reloadSignal: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    LaunchedEffect(reloadSignal) {
        apps = withContext(Dispatchers.IO) { repo.loadApps() }
    }
    val rows = remember(apps) { apps.chunked(COLS) }

    var row by remember { mutableIntStateOf(0) }
    var col by remember { mutableIntStateOf(0) }
    var heroKey by remember { mutableStateOf<String?>(null) }
    var pillIndex by remember { mutableIntStateOf(-1) }
    var inputsOpen by remember { mutableStateOf(false) }
    var inputIndex by remember { mutableIntStateOf(0) }
    var inputRows by remember { mutableStateOf<List<InputRow>>(emptyList()) }
    var ccOpen by remember { mutableStateOf(false) }
    var ccIndex by remember { mutableIntStateOf(0) }
    var menuApp by remember { mutableStateOf<TvApp?>(null) }
    var lastMenuApp by remember { mutableStateOf<TvApp?>(null) }
    var menuIndex by remember { mutableIntStateOf(0) }
    var launching by remember { mutableStateOf<TvApp?>(null) }
    var lastLaunched by remember { mutableStateOf<TvApp?>(null) }
    var pressed by remember { mutableStateOf(false) }
    var longHandled by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var network by remember { mutableStateOf("") }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val deviceName = remember { SystemActions.deviceName(context) }

    var weather by remember { mutableStateOf<Weather.Now?>(null) }
    var feed by remember { mutableStateOf<WhatsNew.Feed?>(null) }
    var slide by remember { mutableIntStateOf(0) }
    val slideProgress = remember { Animatable(0f) }

    // What's New: show the saved copy straight away, then check for the weekly update every 6 hours.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { WhatsNew.cached(context) }?.let { feed = it }
        while (true) {
            val fresh = withContext(Dispatchers.IO) { WhatsNew.refresh(context) }
            if (fresh != null) feed = fresh
            delay(if (fresh == null) 2 * 60_000L else 6 * 60 * 60_000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            network = SystemActions.networkLabel(context)
            delay(15_000)
        }
    }
    // Weather refreshes every 15 minutes, and again whenever you come back home.
    LaunchedEffect(resumeSignal) {
        while (true) {
            withContext(Dispatchers.IO) { Weather.fetch() }?.let { weather = it }
            delay(if (weather == null) 60_000L else 15 * 60_000L)
        }
    }

    LaunchedEffect(rows) {
        if (rows.isNotEmpty()) {
            row = row.coerceIn(0, rows.lastIndex)
            col = col.coerceIn(0, rows[row].lastIndex)
        }
    }

    val focusedApp: TvApp? = rows.getOrNull(row)?.getOrNull(col)
    val gridMode = row > 0

    // The Top Shelf follows focus on the first row and holds still deeper in the grid.
    LaunchedEffect(row, col, rows) {
        if (row == 0) focusedApp?.let { heroKey = it.packageName }
    }
    val heroApp = apps.firstOrNull { it.packageName == heroKey } ?: rows.firstOrNull()?.firstOrNull()
    val slides: List<WhatsNew.Item> = heroApp?.let { feed?.forLabel(it.label) }.orEmpty()

    // Slideshow: a new title every 6 seconds while you rest on an app in the top row.
    LaunchedEffect(heroApp?.packageName, slides.size) {
        slide = 0
        if (slides.size > 1) {
            while (true) {
                delay(6_000)
                if (row == 0 && !ccOpen && menuApp == null && launching == null) {
                    slide = (slide + 1) % slides.size
                }
            }
        }
    }
    LaunchedEffect(heroApp?.packageName, slide, slides.size) {
        slideProgress.snapTo(0f)
        if (slides.size > 1) slideProgress.animateTo(1f, tween(6_000, easing = LinearEasing))
    }

    LaunchedEffect(homeSignal) {
        menuApp = null
        ccOpen = false
        inputsOpen = false
        pillIndex = -1
        row = 0
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(resumeSignal) {
        launching = null
        pressed = false
        now = System.currentTimeMillis()
        runCatching { focus.requestFocus() }
    }

    fun nudge(dx: Float, dy: Float) {
        scope.launch {
            tiltX.snapTo(dx)
            tiltX.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 220f))
        }
        scope.launch {
            tiltY.snapTo(dy)
            tiltY.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 220f))
        }
    }

    // Opens the app straight away. Input is paused for a moment so a double press doesn't open it twice.
    fun launch(app: TvApp) {
        if (launching != null) return
        launching = app
        lastLaunched = app
        repo.markOpened(app.packageName)
        val ok = SystemActions.launchApp(context, app)
        scope.launch {
            delay(if (ok) 1_500L else 0L)
            if (launching == app) launching = null
        }
    }

    fun moveApp(app: TvApp, toIndex: Int) {
        val list = apps.toMutableList()
        val from = list.indexOfFirst { it.packageName == app.packageName }
        if (from < 0) return
        val target = toIndex.coerceIn(0, list.lastIndex)
        list.removeAt(from)
        list.add(target, app)
        apps = list
        repo.saveOrder(list.map { it.packageName })
        row = target / COLS
        col = target % COLS
    }

    val ccItems = listOf(
        CcItem("Screen Saver", Icons.ScreenSaver, "Start the screen saver now") { SystemActions.screenSaver(context) },
        CcItem("WiFi", Icons.Wifi, network) { SystemActions.settings(context, Settings.ACTION_WIFI_SETTINGS) },
        CcItem("Audio", Icons.Speaker, "Sound and output") { SystemActions.settings(context, Settings.ACTION_SOUND_SETTINGS) },
        CcItem("Display", Icons.Display, "Resolution and picture") { SystemActions.settings(context, Settings.ACTION_DISPLAY_SETTINGS) },
        CcItem("Bluetooth", Icons.Bluetooth, "Remotes and headphones") { SystemActions.settings(context, Settings.ACTION_BLUETOOTH_SETTINGS) },
        CcItem("Search", Icons.Search, "Find apps and content") { SystemActions.search(context) },
        CcItem("Settings", Icons.Settings, "All settings") { SystemActions.settings(context, Settings.ACTION_SETTINGS) },
        CcItem("Original Home", Icons.Home, "Open the box's own home screen") { SystemActions.originalHome(context) },
    )

    fun openInputs() {
        val found = SystemActions.inputs(context)
        inputRows = if (found.isNotEmpty()) {
            found.map { inp ->
                InputRow(
                    inp.label,
                    when (inp.connected) { true -> "Connected"; false -> "Nothing connected"; null -> "" },
                ) { SystemActions.openInput(context, inp.id) }
            }
        } else {
            listOfNotNull(
                InputRow("No inputs on this box", "This box has no HDMI in, AV or tuner", null),
                SystemActions.liveTvApp(context)?.let { InputRow("Open Live TV", "The box's own TV app", { SystemActions.openLiveTv(context) }) },
            )
        }
        inputIndex = inputRows.indexOfFirst { it.run != null }.coerceAtLeast(0)
        inputsOpen = true
    }

    fun openMenu(app: TvApp) {
        menuApp = app
        lastMenuApp = app
        menuIndex = 0
    }

    fun runMenu(i: Int) {
        val app = menuApp ?: return
        menuApp = null
        val idx = apps.indexOfFirst { it.packageName == app.packageName }
        when (i) {
            0 -> launch(app)
            1 -> moveApp(app, idx - 1)
            2 -> moveApp(app, idx + 1)
            3 -> moveApp(app, 0)
            4 -> SystemActions.appInfo(context, app.packageName)
            5 -> SystemActions.uninstall(context, app.packageName)
        }
    }

    fun activate() {
        when {
            inputsOpen -> {
                val action = inputRows.getOrNull(inputIndex)?.run
                if (action != null) {
                    inputsOpen = false
                    action()
                }
            }
            menuApp != null -> runMenu(menuIndex)
            ccOpen -> {
                ccOpen = false
                ccItems[ccIndex].run()
            }
            pillIndex >= 0 -> {
                when (pillIndex) {
                    0 -> { ccOpen = true; ccIndex = 0 }
                    1 -> SystemActions.settings(context, Settings.ACTION_WIFI_SETTINGS)
                    2 -> openInputs()
                    else -> SystemActions.settings(context, Settings.ACTION_SETTINGS)
                }
            }
            else -> focusedApp?.let { launch(it) }
        }
    }

    fun move(dx: Int, dy: Int) {
        when {
            inputsOpen -> {
                if (dy != 0) inputIndex = (inputIndex + dy).coerceIn(0, max(0, inputRows.lastIndex))
            }
            menuApp != null -> {
                menuIndex = (menuIndex + dy).coerceIn(0, menuOptions.lastIndex)
            }
            ccOpen -> {
                val next = ccIndex + dx + dy * 4
                val sameRow = dx == 0 || (next >= 0 && next / 4 == ccIndex / 4)
                if (sameRow) ccIndex = next.coerceIn(0, ccItems.lastIndex)
            }
            pillIndex >= 0 -> {
                if (dy > 0) pillIndex = -1
                else if (dx != 0) pillIndex = (pillIndex + dx).coerceIn(0, 3)
            }
            rows.isEmpty() -> Unit
            dy < 0 && row == 0 -> {
                pillIndex = when {
                    col <= 1 -> 0
                    col == 2 -> 1
                    col == 3 -> 2
                    else -> 3
                }
            }
            dy != 0 -> {
                val nr = (row + dy).coerceIn(0, rows.lastIndex)
                if (nr != row) {
                    row = nr
                    col = col.coerceAtMost(rows[nr].lastIndex)
                }
                nudge(0f, dy * 0.6f)
            }
            dx != 0 -> {
                col = (col + dx).coerceIn(0, rows[row].lastIndex)
                nudge(dx * 0.6f, 0f)
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            inputsOpen -> inputsOpen = false
            menuApp != null -> menuApp = null
            ccOpen -> ccOpen = false
            pillIndex >= 0 -> pillIndex = -1
            row > 0 -> {
                row = 0
                col = col.coerceAtMost(rows.firstOrNull()?.lastIndex ?: 0)
            }
            else -> Unit
        }
    }

    fun onKey(ev: KeyEvent): Boolean {
        if (launching != null) return true
        val down = ev.type == KeyEventType.KeyDown
        val up = ev.type == KeyEventType.KeyUp
        return when (ev.key) {
            Key.DirectionUp -> {
                if (down) move(0, -1)
                true
            }
            Key.DirectionDown -> {
                if (down) move(0, 1)
                true
            }
            Key.DirectionLeft -> {
                if (down) move(-1, 0)
                true
            }
            Key.DirectionRight -> {
                if (down) move(1, 0)
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
                val native = ev.nativeKeyEvent
                if (down) {
                    if (native.repeatCount == 0) {
                        pressed = true
                        longHandled = false
                    } else if (!longHandled && (native.isLongPress || native.repeatCount >= 6)) {
                        longHandled = true
                        pressed = false
                        if (menuApp == null && !ccOpen && !inputsOpen && pillIndex < 0) focusedApp?.let { openMenu(it) }
                    }
                } else if (up) {
                    pressed = false
                    if (!longHandled) activate()
                    longHandled = false
                }
                true
            }
            Key.Menu -> {
                if (up) {
                    when {
                        inputsOpen -> inputsOpen = false
                        menuApp != null -> menuApp = null
                        ccOpen -> ccOpen = false
                        pillIndex >= 0 -> {
                            ccOpen = true
                            ccIndex = 0
                        }
                        else -> focusedApp?.let { openMenu(it) }
                    }
                }
                true
            }
            else -> {
                val code = ev.nativeKeyEvent.keyCode
                if (code == android.view.KeyEvent.KEYCODE_TV_INPUT || code == android.view.KeyEvent.KEYCODE_TV) {
                    if (up) { if (inputsOpen) inputsOpen = false else openInputs() }
                    true
                } else false
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { onKey(it) }
            .focusRequester(focus)
            .focusable()
    ) {
        val px = Px(maxWidth / 1920f, LocalDensity.current)
        val timeText = SimpleDateFormat("h:mm a", Locale.US).format(Date(now))
        val clockHm = SimpleDateFormat("h:mm", Locale.US).format(Date(now))
        val clockAp = SimpleDateFormat("a", Locale.US).format(Date(now)).uppercase(Locale.US)
        val dateText = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now))
        val dateCaps = SimpleDateFormat("EEEE d MMMM", Locale.US).format(Date(now)).uppercase(Locale.US)
        val prayer = remember(now / 60_000L) { PrayerTimes.forToday(now) }
        val cairoHours = PrayerTimes.cairoHours(now)
        val nextIndex = prayer.times.indexOfFirst { it > cairoHours }
        val shownNext = if (nextIndex >= 0) nextIndex else 0
        val untilHours = if (nextIndex >= 0) prayer.times[nextIndex] - cairoHours else 24 - cairoHours + prayer.times[0]
        val untilMinutes = Math.round(untilHours * 60).toInt()
        val untilText = (if (untilMinutes >= 60) "${untilMinutes / 60}h " else "") + "${untilMinutes % 60}m"
        val weatherIcon = if (weather?.let { Weather.isCloudy(it.code) } == true) Icons.Cloud else Icons.Sun
        val tempText = weather?.let { "${it.temp}°" } ?: "--°"
        val hour = cairoHours.toInt()
        val greeting = when {
            hour < 5 -> "GOOD NIGHT"
            hour < 12 -> "GOOD MORNING"
            hour < 17 -> "GOOD AFTERNOON"
            hour < 22 -> "GOOD EVENING"
            else -> "GOOD NIGHT"
        } + ", SARHAN"

        // ---------- Ambient glow in the focused app's color ----------
        val glowApp = focusedApp ?: heroApp
        val glow by animateColorAsState(glowApp?.accent ?: Color(0xFF3D6AA8), tween(800), label = "glow")
        val accent by animateColorAsState(heroApp?.accent ?: Color(0xFF3D6AA8), tween(600), label = "accent")
        val drift = rememberInfiniteTransition(label = "drift")
        val d by drift.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "d"
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val c = Offset(px.f(490 + 80 * d), px.f(370 + 40 * d))
                    drawRect(
                        Brush.radialGradient(
                            0f to glow.copy(alpha = 0.62f),
                            0.45f to glow.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            center = c,
                            radius = px.f(1150),
                        )
                    )
                    drawRect(
                        Brush.horizontalGradient(
                            0.3f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        )
                    )
                }
        )

        // ---------- Header: greeting, clock, date, prayer times ----------
        val headerAlpha by animateFloatAsState(if (row >= 2) 0f else 1f, tween(400), label = "headerAlpha")
        Column(
            Modifier
                .offset(x = px.d(120), y = px.d(66))
                .graphicsLayer { alpha = headerAlpha }
        ) {
            Label(greeting, px.s(24), color = Color.White.copy(alpha = 0.62f), weight = FontWeight.Medium, spacing = 0.22f)
            Spacer(Modifier.height(px.d(6)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row {
                    BasicText(
                        clockHm,
                        modifier = Modifier.alignByBaseline(),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = px.s(132),
                            lineHeight = px.s(132),
                            fontFamily = Outfit,
                            fontWeight = FontWeight.ExtraLight,
                            letterSpacing = (-0.03).em,
                        ),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(px.d(14)))
                    Label(
                        clockAp, px.s(32), modifier = Modifier.alignByBaseline(),
                        color = Color.White.copy(alpha = 0.8f), weight = FontWeight.Medium, spacing = 0.08f
                    )
                }
                Spacer(Modifier.width(px.d(28)))
                Box(Modifier.width(px.d(2)).height(px.d(62)).background(Color.White.copy(alpha = 0.22f)))
                Spacer(Modifier.width(px.d(28)))
                Label(dateCaps, px.s(28), color = Color.White.copy(alpha = 0.72f), spacing = 0.16f)
            }
            Spacer(Modifier.height(px.d(12)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px.d(22))) {
                PrayerTimes.names.forEachIndexed { i, name ->
                    val on = i == shownNext
                    val c = if (on) Color.White else Color.White.copy(alpha = 0.45f)
                    val w = if (on) FontWeight.SemiBold else FontWeight.Normal
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(px.d(6)).clip(CircleShape).background(if (on) accent else Color.Transparent))
                        Spacer(Modifier.width(px.d(8)))
                        Label(name.uppercase(Locale.US), px.s(17), color = c, weight = w, spacing = 0.12f)
                        Spacer(Modifier.width(px.d(8)))
                        Label(PrayerTimes.format12(prayer.times[i]), px.s(17), color = c, weight = w, spacing = 0.04f)
                    }
                }
                Label("in $untilText", px.s(17), color = Color.White.copy(alpha = 0.55f), spacing = 0.06f)
            }
        }

        // ---------- Pills: weather (Control Center), Wi-Fi, Settings ----------
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = px.d(52), end = px.d(120))
                .graphicsLayer { alpha = headerAlpha },
            horizontalArrangement = Arrangement.spacedBy(px.d(14))
        ) {
            Pill(weatherIcon, "$tempText Heliopolis", pillIndex == 0 && !ccOpen, px)
            Pill(Icons.Wifi, network.ifBlank { "Network" }, pillIndex == 1 && !ccOpen, px)
            Pill(Icons.Input, "Inputs", pillIndex == 2 && !ccOpen && !inputsOpen, px)
            Pill(Icons.Settings, "Settings", pillIndex == 3 && !ccOpen, px)
        }

        // ---------- Top Shelf: newest title, centred between the prayers and the dock ----------
        val shelfAlpha by animateFloatAsState(if (!gridMode) 1f else 0f, tween(400), label = "shelfAlpha")
        val heroIndex = slide.coerceAtMost(max(0, slides.lastIndex))
        val heroText = HeroText(heroApp, slides.getOrNull(heroIndex), heroIndex, slides.size)
        Box(
            Modifier
                .offset(x = px.d(120), y = px.d(276))
                .width(px.d(980))
                .height(px.d(464))
                .graphicsLayer { alpha = shelfAlpha },
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedContent(
                targetState = heroText,
                transitionSpec = {
                    (fadeIn(tween(500, delayMillis = 100)) + slideInVertically(tween(650, easing = FastOutSlowInEasing)) { it / 12 }) togetherWith
                        fadeOut(tween(240))
                },
                contentAlignment = Alignment.CenterStart,
                label = "heroText"
            ) { ht -> ShelfText(ht, accent, px, now, ht.app?.let { repo.lastOpened(it.packageName) } ?: 0L) }
        }

        // ---------- New on <app>: pinned 64 px above the dock ----------
        if (slides.isNotEmpty()) {
            NewList(
                app = heroApp,
                items = slides,
                current = heroIndex,
                progress = slideProgress.value,
                accent = accent,
                px = px,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = px.d(120), bottom = px.d(404))
                    .graphicsLayer { alpha = shelfAlpha }
            )
        }

        // ---------- Dock band and the app grid below it ----------
        val dockTop by animateDpAsState(
            when {
                row == 0 -> px.d(740)
                else -> px.d(380) - px.d(280) * (row - 1)
            },
            tween(700, easing = FastOutSlowInEasing),
            label = "dockTop"
        )
        Column(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset(x = px.d(116), y = dockTop)
        ) {
            val band = RoundedCornerShape(px.d(52))
            Box(
                Modifier
                    .width(px.d(1688))
                    .height(px.d(238))
                    .clip(band)
                    .background(Color(0xDB161618))
                    .border(px.d(1.5), Color.White.copy(alpha = 0.08f), band)
            ) {}
            Spacer(Modifier.height(px.d(56)))
        }
        Column(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset(x = px.d(146), y = dockTop + px.d(30))
        ) {
            rows.forEachIndexed { r, items ->
                if (r == 1) Spacer(Modifier.height(px.d(30 + 56)))
                if (r > 1) Spacer(Modifier.height(px.d(102)))
                Row(horizontalArrangement = Arrangement.spacedBy(px.d(36))) {
                    items.forEachIndexed { c, app ->
                        val isFocused = !ccOpen && !inputsOpen && pillIndex < 0 && r == row && c == col
                        key(app.packageName) {
                            AppTile(app, isFocused, pressed && isFocused, tiltX, tiltY, px, showLabel = r > 0)
                        }
                    }
                }
            }
        }

        if (apps.isEmpty()) {
            Label(
                "Loading your apps",
                px.s(28),
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        // ---------- Overlays ----------
        AnimatedVisibility(
            visible = ccOpen || menuApp != null || inputsOpen,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }

        AnimatedVisibility(
            visible = ccOpen,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = px.d(60), end = px.d(60)),
            enter = slideInHorizontally(tween(520, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(360)),
            exit = slideOutHorizontally(tween(420)) { it / 2 } + fadeOut(tween(300))
        ) {
            ControlCenterPanel(px, ccItems, ccIndex, timeText, dateText, deviceName, weather, weatherIcon, prayer, shownNext)
        }

        AnimatedVisibility(
            visible = inputsOpen,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = px.d(140), end = px.d(120)),
            enter = scaleIn(initialScale = 0.94f) + fadeIn(),
            exit = scaleOut(targetScale = 0.94f) + fadeOut()
        ) {
            InputsPanel(px, inputRows, inputIndex)
        }

        AnimatedVisibility(
            visible = menuApp != null,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.9f) + fadeIn(),
            exit = scaleOut(targetScale = 0.9f) + fadeOut()
        ) {
            lastMenuApp?.let { AppMenuPanel(px, it, menuIndex) }
        }
    }
}

private fun openedText(time: Long, now: Long): String = when {
    time == 0L -> "Press OK to open"
    now - time < 60_000L -> "Opened just now"
    else -> "Last opened " + DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS)
}

/** Title size by length, as in the design: short titles big, long ones smaller. */
private fun titleSize(title: String): Float = when {
    title.length <= 14 -> 112f
    title.length <= 22 -> 92f
    title.length <= 30 -> 76f
    else -> 64f
}

/** "THU" and "19 NOV" for a feed date, or "SOON" when it has none. */
private fun dateLines(date: String?): Pair<String, String> {
    val day = date?.let { runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it) }.getOrNull() }
        ?: return "SOON" to ""
    return SimpleDateFormat("EEE", Locale.US).format(day).uppercase(Locale.US) to
        SimpleDateFormat("d MMM", Locale.US).format(day).uppercase(Locale.US)
}

@Composable
private fun ShelfText(ht: HeroText, accent: Color, px: Px, now: Long, lastOpened: Long) {
    val app = ht.app ?: return
    val item = ht.item
    Column(Modifier.width(px.d(980))) {
        if (item != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(px.d(10)))
                        .background(accent)
                        .padding(horizontal = px.d(16), vertical = px.d(7))
                ) {
                    Label(WhatsNew.badge(item, now).uppercase(Locale.US), px.s(20), weight = FontWeight.SemiBold, spacing = 0.08f)
                }
                Spacer(Modifier.width(px.d(16)))
                Label("ON " + app.label.uppercase(Locale.US), px.s(22), color = Color.White.copy(alpha = 0.72f), weight = FontWeight.Medium, spacing = 0.18f)
            }
            Spacer(Modifier.height(px.d(18)))
            val size = titleSize(item.title)
            BasicText(
                item.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = px.s(size),
                    lineHeight = px.s(size * 1.02f),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.02).em,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.titleAr?.let {
                Spacer(Modifier.height(px.d(8)))
                Label(it, px.s(36), color = Color.White.copy(alpha = 0.82f))
            }
            Spacer(Modifier.height(px.d(14)))
            Label(item.meta, px.s(26), color = Color.White.copy(alpha = 0.78f), weight = FontWeight.Medium, spacing = 0.04f)
            item.about?.let {
                Spacer(Modifier.height(px.d(8)))
                Label(it, px.s(25), color = Color.White.copy(alpha = 0.7f), weight = FontWeight.Light, maxLines = 2)
            }
        } else {
            BasicText(
                app.label,
                style = TextStyle(
                    color = Color.White,
                    fontSize = px.s(150),
                    lineHeight = px.s(154),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.02).em,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(px.d(14)))
            Label(openedText(lastOpened, now), px.s(26), color = Color.White.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun NewList(
    app: TvApp?,
    items: List<WhatsNew.Item>,
    current: Int,
    progress: Float,
    accent: Color,
    px: Px,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(px.d(38))
    val n = items.size
    val start = if (n > 5) (current - 2).coerceIn(0, n - 5) else 0
    Column(
        modifier
            .width(px.d(570))
            .clip(shape)
            .background(Color(0x8C141416))
            .border(px.d(1.5), Color.White.copy(alpha = 0.1f), shape)
            .padding(px.d(20)),
        verticalArrangement = Arrangement.spacedBy(px.d(6))
    ) {
        Label(
            "NEW ON " + (app?.label ?: "").uppercase(Locale.US),
            px.s(19),
            modifier = Modifier.padding(start = px.d(14), top = px.d(4), bottom = px.d(4)),
            color = Color.White.copy(alpha = 0.58f),
            weight = FontWeight.Medium,
            spacing = 0.18f,
        )
        items.drop(start).take(5).forEachIndexed { j, item ->
            val on = start + j == current
            val bg by animateColorAsState(if (on) Color.White else Color.Transparent, tween(300), label = "rowBg")
            val fg by animateColorAsState(if (on) Color(0xFF0B0B0D) else Color.White, tween(300), label = "rowFg")
            val (d1, d2) = dateLines(item.date)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(px.d(20)))
                    .background(bg)
            ) {
                Row(
                    Modifier.padding(start = px.d(12), end = px.d(16), top = px.d(10), bottom = px.d(10)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.width(px.d(70)), horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(d1, px.s(14), color = fg.copy(alpha = 0.75f), weight = FontWeight.SemiBold, spacing = 0.06f)
                        if (d2.isNotEmpty()) Label(d2, px.s(14), color = fg.copy(alpha = 0.75f), weight = FontWeight.SemiBold, spacing = 0.06f)
                    }
                    Spacer(Modifier.width(px.d(18)))
                    Column {
                        Label(item.title, px.s(25), color = fg, weight = FontWeight.Medium)
                        Spacer(Modifier.height(px.d(3)))
                        Label(item.genre ?: item.type, px.s(17), color = fg.copy(alpha = 0.64f))
                    }
                }
                if (on && n > 1) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(px.d(5))
                            .background(accent)
                    )
                }
            }
        }
    }
}

@Composable
private fun InputsPanel(px: Px, rows: List<InputRow>, index: Int) {
    val shape = RoundedCornerShape(px.d(36))
    Column(
        Modifier
            .width(px.d(520))
            .clip(shape)
            .background(Color(0xF02A2A2E))
            .border(px.d(1.5), Color.White.copy(alpha = 0.14f), shape)
            .padding(px.d(20)),
        verticalArrangement = Arrangement.spacedBy(px.d(6))
    ) {
        Label("INPUTS", px.s(19), modifier = Modifier.padding(start = px.d(14), top = px.d(4), bottom = px.d(6)),
            color = Color.White.copy(alpha = 0.58f), weight = FontWeight.Medium, spacing = 0.18f)
        rows.forEachIndexed { i, r ->
            val on = i == index && r.run != null
            val fg = if (on) Color(0xFF0B0B0D) else Color.White.copy(alpha = if (r.run != null) 1f else 0.6f)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(px.d(20)))
                    .background(if (on) Color.White else Color.Transparent)
                    .padding(horizontal = px.d(20), vertical = px.d(14))
            ) {
                Label(r.title, px.s(26), color = fg, weight = FontWeight.Medium)
                if (r.sub.isNotEmpty()) Label(r.sub, px.s(18), color = fg.copy(alpha = 0.64f))
            }
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String, focused: Boolean, px: Px) {
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.08f), label = "pillBg")
    val fg by animateColorAsState(if (focused) Color(0xFF0B0B0D) else Color.White, label = "pillFg")
    val sc by animateFloatAsState(if (focused) 1.06f else 1f, spring(0.6f, 400f), label = "pillScale")
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .graphicsLayer {
                scaleX = sc
                scaleY = sc
            }
            .height(px.d(66))
            .clip(shape)
            .background(bg)
            .border(px.d(1.5), Color.White.copy(alpha = if (focused) 0f else 0.16f), shape)
            .padding(horizontal = px.d(26)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(rememberVectorPainter(icon), contentDescription = null, modifier = Modifier.size(px.d(26)), colorFilter = ColorFilter.tint(fg))
        Spacer(Modifier.width(px.d(14)))
        Label(text, px.s(24), color = fg, weight = FontWeight.Medium)
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    focused: Boolean,
    pressed: Boolean,
    tiltX: Animatable<Float, AnimationVector1D>,
    tiltY: Animatable<Float, AnimationVector1D>,
    px: Px,
    showLabel: Boolean = true,
) {
    val scale by animateFloatAsState(
        when {
            focused && pressed -> 1.0f
            focused -> 1.07f
            else -> 1f
        },
        spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "scale"
    )
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(260), label = "lift")

    Box(Modifier.size(px.d(296), px.d(178)).zIndex(if (focused) 2f else 0f)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = -lift * px.f(10)
                    if (focused) {
                        rotationY = tiltX.value * 9f
                        rotationX = -tiltY.value * 9f
                    }
                    cameraDistance = 14f * density
                    shadowElevation = px.f(6) + lift * px.f(30)
                    shape = RoundedCornerShape(px.d(26))
                    clip = true
                }
                .background(app.tint)
                .border(px.d(4), Color.White.copy(alpha = 0.92f * lift), RoundedCornerShape(px.d(26)))
        ) {
            val banner = app.banner
            if (banner != null) {
                Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(px.d(84)), filterQuality = FilterQuality.High) }
                    Spacer(Modifier.height(px.d(10)))
                    Label(
                        app.label,
                        px.s(22),
                        modifier = Modifier.padding(horizontal = px.d(16)),
                        color = Color.White.copy(alpha = 0.92f),
                        weight = FontWeight.Medium,
                        align = TextAlign.Center
                    )
                }
            }
            // Shine that follows the tilt, like light catching glass.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        if (lift > 0.01f) {
                            val cx = size.width * (0.3f + tiltX.value * 0.35f)
                            val cy = size.height * (0.1f + tiltY.value * 0.35f)
                            drawRect(
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = 0.34f * lift), Color.Transparent),
                                    center = Offset(cx, cy),
                                    radius = size.width * 0.75f
                                )
                            )
                        }
                    }
            )
        }
        Label(
            app.label,
            px.s(24),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = px.d(204))
                .requiredWidth(px.d(380))
                .graphicsLayer { alpha = if (showLabel) lift else 0f },
            weight = FontWeight.SemiBold,
            align = TextAlign.Center
        )
    }
}

@Composable
private fun ControlCenterPanel(
    px: Px,
    items: List<CcItem>,
    index: Int,
    time: String,
    date: String,
    device: String,
    weather: Weather.Now?,
    weatherIcon: ImageVector,
    prayer: PrayerTimes.Day,
    nextPrayer: Int,
) {
    val shape = RoundedCornerShape(px.d(44))
    Column(
        Modifier
            .width(px.d(600))
            .clip(shape)
            .background(Color(0xEB2A2A2E))
            .border(px.d(1.5), Color.White.copy(alpha = 0.14f), shape)
            .padding(px.d(36)),
        verticalArrangement = Arrangement.spacedBy(px.d(30))
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Label(time, px.s(60), weight = FontWeight.Bold)
                Spacer(Modifier.height(px.d(6)))
                Label(date, px.s(22), color = Color.White.copy(alpha = 0.72f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    Modifier.size(px.d(68)).clip(CircleShape).background(Color(0xFF3D6AA8)),
                    contentAlignment = Alignment.Center
                ) {
                    Label(device.take(1).uppercase(), px.s(26), weight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(px.d(8)))
                Label(device, px.s(18), color = Color.White.copy(alpha = 0.6f))
            }
        }
        // Weather
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(px.d(28)))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = px.d(24), vertical = px.d(20)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(rememberVectorPainter(weatherIcon), contentDescription = null, modifier = Modifier.size(px.d(56)))
            Spacer(Modifier.width(px.d(20)))
            Label(weather?.let { "${it.temp}°" } ?: "--°", px.s(48), weight = FontWeight.Bold)
            Spacer(Modifier.width(px.d(20)))
            Column {
                Label("Sheraton Heliopolis", px.s(22), weight = FontWeight.SemiBold)
                Label(
                    weather?.let { "${it.condition} · H ${it.high}° L ${it.low}°" } ?: (Weather.lastError?.let { "Weather unreachable. " + it.take(48) } ?: "Getting the weather"),
                    px.s(18),
                    color = Color.White.copy(alpha = 0.66f)
                )
            }
        }
        // Prayer times
        Column(verticalArrangement = Arrangement.spacedBy(px.d(12))) {
            Label("PRAYER TIMES", px.s(18), color = Color.White.copy(alpha = 0.6f), weight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(px.d(10))) {
                PrayerTimes.names.forEachIndexed { i, name ->
                    val on = i == nextPrayer
                    val parts = PrayerTimes.format12(prayer.times[i]).split(" ")
                    Column(
                        Modifier
                            .width(px.d(97))
                            .clip(RoundedCornerShape(px.d(20)))
                            .background(if (on) Color.White else Color.White.copy(alpha = 0.08f))
                            .padding(vertical = px.d(14)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val fg = if (on) Color(0xFF0B0B0D) else Color.White
                        Label(name, px.s(17), color = fg.copy(alpha = 0.8f), weight = FontWeight.Medium)
                        Spacer(Modifier.height(px.d(4)))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Label(parts[0], px.s(22), color = fg, weight = FontWeight.Bold)
                            Spacer(Modifier.width(px.d(3)))
                            Label(parts.getOrElse(1) { "" }, px.s(13), color = fg, weight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(px.d(18))) {
            items.chunked(4).forEachIndexed { r, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(px.d(18))) {
                    line.forEachIndexed { c, item -> CcButton(item, r * 4 + c == index, px) }
                }
            }
        }
        Column {
            val item = items[index.coerceIn(0, items.lastIndex)]
            Label(item.name, px.s(26), weight = FontWeight.SemiBold)
            Spacer(Modifier.height(px.d(4)))
            Label(item.desc, px.s(20), color = Color.White.copy(alpha = 0.68f))
        }
    }
}

@Composable
private fun CcButton(item: CcItem, on: Boolean, px: Px) {
    val sc by animateFloatAsState(if (on) 1.08f else 1f, spring(0.6f, 400f), label = "ccScale")
    val bg by animateColorAsState(if (on) Color.White else Color.White.copy(alpha = 0.12f), label = "ccBg")
    val fg by animateColorAsState(if (on) Color(0xFF0B0B0D) else Color.White, label = "ccFg")
    Box(
        Modifier
            .size(px.d(118))
            .graphicsLayer {
                scaleX = sc
                scaleY = sc
                shadowElevation = if (on) px.f(20) else 0f
                shape = RoundedCornerShape(px.d(30))
                clip = true
            }
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Image(
            rememberVectorPainter(item.icon),
            contentDescription = item.name,
            modifier = Modifier.size(px.d(44)),
            colorFilter = ColorFilter.tint(fg)
        )
    }
}

@Composable
private fun AppMenuPanel(px: Px, app: TvApp, index: Int) {
    Column(
        Modifier
            .width(px.d(560))
            .clip(RoundedCornerShape(px.d(40)))
            .background(Color(0xF02A2A2E))
            .padding(px.d(28)),
        verticalArrangement = Arrangement.spacedBy(px.d(8))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = px.d(12))) {
            app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(px.d(64))) }
            Spacer(Modifier.width(px.d(20)))
            Label(app.label, px.s(30), weight = FontWeight.Bold)
        }
        menuOptions.forEachIndexed { i, option ->
            val on = i == index
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(px.d(20)))
                    .background(if (on) Color.White else Color.Transparent)
                    .padding(horizontal = px.d(24), vertical = px.d(16))
            ) {
                Label(option, px.s(26), color = if (on) Color(0xFF0B0B0D) else Color.White, weight = FontWeight.Medium)
            }
        }
    }
}
