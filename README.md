# tv-os: debloated Sony BRAVIA + Koti launcher

Sony BRAVIA 4K GB ATV3 (Android TV 9, API 28) at `192.168.1.99`. 50 manufacturer,
telemetry and unused packages are disabled (never uninstalled), and the stock Google TV
launcher is replaced by **Koti**, a small native launcher in the Apple TV app's style: the
focused app's featured artwork as the backdrop, a glass sidebar, your five apps in a dock,
and a labelled "Jatka katsomista" row built from Android TV's Watch Next.
Everything here is reversible.

```
tools/tv                  adb client (adb-shell over Wi-Fi): shell, screenshot, key, push, install
tools/restore.sh          re-enable what was disabled; give home back to the stock launcher
tools/build-install.sh    build Koti, install it, grant Watch Next access (--set-home)
tools/push-wallpapers.sh  sync wallpapers/ to the TV
tools/disable.sh          disable packages one pm call at a time, logging them for restore
debloat/disable-list.txt  what was disabled and why, by category
backup/<date>/            package lists, getprop, all settings, default home before changes
backup/disabled-by-tvos.txt  what is disabled right now (restore.sh reads this)
launcher/                 Koti source (Java, Android Views, RecyclerView, minSdk 28)
wallpapers/               the photos in rotation + CREDITS.md
PRODUCT.md, DESIGN.md     product brief and the design system as built
```

## Connecting

macOS Local Network privacy blocks the stock `adb` binary ("No route to host"), so
`tools/tv` uses the Python `adb-shell` package from `.venv` with your `~/.android/adbkey`.

```bash
python3 -m venv .venv && .venv/bin/pip install adb-shell
```

```bash
tools/tv shell "pm list packages -d"
```

```bash
tools/tv screenshot shot.png
```

```bash
tools/tv key DOWN RIGHT OK
```

Set `TV_IP` if the TV's address changes. The first connection from a new key shows
"Allow USB debugging?" on the TV. Large pushes go as base64 chunks with an md5 check.

## Re-enabling apps

Everything that was disabled is listed in `debloat/disable-list.txt`. To bring one back:

```bash
tools/restore.sh com.amazon.amazonvideo.livingroom
```

Common ones: Prime Video `com.amazon.amazonvideo.livingroom`, Disney+ `com.disney.disneyplus`,
Viaplay `com.viaplay.android`, Ruutu `com.thirdpresence.ruutu`, Spotify `com.spotify.tv.android`,
Chromecast `com.google.android.apps.mediashell` + `com.sony.dtv.dialservice`,
Google Assistant `com.google.android.katniss` + `com.google.android.speech.pumpkin`,
screen mirroring `screnmirroring.com` + `com.sony.dtv.networkapp.wifidirect`,
Ambient screensaver `com.google.android.backdrop`. A re-enabled app appears in
Koti's Sovellukset automatically.

To undo everything, re-enable all of it, and make the stock launcher home again:

```bash
tools/restore.sh
```

Add `--keep-launcher` to re-enable the apps but keep Koti.

Note: Sony's Discover service (`com.sony.dtv.discovery`) is a *persistent* system app,
so Android starts its process at boot even though it is disabled. Its receivers and
overlay are off; it only idles. Removing it would require uninstalling, which this
setup deliberately never does.

## Koti

**Remote:** Up/Down move exactly one row (dock, Jatka katsomista) and stop at the
edges; Left/Right stop at row ends, and each row remembers its last tile. **Left from a
row's first item, or Back, opens the sidebar** (Koti, Sovellukset, Tulot, Asetukset);
Right or Back closes it. OK opens. Hold OK on a dock tile to move or remove it, on a
card to hide it, or in Sovellukset to add/remove an app from the dock. Number keys 1 to
5 open the dock apps directly. Home always returns to the dock.

**Backdrop:** focusing a dock app shows art for *your* shows in that app first (the
artwork of its "Jatka katsomista" items, newest first: Slow Horses for Apple TV, Pikku
Kakkonen for Areena, your latest videos for YouTube), then the app's own featured titles.
While the app stays focused, a slow carousel crossfades to the next image every 10
seconds; it stops when you move away, and each return home starts again at your newest
show. Netflix publishes only featured titles; MTV Katsomo publishes neither, so it shows
a landscape photo. Images
are fetched in backdrop size where the CDN allows (Areena, Apple, YouTube) and cached on
disk. The carousel interval is `CAROUSEL_MS` in `MainActivity.java`.

**Build and install** (JDK 17 and the Android SDK; see `tools/build-install.sh`):

```bash
tools/build-install.sh
```

The first time, also make it the default home and disable the stock launcher:

```bash
tools/build-install.sh --set-home
```

### Changing the dock

On the TV: hold OK on a dock tile, or add apps from Sovellukset. From the Mac, list
packages in the order you want:

```bash
tools/tv shell "am start -n fi.koti.launcher/.MainActivity --es dock com.yle.webtv,com.netflix.ninja,com.google.android.youtube.tv,fi.mtvkatsomo,com.apple.atve.androidtv.appletv"
```

Back to the default (Apple TV, Yle Areena, MTV Katsomo, Netflix, YouTube):

```bash
tools/tv shell "am start -n fi.koti.launcher/.MainActivity --ez reset_dock true"
```

The defaults live in `launcher/app/src/main/java/fi/koti/launcher/Prefs.java`.

### Changing the wallpapers

The landscape photos are the backdrop for apps without featured art and the screensaver's
pictures, cycled in shuffled order before any repeats. They live in `wallpapers/`;
add, remove or replace JPEGs (ideally 1920×1080, calm and not too bright), then:

```bash
tools/push-wallpapers.sh
```

Only changed files are sent, and photos you deleted are removed from the TV. To use
another folder, pass it: `tools/push-wallpapers.sh ~/Pictures/tv`. With no photos
installed, Koti falls back to a plain dusk gradient. Credits for the bundled set:
`wallpapers/CREDITS.md` (Wikimedia Commons Featured Pictures, CC BY-SA / CC BY).

### Screensaver

Koti's screensaver (the same photos with a large clock, a new photo every ten minutes,
drifting a few dp each minute against burn-in) is set as the system screensaver. To
switch back to Google's Ambient mode:

```bash
tools/restore.sh com.google.android.backdrop
```

### Continue watching

Built from the Watch Next table that the apps fill themselves (today: Apple TV, Yle
Areena and YouTube; Netflix and Katsomo publish none). Koti decides what to show:
newest first, at most 3 per app, leaving out finished items (95%+ watched), anything not
touched for 90 days, watchlist entries, and cards you hid. Each card shows its title and
one fact: "Seuraava jakso", "Uusi jakso", "N min jäljellä" or the app name. The rules
live in `WatchNextRepository.java` (`PER_APP`, `FINISHED`, `MAX_AGE_MS`). Koti cannot
change what an app publishes or delete its rows, only filter them. To bring hidden
cards back:

```bash
tools/tv shell "am start -n fi.koti.launcher/.MainActivity --ez unhide_all true"
```

Koti needs `READ_TV_LISTINGS`, which `build-install.sh` grants.

## When you're done

Turn **USB debugging off** on the TV: Asetukset → Laiteasetukset → Tietoja → (press
Koontiversio 7 times if Kehittäjäasetukset is hidden) → Kehittäjäasetukset →
USB-vianetsintä. While it is on, anything on the local network with an authorised key
can control the TV, and some BRAVIA models cut power to USB devices. The tools here
need it back on to run.
