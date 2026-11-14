# Cheonsang changelog

0.1.0 (2026-11-07) to 2.0.0 (2026-11-15) · Minecraft 26.3 · Fabric

| Series | Summary |
|---|---|
| 0.1 | First sky: real stars, Sun, Moon, and planets, telescope, search |
| 0.2 | Crash safety net, Milky Way 8K |
| 1.0 | Release for friends: real time, date and time in one command |
| 1.1 | Info card, grids and boundaries, constellation art, tracking lock |
| 1.2 | Expansion packs split off, settings screen, lunar libration and place names, Mars rotation, asteroids and variable stars |
| 1.3 | Longitude and local mean time, real solar eclipses, matching the vanilla Moon and night |
| 1.4 | Star flicker fixed, angular distance, observing logbook and notebook |
| 1.5 | Sky brightness model, horizon, light pollution, dark adaptation, and aurora, advancements |
| 1.6 | Higher default resolution, Ultra pack (Milky Way tiles) |
| 1.7 | Real telescope optics, faint stars, 3D telescope |
| 1.8 | Milky Way 12K, 32.09 million Gaia stars, close binaries, expanded search |
| 1.9 | Cheonsang Yeolcha Bunyajido, personal observing location, English, new aurora |
| 1.10 | Observing sketches of galaxies, nebulae, and clusters, /cwhen, screen day/night = real Sun |
| 2.0 | Public release: extra pack, multiplayer location cleanup, abuse protection, VSOP87 planets |

---

## 2.0: Public release

### 2.0.0 (2026-11-15)
- Telescope optics line at the lower left (aperture, focal length, eyepiece, magnification) no longer hidden by the hotbar: shrunk to fit the empty width left of the hotbar, and moved above the health bar if it still does not fit
- Meteors based on the computer clock: in multiplayer, the same time gives the same meteors
- New mod icon (hand-drawn) · cheonsang-extra 1.0.1 (icon only)
- CREDITS: non-commercial item (DSS sketch plates) added to the license summary, M27 attribution, version history notes cleaned up

### 2.0.0-rc2 (2026-11-14)
- Planet positions now computed with VSOP87 (truncated, 1,259 terms). Maximum error over 1800–2050: Saturn 11′→4″ · Jupiter 8′→4″ · Uranus 17′→4″ · Mars 1.1′→13″ · Mercury and Venus 4–6″ (checked against PyEphem)

### 2.0.0-rc1 (2026-11-14)
- When a server OP changes latitude or longitude with `/cele set`, everyone's `/cplace` is cleared (players not online get it on their next login). Changing only the date and time leaves it unchanged · server and client both 2.0.0-rc1
- Abuse protection: logbook/advancement packets and telescope pose packets rate-limited per player per second; the logbook list is sent in one batch once per second
- Meteors run on real time (they keep falling when time is frozen)
- Guidance and explanatory sentences removed from in-game text; status and numbers only

---

## 1.10: The sky through an eyepiece

### 1.10.2 (2026-11-14)
- Expansion packs consolidated: **cheonsang-extra 1.0.0** = Gaia star pack (32.09 million stars to magnitude 15.2 in the telescope) + naked-eye Milky Way 16K/32K tiles + Moon 8K. Replaces the Gaia star pack and the Ultra pack (non-photo parts) (Cheonsang 1.10.2 or later)
- Nebula and galaxy photos and the long-exposure Milky Way (photo pack = Ultra pack): development paused for now. They still work if installed
- extra pack shown in the pack status at the bottom of the settings screen

