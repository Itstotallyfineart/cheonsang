# Cheonsang 2.0.0 User Manual

For Cheonsang 2.0.0 · 2026-11-15

## Contents

1. Overview
2. Installation
3. First Launch
4. Screen Layout
5. Controls
6. The Telescope
7. Command Reference
8. Settings
9. Observing Logbook, Observing Notebook, and Advancements
10. Running a Multiplayer Server
11. Performance
12. Troubleshooting
13. Known Limitations
14. Data Sources
- Appendix A. Astronomical Observing Guide
  - A.1 Observing Conditions
  - A.2 Principles of Telescope Optics
  - A.3 Finding Your Way Around the Sky
  - A.4 Observing Tips by Target
  - A.5 Forecasting and Observing Events
  - A.6 Targets by Season
  - A.7 Observing Records
  - A.8 Observing Exercises
  - A.9 Scope of the Model

## 1. Overview

Cheonsang is a Fabric mod that replaces the sky of the Minecraft Overworld with a real celestial sphere. From the observing location (latitude and longitude) and the date and time, it computes the positions and brightness of stars, the Sun, the Moon, planets, and deep-sky objects, then draws them on screen after applying the atmosphere, sky brightness, light pollution, and telescope optics. Star positions and magnitudes come from the HYG v4.1, Tycho-2, and Gaia DR3 catalogs. Positions of the Sun, Moon, and planets are computed with celestial mechanics formulas.

If the game language is Korean, the screens, command responses, info cards, notebook, and advancements appear in Korean. In any other language they appear in English.

## 2. Installation

### 2.1 Requirements

| Item | Requirement |
| --- | --- |
| Minecraft | Java Edition 26.3 |
| Mod loader | Fabric Loader 0.20.1 or later |
| Java | 25 or later |
| Required mods | Fabric API, Fabric Language Kotlin |
| Optional mods | Mod Menu (opens the settings screen from the mod list) |
| Incompatible | Iris (shader mod). The game will not start if both are installed |

Development and testing used Fabric API 0.165.0+26.3 and Fabric Language Kotlin 1.14.2+kotlin.2.4.21.

### 2.2 Installation Steps

1. Use the Fabric installer to install Fabric Loader (0.20.1 or later) for Minecraft 26.3.
2. Put the Fabric API and Fabric Language Kotlin jars in the `mods` folder of your game directory.
3. Put the Cheonsang main jar (`cheonsang-2.0.0.jar`, 111.5 MB) in the `mods` folder. Delete any cheonsang jar from an earlier version.
4. If needed, put Mod Menu and the extension pack in the same folder.
5. Launch the game with the Fabric profile.

### 2.3 Extension Pack cheonsang-extra

The extension pack `cheonsang-extra-1.0.1.jar` (434.0 MB) is optional and goes in the same `mods` folder as the main mod. Every feature of the main mod works without the extension pack.

| Item | Main mod only | With extension pack |
| --- | --- | --- |
| Telescope stars | 2,409,260 stars, display limit mag 13.0 | Adds 32,061,510 Gaia DR3 stars, display limit mag 15.2 |
| Naked-eye Milky Way | 4K (12K when magnified in the telescope) | 16K and 32K tiles (only the visible part is loaded) |
| Moon map | Up to 4K | 8K |
| Deep-sky objects and observing sketches | All included | No change |

The old Gaia star pack (`cheonsang-stars-*.jar`) contains the same files as the extension pack, so you can remove it. Leaving both installed causes no problems. The status of installed packs is shown at the bottom of the Cheonsang settings screen.

Long-exposure (photo) mode requires a separate photo pack (`cheonsang-ultra`). The 2.0.0 release bundle consists of the main mod and cheonsang-extra and does not include the photo pack. If the photo pack is already installed, it keeps working.

### 2.4 Server Installation

On a multiplayer server, put the Cheonsang main mod, Fabric API, and Fabric Language Kotlin in the server's `mods` folder. The server and every player who connects must all have the same 2.0.0 installed. The extension pack only affects rendering, so it needs to go on each client only.

The sky also works on the client alone when the server does not have Cheonsang. In that case each player sees the sky for the date and location set on their own settings screen (default 2026-08-11, 0° 0°).

## 3. First Launch

### 3.1 Default Sky in a New World

A new world starts on 2026-08-11 at 06:00, at latitude 0° and longitude 0°. The compression rate is 1.0 (real speed). The next day, August 12, has a total solar eclipse, the peak of the Perseid meteor shower, and a waning crescent Moon all at once, and August 28 has a partial lunar eclipse. The point of greatest eclipse for the August 12 total solar eclipse is at about 65°N 25°W.

Worlds from earlier versions keep their saved date and location, and their observing logbook and advancements are preserved.

### 3.2 Basic Rules for Time and Location

| Item | Rule |
| --- | --- |
| Date and time flow | One per world. Set by an OP with `/cele set` and `/cele rate` |
| Observing location | Per player. Starts as the world default location (the `/cele set` value). `/cplace` changes only your own sky |
| Minecraft clock | Local mean time at your observing location. Equal to Universal Time (UTC) at longitude 0° |
| Solar transit | Differs from 12:00 by the equation of time (up to about ±16 minutes) |
| Changing longitude | The Minecraft clock and day/night stay the same; only Universal Time shifts by the longitude |
| Compression rate | How many days the sky advances in one Minecraft day. At 1.0, one Minecraft day is one day of sky |

By default, the day and night on screen (sky color, fog, and light on the ground) follow the actual solar altitude computed by Cheonsang. Mob spawning and sleeping keep the vanilla rule of a 10-minute day and 10-minute night (see section 8.3 to change this).

### 3.3 First Things to Try

1. When night falls, check tonight's highlights with `/cwhen`.
2. Craft a telescope (section 6.1).
3. Hold the telescope, right-click to turn it on, and raise the magnification with the mouse wheel. A control guide appears once the first time you turn it on.
4. Find an object with `/cfind Jupiter` or similar, and left-click to select it and see its info card.
5. In the controls settings, assign keys to "Cheonsang settings" and "Cheonsang observing notebook" (no default).

## 4. Screen Layout

| Element | Location and content |
| --- | --- |
| Sky | Stars, Milky Way, Sun, Moon, planets, deep-sky objects, constellation lines and names (telescope view), meteors, aurora |
| Crosshair | Center of the telescope view (can be turned off in settings) |
| Optics line | One line at the lower left while using the telescope: aperture · objective focal length · eyepiece · magnification (for example, 80mm · focal length 560mm · eyepiece 25mm · ×22) |
| Info card | Information about the selected object. Default position is the top left, opacity 69% |
| Selection marker | A circle around the selected object. While tracking, the card title shows [tracking] |
| Angular distance display | A purple circle and connecting line, and "Angle → name" on the last line of the card |
| Direction arrow | For 60 seconds after running `/cfind`. Turns off once the object is found |
| Error display | "Cheonsang ... error" at the top of the screen (section 12) |

The optics line positions itself so that the hotbar and offhand slot do not cover it. If the empty space on the left of the screen is narrow, the text shrinks automatically (down to half size). If it still does not fit, the line is shown above the health and armor bars.

### 4.1 Info Card Contents

| Target | Items shown |
| --- | --- |
| All | Azimuth and altitude (including atmospheric refraction), right ascension and declination (of date), rise, transit (altitude), and set |
| Stars | HIP and HD numbers, spectral type, magnitude, absolute magnitude, distance |
| Named notable stars | Magnitude, distance, one-line description |
| Deep stars (fainter than mag 9) | Magnitude, color, position, rise and set (no catalog number) |
| Planets | Magnitude, diameter, phase, distance from Earth and from the Sun, elongation from the Sun |
| Moon | Phase, age, diameter, distance |
| Lunar features | Type, diameter, lunar latitude and longitude, libration for that day |
| Sun | Diameter, distance |
| Nebulae, clusters, and galaxies | Type (de Vaucouleurs morphology for galaxies), NGC number, magnitude, size, distance |
| Moons of planets | Magnitude, position, "Currently hidden" when behind the parent planet |

The rise/set line shows Rise and Set (Sunrise and Sunset for the Sun, Moonrise and Moonset for the Moon). Where applicable it shows "Never sets", "At this latitude: never rises", "All day above horizon", or "All day below horizon". The azimuth/altitude, RA/Dec, and rise/set lines can each be hidden on the settings screen.

## 5. Controls

### 5.1 Cheonsang Keys

These are in the "Miscellaneous" category of the controls settings, and all of them can be changed.

| Key name | Default | Action |
| --- | --- | --- |
| Telescope display filter | N | Cycles the display: All → Lines only → Names only → Off |
| Track object (hold + left click) | Left Alt | Hold and left-click to select and lock tracking |
| Objective aperture (hold + scroll) | Left Shift | Hold and scroll to change the aperture |
| Cheonsang settings | None | Opens the Cheonsang settings screen |
| Cheonsang observing notebook | None | Opens the observing notebook |

Turning off constellation lines and names with the N key gives a view closer to a real eyepiece.

### 5.2 Telescope Controls

| Input | Action |
| --- | --- |
| Right-click | Turns the telescope on or off. Each time it starts at ×1 with an 80mm aperture. Turns off when you stop holding it |
| Mouse wheel | Changes the eyepiece: ×1 → 40 · 32 · 25 · 20 · 15 · 12 · 9 · 7 · 5 · 4 · 3mm |
| Aperture key + wheel | Changes the aperture: 50 · 60 · 70 · 80 · 90 · 102 · 114 · 127 · 150 · 200 · 254 · 305mm |
| Left-click | Selects the object at the center of the view (circle marker and live info card) |
| Sneak key (Shift) + left-click, or left-click on empty sky | Clears the selection |
| Tracking key + left-click | Selects and locks tracking. The object stays at the center of the view |
| Ctrl + left-click | With an object selected, measures the angular distance to the object at the center of the view |

