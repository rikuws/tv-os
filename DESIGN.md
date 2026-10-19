---
name: Koti
description: An Apple TV-style home screen for a family's Sony Android TV.
colors:
  white: "#FFFFFF"
  text: "#F5F5F7"
  text-2: "#FFFFFF99"
  text-3: "#FFFFFF66"
  on-focus: "#1C1C1E"
  glass: "#1C1C1FC2"
  glass-strong: "#1C1C1FF5"
  glass-solid: "#1C1C1F"
  glass-edge-top: "#FFFFFF40"
  glass-edge-bottom: "#FFFFFF0D"
  scrim: "#000000"
  plate: "#2C2C2E"
  progress-track: "#FFFFFF4D"
typography:
  display:
    fontFamily: "Inter (variable, bundled; opsz 32), sans-serif"
    fontSize: "28sp"
    fontWeight: 700
  headline:
    fontFamily: "Inter (opsz 32)"
    fontSize: "20sp"
    fontWeight: 700
  title:
    fontFamily: "Inter (opsz 14)"
    fontSize: "18sp"
    fontWeight: 500
  body:
    fontFamily: "Inter (opsz 14)"
    fontSize: "17sp"
    fontWeight: 600
  label:
    fontFamily: "Inter (opsz 14)"
    fontSize: "14sp"
    fontWeight: 600
  caption:
    fontFamily: "Inter (opsz 14)"
    fontSize: "12sp"
    fontWeight: 500
rounded:
  card: "8dp"
  capsule: "20dp"
  row: "24dp"
  panel: "30dp"
spacing:
  edge: "48dp"
  dock-gap: "18dp"
  card-gap: "18dp"
components:
  sidebar-capsule:
    backgroundColor: "{colors.glass}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    rounded: "{rounded.capsule}"
    height: "40dp"
  sidebar-panel:
    backgroundColor: "{colors.glass-strong}"
    rounded: "{rounded.panel}"
    width: "250dp"
  menu-row:
    textColor: "{colors.text}"
    typography: "{typography.title}"
    rounded: "{rounded.row}"
    height: "48dp"
  menu-row-focused:
    backgroundColor: "{colors.white}"
    textColor: "{colors.on-focus}"
  app-tile:
    rounded: "{rounded.card}"
    width: "160dp"
    height: "90dp"
  continue-card:
    rounded: "{rounded.card}"
    width: "156dp"
    height: "88dp"
  apps-page:
    backgroundColor: "{colors.glass-solid}"
    rounded: "{rounded.panel}"
---

# Design System: Koti

## Overview

**Creative North Star: "The Apple TV App, at Home on a Sony"**

Koti speaks the Apple TV app's language (tvOS 26): content artwork fills the screen, a floating glass sidebar holds navigation, cards lift into light when focused, and type is white and confident. The owner pinned this direction with a photo of the Apple TV app running on this TV. It replaced two earlier worlds (a procedural blue-hour lake, then rotating landscape photos); that photo set survives as the fallback backdrop and the screensaver.

The screen is the household's apps and shows, nothing else. The backdrop is art for the household's own shows in the focused dock app (its Watch Next artwork, newest first), then that app's featured titles (Android TV preview channels, the platform's top shelf); apps that publish neither fall back to a landscape photo. A slow carousel (10 s, 700 ms crossfade) runs only while a dock app holds focus, like the Apple TV app's hero. No ads, no recommendation rows, no other motion unless the remote moves.

**Key Characteristics:**
- Art-first: full-bleed app artwork under a deep bottom scrim; rows sit on the dark.
- Glass without blur: tinted dark fill, a faint top wash, a bright top edge.
- Focus = lift + scale + deeper shadow + a sheen that slides in. No rings, no dimming of the rest.
- Labels under every card, always visible.

## Colors

Neutral: white type and dark glass; all colour comes from artwork.