### 1.10.1 (2026-11-14)
- Nebulae, planetary nebulae, and globular clusters also shown as seen through an eyepiece (based on DSS2 red and blue and DSS1 red plates). Star grains removed, leaving only a smooth glow for the shape; the stars on top are drawn from real star data
- Nebulae mainly use the blue plate, matching dark-adapted eye sensitivity (sensitive to blue-green, weak in red). Restored the overexposed white centers of M42, M8, M17, and others
- The 13 globular clusters differ in shape according to their measured core radii (47 Tuc sharp · ω Cen and M22 broad). Planetary nebulae: the two eyes of M97, the ring hole of C63, the ring of M57
- Screen day/night (sky, fog, ground light) follows Cheonsang's real solar altitude: bright at night during high-latitude white nights and dark during the day in polar night. Mob spawning and sleeping stay vanilla. Can be turned off in the settings screen
- `/cfind <object>`: if not visible now, shows the best night + [place]·[go]·[next]. A space after the name suggests true/false
- `/cwhen` result buttons [place] (personal observing location) · [go] (OP, `/cele set` to that date and place) · [next]; result text is numbers only
- For dates with no dark hours (white nights), points to the time when the Sun is lowest (previously fixed at 22:00, which moved you into daytime)
- Old server config compression rate 3.261 migrated once to 1.0 (for existing worlds use `/cele rate 1`)
- Fixed stripes appearing on some galaxy photo plates (15, including M17)

### 1.10.0 (2026-11-14)
- Galaxies shown as seen through an eyepiece instead of as a glow: based on real sky photos (DSS2), with only the parts brighter than the visibility threshold shown in gray. Small apertures and bright skies show a central smudge; large apertures, dark skies, and higher magnification show arms and dust lanes
- 264 galaxies added (341 down to magnitude 11, e.g. `/cfind NGC2903`). Galaxies without photos use a drawing by morphological type
- `/cwhen`: when and where an object is best seen · solar eclipses (total, annular, partial), lunar eclipses (total, partial, penumbral), meteor showers, full Moon, new Moon, planetary conjunctions, next events. Result [go] = `/cplace` to that place
- `/ctonight` is the old name of `/cwhen` (hidden from autocomplete, still runs)
- `/cfind … true`: tracking lock goes directly to the found object for planets and deep-sky objects too (a nearby galaxy was being picked instead of faint Neptune)
- Fixed `/cplace` buttons and the settings screen location field not parsing in languages that use a decimal comma
- Credits: DSS photo plates are CC BY-NC-SA 4.0 (non-commercial)

---

## 1.9: Cheonsang Yeolcha Bunyajido and observing location

### 1.9.2 (2026-11-14)
- Telescope eyepiece pose visible to other players in multiplayer (server and client both need 1.9.2)
- Gaia star pack 1.1.0 (`cheonsang-stars-1.1.0.jar`, Cheonsang 1.9.0 or later): includes the main mod's star list changes from 1.8.1–1.9.0 (WDS positions of companion stars, Eta Carinae, and others). Do not use 1.0.0 with 1.9.x
- Official 1.9.x release bundle: main mod + Gaia pack 1.1.0