Tracking is released when you press tracking key + left-click again, press Shift + left-click, or move the mouse a large amount. It is also released automatically when the object sets below the horizon. Tracking continues while chat or other screens are open. While a screen such as the notebook, settings, or advancements is open, wheel input goes to that screen.

## 6. The Telescope

### 6.1 Crafting

Craft it at a crafting table with a shaped recipe. In Creative mode it is in the "Tools & Utilities" tab.

| | Left | Center | Right |
| --- | --- | --- | --- |
| Top row | | Glass Pane | |
| Middle row | | Spyglass | |
| Bottom row | Iron Ingot | Iron Ingot | Iron Ingot |

The item is named "Telescope". It is drawn as a 3D refractor model, and when you magnify, the player takes a pose with the eyepiece held to the eye. In multiplayer, other players also see this pose.

### 6.2 Optical Values

| Item | Value |
| --- | --- |
| Focal ratio | Fixed at f/7 (objective focal length = aperture × 7) |
| Magnification | Objective focal length ÷ eyepiece focal length. Maximum 305mm + 3mm = ×712 |
| Limiting magnitude | Naked-eye limit + aperture gain. With a naked-eye limit of 6.5, equal to 7.7 + 5·log10(aperture in cm). At the default setting (naked eye 6.0), 11.7 at 80mm and 14.6 at 305mm. Reduced further by sky brightness and dark adaptation |
| Limiting magnitude cap | 13.0 with the main mod only, 15.2 with the extension pack installed |
| Star image size | Diffraction radius (138″/D, D in mm) combined with 1.5″ seeing. Close binaries split depending on aperture |
| Exit pupil | Above 7mm, the image dims by the excess |
| Over-magnification | When magnification exceeds aperture (mm) × 2, the image blurs (magnification is not blocked) |
| Field of view | Round field, slight seeing shimmer |

| Aperture | Objective focal length | Magnification range | Limiting magnitude (naked eye 6.0 / 6.5) | Reference maximum magnification |
| --- | --- | --- | --- | --- |
| 50mm | 350mm | ×8.8–×117 | 10.7 / 11.2 | ×100 |
| 80mm (default) | 560mm | ×14–×187 | 11.7 / 12.2 | ×160 |
| 127mm | 889mm | ×22–×296 | 12.7 / 13.0 | ×254 |
| 305mm | 2,135mm | ×53–×712 | 14.6 / 15.1 | ×610 |

For 127mm and 305mm, the part of the naked-eye 6.5 values beyond mag 13.0 appears only with the extension pack.

For an f/7 telescope, the exit pupil for each eyepiece is the same regardless of aperture:

| Eyepiece (mm) | 40 | 32 | 25 | 20 | 15 | 12 | 9 | 7 | 5 | 4 | 3 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Exit pupil (mm) | 5.7 | 4.6 | 3.6 | 2.9 | 2.1 | 1.7 | 1.3 | 1.0 | 0.7 | 0.6 | 0.4 |

The star image radius is 1.88″ at 50mm, 1.37″ at 80mm, 1.11″ at 127mm, and 0.94″ at 305mm. When two stars are separated by enough more than the star image size, they split into two on screen on their own. Appendix A.2 explains what these values mean.

### 6.3 What You See at Each Magnification

| Magnification | What you see |
| --- | --- |
| ×1 | Normal view, crosshair, names, left-click selection |
| ×10 and above | Milky Way, constellation lines, nebula glow, background stars |
| ×50 and above | Planet disks, the whole Moon, glow of nebulae and clusters, galaxy observing sketches (with the photo pack and long-exposure mode, 45 photos are overlaid) |
| ×150 and above | Planet surface markings, Saturn's rings, Jupiter's moons, seven moons of Saturn, lunar craters and feature names |

Saturn's moon Mimas (mag 12.9) is at the limit near 305mm. Constellation lines are drawn in full even at high magnification. Above ×700 you can still select objects precisely with a left-click, including a faint companion right next to a bright star, such as Sirius B.

### 6.4 Finding and Tracking

Running `/cfind <name> true` turns your view to that object. If you are holding a telescope, it turns on and tracking locks. Moons, planets, nebulae, clusters, and galaxies lock directly onto the object found; for stars, the one at the center of the view is selected. Moons stay tracked even behind their planet and show "Currently hidden". If the object is below the horizon, the view is not turned.

At high magnification the field is narrow and the target drifts out quickly, so it is easier to find the object at low magnification, lock it with tracking key + left-click, and then raise the magnification.

## 7. Command Reference

In every command, command words (set, solar, total, and so on) are English only. Object names can be given in Korean or English. Latitude is positive to the north, and longitude is positive to the east. Use a period (.) as the decimal point.

### 7.1 Commands for Everyone

#### /cele

```
/cele
```

Shows the sky date and time (local mean time), the compression rate (how many Minecraft days make one year), and the world default location. The next line shows sunrise, solar transit, and sunset, and moonrise and moonset. When real time is on, the city name and local time are also shown.

#### /cplace

```
/cplace
/cplace <latitude> <longitude>
/cplace <place>
/cplace reset
```

| Form | Action |
| --- | --- |
| `/cplace` | Shows your observing location and the world default location |
| `/cplace <latitude> <longitude>` | Moves only your sky to that location. Latitude −90–90, longitude −180–180 |
| `/cplace <place>` | Sets the location by place name (English id or Korean name, with autocomplete) |
| `/cplace reset` | Returns to the world default location |

Examples: `/cplace 70 20`, `/cplace -30 135`, `/cplace seoul` (Korean city names also work)

This does not affect anyone else's sky and does not require OP permission. The value is saved separately for each server and world in `config/cheonsang/places.json`. Observations made from a moved location also count toward the logbook and advancements.

Place names (from north to south):

| id | Name | Latitude | Longitude |
| --- | --- | --- | --- |
| longyearbyen | Longyearbyen | 78.22 | 15.65 |
| reykjavik | Reykjavik | 64.15 | −21.94 |
| london | London | 51.51 | −0.13 |
| greenwich | Greenwich | 51.48 | 0.00 |
| newyork | New York | 40.71 | −74.01 |
| seoul | Seoul | 37.57 | 126.98 |
| cairo | Cairo | 30.04 | 31.24 |
| hongkong | Hong Kong | 22.32 | 114.17 |
| singapore | Singapore | 1.35 | 103.82 |
| lima | Lima | −12.05 | −77.04 |
| sydney | Sydney | −33.87 | 151.21 |
| buenosaires | Buenos Aires | −34.60 | −58.38 |
| ushuaia | Ushuaia | −54.80 | −68.30 |
| mcmurdo | McMurdo | −77.85 | 166.67 |

The 13 places other than greenwich are also available as real-time cities.

#### /cfind

```
/cfind <name>
/cfind <name> true
/cfind <name> false
```

| Form | Action |
| --- | --- |
| `/cfind <name>` | Shows altitude and azimuth, and a direction arrow for 60 seconds |
| `/cfind <name> true` | Turns your view to the object. If you are holding a telescope, turns it on and locks tracking |
| `/cfind <name> false` | Arrow only |

Typing a space after the name brings up true and false in autocomplete. If the object cannot be seen now (below the horizon, or the sky is still bright), the best-night line from `/cwhen <object>` follows, with [place], [go], and [next] buttons.

Names you can search for (spaces and case are ignored; Korean names also work with the English UI):

| Category | Examples |
| --- | --- |
| Sun, Moon, and planets | Sun, Moon, Saturn, and their Korean names (two Korean words are accepted for the Sun) |
| Moons | Titan, Rhea, Iapetus, Tethys, Dione, Enceladus, Mimas, Io, Europa, Ganymede, Callisto, Saturn Ⅰ–Ⅷ, Jupiter Ⅰ–Ⅳ, Saturn VI |
| Asteroids | Ceres, 1 Ceres, Vesta |
| Star names | Vega, and its Korean names, including the traditional name Jingnyeoseong |
| 18 named notable stars | Proxima Centauri, Barnard's Star, Wolf 359, 61 Cygni, Tabby's Star, VY Canis Majoris, UY Scuti, Cygnus X-1, Eta Carinae, and others |
| Star designations | α Lyr, Alpha Lyrae, 61 Cyg, ο1 Eri, and Korean forms (constellation name before or after the Greek letter, or the letter spelled out with a short constellation name) |
| Numbers | HIP 32349, HD 48915 |
| Companion stars | Sirius B, Almach C (stars within 2′ of the primary are lettered B, C, … in order of brightness) |
| Constellations | Lyra, Lyr, Lyrae, and the Korean full or short name |
| Cheonsang Yeolcha asterisms | Bukdu, Heonwon, Jujeong, and others, in Hangul or romanized form (if the name matches a Western constellation, the Western one comes first) |
| Nebulae, clusters, and galaxies | M31, Messier 31, C14, Caldwell 14, NGC 104, NGC2903, IC, Mel, and Cr numbers, Korean and English names |
| Meteor showers | Perseid meteor shower, PER (radiant) |
| Lunar features | Mare Tranquillitatis, Tycho, and others, 787 in all (if on the far side, you are guided to the center of the Moon) |
| Variable stars | Mira |

Faint stars with no name or number cannot be searched.

#### /cwhen

```
/cwhen
/cwhen <object>
/cwhen solar <total|annular|partial>
/cwhen lunar <total|partial|penumbral>
/cwhen meteor [name]
/cwhen full
/cwhen new
/cwhen conj
/cwhen next
```

This command is computed only on your own client, so it works even when the server does not have Cheonsang. The computation runs on a separate thread ("Calculating…"). Entering the same command again cancels the previous computation.

