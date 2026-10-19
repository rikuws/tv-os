# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

(Android TV 9 / API 28 on a Sony BRAVIA 4K GB ATV3, 1920×1080 at 320 dpi = 960×540 dp, driven only by a D-pad remote. Leanback/10-foot conventions apply, not phone Material navigation.)

## Stack

Java, Android Views, AndroidX RecyclerView, minSdk 28, debug build. Built with Gradle on macOS, installed over ADB (adb-shell Python client) and set as the default HOME activity. No third-party launcher.

## Users

A Finnish family with kids sharing one living-room TV. Adults and children both pick what to watch; kids recognise apps and shows by picture, not by text. Viewed from the sofa, evenings, often in dim light.

## Product Purpose

Replace the stock Google TV launcher (ad hero, promo rows, Sony Select) with a calm home screen that gets the family into their apps immediately. Success: power on, one or two presses, and the right app or show is playing, with nothing advertising at them.

## Positioning

A home screen for this one household's TV: their five apps, their recently watched shows from those apps, their inputs. No store, no recommendations, no network polling, no account.

## Operating Context

- First move on power-on is usually opening an app, so the app dock leads; resuming a show is second.
- Dock apps, in order: Apple TV (`com.apple.atve.androidtv.appletv`), Yle Areena (`com.yle.webtv`), MTV Katsomo (`fi.mtvkatsomo`), Netflix (`com.netflix.ninja`), YouTube (`com.google.android.youtube.tv`). Must be easy to reconfigure. (HBO Max was in the dock originally; it stays installed and is reachable from Sovellukset.)
- Also installed and kept: HBO Max (`com.wbd.stream`), the Sony TV app (tuner + HDMI 1–4 + AV), Play Store, Settings.
- Continue-watching comes from the Android TV Watch Next table (TvProvider), which these apps already populate; needs `READ_TV_LISTINGS`.
- Remote: Sony IR/BT remote with D-pad, Back, Home, Input, TV keys. The Input key opens Sony's own input picker (`com.sony.dtv.scrums.action`).

## Capabilities and Constraints

- D-pad navigation must be deterministic: Up/Down move exactly one row, row ends and the sidebar's ends never fall through, Left from a row's first item (or Back) opens the sidebar, and the last focused tile is remembered. (The original top bar became the tvOS-style sidebar at the owner's choice.)
- Light: the TV has ~2 GB RAM (~876 MB free after the debloat). No blur, no video, no network polling. Watch Next posters and each app's featured art are fetched on demand and cached on disk.
- Visual direction pinned by the owner: the Apple TV app (tvOS 26 sidebar style), from a photo of it running on this TV.
- Launcher text in Finnish.
- Screen captures of live TV are blocked (secure layer); everything the launcher draws must be verifiable via `screencap`.

## Brand Commitments

None. No external brand; the household's apps bring their own logos and colour.

## Evidence on Hand

- Real app banners/icons from the installed packages (`ApplicationInfo.loadBanner` / `loadIcon`).
- Real Watch Next rows on the TV (Yle Areena kids' shows observed in the stock Play Next row).
- Screenshots of the stock launcher in `shots/`.

## Product Principles

1. The household's apps are the content; the launcher is the frame.
2. One press to anything the family does daily; nothing on screen that sells.
3. Focus is always obvious from across the room, and movement is predictable.
4. Pictures over words, so kids can navigate it.
5. Cheap to run: nothing animates or loads unless the user moved, except the backdrop carousel the owner chose, which runs only while a dock app holds focus.

## Accessibility & Inclusion

Readable from ~3 m: no text below 12 sp on the 960 dp-wide canvas, card labels 14 sp; focus shown by more than colour alone (scale + lift + shadow + brightness + sheen, the tvOS way). Kids who can't read yet must be able to find their app by its picture.
