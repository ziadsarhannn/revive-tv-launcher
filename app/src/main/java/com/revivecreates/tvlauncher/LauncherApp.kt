package com.revivecreates.tvlauncher

import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align),
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
    var pillFocused by remember { mutableStateOf(false) }
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

    LaunchedEffect(homeSignal) {
        menuApp = null
        ccOpen = false
        pillFocused = false
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

    fun launch(app: TvApp) {
        if (launching != null) return
        launching = app
        lastLaunched = app
        scope.launch {
            delay(420)
            repo.markOpened(app.packageName)
            val ok = SystemActions.launchApp(context, app)
            if (!ok) {
                launching = null
            } else {
                delay(4000)
                if (launching == app) launching = null
            }
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
            menuApp != null -> runMenu(menuIndex)
            ccOpen -> {
                ccOpen = false
                ccItems[ccIndex].run()
            }
            pillFocused -> {
                ccOpen = true
                ccIndex = 0
            }
            else -> focusedApp?.let { launch(it) }
        }
    }

    fun move(dx: Int, dy: Int) {
        when {
            menuApp != null -> {
                menuIndex = (menuIndex + dy).coerceIn(0, menuOptions.lastIndex)
            }
            ccOpen -> {
                val next = ccIndex + dx + dy * 4
                val sameRow = dx == 0 || (next >= 0 && next / 4 == ccIndex / 4)
                if (sameRow) ccIndex = next.coerceIn(0, ccItems.lastIndex)
            }
            pillFocused -> {
                if (dy > 0) pillFocused = false
            }
            rows.isEmpty() -> Unit
            dy < 0 && row == 0 -> {
                pillFocused = true
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
            menuApp != null -> menuApp = null
            ccOpen -> ccOpen = false
            pillFocused -> pillFocused = false
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
                        if (menuApp == null && !ccOpen && !pillFocused) focusedApp?.let { openMenu(it) }
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
                        menuApp != null -> menuApp = null
                        ccOpen -> ccOpen = false
                        pillFocused -> {
                            ccOpen = true
                            ccIndex = 0
                        }
                        else -> focusedApp?.let { openMenu(it) }
                    }
                }
                true
            }
            else -> false
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
        val dateText = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now))
        val prayer = remember(now / 60_000L) { PrayerTimes.forToday(now) }
        val cairoHours = PrayerTimes.cairoHours(now)
        val nextIndex = prayer.times.indexOfFirst { it > cairoHours }
        val shownNext = if (nextIndex >= 0) nextIndex else 0
        val untilHours = if (nextIndex >= 0) prayer.times[nextIndex] - cairoHours else 24 - cairoHours + prayer.times[0]
        val untilMinutes = Math.round(untilHours * 60).toInt()
        val untilText = (if (untilMinutes >= 60) "${untilMinutes / 60}h " else "") + "${untilMinutes % 60}m"
        val weatherIcon = if (weather?.let { Weather.isCloudy(it.code) } == true) Icons.Cloud else Icons.Sun
        val tempText = weather?.let { "${it.temp}°" } ?: "--°"

        // ---------- Top Shelf ----------
        val heroBlur by animateDpAsState(if (gridMode) px.d(48) else 0.dp, tween(700, easing = FastOutSlowInEasing), label = "heroBlur")
        val heroScale by animateFloatAsState(if (gridMode) 1.12f else 1f, tween(700, easing = FastOutSlowInEasing), label = "heroScale")
        val heroDim by animateFloatAsState(if (gridMode) 0.5f else 0f, tween(700), label = "heroDim")

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = heroScale
                    scaleY = heroScale
                }
                .then(if (heroBlur > 0.5.dp) Modifier.blur(heroBlur) else Modifier)
        ) {
            Crossfade(targetState = heroApp, animationSpec = tween(550), label = "hero") { app ->
                if (app != null) HeroArt(app, px) else Box(Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = heroDim)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.62f), 0.62f to Color.Transparent)
            )
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(px.d(460))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
        )

        val textAlpha by animateFloatAsState(if (!gridMode && launching == null) 1f else 0f, tween(400), label = "textAlpha")
        val textShift by animateDpAsState(if (gridMode) px.d(-40) else 0.dp, tween(600), label = "textShift")
        Crossfade(
            targetState = heroApp,
            animationSpec = tween(450),
            modifier = Modifier
                .offset(x = px.d(120), y = px.d(330) + textShift)
                .width(px.d(860))
                .graphicsLayer { alpha = textAlpha },
            label = "heroText"
        ) { app ->
            if (app != null) {
                Column {
                    Label(app.label, px.s(80), weight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(px.d(18)))
                    Label(openedText(repo.lastOpened(app.packageName), now), px.s(28), color = Color.White.copy(alpha = 0.78f))
                }
            }
        }

        // ---------- App grid ----------
        val gridY by animateDpAsState(
            if (!gridMode) px.d(806) else px.d(120) - px.d(280) * max(0, row - 2),
            tween(700, easing = FastOutSlowInEasing),
            label = "gridY"
        )
        Column(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset(x = px.d(120), y = gridY),
            verticalArrangement = Arrangement.spacedBy(px.d(102))
        ) {
            rows.forEachIndexed { r, items ->
                Row(horizontalArrangement = Arrangement.spacedBy(px.d(48))) {
                    items.forEachIndexed { c, app ->
                        val isFocused = !ccOpen && !pillFocused && r == row && c == col
                        key(app.packageName) {
                            AppTile(app, isFocused, pressed && isFocused, tiltX, tiltY, px)
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

        // ---------- Time, weather and next prayer. Press Up to focus it, OK for Control Center ----------
        val infoAlpha by animateFloatAsState(if (!gridMode && !ccOpen && launching == null) 1f else 0f, label = "infoAlpha")
        val infoBg by animateColorAsState(if (pillFocused) Color.White.copy(alpha = 0.16f) else Color.Transparent, label = "infoBg")
        val infoScale by animateFloatAsState(if (pillFocused) 1.06f else 1f, spring(0.6f, 400f), label = "infoScale")
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = px.d(36), end = px.d(96))
                .graphicsLayer {
                    alpha = infoAlpha
                    scaleX = infoScale
                    scaleY = infoScale
                }
                .clip(RoundedCornerShape(px.d(28)))
                .background(infoBg)
                .padding(horizontal = px.d(24), vertical = px.d(12)),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(px.d(10))
        ) {
            Label(timeText, px.s(44), weight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    rememberVectorPainter(weatherIcon),
                    contentDescription = null,
                    modifier = Modifier.size(px.d(28)),
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.88f))
                )
                Spacer(Modifier.width(px.d(10)))
                Label("$tempText Heliopolis", px.s(22), color = Color.White.copy(alpha = 0.88f), weight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = px.d(16), vertical = px.d(5))
                ) {
                    Label(
                        "${PrayerTimes.names[shownNext]} ${PrayerTimes.format12(prayer.times[shownNext])}",
                        px.s(22),
                        weight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(px.d(12)))
                Label("in $untilText", px.s(22), color = Color.White.copy(alpha = 0.75f))
            }
        }

        // ---------- App opening zoom ----------
        val lAlpha by animateFloatAsState(if (launching != null) 1f else 0f, tween(380), label = "lAlpha")
        val lScale by animateFloatAsState(if (launching != null) 1f else 0.4f, tween(480, easing = FastOutSlowInEasing), label = "lScale")
        val shown = lastLaunched
        if (lAlpha > 0.01f && shown != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = lAlpha
                        scaleX = lScale
                        scaleY = lScale
                    }
                    .background(if (shown.banner != null) shown.heroColor else shown.tint),
                contentAlignment = Alignment.Center
            ) {
                val banner = shown.banner
                if (banner != null) {
                    Image(
                        banner,
                        contentDescription = shown.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(px.d(720), px.d(405)).clip(RoundedCornerShape(px.d(36)))
                    )
                } else {
                    shown.icon?.let { Image(it, contentDescription = shown.label, modifier = Modifier.size(px.d(260))) }
                }
            }
        }

        // ---------- Overlays ----------
        AnimatedVisibility(
            visible = ccOpen || menuApp != null,
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

@Composable
private fun HeroArt(app: TvApp, px: Px) {
    val drift = rememberInfiniteTransition(label = "drift")
    val t by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "t"
    )
    Box(Modifier.fillMaxSize().background(app.heroColor)) {
        val art = app.banner
        if (art != null) {
            if (Build.VERSION.SDK_INT >= 31) {
                Image(
                    art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.55f
                            scaleX = 1.2f + t * 0.06f
                            scaleY = 1.2f + t * 0.06f
                            translationX = -t * px.f(60)
                        }
                        .blur(px.d(80))
                )
            }
            Image(
                art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = px.d(150), end = px.d(120))
                    .size(px.d(760), px.d(428))
                    .graphicsLayer {
                        translationX = -t * px.f(28)
                        translationY = t * px.f(10)
                        scaleX = 1f + t * 0.02f
                        scaleY = 1f + t * 0.02f
                        shadowElevation = px.f(40)
                        shape = RoundedCornerShape(px.d(30))
                        clip = true
                    }
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = px.d(90), end = px.d(120))
                    .size(px.d(760))
                    .graphicsLayer {
                        translationX = -t * px.f(70)
                        translationY = t * px.f(36)
                    }
                    .clip(CircleShape)
                    .background(app.tint)
            )
            app.icon?.let {
                Image(
                    it,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = px.d(330), end = px.d(360))
                        .size(px.d(280))
                        .graphicsLayer { translationX = -t * px.f(50) }
                )
            }
        }
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
) {
    val scale by animateFloatAsState(
        when {
            focused && pressed -> 1.02f
            focused -> 1.1f
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
                    shape = RoundedCornerShape(px.d(22))
                    clip = true
                }
                .background(app.tint)
        ) {
            val banner = app.banner
            if (banner != null) {
                Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(px.d(84))) }
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
                .graphicsLayer { alpha = lift },
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
                    weather?.let { "${it.condition} · H ${it.high}° L ${it.low}°" } ?: "Weather updates when online",
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