| Form | Result |
| --- | --- |
| `/cwhen` | Tonight: sunset, sunrise, and the fully dark period; Moon phase, moonrise and moonset, and the moonless period; visible planets; five bright nebulae, clusters, and galaxies that rise high; meteor showers. Objects fainter than the naked-eye limit are marked "(telescope)". The end of the first line shows the "Bortle here" value |
| `/cwhen <object>` | One line for the best night to see the object (date, time, altitude, azimuth; phase % for the Moon and lunar features). Names are the same as for `/cfind`. If several names match, "other matches" are listed |
| `/cwhen solar <type>` | The next solar eclipse. Latitude and longitude of the point of greatest eclipse, the time there, solar altitude, duration, path width, and how much is visible from here |
| `/cwhen lunar <type>` | The next lunar eclipse. Times of penumbral and umbral contact, totality, maximum, and end, and the fraction covered. If the Moon is up, "visible here" and the Moon's altitude |
| `/cwhen meteor [name]` | The next meteor shower peak. Radiant altitude, expected count per hour, and the Moon. Without a name, the nearest shower |
| `/cwhen full` · `new` · `conj` | The next full moon · new moon · planetary conjunction (for your observing location) |
| `/cwhen next` | One line each for the next solar eclipse, lunar eclipse, full moon, new moon, meteor shower, and planetary conjunction (for eclipses, the nearest one of any type) |

`/cwhen <object>` judges a night by the object's altitude, the darkness of the sky (solar altitude), moonlight, and the margin below the limiting magnitude. If the object does not rise or stays too low at that latitude, a latitude where it is well placed is also suggested. The same tonight summary is also available in the "Tonight" tab of the observing notebook.

Argument details:

| Argument | Values |
| --- | --- |
| solar type | total, annular, partial. partial means an eclipse that is not total or annular anywhere (NASA classification). Hybrid eclipses are found under both total and annular |
| lunar type | total, partial, penumbral |
| meteor name | QUA, LYR, ETA, PER, ORI, LEO, GEM, or the Korean or English name |

Entering only `/cwhen solar` or `/cwhen lunar` shows a "Choose a type:" line. Clicking a type fills in the command.

Search ranges: stars, nebulae, and galaxies 1 year; planets, asteroids, and moons 3 years; the Moon 60 days; lunar features 120 days; solar and lunar eclipses 60 years.

Times in the results are the local mean time at the result's location, which is the same time the Minecraft clock will show after [go].

| Button | Action |
| --- | --- |
| [place] | `/cplace` to the suggested latitude (anyone, your sky only) |
| [go] | For object results: `/cele set` to that date, time, and place (OP only, whole world). For eclipse and meteor shower results: `/cplace <latitude> <longitude>` (anyone, your sky only, date and time unchanged). No button if you are already within 0.5° |
| [next] | The next result after that |

Examples: `/cwhen M51`, `/cwhen Saturn`, `/cwhen solar total`, `/cwhen lunar partial`, `/cwhen meteor PER`

#### /clog

```
/clog
/clog list
```

| Form | Action |
| --- | --- |
| `/clog` | Opens the observing notebook on the Logbook tab |
| `/clog list` | Shows the number of logbook entries, the advancement count, and the 10 most recent entries in chat |

#### /ctonight

The old name of `/cwhen`. It still runs but does not appear in autocomplete. Use `/cwhen`.

### 7.2 OP Commands (Permission Level 2)

The set, rate, realtime, and reset subcommands of `/cele` require OP permission (game operator, permission level 2). In singleplayer they are available in worlds with cheats allowed. Command responses appear in the client language of the player who entered the command.

#### /cele set

```
/cele set <year> <month> <day> <hour> <minute> <latitude> <longitude>
/cele set today
```

Sets the sky date and time (local mean time) and the world default location.

| Argument | Range | Description |
| --- | --- | --- |
| year | −2999–2999 | |
| month | 1–12 | |
| day | 1–last day of the month | |
| hour | 0–23 | If given, the world time is also adjusted |
| minute | 0–59 | If only the hour is given, minute 0 |
| latitude | −90–90 or a place name | If a place name is used, omit the longitude |
| longitude | −180–180 | East +, west − |

- Trailing arguments can be omitted, and `~` keeps the current value.
- If hour and minute are omitted, the world clock stays as it is and only the date changes.
- If the date is outside 1800–2050, a planet error warning is appended to the response.
- Changing latitude or longitude clears the personal location of everyone who set one with `/cplace`. Changing only the date and time keeps them.
- `/cele set today` changes to today's date and keeps the time and location.

Examples:

```
/cele set 2026 8 12 18 30 40.4 -3.7
/cele set ~ ~ ~ ~ ~ -33.9 151.2
/cele set 2026 8 12 ~ ~ seoul
```

#### /cele rate

```
/cele rate <rate>
```

Sets the compression rate between 0.01 and 1000. A value of 1 is real speed: one day of sky passes in one Minecraft day (20 minutes). Larger values speed up the change of seasons and constellations. The response also shows how many Minecraft days make one year.

#### /cele realtime

```
/cele realtime
/cele realtime <city>
/cele realtime off
```

With real time on, one Minecraft day equals one real day, and the sky follows the location and current time of that city. If the city is omitted, seoul is used. The cities are the 13 places in the place table in section 7.1, excluding greenwich.

While real time is on, `/cele set`, `rate`, and `reset` are blocked. Times on the info card and in `/cwhen` are shown in that city's clock time. Even if you skip the night in a bed, the sky returns to real time within 5 seconds. The setting persists across server restarts. `/cele realtime off` restores the date, compression rate, and location from before it was turned on.

#### /cele reset

```
/cele reset
```

Resets the sky to the defaults (2026-08-11 epoch, with the compression rate and location from the server config, 0° 0° by default).

### 7.3 Diagnostic Commands

| Command | Action |
| --- | --- |
| `/cvis` | Shows which visibility effects are on or off |
| `/cvis <effect> on\|off` | Turns one effect on or off (not saved). Effects: twilight (Twilight), moon (Moonlit sky), airglow (Airglow), extinction (Extinction), refraction (Refraction), colorloss (Color loss), light (Light pollution), dark (Dark adaptation), zodiacal (Zodiacal light & gegenschein), twinkle (Twinkling), aurora (Aurora) |
| `/cbench` | Performance benchmark. Run it at night while holding a telescope to measure 5 scenes for 12 seconds each |
| `/cbench stop` | Stops the benchmark |

`/cbench` results are saved in `cheonsang_bench/date_time/` in the game directory. `/cvis` can also be used in class to compare the effect of each visibility effect.

## 8. Settings

### 8.1 Cheonsang Settings Screen

Assign a key to "Cheonsang settings" in the "Miscellaneous" category of the controls settings and press it, or open the screen from the mod list if Mod Menu is installed. Changes are saved immediately.

Left column (sky display):

| Item | Default | Description |
| --- | --- | --- |
| Constellation lines | On | Lines connecting constellation stars |
| Names | On | Constellation and star names |
| Show planets | On | Planet and Moon names |
| Crosshair | On | Marker at the center of the telescope view |
| Equatorial grid | Off | Celestial equator, ecliptic, lunar orbit, ascending node, descending node |
| Alt-az grid | Off | Horizon, north/east/south/west |
| Boundaries | Off | Western constellation boundaries |
| Constellation art | Off | 85 Stellarium illustrations (at ×1 only) |
| Constellations | Western 88 | Western 88 / Cheonsang Yeolcha Bunyajido. Up to 8 Cheonsang Yeolcha asterisms are shown at a time. Boundaries, art, and star designations follow the Western system |

Right column:

| Item | Default | Description |
| --- | --- | --- |
| Photo mode | Off | Turns on only with the photo pack. Without it, the button is gray |
| Info card | On | |
| Card position | Top left | Top left, Top right, Bottom left, Bottom right |
| Card opacity | 69% | 25%, 50%, 69%, 85%, 100% |
| Card: Az/Alt | On | |
| Card: RA/Dec | On | |
| Card: rise/set | On | |
| Text size | ×1 | ×0.75, ×1, ×1.25, ×1.5 (card and sky labels) |
| Day/night visuals = Cheonsang Sun | On | Sky color, fog, and light on the ground follow Cheonsang's actual solar altitude. When off, the vanilla day is used |

Bottom:

| Item | Description |
| --- | --- |
| Pack status | Shows the installed extension pack and photo pack |
| Date (year month day) | Appears only when the server does not have Cheonsang. Example: enter `2026 8 12`, then Apply. If the server has Cheonsang, "Sky date: server" is shown |
| Observing location | Enter "latitude longitude" or a place name (for example, `37.5 127` or `seoul`), then Apply. The "World default" button is the same as `/cplace reset` |
| Current location | Your observing location and the world default location |

The N key (Telescope display filter) works independently of the settings screen.

### 8.2 Client Config File

The file is `config/cheonsang/client.json`. Changes to the three items below take effect after restarting the game.

| Key | Default | Range | Description |
| --- | --- | --- | --- |
| enabled | true | | Turns on the Cheonsang sky |
| nakedEyeLimit | 6.0 | 4–6.5 | Naked-eye limiting magnitude |
| meteors | 1.0 | 0–5 | Meteor amount (0 turns them off) |
| display | | | Settings screen values (saved automatically; if `korean` is true, Cheonsang Yeolcha Bunyajido asterisms are used) |

### 8.3 Server Config File

The file is `config/cheonsang/server.json`. It is read when the server starts.

| Key | Default | Description |
| --- | --- | --- |
| rate | 1.0 | Compression rate (0.01–1000) |
| lat | 0 | Default latitude for new worlds |
| lon | 0 | Default longitude for new worlds (east +, west −) |
| vanillaMoon | true | Matches the vanilla Moon phase and surface slime spawning to the Cheonsang Moon |
| vanillaNight | false | Matches mob spawning and the night check to the actual solar altitude (−6°) |