### Primary
- **White** (#FFFFFF): the focused menu capsule, progress bars, the "in dock" dot.
- **Label white** (#F5F5F7): primary text.

### Neutral
- **Secondary / tertiary text** (white 60% / 40%): subtitles, hints, the clock in the panel header.
- **On focus** (#1C1C1E): text and icons on a white capsule.
- **Glass** (#1C1C1F at 76% / 96% / 100%): capsule / list panels and sidebar / full pages.
- **Glass edge** (white 25% at the top fading to 5%): the rim light that makes glass read as glass.
- **Scrim** (black): top 0 to 28% at up to 50%; bottom 34% to 82% rising to 93%; 50% behind open panels.
- **Plate** (#2C2C2E): tiles for apps without a banner.

### Named Rules
**The Art Brings the Colour Rule.** The UI adds no hue. No accent buttons, no coloured focus.

**The Solid Page Rule.** Without blur, translucent glass over busy content ghosts. Small panels are 96% glass over a dimmed scene; full pages are opaque.

## Typography

**Font:** Inter, variable, bundled (OFL), the closest open relative of SF Pro. Optical size 32 for headings, 14 for text.

### Hierarchy
- **Display** (700, 28sp): page titles ("Sovellukset").
- **Headline** (700, 20sp): row headings ("Jatka katsomista").
- **Title** (500, 18sp): sidebar and menu rows.
- **Body** (600, 17sp): capsule time, panel greeting.
- **Label** (500, 14sp): card titles.
- **Caption** (500, 12sp): card subtitles ("Seuraava jakso", "46 min jäljellä", app name).

## Layout

960 x 540 dp, 48dp edge rule, absolute positions so focus never shifts the layout:
- 28dp: sidebar capsule (home glyph + time), 40dp from the left.
- 250dp: dock, five 160 x 90 dp banners, 18dp gaps, centred.
- 362dp: "Jatka katsomista".
- 396dp: cards 156 x 88 dp, 18dp gaps, title and subtitle beneath; the row peeks 48dp of the next card. A focused card still clears the heading by about 12dp.
- Sidebar panel 250dp wide at (28, 22). Menus open upward from the tile or card they belong to.

## Elevation & Depth

Tiles rest at 3dp (cards 2dp) and spring up 12dp with a 7dp lift (cards 4dp) when focused, so the shadow deepens. The theme raises Android's shadow strength (ambient 0.16, spot 0.42) so lifted cards visibly float. The sidebar panel sits at 24dp, the capsule at 6dp, list panels at 24dp over a 50% scrim.

### Named Rules
**The Light Is the Focus Rule.** Unfocused content stays at full brightness, as on tvOS. A focused card scales to 1.10, rises and gains shadow, and a specular sheen (white ~20% at the top-left, gone by 40%) slides in from outside the corner over 450 ms, like light catching it as it lifts. Its labels drop 4dp and brighten. Holding OK presses the card to ~95%. Pushing past a row end nudges the card 6dp that way and springs it back. No outlines. All of it lives in `FocusFx`.

## Shapes

Cards and app tiles 8dp (≈5% of their width, measured from the Apple TV app on this TV). Capsule 20dp, menu rows 24dp (full capsule), panels 30dp. Card art is rendered at 116% of its resting size, so the focus zoom never upsamples on screen.

## Components

### Sidebar
Collapsed: a glass capsule with the home glyph and the time. Open (Left from a row's first item, or Back): a glass panel growing from its corner (scale 0.9 to 1, 220 ms) over a 50% dim, with greeting and time in the header, then Koti, Sovellukset, Tulot, Asetukset. Up/Down walk the items; Right or Back closes.

### App tile
The app's TV banner at the highest density it ships, 12dp corners. Hold OK (or Menu) to move or remove it.

### Continue card
Watch Next artwork (the app icon on a plate until it loads), a white progress bar on a dark bottom fade, and an "Uusi" badge (dark rounded label, 11sp semibold) on new episodes. Below: the title (14sp medium) and one line: the episode code if known ("K2, J16", parsed out of Areena's titles), then "Seuraava jakso", "N min jäljellä" or the app name. Hold OK to hide a card.

### Panels
Inputs and menus: 320dp list panels with a white capsule on focus. Sovellukset: an opaque full page with a five-column grid.

## Do's and Don'ts

### Do:
- **Do** let the focused app's art own the screen; keep UI on the bottom scrim and in glass.
- **Do** use springs (stiffness 520, damping 0.72) so the D-pad retargets instead of queueing.
- **Do** keep every D-pad edge a wall and remember each row's last focus.

### Don't:
- **Don't** add focus rings, accent colours, or a recommendations row.
- **Don't** use translucent glass for a full page (see The Solid Page Rule).
- **Don't** animate anything while idle beyond the dock carousel; no video, no blur (about 2 GB of RAM).
- **Don't** put an eyebrow label above a heading.