### 1.9.1 (2026-11-14)
- Aurora redrawn with a shader: for each sky pixel, light is accumulated along the aurora layer (80–320 km altitude, including Earth's curvature), so three thick curtains appear with perspective and depth
- Aurora oval position changes with latitude as in reality (south of the oval: northern sky; under the oval: overhead; north of the oval: southern sky)
- Curtains no longer get too bright when seen edge-on, and the brightest parts are soft (no solid green pillars)
- No stripes, ripple patterns, or sand-like grain; a faint veil of light below the curtains
- Aurora computed on the graphics card (removed the 1.9.0 mesh rebuilt every 0.05 seconds)

### 1.9.0 (2026-11-14)
- 289 constellations of the Cheonsang Yeolcha Bunyajido (switch between Western 88 and Cheonsang Yeolcha in the settings screen), 4 advancements added
- `/cplace`: an observing location that changes only your own sky (latitude longitude · city name · reset). "Observing location" field in the settings screen
- English support (screens and command replies follow the game language)
- 7 moons of Saturn (Mimas, Enceladus, Tethys, Dione, and Iapetus added), cards and search for 18 well-known named stars
- Solar and lunar eclipse advancements accept partial eclipses, advancement progress shown as a fraction
- Aurora redrawn: three layers of arc-shaped curtains, light pillars, and ripples; overhead with corona at high latitudes or strong activity
- Telescope: the mouse wheel goes to the open screen when a screen is open; equatorial mount and tracking lock stay on when chat is opened
- Reduced horizon sparkle, graphics memory default set to automatic (1/4 of card memory), Eta Carinae brightness corrected
- Telescope recipe and icon changed, new worlds start at 2026-08-11 06:00
- Dedicated servers: server and client both need 1.9.0 (sky data format changed)

---

## 1.8: Milky Way and faint stars

### 1.8.4 (2026-11-12)
- `/cfind <moon> true`: tracking lock goes directly to the found moon without reselecting the center of the screen (previously Saturn was picked)
- Moon tracking kept at low magnification or behind the parent planet; when hidden, "Currently hidden" is shown (action bar, card, search result)

### 1.8.3 (2026-11-12)
- Moons, asteroids, and planets that appear as points now drawn the same way as stars: fixed flickering or disappearing when moving the view
- Fixed constellation lines disappearing entirely at high magnification (around ×700)
- `/cfind` search expanded: moons, English names, star designations (α Lyr · Korean names · Alpha Lyrae · 61 Cygni), HD numbers, companion stars (Sirius B), constellation abbreviations and Latin genitives, long Messier and Caldwell forms, meteor shower radiants, 787 lunar place names
- All names appear in autocomplete

### 1.8.2 (2026-11-12)
- Fixed stars not being selectable with left click at high magnification (above ×700)
- Faint companions right next to bright stars (Sirius B and others) can now be selected
- 105 companion stars from HYG moved to current WDS positions (Sirius B 5.1″ → 11.1″, Rigil Kentaurus B 19″ → 8.1″, γ Virginis B 9.4″ → 3.4″)

### 1.8.1 (2026-11-12)
- Left-click info card for faint stars below magnitude 9 too (magnitude, color, position, rise/set)
- Fixed companion stars entered twice, which made Castor, Porrima, and Mizar appear as three (over 59 pairs)
- Added binaries written as "A,BC" (Almach and others)

### 1.8.0 (2026-11-12)
- Telescope Milky Way 8K → 12K (from the NASA 32K original)
- Scattered fake fill-in stars removed entirely: all telescope stars are real observational data
- 7,501 close binaries (WDS): binaries that were a single point split into two once the aperture allows
- First Gaia star pack (see Expansion packs below)
- With the Gaia pack installed, the Milky Way image is reduced by the starlight newly drawn (the same light is not added twice)

## 1.7: Real telescope optics

### 1.7.1 (2026-11-12)
- 3D refracting telescope model; eyepiece pose when zoomed in (first and third person)
- Removed action bar messages and limit warnings at the bottom of the screen; one line at the lower left shows only aperture, focal length, eyepiece, and magnification
- Controls shown once on first launch

### 1.7.0 (2026-11-12)
- Real telescope optics: objective aperture 50–305mm in 12 steps (Shift+wheel), eyepiece 40–3mm in 11 steps (wheel), f/7
- Limiting magnitude 7.7 + 5·log10(aperture in cm) (80mm magnitude 12.2, 305mm magnitude 15.1), resolution, exit pupil, blur from overmagnification
- Round field of view, slight seeing jitter
- Stars fainter than magnitude 9: real Tycho-2 stars
- Digital zoom removed (magnification through optics only)

## 1.6: Image quality

### 1.6.2 (2026-11-11)
- Ultra pack: 45 nebula and galaxy photos re-downloaded from the originals, 31 aligned to real star positions
- Noise removed from the Milky Way image

### 1.6.1 (2026-11-11)
- Milky Way tile loading: the large Milky Way is split into 1024px tiles and only the visible ones are loaded (graphics memory up to 512MB)
- Ultra pack support (see Expansion packs below)

### 1.6.0 (2026-11-11)
- Higher default resolution (HD pack merged in): Milky Way 8K/4K, Moon map 4K (256 → 1K → 2K → 4K as you zoom in), Mars 2K, real band colors for Jupiter and Saturn, real cross-section of Saturn's rings
- Telescope appears in the Creative "Tools & Utilities" tab
- `/cfind HIP 21421`: unnamed stars by HIP number
- Stars such as Aldebaran can be selected with left click even inside wide clusters like the Hyades and Pleiades
- Planet colors near the horizon match the stars, point and disk colors match
- Advancement titles Blood Moon (lunar eclipse) and Eclipse (solar eclipse)

## 1.5: Sky light

### 1.5.4 (2026-11-11)
- Advancement titles cleaned up
- Jupiter and Saturn moon positions use the same refracted position as the disk (matches the left-click circle)
- Less reddening of stars near the horizon (Canopus was too red)
- `/cfind <name> true`: turns the view to the object and, if holding the telescope, turns it on with tracking lock
- Team itsnotfineart, MIT license, icon, English CREDITS

### 1.5.3 (2026-11-11)
- 20 Minecraft advancements in a "Cheonsang" tab (Moon, lunar eclipse, solar eclipse, 7 planets, 4 Jupiter moons, 88 constellations, 110 Messier, 109 Caldwell, and more)
- Logbook registration chat message removed
- 69 nebulae and clusters in the Large and Small Magellanic Clouds (NGC 1850, 2014, and others): glow, left click, search, logbook

### 1.5.2 (2026-11-11)
- Real stars in the Large and Small Magellanic Clouds (Gaia DR3 magnitude 9–11, about 3,150 stars)
- Hotfix 1.5.2.1: fixed `/ctonight next` error, misaligned selection circle, broken settings screen / removed sky brightness changing when turning your head (single zenith-based value)

### 1.5.1 (2026-11-11)
- Light pollution: Bortle scale 1–9 from nearby block light, orange dome on the horizon toward villages
- Dark adaptation: faint stars disappear in bright places or with a torch in hand, and recover over up to 30 seconds in the dark
- Star twinkling (stronger near the horizon, none for planets)
- Zodiacal light and gegenschein, high-latitude aurora

### 1.5.0 (2026-11-11)
- Sky brightness model: sky brightness computed from airglow, twilight, and moonlight sets the limiting magnitude for stars and the intensity of the Milky Way and nebulae
- Horizon: stars dimmed and reddened by atmospheric extinction, raised about 0.5° by refraction, Sun and Moon flattened and reddened
- Blue tint to moonlit sky, green airglow band, color removed from faint glows
- Removed the temporary handling that dropped the Milky Way and Magellanic Clouds to 0 with magnification (they remain faintly at high magnification)

## 1.4: Observing tools

### 1.4.0 (2026-11-11)
- Fixed stars flickering as the sky rotates (stars computed directly as circles, with a minimum size)
- Fixed tracking jitter at high magnification
- Angular distance: Ctrl+left click after selecting
- Observing logbook (registers only when actually visible) · observing notebook (tonight, upcoming events, logbook) · `/clog`

## 1.3: Calculation basis

### 1.3.1 (2026-11-11)
- Vanilla Moon phase and slime spawning matched to the Cheonsang Moon
- Vanilla night matching (optional): mob spawning and night checks use the real solar altitude
- Sky works with each player's own date and location even if the server does not have Cheonsang
- Digital zoom ×500 and ×1000 (removed in 1.7.0)

### 1.3.0 (2026-11-11)
- Longitude added, Minecraft clock = local mean time of that place
- `/cele set <year> <month> <day> <hour> <minute> <latitude> <longitude>`, place name allowed in the latitude slot
- Real solar eclipses: the Moon computed for the observer, so only eclipses actually visible from that place occur
- Fixed Milky Way tile blocks at high magnification, overexposed full Moon, star color saturation, and info card size; Great Red Spot easier to see

## 1.2: Packs, settings, and small bodies

### 1.2.5 (2026-11-11)
- Asteroids Ceres and Vesta (1900–2050)
- Variable stars Algol and Mira (brightness actually changes)
- Hotfix 1.2.5.1: fixed crash on entering a world (key settings)

### 1.2.4 (2026-11-11)
- Real map of Mars, showing the face turned toward Earth at that time (rotation)
- Jupiter's Great Red Spot at its position for that time
- Saturn's moons Titan and Rhea

### 1.2.3 (2026-11-11)
- Earthshine: the dark side of crescent Moons is brightest
- Lunar libration: the limb shows or hides a little more each day
- 787 lunar place names (121 in Korean), left click at ×200 and above

### 1.2.2 (2026-11-11)
- Sun and Moon at real size (previously 3× for the naked eye)
- Milky Way stays smooth when zoomed (bicubic interpolation)
- Long-exposure mode Milky Way not reduced with magnification
- 1,609 real Gaia DR3 stars around 52 open clusters
- Performance: less work for constellation lines, info card, and planet calculations; `/cbench` in all builds

### 1.2.1 (2026-11-10)
- Cheonsang settings screen (assigned key or ModMenu): sky display items, long exposure, info card position, opacity, and lines, text size
- `/cfilter` removed (moved to the settings screen), `/cele next` → `/ctonight next`

### 1.2.0 (2026-11-10)
- Expansion packs split off: long-exposure Milky Way and nebula photos → Photo pack, realistic Milky Way 8K → HD pack (main mod down to 15.8MB)
- Large images preloaded when the telescope is held, unloaded after 2 minutes unused
- Grayscale Milky Way memory reduced to 1/4

## 1.1: Information and tools

### 1.1.5 (2026-11-08)
- Constellation art only at ×1
- Fixed fog color that made distant ground and the horizon glow blue at night
- Info card matched to Stellarium (altitude with refraction, RA/Dec for the current date, Moon for the observer, standard altitude for rise/set)

### 1.1.4 (2026-11-08)
- Tracking key registered in Controls (default left Alt): fixed Alt+left click tracking not working
- Constellation art no longer dimmed by moonlight
- Telescope starts at ×1 each time it is turned on

### 1.1.3 (2026-11-08)
- Alt+left click = select + tracking lock
- `/cfind` NGC, IC, Mel, and Cr numbers
- Field width in the magnification display, less lag from boundaries, broken lines fixed
- In real-time mode, card and `/ctonight` times use that city's clock

### 1.1.2 (2026-11-08)
- 85 constellation art images (Stellarium)
- Lunar orbit path with ascending and descending nodes
- Fixed sky jitter when time is frozen or in real-time mode
- Overlapping boundary edges removed (less lag)

### 1.1.1 (2026-11-08)
- Shift+left click = deselect
- Less lag from grids and the selection circle
- 77 galaxy morphologies (de Vaucouleurs)

### 1.1.0 (2026-11-08)
- Info card + selection circle (distance, spectral type, absolute magnitude, rise/set)
- Equatorial and alt-az grids, constellation boundaries
- Rise, transit, and set times, `/ctonight` sights for tonight
- `/cele next`: next solar and lunar eclipses, meteor shower peaks, full Moon, new Moon, planetary conjunctions

## 1.0: Release for friends

### 1.0.2 (2026-11-08)
- Fixed 163 nebula and galaxy glows fainter than magnitude 6 not showing (per magnification step)
- Fixed the Large and Small Magellanic Clouds, M24, and C99, which existed only in the Milky Way image, disappearing when zoomed in
- Stellar objects (M40, M73) shown, C99 magnitude display fixed

### 1.0.1 (2026-11-08)
- `/cele set <year> <month> <day> <hour> <minute>`: date and time in one command

### 1.0.0 (2026-11-08)
- `/cele realtime [city]`: one Minecraft day = one real day, 13 cities
- Fixed swirl pattern at the Milky Way's north pole
- Higher-resolution nebula and galaxy photos

## 0.2

### 0.2.0 (2026-11-08)
- Crash safety net: on an error, only that part is turned off for the current session and the game continues
- Milky Way NASA 8K/4K (realistic and long exposure)
- Default compression rate 1.0 (real speed)

## 0.1: First sky

### 0.1.13 (2026-11-08)
- Default = realistic (as seen by the eye); long exposure must be turned on
- Pleiades (M45) photo aligned to real star positions
- Commands renamed to English

### 0.1.12 (2026-11-08)
- Constellation line breaks only by magnification, label positions fixed
- Less star twinkle, stronger star colors
- Uranus and Neptune, left click on Jupiter's moons
- N key cycles the telescope display

### 0.1.11 (2026-11-08)
- Total solar eclipse effect: sky and ground darken, stars and planets appear, corona
- Fixed the dark side of the Moon being painted black
- Stronger constellation lines, label overlap avoided

### 0.1.10 (2026-11-08)
- Fixed crash when aiming at Venus or Mercury
- M31 photo orientation corrected

### 0.1.9 (2026-11-07)
- Arrow turns off when the object found with `/cfind` reaches the center of the crosshair

### 0.1.8 (2026-11-07)
- Telescope magnification in 6 steps (×1·×7·×25·×75·×200·×300)
- Search command split off as `/cfind`
- Daytime Moon brighter than the sky, even all the way to the limb

### 0.1.7 (2026-11-07)
- Fixed the game crashing on `/cele lat` · `time` · `set` · `rate` · `reset` (introduced in 0.1.5: commands forwarded to the server were intercepted again and looped endlessly)
- These commands now go straight to the server the way Minecraft normally sends them

### 0.1.6 (2026-11-07)
- Sun, Moon, and sunset refinements: solar glare, blurred naked-eye Moon map, Moon occluder

### 0.1.5 (2026-11-07)
- Fixed `/cele` subcommands being blocked
- Finder window removed

### 0.1.4 (2026-11-07)
- Telescope field of view is a circle the size of the screen, darkened only at the edge
- Fixed the view flipping when looking straight up

### 0.1.3 (2026-11-07)
- 0.1.3-a: round field of view, crosshair, constellation lines (nearest 3), planet and Moon names, star name on left click
- 0.1.3-b: equatorial tracking, `/cele find`, twilight from the real Sun direction, nebula glow, Milky Way 4K
- 0.1.3-c: solar disk computation (limb darkening), NASA LRO Moon map + real-time phase, real-size Sun and Moon in the telescope, planets 512px
- Telescope magnification limit 100 → 300

### 0.1.2 (2026-11-07)
- Fixed crash at night and when joining existing maps (star shader)
- If a shader fails, only that part is skipped instead of crashing

### 0.1.1 (2026-11-07)
- Fixed crash right after entering a world

### 0.1.0 (2026-11-07)
- First version: real positions of stars, Sun, Moon, and planets, Milky Way, nebula and galaxy photos, meteors, solar and lunar eclipses
- Telescope, compression rate and latitude settings

---

## Expansion packs

| Pack | Version | Date | Contents |
|---|---|---|---|
| extra (cheonsang-extra) | 1.0.0 | 2026-11-14 | All of Gaia star pack 1.1.0 + naked-eye Milky Way 16K/32K + Moon 8K (photos excluded), Cheonsang 1.10.2 or later, 434MB |
| Gaia star pack (cheonsang-stars) | 1.1.0 | 2026-11-14 | Includes the main mod's star list changes from 1.8.1–1.9.0, Cheonsang 1.9.0 or later (use this version with 1.9.x), 189MB |
| Gaia star pack (cheonsang-stars) | 1.0.0 | 2026-11-12 | 32.09 million telescope stars to magnitude 15.2 (Gaia DR3), 189MB |
| Ultra pack (cheonsang-ultra) | 2.1.0 | 2026-11-12 | 45 nebula and galaxy photos re-downloaded and aligned to star positions, about 790MB |
| Ultra pack (cheonsang-ultra) | 2.0.0 | 2026-11-11 | Naked-eye Milky Way 16K/32K, long-exposure color 16K + hydrogen-alpha, Moon 8K, replaces the Photo pack, about 740MB |
| Photo pack (cheonsang-photo) | 1.0.1 | 2026-11-11 | Icon and credits (images unchanged); later merged into the Ultra pack |
| Photo pack (cheonsang-photo) | 1.0.0 | 2026-11-10 | Long-exposure Milky Way and nebula photos, 110MB |
| HD pack (cheonsang-hd) | 1.0.1 | 2026-11-11 | Icon and credits; merged into the main mod from 1.6.0 |
| HD pack (cheonsang-hd) | 1.0.0 | 2026-11-10 | Realistic Milky Way 8K for telescope zoom, 18MB |