### 8.4 Other Saved Files

| File | Contents |
| --- | --- |
| `config/cheonsang/places.json` | Your observing location (separate for each server and world) |
| `config/cheonsang/log_local.json` | The observing logbook when the server does not have Cheonsang |

## 9. Observing Logbook, Observing Notebook, and Advancements

### 9.1 Observing Logbook

When you left-click an object with the telescope to select it (including the Alt and Ctrl combinations), it is recorded the first time if it is actually visible at that moment. The date, time, magnification, and naked eye or telescope are recorded.

| Condition | Details |
| --- | --- |
| Visibility | Above the horizon, within the limiting magnitude after twilight and moonlight losses, and not raining |
| Automatic entries | Solar and lunar eclipses (including partial eclipses, excluding penumbral lunar eclipses) and meteor shower peak nights, if you are in a spot open to the sky |
| Date basis | World Universal Time (does not shift when you move your observing location) |
| Storage | On the server (per player) if the server has Cheonsang, otherwise `config/cheonsang/log_local.json` |

### 9.2 Observing Notebook

Open it with `/clog` or the "Cheonsang observing notebook" key. There are three tabs, "Tonight", "Upcoming", and "Logbook", and the wheel switches between them.

### 9.3 Advancements

The "Cheonsang" tab of the advancements screen (L) has 24 advancements, judged from the observing logbook. Advancements that collect several targets show a progress fraction (for example, Messier 3/110, Saturn moons 2/7), and targets already found are filled in again when you join.

| Advancement | Condition |
| --- | --- |
| Cheonsang | Get a telescope |
| Honeymoon | Select the Moon with a telescope |
| Once in a Blood Moon | Watch a lunar eclipse under open sky (partial lunar eclipses count) |
| Throwing Shade | Watch a solar eclipse under open sky (partial solar eclipses count) |
| Roll Call | The 7 planets |
| Mercurial | Mercury |
| Starry Messenger | The four Galilean moons of Jupiter |
| The Fellowship of the Rings | The seven major moons of Saturn |
| Minor Details | Ceres and Vesta |
| All 88 Keys | The brightest star of each of the 88 constellations |
| Messier Marathon | The 110 Messier objects |
| Caldwell Done | The 109 Caldwell objects |
| Far, Far Away | M31, M33, Large Magellanic Cloud, Small Magellanic Cloud, M51, M81, M82, M101, M104, C77 |
| Wish Upon a Star | See a shooting star |
| It's Raining Stars | Watch a meteor shower under a dark sky near its peak |
| Demon's Wink | Select Algol when it is fainter than mag 3 |
| Curtain Call | See an aurora above latitude 60° |
| False Dawn | See the zodiacal light in a very dark sky |
| Down Under | Acrux, Large Magellanic Cloud, Small Magellanic Cloud |
| Rule of Thumb | Measure the angular distance between two objects |
| Room for the Moon | The 28 brightest stars, one for each of the 28 lunar mansions |
| Palace Hopping | One star each from Jamiwon, Taemiwon, and Cheonsiwon |
| Full Ladle | The seven stars of Bukdu |
| Live Long and Prosper | Noinseong (Canopus) |

The four Cheonsang Yeolcha advancements are granted if the relevant stars are in the logbook. Stars selected while the Western constellations are displayed, and stars recorded earlier, also count. Auroras, the southern sky, and solar eclipses can all be reached by moving your location with `/cplace`, so these advancements can be earned without OP permission.

## 10. Running a Multiplayer Server

### 10.1 Basic Setup

| Item | Details |
| --- | --- |
| Version | Cheonsang 2.0.0 on the server and every client |
| Sky synchronization | The server sends the date and time, compression rate, world default location, and real-time city to connected players |
| Personal location | Each player sets their own with `/cplace`. No OP needed, no effect on others |
| Client-side computation | `/cwhen`, `/cfind`, and galaxy observing sketches are computed on each player's own client |
| Meteors | Determined from each computer's clock, so everyone sees the same meteors. If computer clocks differ, meteors appear earlier or later by that difference |
| Telescope pose | Others can see when a player is magnifying |
| Observing logbook | Saved per player on the server |

### 10.2 Examples for Classes and Events

| Purpose | Command |
| --- | --- |
| Observe a solar eclipse | Find the date and place with `/cwhen solar total`, then use [go] or `/cele set <date> <time> <latitude> <longitude>` |
| The current sky over Seoul | `/cele realtime seoul` |
| Watch the seasons change quickly | `/cele rate 30` or similar (one year is about 12 days) |
| Go to the evening of a specific date | `/cele set 2026 12 14 21 0 seoul` |
| Compare different latitudes | Each participant runs `/cplace <latitude> <longitude>` |

If an OP changes latitude or longitude with `/cele set`, every participant's `/cplace` is cleared. To change only the date and time, fill the location fields with `~` or omit the location arguments.

### 10.3 Mob Spawning and Night

With the default settings, mob spawning and sleeping follow the vanilla 10-minute day cycle. The screen follows the Cheonsang Sun, so mobs can spawn even when the sky is bright. To match mob spawning and the night check to the actual solar altitude (−6°), set `vanillaNight` in `server.json` to true. The server has a per-second processing limit to prevent packet abuse.

## 11. Performance

### 11.1 Performance-Related Design

| Item | Details |
| --- | --- |
| Star rendering | The sky is divided into 41,252 cells of about 1°², and only cells near the field of view are drawn |
| Extension pack stars | A separate thread reads the cells, and the render thread does not wait. Cache limit of 3 million stars |
| Graphics memory | Milky Way tiles and photos are automatically allotted 1/4 of the graphics card memory (minimum 256MB). If it cannot be read, 512MB + 384MB |
| Releasing large textures | Large images are unloaded 2 minutes after you put the telescope away |
| Aurora and observing sketches | Computed in graphics card shaders. Not drawn when there is no aurora |
| `/cwhen` | Computed on a separate thread. Eclipses far in the future can take a few seconds |

### 11.2 Improving Performance

- Removing the extension pack reduces the memory and loading load of telescope stars and Milky Way tiles.
- Lower the Minecraft render distance.
- Turn off displays such as constellation art, grids, and boundaries.
- When there are many meteors, lower `meteors` in `client.json`.
- Use `/cbench` to measure FPS per scene on your own computer and compare.

## 12. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| The game does not start | If Iris is installed as well, remove it. Check that Java 25, Fabric Loader 0.20.1 or later, Fabric API, and Fabric Language Kotlin are present |
| On a server, my sky does not match other players' | The server and client versions differ. Bring everything to 2.0.0 |
| On a server, the sky date is stuck at 2026-08-11, 0° 0° | The server does not have Cheonsang. Each player sets the date and location on the settings screen, or install Cheonsang on the server |
| Meteors look slightly different from other players' | Meteors follow each computer's clock. Turning on automatic time sync in the operating system reduces the difference |
| My observing location changed suddenly | An OP changed the world location with `/cele set`, which clears `/cplace`. Set it again with `/cplace` |
| The wheel does not change magnification | While the notebook, settings, or advancements screen is open, the wheel goes to that screen. Check that the telescope is on (right-click) |
| The optics line is small or sits above the health bar | The screen is narrow or the GUI scale is large, so the line moved to avoid overlapping the hotbar |
| Few stars are visible | Moonlight, twilight, light pollution, or dark adaptation. Check the Bortle value on the first line of `/cwhen` and the Moon line, and put down any light source in your hand |
| Telescope stars stop at mag 13 | That is the limit of the main mod alone. Install cheonsang-extra to see down to mag 15.2 |
| "Not found" from `/cfind` | Faint stars with no name or number cannot be searched. Check the spelling and the designation format (for example, α Lyr) |
| An object is not added to the logbook | The object is below the horizon, beyond the limiting magnitude, or it is raining. Objects already recorded are not recorded again |
| The solar eclipse advancement does not trigger | You must be under open sky, and more than about 5% of the Sun's diameter must be covered. Penumbral lunar eclipses do not count as lunar eclipses |
| The long-exposure mode button is gray | The photo pack is not installed |
| Solar transit is not at 12:00 | The Minecraft clock is local mean time, so it differs by the equation of time (up to about ±16 minutes) |
| The sky is dark but no mobs spawn, or the reverse | Mob spawning follows the vanilla clock. See `vanillaNight` (section 10.3) |
| `/cele set` is refused in real time | While real time is on, set, rate, and reset are blocked. Run `/cele realtime off` first |
| "Cheonsang ... error" at the top of the screen | Only that feature (Sky, Telescope, Find, Keys, and so on) is turned off for this session, and the game continues. It turns back on when you reconnect. Details are written to `latest.log` |
| `/cplace` rejects the numbers | Use a period (.) as the decimal point. Latitude −90–90, longitude −180–180 |
| The sky is vanilla in other dimensions | The Cheonsang sky works only in the Overworld |

## 13. Known Limitations

