# Revive TV

An Apple TV style home screen for Android TV boxes, built for the H96 Max (Android TV 14).

* Full screen Top Shelf that follows the app in focus, using each app's own TV artwork
* Tiles that lift, tilt and catch the light as you move with the remote
* Smooth scroll into the full app grid, with the Top Shelf blurring behind it
* Time (12 hour), live weather for Sheraton Heliopolis and today's prayer times
* Control Center: screen saver, WiFi, audio, display, Bluetooth, search, settings, and a button back to the original home screen
* Hold OK (or press Menu) on any app to move it, see app info or uninstall it

Weather comes from [Open-Meteo](https://open-meteo.com) (free, no account). Prayer times are calculated on the box with the Egyptian General Authority of Survey method, so they need no internet.

## Install on the TV

Every push to `main` builds a fresh APK automatically (Actions tab). The latest one is always at:

```
https://github.com/<your username>/revive-tv-launcher/releases/download/latest/revive-tv.apk
```

1. On the box, install **Downloader** (by AFTVnews) from the Play Store.
2. Open it, type the link above and press Go. It downloads and offers to install.
3. Allow Downloader to install unknown apps when asked.
4. Press Home on the remote. If the box asks which home app to use, pick **Revive TV** and choose **Always**.

Updates: open the same link again in Downloader. The new build installs over the old one and keeps your settings.

### If the box keeps opening its old home screen

From a computer on the same WiFi, with Network debugging on in the box's Developer options:

```
adb connect <box IP>:5555
adb shell cmd package set-home-activity com.revivecreates.tvlauncher/.MainActivity
```

## Remote controls

| Button | Does |
| --- | --- |
| Arrows | Move focus |
| OK | Open the app |
| Hold OK, or Menu | App options |
| Up from the top row | Focus the clock, then OK opens Control Center |
| Back | Close a panel or return to the top row |
| Home | Jump back to the top |

## Going back to the original home screen

Any of these works. Nothing on the box is removed or replaced.

* Control Center › **Original Home** opens it right away.
* Settings › Apps › Default apps › Home app, then pick the original launcher.
* Settings › Apps › Revive TV › **Uninstall**. The box falls back to its original home screen.
* If you used the adb command above, set the original back with the same command and its package name.