- Shader mods (Iris) are not supported.
- If a server OP changes latitude or longitude with `/cele set`, everyone's `/cplace` is cleared.
- In a world with real time on, changing longitude with `/cplace` makes the sky's Universal Time differ from reality by the longitude difference.
- The mod includes 289 Cheonsang Yeolcha Bunyajido asterisms. It does not include 5 that cannot be matched to modern stars (Cheonjik, Cheonmyo, Donggu, Gunmun, and Gibu) or the 23 asterisms near the south celestial pole added during the Ming dynasty.
- The visibility in `/cwhen` considers only altitude, darkness, and moonlight, not light pollution or weather. Moons of planets are computed from the parent planet's position, and lunar features ignore libration. In worlds with a compression rate other than 1, the displayed times may be off.
- Galaxy observing sketches can flicker when they are very small on screen, and very bright galaxy nuclei look flattened. A large galaxy such as M31 looks blurry when viewed large at high magnification. The outer wings of M42 and the body shape of M27 are less distinct because of the limits of the photos. Planetary nebulae smaller than 1′ are drawn as disks.
- Meteors run on the computer clock and are independent of the game's time freeze and compression rate.
- Only the day and night on screen follow the Cheonsang Sun; mob spawning and sleeping follow vanilla.
- There is no weather, clouds, or change in atmospheric transparency. Seeing is fixed at 1.5″ and the extinction coefficients are fixed. The sky brightness value at a single point at the zenith is applied to the whole sky.
- Lens aberrations, central obstruction, and transmission are not computed. The 30-second dark adaptation recovery is shortened for gameplay.
- Star positions apply only precession to J2000 coordinates, without nutation, aberration, or proper motion (about 1′ compared with Stellarium). For the Moon, physical libration and nutation are left out, giving an error of around 0.04°.
- Planet positions are computed with an abridged VSOP87D theory (1,259 terms with amplitude 3×10⁻⁶ rad or more). The maximum error against PyEphem over 1800–2050 is Mercury 4.3″, Venus 5.4″, Mars 12.6″, Jupiter 4.3″, Saturn 3.8″, Uranus 4.0″, and Neptune 2.4″, and the maximum over the whole range from 1000 BC to AD 3000 is about 2′.
- Ceres, Vesta, and the moons of Saturn are fitted to data for 1900–2050, so outside that range their accuracy drops or they are not shown.
- The calendar range is 2999 BC to AD 2999.
- Stars fainter than mag 15.2 are included only in the Milky Way image. Pluto is not included.

## 14. Data Sources

The detailed list is in CREDITS.txt inside the jar. The mod source code is under the MIT License, and the DSS-based sketch plates are under CC BY-NC-SA 4.0 (noncommercial).

| Data | Use |
| --- | --- |
| HYG v4.1, Tycho-2, Gaia DR3 (ESA), WDS, GCVS | Stars, binaries, variable stars |
| d3-celestial, Stellarium | Constellation lines and boundaries, constellation art |
| Stellarium Chinese sky culture, Unicode Unihan | Cheonsang Yeolcha Bunyajido lines, verification of Sino-Korean readings |
| OpenNGC, DSS2 and DSS1 (STScI), Harris (1996, 2010 edition) | Deep-sky objects, observing sketch plates, core radii of globular clusters |
| NASA SVS | Milky Way, Moon map |
| NASA/JPL Horizons, Solar System Scope | Moons of Saturn and asteroids, planet surfaces |
| IAU WGPSN / USGS | Lunar feature names |
| IMO | Meteor shower schedule |

Minecraft is a trademark of Mojang Synergies AB. Cheonsang is not affiliated with Mojang or Microsoft.

---

## Appendix A. Astronomical Observing Guide

This appendix covers the concepts of real astronomical observing together with the matching features in Cheonsang. For telescope controls see section 5.2, for optical values section 6.2, for command syntax section 7, and for the observing logbook and advancements section 9.

### A.1 Observing Conditions

#### A.1.1 Limiting Magnitude and Sky Brightness

The limiting magnitude is the magnitude of the faintest star visible under given conditions. The naked-eye limiting magnitude (NELM) is roughly 6–6.5 under a dark sky with fully adapted eyes. Magnitude is a logarithmic scale: a difference of 5 magnitudes is a factor of 100 in brightness, and 1 magnitude is about a factor of 2.512.

The condition that affects the limiting magnitude most is sky brightness. Sky brightness is given in magnitudes per square arcsecond (mag/arcsec²), and larger values mean a darker sky. The zenith of a natural sky with no artificial light is about 22 mag/arcsec².

Cheonsang computes sky brightness by adding airglow, twilight, moonlight, light pollution, and zodiacal light, and reduces the limiting magnitude by this value. The baseline naked-eye limit is set by `nakedEyeLimit` in `client.json` (section 8.2). The loss from sky brightness is about 1.7 magnitudes on a full-moon night and about 5.4 magnitudes in twilight with the Sun at −6°. On a night with a full Moon, faint stars and the Milky Way are lost in the sky.

#### A.1.2 Light Pollution and the Bortle Scale

The Bortle scale is an observer's scale that rates the darkness of the night sky from 1 (darkest sky) to 9 (inner city). In real observing, the farther you go from a city, the more the Milky Way, the zodiacal light, and faint nebulae appear, in that order.

Cheonsang estimates a Bortle index by sampling the block light of cells open to the sky (sky light 15) within ±64 blocks of the observer. Next to a village with many light sources such as torches and lanterns, the sky gets brighter and an orange dome forms on the horizon toward the village. Lights inside houses or caves are not counted, but light leaking through windows is. Check the current value with "Bortle here" at the end of the first line of `/cwhen`.

| Bortle | Zenith brightness (mag/arcsec²) | Naked-eye limit (default 6.0) |
| --- | --- | --- |
| 1 | 21.9 | 6.0 |
| 3 | 21.4 | 5.8 |
| 5 | 20.0 | 5.2 |
| 7 | 18.5 | 4.3 |
| 9 | 17.5 | 3.7 |

A good observing site is more than 64 blocks from a village, or a hill or plain with no light-source blocks nearby. If you build an observatory, placing lights under a roof or indoors keeps them from affecting the sky brightness.

#### A.1.3 Dark Adaptation

When a person enters a dark place, the pupils widen and the sensitivity of the rod cells in the retina rises gradually. In reality, full adaptation takes 20–30 minutes, and a single look at a bright light undoes it at once. Observers use red flashlights because rod cells are insensitive to red light, so it does less harm to adaptation. Averted vision, looking slightly off the center of the field, makes faint objects easier to see.

In Cheonsang, dark adaptation is lost immediately when the block light at eye level or a light source in your hand is bright, and it recovers over up to 30 seconds in the dark. The maximum loss is 3 magnitudes. If you were holding a torch just before observing, put it down and wait a moment before you start.

#### A.1.4 The Atmosphere: Extinction, Refraction, Twinkling, and Seeing

The closer an object is to the horizon, the thicker the layer of atmosphere its light passes through. This thickness is called airmass. It is 1 at the zenith and about 2 at an altitude of 30°. The dimming of light by absorption and scattering in the atmosphere is called extinction. Short-wavelength blue light is reduced more, so stars near the horizon look fainter and redder.

Cheonsang's extinction coefficients are V 0.172, B 0.30, and R 0.09 mag/airmass. Atmospheric refraction lifts objects at the horizon by about 0.5°, and the Sun and Moon are flattened vertically near the horizon. In real observing as well, the basic rule is to observe a target when it is high (altitude 30° or more, ideally near transit).

Scintillation (twinkling) is the rapid change in starlight intensity caused by fluctuations in the refractive index of the air, and it is strongest toward the horizon. In Cheonsang, stars twinkle to the naked eye and at low magnification, and the twinkling disappears at high magnification. Planets have an apparent disk, so they twinkle less in reality as well.

Seeing is the amount by which the telescopic image is smeared by turbulence in the atmosphere, expressed as the diameter of a star image in arcseconds. At a typical mid-latitude site it is about 1–3″. Cheonsang fixes seeing at 1.5″ and adds a slight shimmer to the high-magnification view. Transparency refers to how clear the atmosphere is and is a separate condition from seeing. Cheonsang has no clouds and no day-to-day change in transparency, and nothing is recorded in the observing logbook while it is raining.

#### A.1.5 Moonlight and Twilight

Twilight is the period after sunset when the sky is still bright. By solar altitude it is divided into civil twilight (down to −6°), nautical twilight (down to −12°), and astronomical twilight (down to −18°). The sky becomes fully dark only when the Sun is below −18°. In summer at high latitudes the Sun does not go below −18°, so the sky never becomes fully dark all night.

`/cwhen` reports both the "fully dark" period and the "moonless dark" period for the night. If you plan to observe galaxies and faint nebulae in the moonless dark period, and bright targets such as the Moon, planets, and double stars on moonlit nights, you can use the whole month evenly.

### A.2 Principles of Telescope Optics

The aperture and eyepiece steps, magnification range, limiting magnitude, exit pupil, and star image size of the Cheonsang telescope are in the tables in section 6.2.

#### A.2.1 Aperture and Light-Gathering Power

Aperture is the diameter of the objective lens. Light-gathering power is proportional to the square of the aperture, so doubling the aperture gathers four times as much light. The limiting magnitude of a telescope is commonly given by this approximation:

m_lim = 7.7 + 5 log10(D / 1 cm)

This is an empirical formula based on a naked-eye limit of mag 6.5. Cheonsang gets the same result by adding an aperture gain to the naked-eye limit. At the default setting (naked eye mag 6.0) the result is 0.5 magnitude lower. It is then reduced again by the actual sky brightness and the state of dark adaptation.

#### A.2.2 Magnification and Eyepieces

Magnification is the objective focal length divided by the eyepiece focal length.

M = F / f

Raising the magnification does not show more. By experience, the highest useful magnification is about 2 times the aperture in mm. Beyond that the image only gets bigger and blurrier (empty magnification). Cheonsang also blurs the image past this limit (over-magnification in section 6.2).

#### A.2.3 Exit Pupil

The exit pupil is the diameter of the beam of light leaving the eyepiece. It equals the aperture divided by the magnification, which is the same as the eyepiece focal length divided by the focal ratio. In the dark, the human pupil opens to at most about 7mm, so if the exit pupil is larger than 7mm, part of the light does not enter the eye. Conversely, the smaller the exit pupil, the lower the surface brightness of extended objects such as nebulae and galaxies.

Rough guidelines used in the field are below. For the exit pupil of each eyepiece, see the table in section 6.2.

| Exit pupil | Use |
| --- | --- |
| 4–7mm | Extended nebulae, large open clusters, the Milky Way, comets. Finding targets |
| 2–3mm | Most galaxies and globular clusters, small nebulae |
| About 1mm | Planetary nebulae, cores of globular clusters, the Moon |
| 0.5–0.7mm | Planetary detail, close double stars |

#### A.2.4 Resolution

Because light diffracts, a star's image forms a small disk called the Airy disk. The radius of the first dark ring of the Airy disk is about 138″/D (mm). The Rayleigh criterion takes this value as the limit for separating two stars. The Dawes limit, obtained from observers' experience, is slightly smaller at 116″/D (mm).

Cheonsang sets the size of a star image by combining the diffraction radius 138″/D with 1.5″ of seeing. It contains 7,459 companions of close binaries from WDS, and stars that appear as a single point to the naked eye, such as Castor, Mizar, and ε Lyrae, split depending on aperture and magnification.

A larger aperture gives better resolution, but seeing sets a lower bound on the star image size, so the gain shrinks at large apertures. This is also why, in the star image radii in section 6.2, the difference between 127mm and 305mm is smaller than the difference between 50mm and 127mm.

### A.3 Finding Your Way Around the Sky

#### A.3.1 Two Coordinate Systems

The horizontal coordinate system is based on the observer. Altitude is the angle measured up from the horizon, and azimuth is the angle measured from north toward east. The altitude and azimuth of the same star change with time and place.

The equatorial coordinate system is based on the celestial sphere. Right ascension (α) is the angle measured eastward from the vernal equinox, expressed in units of time, and declination (δ) is the angle measured north or south from the celestial equator. A star's right ascension and declination barely change, so star charts and catalogs use these coordinates.

The info card (section 4.1) shows both coordinates. Turning on the equatorial grid and alt-az grid on the settings screen (section 8.1) lets you compare the two systems directly on the sky.

At latitude φ, the transit altitude of an object at declination δ is 90° − φ + δ (in the northern hemisphere, when the object passes south of the zenith). The altitude of Polaris is nearly equal to the local latitude. An object with declination lower than −(90° − φ) never rises at that latitude. The "At this latitude: never rises" and "Never sets" labels in the rise/set line of the info card come from this relation.

#### A.3.2 Estimating Angular Distance

With your arm fully extended, the width of your little finger is about 1°, the width of your fist about 10°, and a hand span from thumb to little finger about 20°. The diameter of the full Moon is about 0.5°. The two stars at the end of the Big Dipper's bowl (Dubhe and Merak) are about 5° apart.

In Cheonsang you can compare a hand estimate with a measured value using angular distance measurement (section 5.2, Ctrl + left-click). The first time you measure an angular distance, you get the advancement "Rule of Thumb".

#### A.3.3 Finding Constellations and Star Hopping

Constellations are learned by starting from reference patterns of bright stars and expanding to neighboring constellations. Common signposts are:

- Extending the line through the two stars at the end of the Big Dipper's bowl (Merak→Dubhe) about 5 times reaches Polaris.
- Following the curve of the Big Dipper's handle leads to Arcturus in Boötes, and continuing on leads to Spica in Virgo.
- On summer evenings, Vega, Deneb, and Altair form the Summer Triangle.
- On winter evenings, extending Orion's Belt to the southeast reaches Sirius, and to the northwest reaches Aldebaran.
- On autumn evenings, follow the stars of Andromeda northeast from the Great Square of Pegasus to find M31.

To find faint objects with a telescope, use star hopping. Put a bright reference star in the field with a low-power eyepiece (40mm or 32mm), move one field at a time along the star patterns you checked on a chart, and raise the magnification once you find the target. The same order works well in Cheonsang: lock tracking on the object you found (section 6.4), then raise the magnification.

When practicing finding objects yourself, use only the direction arrow from `/cfind <name>`. When short on time, turn the view directly with `/cfind <name> true`. The table in section 7.1 lists the name formats you can search for.

#### A.3.4 Cheonsang Yeolcha Bunyajido Asterisms

The Cheonsang Yeolcha Bunyajido (天象列次分野之圖) is a star chart carved in stone during the reign of King Taejo of Joseon. It divides the sky into the Three Enclosures (Samwon, 三垣) and the 28 lunar mansions (二十八宿). The Three Enclosures are Jamiwon (紫微垣) around the north celestial pole, Taemiwon (太微垣) near Leo and Virgo, and Cheonsiwon (天市垣) near Ophiuchus and Hercules. The 28 lunar mansions are zones near the ecliptic and celestial equator, in each of which the Moon was thought to stay for one day.

Changing the "Constellations" item on the settings screen to "Cheonsang Yeolcha Bunyajido" shows 289 asterisms of the Bocheonga (步天歌) system as lines and names in the telescope view. With the English UI, the names appear romanized (for example, Bukdu). Switching between this and the Western 88 constellations on the same sky lets you compare how the same stars were grouped in two cultures. For example, the Big Dipper is part of Ursa Major in the Western system and a separate asterism, Bukdu, in the Cheonsang Yeolcha system. Noinseong (老人星, the Old Man Star) is Canopus in the Western constellation Carina.

### A.4 Observing Tips by Target

#### A.4.1 The Moon

The Moon is better observed near first or last quarter than at full Moon. Near the terminator, the boundary between light and shadow, craters and mountain ranges cast long shadows and the terrain looks three-dimensional. At full Moon the light falls head-on and there are almost no shadows, but the bright rays radiating from craters such as Tycho are easy to see.

The Cheonsang Moon uses a NASA LRO map, and the resolution increases as you magnify (section 2.3). Optical libration is included, so features near the limb appear or hide a little from day to day. The 787 lunar feature names (IAU) can be found with `/cfind Mare Tranquillitatis` or `/cfind Tycho`. Around the crescent phases you can also see earthshine, where the dark part of the Moon glows faintly with light reflected from Earth.

The apparent diameter of the Moon differs by about 14% between perigee and apogee, so you can record the diameter on the info card over several days and compare.

#### A.4.2 Planets

Planets have high surface brightness, so they can be observed even when the Moon is bright or the sky is a little bright. Detail shows at as high a magnification as seeing allows (exit pupil 0.5–1mm).

| Target | Observing tips |
| --- | --- |
| Mercury | Never more than about 28° from the Sun, so it is seen low in twilight, in the western sky just after sunset or the eastern sky before sunrise. Find the time of greatest elongation with `/cwhen Mercury`. Finding it gives the advancement "Mercurial" |
| Venus | The brightest planet. Shows phases like the Moon in a telescope, and the closer it is to Earth, the larger and thinner it looks |
| Mars | Large and bright around opposition, when it comes close to Earth. A real map of the face turned toward Earth at that time is drawn |
| Jupiter | Even at low magnification the four Galilean moons (Io, Europa, Ganymede, Callisto) appear in a line. At high magnification the belts and the Great Red Spot (at its longitude for that time) are visible |
| Saturn | The rings are visible at ×50 and above. The ring tilt is computed from the positions of Earth and Saturn on the observing date. Earth crossed the ring plane in 2025, so for a few years after that the rings appear narrowly open. Seven moons (Titan mag 8.3 to Mimas mag 12.9) |
| Uranus and Neptune | Appear as small disks or like stars. Neptune is beyond the naked-eye limit, so a telescope is needed |
| Ceres and Vesta | Appear as points indistinguishable from stars. Looking at the same area a few days apart shows that they have moved against the background stars (shown only for 1900–2050) |

When a moon goes behind its planet it is not visible, and the card and `/cfind` results show "Currently hidden".

#### A.4.3 Double Stars and Variable Stars

Double stars are good targets for checking aperture and resolution. Pairs with color contrast (Albireo in Cygnus, Almach in Andromeda) look good even at low magnification, and close pairs are useful for aperture comparison exercises.

| Target | Separation | Notes |
| --- | --- | --- |
| Mizar (ζ Ursae Majoris) | 14.4″ | Easily split with a small telescope. Separate from Alcor, which is visible to the naked eye |
| Castor (α Geminorum) | 5.4″ | Mag 1.93 and 2.97 |
| γ Virginis (Porrima) | 3.4″ | Pair of similar brightness |
| ε Lyrae | 2.1″ / 2.4″ | The "Double Double": two stars at low magnification, each of which splits into two again at high magnification |
| Sirius B | 11.1″ | Wide separation, but difficult because the primary is very bright |

Among variable stars, Algol (β Persei) is an eclipsing binary that fades from mag 2.1 to 3.4 for about 10 hours every 2.8673 days. Mira (ο Ceti) varies between mag 3.4 and 9.3 with a period of about 332 days. In real observing, the magnitude is estimated by comparison with nearby comparison stars of known brightness. Selecting Algol when it is fainter than mag 3 gives the advancement "Demon's Wink".

#### A.4.4 Star Clusters

An open cluster is a loose group of tens to hundreds of young stars, best seen at low magnification with a wide field. In Cheonsang the surroundings of open clusters are filled with their actual member stars from Gaia DR3. The Pleiades (M45) is good to the naked eye, and the Beehive (M44) and the Double Cluster in Perseus (C14) at low magnification comparable to binoculars.

A globular cluster is a dense, spherical group of hundreds of thousands of old stars. In a small aperture it looks like a round glow with a bright center, and as the aperture grows it resolves into individual stars, starting from the edge. Cheonsang draws globular clusters with a King profile and uses measured core radii from the Harris catalog for 13 clusters. As a result, 47 Tuc (C106) has a sharply peaked center, while ω Cen (C80) and M22 look broad.

#### A.4.5 Nebulae

An emission nebula is gas around hot stars that emits light. In photographs the red light of the hydrogen-alpha line (656 nm) stands out, but the dark-adapted human eye is insensitive to red and sensitive to blue-green (oxygen [O III] 501 nm, hydrogen-beta 486 nm), so nebulae look gray or pale blue-green. Cheonsang's nebula sketches reflect this and were made mainly from DSS blue photographs. The Trapezium cluster at the center of M42, the Orion Nebula, is drawn from catalog stars.

A planetary nebula is the shell of gas thrown off by a dying star. Planetary nebulae are small and have relatively high surface brightness, so they hold up well to higher magnification. M57, the Ring Nebula, shows its ring shape at medium magnification, and M27, the Dumbbell Nebula, shows an apple-core shape at low magnification. In M97, the Owl Nebula, two dark "eyes" are visible.

Nebulae and galaxies have low surface brightness, so they need moonless darkness, a site with little light pollution, and sufficient dark adaptation. Extended sources with low surface brightness are drawn in desaturated gray to match the characteristics of the eye's rod cells.

#### A.4.6 Galaxies

Most galaxies are very faint, and often only the brightest central part is visible. How much of a galaxy can be seen depends on aperture, magnification (exit pupil), and sky brightness together. Cheonsang draws 341 galaxies as they would look in an eyepiece (observing sketches). The base is real sky photography (DSS2 red, 339 galaxies), and galaxies without photos are drawn according to their Hubble type. Only the parts brighter than the visibility threshold appear, in gray.

- Small aperture and bright sky: only the central smudge is visible.
- Large aperture and dark sky: the disk appears wide, and structure such as spiral arms, bars, dust lanes, and knots appears.

M31, the Andromeda Galaxy, and M33, the Triangulum Galaxy, appear the same way to the naked eye. M31 is very large, so it is better to view all of it at low magnification. In M51, the Whirlpool Galaxy, the spiral arms and the bridge to its companion galaxy show with a large aperture and a dark sky. In M104, the Sombrero Galaxy, and in edge-on galaxies, look for the dark lane.

### A.5 Forecasting and Observing Events

#### A.5.1 Planning an Observing Session

Plan observations with the tonight summary from `/cwhen` and the best-night search with `/cwhen <object>` (syntax and result fields in section 7.1). These forecasts do not consider light pollution or weather, so check the Bortle value of your actual site separately on the first line of `/cwhen`.

#### A.5.2 Solar Eclipses

A solar eclipse occurs when the Moon covers the Sun. It happens only around new moon, when the Moon lines up exactly in the same direction as the Sun. A total (or annular) eclipse is seen only within the narrow path where the Moon's shadow reaches Earth, and a partial eclipse is seen over the wide area outside it. When the Moon is too far from Earth to cover the whole Sun, the eclipse is annular.

Find the next solar eclipse with `/cwhen solar <type>`, and move your observing location to the point of greatest eclipse with the [go] button (section 7.1). There is a total solar eclipse on 2026-08-12, the day after the default date of a new world, with the point of greatest eclipse at about 65°N 25°W.

In Cheonsang, during a total solar eclipse the sky and ground darken, and bright stars, planets, and the corona become visible. An eclipse appears only to the extent that it is actually visible from that location.

When observing the real Sun, a certified solar filter must always cover the front of the objective. Looking at the Sun through a telescope or binoculars without a filter can cause instant loss of sight. Naked-eye viewing also requires eclipse glasses, except during the fully covered period of totality. In class, this difference between in-game and real observing must be pointed out.

#### A.5.3 Lunar Eclipses

A lunar eclipse occurs around full moon when the Moon enters Earth's shadow. Earth's shadow is divided into the dark umbra and the lighter penumbra. A penumbral lunar eclipse only dims the Moon slightly and is hard to notice. When part of the Moon enters the umbra, the eclipse is partial, and when the whole Moon enters the umbra, it is total. The Moon looks red during a total lunar eclipse because red light refracted through Earth's atmosphere enters the shadow.

A lunar eclipse is seen at the same moment from anywhere the Moon is up. Check the times and the fraction covered with `/cwhen lunar <type>` (section 7.1). In a new world there is a partial lunar eclipse on 2026-08-28. Lunar eclipses are safe to watch with the naked eye.

#### A.5.4 Meteor Showers

A meteor shower occurs when Earth passes through a trail of dust left by a comet. The meteors appear to spread out from one point in the sky, the radiant. The strength of a shower is given as the zenithal hourly rate (ZHR). The ZHR is the number of meteors one person could see in an hour under an ideal sky, with the radiant at the zenith and a limiting magnitude of 6.5. The number actually seen is reduced according to the radiant altitude and the sky brightness.

Cheonsang computes the expected count per hour as ZHR × sin(radiant altitude) × moonlight correction. The number of meteors changes with the peak date, radiant altitude, moonlight, and rain, and in addition, 10 sporadic meteors appear per hour. Meteors run on the computer clock regardless of the compression rate of game time (section 10.1).

| Shower | Code | ZHR | Peak | Parent body |
| --- | --- | --- | --- | --- |
| Quadrantids | QUA | 110 | Early January | Asteroid 2003 EH1 |
| Lyrids | LYR | 18 | Late April | Comet Thatcher (C/1861 G1) |
| Eta Aquariids | ETA | 50 | Early May | Comet Halley |
| Perseids | PER | 100 | Mid-August | Comet Swift–Tuttle |
| Orionids | ORI | 20 | Late October | Comet Halley |
| Leonids | LEO | 15 | Mid-November | Comet Tempel–Tuttle |
| Geminids | GEM | 150 | Mid-December | Asteroid 3200 Phaethon |

Find the next peak with `/cwhen meteor [name]`. If the radiant is low from your location, a better place and a [go] button are added. Meteor showers do not need a telescope. In real observing, the practice is to find a place with a wide-open sky and look at the sky 40–60° away from the radiant. Your first meteor gives the advancement "Wish Upon a Star", and being in a spot open to the sky on a meteor shower peak night gives "It's Raining Stars".

#### A.5.5 Aurora

An aurora forms when charged particles from the solar wind follow Earth's magnetic field into the upper atmosphere over the polar regions and excite oxygen and nitrogen. The most common color is the 557.7 nm green emitted by oxygen atoms at heights of about 100–150 km, and oxygen higher up (about 200 km and above) emits 630 nm red light. Auroras appear mainly in a ring-shaped band around the magnetic poles (the auroral oval).

In Cheonsang, auroras appear at night (solar altitude below −10°) at latitudes above 60° (north or south), and are strongest at 67–75°. The strength of solar activity is chosen at random each night. The aurora is drawn along a layer 80–320 km high, green at the bottom and redder toward the top. The position of the auroral band is latitude 69° − 3° × strength. If the observer is on the equator side of the band, the aurora is seen in the poleward sky; if under the band, overhead; and if on the pole side of the band, in the equatorward sky. It weakens when moonlight is bright and fades as telescope magnification increases.

Auroras are best viewed over a wide sky with the naked eye. Move your observing location with `/cplace 70 20` (near northern Norway), `/cplace longyearbyen`, or in the southern hemisphere `/cplace mcmurdo`, and wait several nights. Your first aurora gives the advancement "Curtain Call".

#### A.5.6 Zodiacal Light and Gegenschein

The zodiacal light is a faint column of light caused by interplanetary dust scattering sunlight. It rises at a slant from the horizon along the ecliptic. At northern mid-latitudes it is seen best in the western sky after sunset in spring and in the eastern sky before sunrise in autumn, when the ecliptic makes a steep angle with the horizon. The gegenschein is a fainter glow seen directly opposite the Sun.

In Cheonsang these appear only in a very dark sky, with the Sun below −15°, no Moon, and Bortle below 4. Your first sighting gives the advancement "False Dawn".

#### A.5.7 Other Forecasts

Check forecasts for full moon, new moon, and planetary conjunctions with `/cwhen full`, `new`, `conj`, and `next` (section 7.1). New moon is the best time for observing deep-sky objects. A planetary conjunction is a rare chance to fit two planets in the same low-power field. Stars, planets, and moons behind the lunar disk are not drawn, so occultations by the Moon also appear according to the computed positions, but there is no feature for forecasting occultations.

### A.6 Targets by Season

The tables below are based on targets that are high around 9 p.m. in each season near latitude 35–40°N (Seoul is at 37.6°N). Move your observing location with `/cplace seoul` or `/cplace 37.6 127.0`. Every target in the tables can be found with `/cfind`.

#### A.6.1 Spring (March–May)

| Type | Targets | Observing notes |
| --- | --- | --- |
| Constellations | Ursa Major, Leo, Boötes, Virgo | The "Spring Arc" from the Big Dipper to Arcturus and Spica |
| Double stars | Mizar and Alcor, γ Leonis (Algieba) | Mizar splits even at low magnification |
| Clusters | M44 Beehive, M3 | M44 with a wide field, M3 for aperture comparison |
| Galaxies | M81 and M82, M51, M101, M104, the Virgo Cluster galaxies including M87 | Moonless nights, large aperture |
| Cheonsang Yeolcha | Taemiwon, Bukdu | Taemiwon is near Leo and Virgo |

#### A.6.2 Summer (June–August)

| Type | Targets | Observing notes |
| --- | --- | --- |
| Constellations | Lyra, Cygnus, Aquila, Scorpius, Sagittarius | The Summer Triangle, the center of the Milky Way on the southern horizon |
| Double stars | ε Lyrae, Albireo (β Cygni) | Resolution comparison, color contrast |
| Globular clusters | M13, M92, M4, M22 | M13 aperture comparison exercise |
| Nebulae | M57, M27, M8, M20, M17 | Comparing planetary and emission nebulae |
| Open clusters | M11 | A densely packed open cluster |
| Events | Perseid meteor shower (mid-August) | Radiant in the northeast, higher after midnight |
| Cheonsang Yeolcha | Cheonsiwon, Jingnyeo | Jingnyeo is the area around Vega in Lyra |

#### A.6.3 Autumn (September–November)

| Type | Targets | Observing notes |
| --- | --- | --- |
| Constellations | Pegasus, Andromeda, Cassiopeia, Perseus | The Great Square of Pegasus |
| Galaxies | M31, M32, and M110, M33 | Low magnification, wide field |
| Clusters | C14 Double Cluster in Perseus, M15, M2 | Both halves of the Double Cluster in one low-power field |
| Double and variable stars | Almach (γ Andromedae), Algol | Observing Algol's eclipse |
| Events | Orionid meteor shower (late October), Leonid meteor shower (mid-November) | |

#### A.6.4 Winter (December–February)

| Type | Targets | Observing notes |
| --- | --- | --- |
| Constellations | Orion, Taurus, Gemini, Auriga, Canis Major | The Winter Hexagon |
| Nebulae | M42 and M43, M1 | The Trapezium at the center of M42 |
| Clusters | M45 Pleiades, Hyades, M35, M36, M37, and M38, M41 | Low magnification |
| Double stars | Castor, Rigel, Sirius B | Sirius B needs high magnification and a large aperture |
| Events | Geminid meteor shower (mid-December), Quadrantid meteor shower (early January) | |

#### A.6.5 Southern Hemisphere and Low Latitudes

Targets in the southern sky can be seen only by moving your observing location. Move with `/cplace sydney` (33.9°S), `/cplace -30 135`, or similar.

| Target | Notes |
| --- | --- |
| Acrux in Crux, the Large Magellanic Cloud, the Small Magellanic Cloud | Finding all three gives the advancement "Down Under" |
| C80 ω Cen, C106 47 Tuc | The brightest globular clusters |
| C92 Eta Carinae Nebula, C94 Jewel Box | |
| Canopus (Noinseong) | Declination about −52.7°. In the northern hemisphere it rises low above the southern horizon only south of about 37°N. Can be seen from `/cplace 33.5 126.5` (near Jeju). Finding it gives the advancement "Live Long and Prosper" |
| C77 Centaurus A | A bright peculiar galaxy in the southern sky |

### A.7 Observing Records

The rules for the observing logbook, observing notebook, and advancements are in section 9. Real observers record the observing conditions with their results so that others can reproduce them. Most of these items can be read directly from Cheonsang's screens and command output.

| Item | Where to find it in Cheonsang |
| --- | --- |
| Date and time (local mean time) | `/cele`, info card |
| Observing location (latitude and longitude) | `/cplace`, bottom of the settings screen |
| Equipment (aperture, eyepiece, magnification) | Optics line in the telescope view (section 4) |
| Sky conditions (Bortle, Moon phase, solar altitude) | First line and Moon line of `/cwhen` |
| Altitude and azimuth of the target | Info card |
| Observation | Shape, brightness, color, and structure, in words and sketches |

### A.8 Observing Exercises

Each exercise assumes singleplayer (a world with cheats allowed) or a server where the teacher has OP permission. `/cele set`, which moves the date, and `/cele rate`, which changes the speed of the sky, are OP commands (section 7.2). At the default compression rate of 1.0, one day of sky passes in one Minecraft day (20 minutes).

#### Exercise 1. Tracking Jupiter's Moons and Finding Jupiter's Mass

Goal: Record the changing positions of the Galilean moons to find their orbital periods, and estimate Jupiter's mass with Kepler's third law.

1. Find a night when Jupiter is well placed with `/cwhen Jupiter`. If you are an OP, use [go] to move to that date and time.
2. Lock the telescope on Jupiter with `/cfind Jupiter true` and set the magnification to around ×100.
3. Put one moon (for example, Io) at the center of the view and left-click to select it, then put Jupiter at the center and use Ctrl + left-click to measure the angular distance between them. Also note whether the moon is east or west of Jupiter.
4. Repeat the same measurement at regular intervals of game time (for example, every 2 hours of Minecraft time). Also note the distance to Earth from Jupiter's card.
5. Plot angular distance against time. The result is a sine curve: one cycle is the orbital period, and the maximum angular distance corresponds to the orbital radius.
6. Find the orbital radius a = Earth–Jupiter distance × maximum angular distance (in radians), then compute Jupiter's mass from M = 4π²a³ / (G T²).

Reference values: the actual orbital periods are Io 1.769 days, Europa 3.551 days, Ganymede 7.155 days, and Callisto 16.69 days, and Jupiter's mass is about 1.90 × 10²⁷ kg. When a moon goes behind Jupiter it shows "Currently hidden", so record those times as well. Selecting all four moons gives the advancement "Starry Messenger".

Discussion topics: What Galileo argued from the same observations in 1610, and where the measurement errors come from.

#### Exercise 2. Phases and Apparent Diameter of Venus

Goal: Observe that the phase and apparent diameter of Venus change together, and confirm the evidence for heliocentrism.

1. Find a date when Venus is in the evening sky (around greatest eastern elongation) with `/cwhen Venus`.
2. View Venus at ×100 or more, and note the phase (illuminated fraction), diameter, elongation from the Sun, and magnitude from the card.
3. Move the date in steps of 2–3 weeks with `/cele set` and repeat until just before inferior conjunction (same direction as the Sun).
4. Organize the phase and diameter in a table and a graph. Confirm that the diameter grows as the phase thins.

Points to observe: Venus is a thin, large crescent when close to Earth, and round and small when far away. The actual apparent diameter changes from about 10″ to more than 60″. Discuss the point that in the geocentric model (the Ptolemaic system), Venus is always closer than the Sun, so a full phase could never appear.

Extension: Observe the phases of Mercury the same way, and connect the reason Mercury is seen only in twilight to its greatest elongation.

#### Exercise 3. Comparing Apertures on M13

Goal: Compare the same target at several apertures to see the effects of light-gathering power and resolution.

1. Find a time on a summer evening when M13 (the globular cluster in Hercules) is high, using `/cwhen M13`. Choose a moonless night.
2. At a site with little light pollution (a low Bortle value), lock on with `/cfind M13 true`.
3. Compare four apertures at similar magnifications.

| Aperture | Eyepiece | Magnification | Exit pupil |
| --- | --- | --- | --- |
| 50mm | 5mm | ×70 | 0.7mm |
| 80mm | 9mm | ×62 | 1.3mm |
| 127mm | 15mm | ×59 | 2.1mm |
| 305mm | 32mm | ×67 | 4.6mm |

4. Sketch what you see at each aperture (size of the glow, central concentration, how far individual stars are visible). Left-click the faintest star in view to check its magnitude, and compare it with the limiting magnitude for that aperture (section 6.2).
5. Repeat the same experiment on a full-moon night or next to a village to compare the effect of sky brightness.

Points to observe: At 50mm the cluster appears only as a faint central glow, and at 305mm it resolves into individual stars starting from the outside. Summarize both points: at the same magnification a larger exit pupil gives higher surface brightness, and a larger aperture shows fainter stars.

#### Exercise 4. Checking Resolution with Double Stars

Goal: Confirm that resolution is inversely proportional to aperture.

1. Select ε Lyrae (`/cfind ε Lyr true`).
2. After confirming that it splits into two stars at low magnification, raise the aperture step by step from 50mm and find the smallest aperture at which each pair (2.1″, 2.4″) splits into two again. Keep the magnification at ×100 or more.
3. Measure Castor (5.4″) and γ Virginis (3.4″) the same way.
4. Compare the smallest apertures you measured with the values computed from the Rayleigh criterion 138″/D and the Dawes limit 116″/D.

Discussion topics: Why the resolution of a large aperture falls short of the theoretical value in real observing (seeing), and how the fixed 1.5″ seeing in Cheonsang shows up in the results.

#### Exercise 5. Light Pollution and Naked-Eye Limiting Magnitude

Goal: Measure the effect of sky brightness on the number of visible stars.

1. Choose three observing points: the middle of a village with many torches, a spot 32 blocks from the village, and a hill more than 64 blocks away with no light sources.
2. At each point, note the Bortle value on the first line of `/cwhen`.
3. Look at the same constellation (for example, Ursa Minor) at ×1, left-click the faintest visible star, and note its magnitude.
4. Repeat the same measurements on a full-moon night and on a night with a waning crescent Moon.
5. Tabulate the relationship between the Bortle value, Moon phase, and naked-eye limiting magnitude, and compare it with the Bortle table in section A.1.2.

Extension: Compare holding a torch right before observing and then putting it down with waiting in the dark, to check the effect of dark adaptation.

#### Exercise 6. The Light Curve of Algol

Goal: Measure the brightness changes of an eclipsing binary and find its period.

1. Find Algol with `/cfind Algol` and read its magnitude from the card.
2. Record the magnitude every hour of game time. At a compression rate of 1, Algol's period (2.8673 days) is about 2.9 days of Minecraft time, or about 1 hour of real time.
3. Plot time against magnitude and find the time of minimum, the duration of minimum, and the period.

Points to observe: The brightness stays constant, then fades from mag 2.1 to 3.4 over about 10 hours and returns. Explain this with the eclipsing binary model, in which a dim companion passes in front of the bright primary.

### A.9 Scope of the Model

Observations in Cheonsang are simulations produced by a computational model, and the model's simplifications and accuracy are summarized in section 13. In class, it is worth covering the following differences among them.

- Sky brightness applies the value at a single point at the zenith to the whole sky, so the light-pollution dome toward the horizon and the brightness near the Moon are not reflected in a direction-dependent limiting magnitude.
- Dark adaptation recovers in 30 seconds, shorter than in reality (20–30 minutes).
- Differences between individual observers are represented only by the naked-eye limiting magnitude setting (4–6.5).
- Stars fainter than mag 15.2 are not drawn as individual stars and are included only in the Milky Way image.
- Real solar observing always requires a dedicated solar filter.

