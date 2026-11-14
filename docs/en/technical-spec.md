# Cheonsang 2.0.0 Technical Specification

Based on Cheonsang 2.0.0 · 2026-11-15

## Contents

1. Overview
2. Runtime environment and build configuration
3. Module structure
4. Astronomical computation models
5. Data sources and processing
6. Rendering
7. Performance
8. Commands, keys, and settings
9. Multiplayer synchronization and abuse prevention
10. Fault isolation
11. Known limitations

---

## 1. Overview

Cheonsang is a Fabric mod that replaces the sky of the Minecraft Java Edition Overworld with the results of astronomical computation. It takes the observing location (latitude and longitude) and the sky time (Julian Day) as input, computes the direction and apparent brightness of stars, the Sun, the Moon, planets, moons, asteroids, and deep-sky objects, decides visibility from the atmosphere, sky brightness, and telescope optics, and draws the result with GPU shaders.

| Item | Value |
| --- | --- |
| Mod ID | `cheonsang` (expansion pack `cheonsang_extra`) |
| Package | `kr.inf.cheonsang` |
| Environment | `environment: "*"` (one jar for server and client; runs client-only as well) |
| License | Code: MIT. Data files follow the licenses of the original sources (CREDITS.txt, Section 5.14) |
| Source size | 56 Kotlin and 13 Java files, 10,918 lines (including the VSOP87 coefficient file), 11 GLSL shaders |
| Translations | 546 keys each in `ko_kr` and `en_us`. Korean when the game language is Korean, English otherwise |
| Advancements | 24 (`data/cheonsang/advancement/`) |
| Mod icon | `assets/cheonsang/icon.png` 128×128 (hand-drawn, CC BY-SA 4.0). Expansion pack: `assets/cheonsang_extra/icon.png` 128×128 |
| Release file size | Main mod `cheonsang-2.0.0.jar` 111.5 MB, expansion pack `cheonsang-extra-1.0.1.jar` 434.0 MB |

---

## 2. Runtime environment and build configuration

Values were taken from `gradle.properties`, `build.gradle.kts`, and `fabric.mod.json`.

| Item | Value |
| --- | --- |
| Minecraft | 26.3 (`"minecraft": "~26.3"`) |
| Fabric Loader | 0.20.1 or later (`>=0.20.1`) |
| Fabric API | 0.165.0+26.3 |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 |
| Kotlin | 2.4.21 (JVM target 25) |
| Java | 25 (`options.release = 25`, required: `>=25`) |
| Fabric Loom | 1.19-SNAPSHOT (`net.fabricmc.fabric-loom`, `splitEnvironmentSourceSets`) |
| Mod Menu | 21.0.0 (optional, entry point for the settings screen) |
| Declared conflict | `"breaks": { "iris": "*" }`. The game does not start if Iris is installed alongside |
| Expansion pack | cheonsang-extra 1.0.1. Dependencies `cheonsang >=1.10.2`, `minecraft ~26.3`, `environment: "client"` |

---

## 3. Module structure

### 3.1 Entry points and packages

| Entry point | Class | Role |
| --- | --- | --- |
| `main` | `kr.inf.cheonsang.Cheonsang` | Server and common initialization: registers `SkyServer`, `LogServer`, `PoseServer`, `VanillaSync`, `Telescope` |
| `client` | `kr.inf.cheonsang.client.CheonsangClient` | Rendering, client commands, key mappings, packet reception, config files |
| `modmenu` | `kr.inf.cheonsang.client.ModMenuEntry` | Connects the Mod Menu settings screen |

| Source set | Package | Main contents |
| --- | --- | --- |
| main (common) | `kr.inf.cheonsang` | `SkyServer` (sky state, `/cele`), `LogServer` (observing logbook), `PoseServer` (telescope pose), `RateLimit`, `RealTime` (13 real-time cities), `VanillaSync` (vanilla Moon phase and night), `Telescope` (item), `NextEvents`, `L` (translation) |
| main (common) | `kr.inf.cheonsang.astro` | `AstroMath` (vectors, matrices, ΔT), `Calendar`, `SkyClock`, `Frames`, `Sun`, `Moon` and `MoonTerms`, `Planets` and `Vsop87Data`, `Eclipse`, `RiseSet`, `Events`, `WhenCalc` |
| client | `kr.inf.cheonsang.client` | `TelescopeView`, `TelescopeHud`, and `Optics` (telescope), `Visibility` (visibility model), `AmbientLight` (light pollution, dark adaptation, aurora conditions), `InfoCard`, `FindGuide` (`/cfind`), `CWhen` (`/cwhen`), `Place` (`/cplace`), `LogBook` and `NotebookScreen`, `SettingsScreen`, `Bench`, `Safety`, `PoseSync` |
| client | `kr.inf.cheonsang.client.sky` | `CheonsangSky` (main sky renderer), `StarCatalog`, `DeepStars`, `TileSky`, `PhotoSky`, `GalaxySketch`, `Meteors`, `SmallBodies`, `SkyData`, `Packs`, `VramBudget`, `VisualDay`, `GrayTexture` |

The astronomical computation (`astro`) lives in the common source set, so server commands (`/cele`, next-event calculation) and client rendering use the same code.

### 3.2 Mixins

| Config file | Targets |
| --- | --- |
| `cheonsang.mixins.json` (common) | `EnvironmentAttributeSystemMixin` |
| `cheonsang.client.mixins.json` | `SkyRendererMixin`, `CameraMixin`, `MouseHandlerMixin`, `MinecraftMixin`, `AtmosphericFogMixin`, `LightmapMixin`, `AvatarRendererMixin`, `EntityRendererAccessor`, `FirstPersonHandsMixin`, `TimelineMixin`, `AttributeTrackSamplerMixin` |

For on-screen day and night, only the visual tracks of the 26.3 `day` timeline (sky, fog, cloud color, sky light on the ground, star brightness, sunset glow) are sampled at a virtual time matched to the Cheonsang Sun altitude (`TimelineMixin`, `AttributeTrackSamplerMixin`). This happens only on the render thread. Game rules such as mob spawning and sleeping follow the vanilla clock. The `visualDay` setting turns this off (on by default).

### 3.3 Network packets

| ID | Direction | Fields (codec) | Purpose |
| --- | --- | --- | --- |
| `cheonsang:sky4` | Server → client | epochTick (VAR_LONG), epochJd (DOUBLE), rate (DOUBLE), lat (DOUBLE), lon (DOUBLE), city (STRING_UTF8) | Per-dimension sky clock, time compression rate, world default location, real-time city |
| `cheonsang:placegen` | Server → client | gen (VAR_INT) | World default location change counter. If the received value differs from the previous one, the client clears its own `/cplace` |
| `cheonsang:log_add` | Client → server | key (UTF-8 ≤96), name (UTF-8 ≤96), zoom (DOUBLE) | Adds a logbook entry. The `adv:` prefix grants an advancement criterion |
| `cheonsang:log_all` | Server → client | lines (list of UTF-8 ≤256 strings, up to 20,000 lines) | The player's full logbook |
| `cheonsang:pose_set` | Client → server | on (BOOL) | Whether the telescope is zoomed in (above ×1) |
| `cheonsang:pose` | Server → client | id (UUID), on (BOOL) | Telescope pose of another player |

All packets are registered with Fabric `PayloadTypeRegistry`. Before sending, `ServerPlayNetworking.canSend` checks that the receiver can accept the channel (`placegen`, `pose`).

### 3.4 Saved data and config files

| Location | Format | Contents |
| --- | --- | --- |
| World save `cheonsang:sky` | `SavedData`, string → list of doubles | Per dimension ID: [epoch tick, epoch Julian Day, compression rate, latitude] (+ city index and pre-activation values in real-time mode); longitude under the `#lon` key; location change counter under the `#gen` key |
| World save `cheonsang:log` | `SavedData`, UUID → (entry key → "Julian Day\|magnification\|name") | Observing logbook. Up to 15,000 entries per player |
| `config/cheonsang/server.json` | JSON | `rate`(1.0), `lat`(0), `lon`(0), `vanillaMoon`(true), `vanillaNight`(false) |
| `config/cheonsang/client.json` | JSON | `enabled`(true), `nakedEyeLimit`(6.0, 4–6.5), `meteors`(1.0, 0–5), `display` (settings screen values) |
| `config/cheonsang/places.json` | JSON | Personal observing locations, per server and world |
| `config/cheonsang/log_local.json` | JSON | Logbook used when the server does not have Cheonsang |

### 3.5 Expansion pack structure

The expansion pack cheonsang-extra 1.0.1 is a client-only jar that adds files to the same resource namespace as the main mod (`assets/cheonsang/`). Every 5 seconds the main mod checks the `format` value in `assets/cheonsang/pack/<name>.json` to decide the pack status (installed, missing, update needed). There are two pack descriptor files: `extra.json` (`{"format": 1, "pack": "extra", "version": "1.0.1"}`) and `stars.json` (`{"format": 1, "pack": "stars", "version": "1.1.0", "bands": 180}`).

The expansion pack contains Gaia star cell files, 16K and 32K naked-eye Milky Way tiles, and an 8K Moon map. File lists and sizes are in Section 5.5. Gaia star cell files store each star in 6 bytes (position within the cell 16 bits × 2, magnitude and color 8 bits each) and compress each cell with zlib. Each pack cell also contains the main mod's stars with the same values, so when a cell is replaced by the pack cell the existing stars stay and only fainter stars are added. One Milky Way tile is 1028×1028: 1024 px plus a 2 px border.

---

## 4. Astronomical computation models

### 4.1 Time scales

| Item | Specification |
| --- | --- |
| Base unit | Julian Day (JD, UT). J2000.0 = JD 2451545.0 |
| Calendar conversion | Meeus Chapter 7. Gregorian calendar from 1582-10-15, Julian calendar before that. Years −2999–2999 (astronomical year numbering) |
| Dynamical time | TT = UT + ΔT. Orbits of the Sun, Moon, and planets are computed in TT Julian centuries |
| ΔT | Espenak and Meeus (NASA) piecewise polynomials: 1900–1920, 1920–1941, 1941–1961, 1961–1986, 1986–2005, 2005–2050, 2050–2150. Outside this range, the long-term parabola ΔT = −20 + 32u², u = (y − 1820)/100. The 2050–2150 segment is −20 + 32u² − 0.5628(2150 − y) |
| Sidereal time | Greenwich mean sidereal time GMST (Meeus 12.4) |
| Sky clock | JD = epochJd + (tick − epochTick) / 24000 × rate. Local sidereal time = right ascension of the mean Sun + hour angle of the mean Sun at the Minecraft time (equal to GMST + longitude at compression rate 1) |
| Game time | Local mean time at the observing longitude. Tick 0 = 06:00, tick 6000 = noon |
| New world epoch | 2026-08-11 06:00 (longitude 0°, UT) = world tick 0, latitude 0°, longitude 0°, compression rate 1.0 |
| Compression rate range | 0.01–1000 (`/cele rate`) |

### 4.2 Coordinate systems

| Coordinate system | Use |
| --- | --- |
| J2000 equatorial (mean equinox) | Reference frame for star and deep-sky catalogs and for planet and moon directions |
| Equatorial of date (mean equinox) | Sun and Moon positions, the sky at the time of observation |
| J2000 ecliptic | Intermediate frame for heliocentric planet positions (obliquity 23.43928°) |
| Horizontal | Altitude (up from the horizon) and azimuth (north 0°, east 90°) |
| World axes | East = +X, up = +Y, north = −Z |

The horizontal transform builds hour-angle coordinates from local sidereal time and then rotates by latitude (`Frames.horizon`). Right ascension and declination on the info card are of date, and altitude includes atmospheric refraction.

### 4.3 Precession and obliquity

| Item | Specification |
| --- | --- |
| Precession | Meeus 21.2 and 21.3 (cubic polynomials in ζ, z, θ). Matrix R = Rz(z) · Ry(−θ) · Rz(ζ), J2000 equatorial → equatorial of date |
| Obliquity | Mean obliquity ε₀ (Meeus 22.2) |
| Nutation | Not applied |
| Aberration | Applied to the Sun only (−0.00569°). Annual aberration of stars is not applied |
| Proper motion | Not applied (Gaia positions were moved once from J2016.0 to J2000.0) |

Star positions use J2000 coordinates with precession only. The difference from Stellarium is about 1′.

### 4.4 Sun

The low-accuracy formulas of Meeus Chapter 25 are used (mean longitude L₀, mean anomaly M, eccentricity e, equation of center C, radius r = 1.000001018(1 − e²)/(1 + e cos ν)). Aberration of −0.00569° is added to the longitude, and the result is converted to equatorial coordinates with the mean obliquity of date. The apparent radius is computed from a solar radius of 696,000 km.

### 4.5 Moon

| Item | Specification |
| --- | --- |
| Position | All periodic terms of Meeus Chapter 47: 60 terms for longitude and distance (Table 47.A), 60 terms for latitude (Table 47.B), additive terms A₁, A₂, A₃, eccentricity correction E |
| Phase | Meeus Chapter 48: phase angle, illuminated fraction, position angle of the bright limb χ |
| Libration | Optical libration and position angle of the axis P from Meeus Chapter 53. Physical libration and nutation are excluded (difference around 0.04°) |
| Libration check | Meeus Example 53.a (1992-04-12): l −1.20°, b +4.19°, P 15.07° (book: −1.206°, 4.194°, 15.08°) |
| Observer parallax | Observer position (Earth radius 6378.14 km, latitude, local sidereal time) subtracted from the geocentric direction |
| Constants | Lunar radius 1737.4 km |

### 4.6 Planets

| Item | Specification |
| --- | --- |
| Theory | Truncated VSOP87D (Bretagnon & Francou 1988). Only terms with amplitude of at least 3×10⁻⁶ rad are kept, 1,259 terms. Coefficients are read from the VSOP87D tables included in PyMeeus |
| Bodies | Mercury, Venus, Mars, Jupiter, Saturn, Uranus, Neptune + Earth (Earth's position also from VSOP87) |
| Series | L, B, R = Σₖ τᵏ Σ A cos(B + Cτ), τ = TT Julian millennia |
| Coordinate transform | Heliocentric ecliptic of date → equatorial of date (mean obliquity) → J2000 equatorial (transpose of the precession matrix) → J2000 ecliptic |
| Geocentric direction | Heliocentric planet position − heliocentric Earth position, converted to J2000 equatorial |
| Light-time | 2 iterations, 0.0057755 days/AU |
| Apparent magnitude | Approximate formulas of Meeus Chapter 41. Saturn includes the ring tilt term |
| Apparent diameter | Diameter at 1 AU (Meeus Chapter 55) ÷ distance from Earth |
| Saturn's rings | Tilt B and position angle P from the IAU north pole of rotation (α 40.589°, δ 83.537°) |
| Rotation | Mars: IAU 2009 model. Jupiter: System II (IAU pole + 870.27°/day). Great Red Spot System II longitude = 91° on 2026-06-01, increasing by 1.75° per month (linear fit to the JUPOS trend), planetographic latitude −22.5° |
| Computation cost | 28 µs for one pass over all 7 planets |
| Render update | Two epochs 1 second of real time apart are computed and directions between them are interpolated. If more than 0.5 days of sky time pass in 1 second, the computation runs every frame |

The maximum errors against PyEphem are as follows.

| Period | Mercury | Venus | Mars | Jupiter | Saturn | Uranus | Neptune |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1800–2050 | 4.3″ | 5.4″ | 12.6″ | 4.3″ | 3.8″ | 4.0″ | 2.4″ |

Over the full range from 1000 BCE to 3000 CE, the maximum error is about 2′. Over the same 1800–2050 interval, the previous method, the JPL approximate orbital elements (Standish & Williams 1992), had errors of 7.9′ for Jupiter, 11.5′ for Saturn, and 17.1′ for Uranus. Mars and Jupiter rotation checked against JPL Horizons gave a Mars central meridian longitude of 1.27° (Horizons 1.28°) and Jupiter System III of 45.41° (Horizons 45.41°).

### 4.7 Moons

| Bodies | Model | Accuracy |
| --- | --- | --- |
| The 4 Galilean moons of Jupiter | Low-accuracy method of Meeus Chapter 44. Coordinates in Jupiter's equatorial plane are converted to east and north offsets on the sky using the position angle of Jupiter's axis. In front of Jupiter when cos u > 0 | About 0.5 Jupiter radii |
| 7 moons of Saturn | Orbit formulas fitted to JPL Horizons vectors for 1900–2050. λ = λ₀ + nt in Saturn's equatorial plane, periodic terms for equation of center and radius, long-period libration terms, out-of-plane height terms, a 2λ term for Iapetus | Mean error: Titan 0.7″, Rhea 0.3″, Mimas 0.66″, Enceladus 0.07″, Tethys 0.52″, Dione 0.02″, Iapetus 1.02″. Maximum 2.6″ |
| Magnitudes of Saturn's moons | Titan 8.3, Rhea 9.7, Tethys 10.2, Dione 10.4, Iapetus 11.0, Enceladus 11.7, Mimas 12.9 (mean near opposition) | |
| Occultation test | A moon behind its parent planet (behind along the line of sight, within Saturn's radius of 60,268 km) is hidden | |

### 4.8 Asteroids

Ceres and Vesta are interpolated with 4-point Lagrange interpolation from JPL Horizons heliocentric position tables (1900–2050, 4-day step, `asteroids.bin`). They are not shown for dates outside the table range.

### 4.9 Solar and lunar eclipses

| Item | Specification |
| --- | --- |
| On-screen solar eclipse | No separate event. The Moon covers the Sun according to its observer-based direction and apparent size |
| On-screen lunar eclipse | Shadow radii from Meeus Chapter 54, with the Danjon 1.02 enlargement. Umbra = 1.02(0.998340π − s + πₛ), penumbra = 1.02(0.998340π + s + πₛ). π: lunar horizontal parallax, s: apparent solar radius, πₛ = 8.794″/distance (AU) |
| Solar eclipse prediction | For each new moon, the central line where the shadow axis meets the Earth ellipsoid is computed at 1-minute steps. At each point on the central line, observer parallax decides total or annular (hybrid if it changes along the path). Duration at 10-second steps. Search range 60 years |
| Lunar eclipse prediction | For each full moon, umbral and penumbral coverage classify total, partial, or penumbral, with the times of P1, U1, U2, maximum, U3, U4, and P4 |
| Logbook criteria | Solar eclipse: more than about 5% of the Sun's diameter covered with the Sun above the horizon. Lunar eclipse: more than 2% umbral (penumbral eclipses excluded) |

Results checked against NASA data:

| Item | Cheonsang | NASA |
| --- | --- | --- |
| 2026-08-12 total solar eclipse, point of greatest eclipse | 65.1°N 25.1°W | 65.2°N 25.2°W |
| Same eclipse, totality duration · path width | 2 min 19 s · 301 km | 2 min 18 s · 294 km |
| 2026-08-28 partial lunar eclipse, umbral magnitude | 0.935 | 0.930 |
| 2028-12-31 total lunar eclipse, 6 contacts | within ±1 min | |

### 4.10 Atmospheric refraction

| Item | Specification |
| --- | --- |
| Geometric → apparent (drawing) | Sæmundsson: R(′) = 1.02 / tan(h + 10.3/(h + 5.11)), applied for h ≥ −1.5° (h clamped at −0.9°) |
| Apparent → geometric (selection) | Bennett: R(′) = 1 / tan(h + 7.31/(h + 4.4)), h ≥ −1.0° |
| Atmospheric conditions | Fixed at 1010 hPa, 10 ℃ |
| Where applied | Stars per vertex in the vertex shader (`stars.vsh`); Sun, Moon, and planets on the CPU |

### 4.11 Rising and setting

| Item | Specification |
| --- | --- |
| Reference altitude | Sun −0°50′; stars, planets, and deep-sky objects −0°34′; Moon 0.7275π − 34′ (π: horizontal parallax) |
| Coordinates | Sun and Moon: equatorial of date. Stars, planets, and deep-sky objects: J2000 + precession |
| Search | The day is sampled at 10-minute steps → 25 bisection iterations on bracketing intervals → transit by 30 golden-section iterations around the highest sample |
| Output | Rise, transit (altitude), set. Distinguishes never sets, never rises, up all day, and down all day |

### 4.12 Visibility model

A single function computes the limiting magnitude in the viewing direction. Star display, the intensity of extended sources, and logbook decisions all use this value. The order of steps is fixed.

| Step | Formula and values | Source |
| --- | --- | --- |
| ① Intrinsic brightness | Point source magnitude m, extended source surface brightness S (mag/arcsec²) | Catalog |
| ② Atmospheric extinction | Δm = k(X − 1), X = 1 / (sin h + 0.50572(h + 6.07995)^−1.6364), X ≤ 40. k: V 0.172, B 0.30, R 0.09 mag/airmass | Kasten & Young (1989) |
| ③ Sky brightness | B = natural sky (airglow, van Rhijn) + twilight (table by solar altitude) + moonlight + light pollution + zodiacal light and gegenschein. nL = 34.08 · exp(20.7233 − 0.92104 · mag) | Schaefer (1990), Krisciunas & Schaefer (1991) |
| Sky loss | Δm_sky = 1.5 · log₁₀((1 + B/B₀)/2), B₀ = 22.0 mag/arcsec². About 1.7 mag on a full-moon night, about 5.4 mag at −6° twilight | |
| ④ Dark adaptation | Up to 3 mag loss, recovered over 30 seconds | |
| ⑤ Equipment | Point source gain (Section 6.4); extended source brightness ratio = (exit pupil/7mm)², at most 1 | |
| ⑥ Decision | Point source: m + Δm ≤ limit. Extended source: compared with the surface brightness threshold S_thr(a) = 26.5 − 2.5 log₁₀(1 + (15′/a)²) | |

The twilight zenith brightness table (solar altitude → mag/arcsec²) is −24° 30.0, −18° 24.0, −15° 21.0, −12° 18.2, −9° 15.2, −6° 12.2, −3° 9.5, 0° 7.3, 5° 5.5, 10° 4.6, 20° 3.9, 40° 3.4, 90° 3.0. Sky brightness applies the value at the single zenith point to the whole sky.

Light pollution samples the surroundings within ±64 blocks at 8-block spacing (17×17 = 289 points), sums the block light of surface cells with sky light 15 with distance weighting, and converts the sum to a Bortle index = 1 + 8(1 − e^(−ΣL/800)). Zenith brightness for Bortle 1–9 is 21.9, 21.6, 21.4, 20.8, 20.0, 19.1, 18.5, 18.0, 17.5 mag/arcsec². Samples are read 40 points per tick and recomputed every 2 seconds or after a move of 8 blocks or more.

### 4.13 Accuracy summary by model

| Model | Source | Accuracy (reference) | Validated range |
| --- | --- | --- | --- |
| ΔT | Espenak & Meeus (NASA) | Identical to the original polynomials | 1900–2150 |
| Precession | Meeus Chapter 21 | Nutation not applied | |
| Star positions | J2000 + precession | About 1′ against Stellarium | |
| Sun | Meeus Chapter 25 | Max 43.7″ (PyEphem, 400 random epochs) | 1900–2100 |
| Moon | Meeus Chapter 47 | Max 30.0″, distance max 9.8 km (PyEphem, 400 epochs) | 1900–2100 |
| Lunar libration | Meeus Chapter 53 (optical) | Physical libration and nutation excluded, around 0.04° | |
| Planets | Truncated VSOP87D, 1,259 terms | Max 2.4″–12.6″ (PyEphem) | 1800–2050 |
| Planets (long term) | Same | Max about 2′ | 1000 BCE–3000 CE |
| Jupiter's moons | Meeus Chapter 44 | About 0.5 Jupiter radii | |
| Saturn's moons | Formulas fitted to Horizons | Mean 0.02″–1.02″, max 2.6″ | 1900–2050 |
| Ceres and Vesta | Interpolation of Horizons position tables | Table interpolation | 1900–2050 |
| Solar and lunar eclipses | Sun and Moon models above | Checked against NASA (Section 4.9) | 1900–2050 |
| Atmospheric refraction | Sæmundsson, Bennett | Fixed at 1010 hPa and 10 ℃ | |

The formulas were validated by reimplementing them in Python and comparing the formulas and original coefficient tables with PyEphem, Meeus examples, and NASA eclipse data. All 82 checks in the batch validation `verify_all.py` passed before the planet computation was switched to VSOP87. After the switch, planet positions were checked separately with `vsop_check.py`. Direct measurement of the game screen is not included.

---

## 5. Data sources and processing

### 5.1 Basis of measurement

This section covers the external data included in the main mod `cheonsang-2.0.0.jar` and the expansion pack `cheonsang-extra-1.0.1.jar`, and the external material used as coefficients in the formulas. For each, it lists the source, edition, license, original size, processing method, output size, and validation results. The figures are based on the following.

- Size and count of output files: measured directly by extracting the release jars. Counts of binary files were read from the count field in the file header. File sizes are in bytes.
- Original size: measured directly from the files in `cheonsang/_data_raw/`.
- Processing method: taken from the descriptions and processing code in `cheonsang/_tools/*.py`.
- Validation results: values recorded in the validation reports (`_preview/verify_report.txt`, the 1.9.0 Cheonsang Yeolcha validation report, `_tools/korean_sky_report.md`) and in the output of comparison scripts.

### 5.2 Dataset list

| Field | Dataset | Edition and source | Output files |
| --- | --- | --- | --- |
| Naked-eye stars | HYG Database | v4.1 (astronexus) | stars.bin, star_meta.bin, star_info.bin, star_names.json |
| Telescope stars | Tycho-2 | CDS I/259 | deep_stars.bin |
| Telescope star correction, cluster and Magellanic stars, expansion pack stars | Gaia DR3 | gaia_source, tycho2tdsc_merge_best_neighbour, VizieR I/355 | deep_stars.bin, faint_stars.bin, expansion pack cheonsang_stars/ |
| Close double stars | WDS | VizieR B/wds | Merged as companions into stars.bin and others |
| Variable stars | GCVS | VizieR B/gcvs | Light curve parameters in code |
| Positions of well-known stars | HYG v4.1, SIMBAD | CDS | famous_stars.json |
| Western constellation lines and boundaries | d3-celestial | Olaf Frohn | constellations.json, bounds.json |
| Western constellation art | Stellarium "modern" | Art by Johan Meuris | art_atlas.png, art.json |
| Cheonsang Yeolcha Bunyajido asterisms | Stellarium "chinese", Unicode Unihan | stellarium-skycultures | korean_constellations.json |
| Deep-sky objects | OpenNGC, Wikipedia | Mattia Verga | dso.json, dso_dist.json, dso_morph.json |
| Eyepiece sketches | Digitized Sky Survey (DSS2 Red and Blue, DSS1 Red) | STScI/MAST, NASA SkyView | galaxy_atlas.png, galaxy_sketch.json |
| Globular cluster core radii | Harris catalog | 1996, 2010 edition | Parameters for generating the sketch atlas |
| Milky Way | NASA SVS Deep Star Maps 2020 | SVS #4851 | milkyway_real.png, milkyway_eye_12k.png, dso/mw_\*.png, expansion pack tiles/eye16 and eye32 |
| Moon map | NASA SVS CGI Moon Kit (LRO) | SVS #4720 | moon_map\*.png, expansion pack moon_map_8k.png |
| Lunar feature names | IAU WGPSN / USGS Gazetteer | Downloaded 2026-11-11 | moon_names.json |
| Planet surfaces and rings | Solar System Scope (INOVE) | 8k_mars, 8k_jupiter, 8k_saturn, 8k_saturn_ring_alpha | mars_map.png, jupiter.png, saturn.png, saturn_ring\*.png |
| Saturn's moons, asteroids | NASA/JPL Horizons | 1900–2050 | Orbit formula coefficients in code, asteroids.bin |
| Planet positions | VSOP87D | Bretagnon & Francou (1988), coefficients as included in PyMeeus | Vsop87Data.kt (generated code) |
| Lunar position coefficients | Meeus Chapter 47 tables | astronomia moonposition.js | MoonTerms.kt (generated code) |
| Meteor showers | IMO meteor shower calendar | International Meteor Organization | meteors.json |
| Great Red Spot longitude | JUPOS | H.-J. Mettig et al. | Trend values in code |
| Aurora | No external data | Shader model | aurora.vsh, aurora.fsh |

### 5.3 Main mod astronomy data files (`assets/cheonsang/sky/`)

| File | Size (bytes) | Count | Contents |
| --- | --- | --- | --- |
| `stars.bin` | 1,455,012 | 90,938 | Naked-eye stars (HYG v4.1 to magnitude 9.0, 83,479 + WDS companions 7,459), sorted by brightness |
| `star_meta.bin` | 454,694 | 90,938 | Constellation index, Greek letter, superscript, Flamsteed number |
| `star_info.bin` | 1,304,349 | | Distance, absolute magnitude, HD number, and spectral type for the info card |
| `star_names.json` | 7,832 | 188 | Korean names of bright stars |
| `famous_stars.json` | 5,283 | 18 | Well-known stars without proper names |
| `deep_stars.bin` | 29,076,868 | 2,409,260 | Telescope stars (Tycho-2 + Gaia DR3 correction, to magnitude 15.37), format `DSB2`, 180 bands of 1°, 41,252 sky cells |
| `faint_stars.bin` | 2,461,140 | 153,821 | Fallback data for magnitudes 9–11, used only when `deep_stars.bin` is absent |
| `constellations.json` | 22,382 | 89 | Western constellation lines (88 constellations, Serpens split into Caput and Cauda) |
| `bounds.json` | 98,952 | 104 | Groups of constellation boundary lines |
| `art.json` | 15,228 | 85 | Placement of constellation art |
| `korean_constellations.json` | 90,647 | 289 | Cheonsang Yeolcha Bunyajido asterisms |
| `dso.json` | 76,279 | 554 | Deep-sky object position, size, orientation, magnitude (including 110 Messier and 109 Caldwell) |
| `dso_dist.json` | 9,012 | 531 | Deep-sky object distances |
| `dso_morph.json` | 5,345 | 340 | Galaxy morphology |
| `galaxy_sketch.json` | 10,279 | 462 | Sketch atlas cell index and light area |
| `moon_names.json` | 39,513 | 787 | Lunar feature names (121 with Korean names) |
| `meteors.json` | 937 | 7 | Meteor showers (sporadic meteors 10 per hour) |
| `asteroids.bin` | 330,956 | 2 | Ceres and Vesta position tables (4-day step) |

### 5.4 Main mod textures (`assets/cheonsang/textures/sky/`)

| File | Resolution | Size (bytes) | Contents |
| --- | --- | --- | --- |
| `milkyway_real.png` | 4096×2048 | 4,600,528 | Naked-eye Milky Way |
| `milkyway_eye_12k.png` | 12288×6144 | 49,101,497 | Milky Way for the telescope |
| `dso/mw_lmc`, `mw_smc`, `mw_c99` | 1024×1024 | 648,823 · 424,887 · 1,042,279 | Large and Small Magellanic Clouds, C99 Milky Way patch |
| `dso/mw_m24` | 512×512 | 230,215 | M24 Milky Way patch |
| `galaxy_atlas.png` | 4096×2048 | 1,725,631 | Deep-sky sketch atlas (128×128 cells) |
| `art_atlas.png` | 4096×2816 | 1,851,167 | 85 constellation art images |
| `moon_map_small.png` · `moon_map_soft.png` | 256×128 | 49,961 · 9,735 | Moon map (low magnification) |
| `moon_map_1k.png` | 1024×512 | 710,851 | Moon map |
| `moon_map.png` | 2048×1024 | 2,830,157 | Moon map |
| `moon_map_4k.png` | 4096×2048 | 12,288,337 | Moon map |
| `phase_00`–`phase_15.png` | 512×512 | 16 images | Moon phase images (generated by the project) |
| `mars_map.png` | 2048×1024 | 2,888,551 | Mars surface |
| `jupiter.png` · `saturn.png` | 1024×1024 | 289,388 · 252,992 | Jupiter and Saturn disks |
| `saturn_ring.png` · `saturn_ring_front.png` | 2048×2048 | 4,301,582 · 3,944,601 | Saturn's rings |
| `grs.png` | 128×64 | 2,334 | Jupiter's Great Red Spot |

### 5.5 Expansion pack files (cheonsang-extra 1.0.1)

| Path (inside the expansion pack jar) | Count | Size (bytes, uncompressed) | Contents |
| --- | --- | --- | --- |
| `assets/cheonsang/pack/extra.json` | 1 | 50 | Pack descriptor (version 1.0.1) |
| `assets/cheonsang/pack/stars.json` | 1 | 64 | Star pack descriptor (version 1.1.0, bands 180) |
| `cheonsang_stars/index.bin` | 1 | 371,280 | Sky cell index, per-cell share of Milky Way starlight |
| `cheonsang_stars/cNNNNN.bin` | 41,252 | 192,460,965 | Per-cell star lists (main mod stars + Gaia DR3 V ≤ 15.2) |
| `assets/cheonsang/textures/sky/tiles/eye16/` | 128 | 47,260,279 | Naked-eye Milky Way 16K, 16384×8192 (16×8 tiles) |
| `assets/cheonsang/textures/sky/tiles/eye32/` | 512 | 160,749,343 | Naked-eye Milky Way 32K, 32768×16384 (32×16 tiles) |
| `assets/cheonsang/textures/sky/moon_map_8k.png` | 1 | 49,113,719 | Moon map 8192×4096 |
| `assets/cheonsang_extra/icon.png` | 1 | 2,199 | Expansion pack icon 128×128 |

The whole jar has 41,901 entries (including `fabric.mod.json`, `CREDITS.txt`, `LICENSE.txt`, and `.mcmeta`), 449,961,204 bytes uncompressed. There are 32,061,510 Gaia stars (to V 15.2).

### 5.6 Star data

#### 5.6.1 HYG Database v4.1

- Source: David Nash / astronexus, https://github.com/astronexus/HYG-Database. A star list that merges the Hipparcos, Yale Bright Star, and Gliese catalogs.
- License: CC BY-SA 4.0. Derived files are also shared under CC BY-SA 4.0.
- Original: `hygdata_v41.csv`, 33,932,548 bytes, 119,626 rows.
- Processing: `build_data.py` cuts the list at magnitude 9.0, converts it to a big-endian binary format (per star: right ascension and declination as float, magnitude×100 as short, B−V×1000 as short, HIP as int, 16 bytes), and sorts it by brightness. `make_star_info.py` writes distance, absolute magnitude, HD number, and spectral type to star_info.bin in the same order. star_meta.bin holds the constellation index, Bayer letter, superscript, and Flamsteed number. The Korean star names were written by this project and placed in star_names.json.
- Result: 83,479 HYG stars. With the WDS companions added, the final stars.bin has 90,938 stars (Section 5.3).
- Correction: Eta Carinae (η Car) appears in the source data at magnitude 6.21 and at a position 0.7′ off. On load it is corrected to magnitude 4.3 and the catalog position.
- Validation: altitude and azimuth of 4 stars at game times of tick 18000 and 13000 agreed with PyEphem within 0.1°.

#### 5.6.2 Tycho-2

- Source: Høg E. et al. (2000), A&A 355, L27. VizieR I/259 (doi:10.26093/cds/vizier.1259).
- License: CREDITS states no separate terms of use. The project follows source attribution and the VizieR usage notice.
- Original: 22 files `tycho2_full/tyc2.dat.*` (about 160MB, 2,539,913 stars).
- Processing: handled by `make_deep_stars.py`. Magnitudes use the Tycho conversion V = VT − 0.090(BT−VT), B−V = 0.850(BT−VT). Stars with a Gaia DR3 counterpart (cross-match table of 2,502,118 pairs) take the Gaia magnitude, color, and J2000 position, but keep the Tycho values if Gaia V and Tycho V differ by more than 0.75 mag. Stars that overlap HYG (V 9.8 or brighter, within 5″) are removed. The sky is divided into 180 declination bands of 1°, each band gets max(1, round(360·cos δ)) right ascension cells, giving 41,252 cells of about 1°², and stars are sorted by brightness within each cell.
- Result: deep_stars.bin (format DSB2), 2,409,260 stars, magnitude limit 15.37. It includes Gaia stars of open clusters and the Magellanic Clouds and has no synthetic stars.

#### 5.6.3 Gaia DR3

- Source: Gaia Collaboration, Vallenari et al. (2023), A&A 674, A1. ESA Gaia Archive, VizieR I/355/gaiadr3.
- License: CC BY-SA 3.0 IGO. Derived files are also shared under CC BY-SA 3.0 IGO, and the ESA/DPAC notice is included in CREDITS.
- Uses and originals:
  - Stars around 52 open clusters: `fetch_gaia_clusters.py`, G < 11.6, `gaia_open_clusters.tsv` 168,079 bytes.
  - Stars of the Large and Small Magellanic Clouds: `fetch_gaia_magellan.py`, G < 11.6, LMC radius 7°, SMC 3.75°, `gaia_magellan.tsv` 346,903 bytes.
  - Tycho-2 cross-match table: `A2_tycho2_gaia_xmatch.csv` (gaiadr3.tycho2tdsc_merge_best_neighbour), about 359MB.
  - All-sky stars (expansion pack): gaia_source G < 15.5, compressed VOTable `A1_*.vot.gz`, 53,860,707 rows. Converted to npz with `gaia_vot_to_npy.py` (about 1.7GB). All of `_data_raw/gaia` is about 4.4GB.
  - Starlight totals per HEALPix (level 5) cell: `A3_healpix5_gbin_flux.csv`, about 14MB.
- Processing: shared conversion formulas are in `gaia_conv.py`. G→V uses the cubic polynomial of Riello et al. (2021), A&A 649, A3: G−V = −0.02704 + 0.01424x − 0.2156x² + 0.01426x³ (x = BP−RP; outside −0.5–5.0 the boundary value is used). BP−RP→B−V uses a table (`bv_table.json`) built from medians at 0.1 steps over Tycho-2 cross-matched stars. Positions are moved from J2016.0 to J2000.0 with linear proper motion. If BP−RP is missing, V = G + 0.1 and B−V = 0.65.
- Expansion pack processing (`make_gaia_pack.py`, `make_extra_pack.py`): only stars with 9.0 < V ≤ 15.2 are kept, and HYG stars (V 9.8 or brighter, within 5″) and main mod stars (within 2″, magnitude difference under 0.75) are removed. The cells are the same 41,252 cells as the main mod, and each cell holds the main mod stars and the remaining Gaia stars sorted by brightness. Each star takes 6 bytes (position within the cell 16 bits × 2, (V−6)×25 in 8 bits, (B−V+0.5)×80 in 8 bits), and each cell is compressed with zlib. index.bin holds each cell's share of Milky Way starlight (limiting V 11.5–15.5 at 0.5 mag steps).
- Result: of the 153,821 stars in faint_stars.bin, the Gaia stars are 1,609 open cluster stars and about 3,150 Magellanic Cloud stars; the rest are synthetic background stars. This file is not read when deep_stars.bin is present, and the cluster and Magellanic Gaia stars are also in deep_stars.bin. The expansion pack's cheonsang_stars/ has 41,253 files (41,252 cells + index.bin), 192,832,245 bytes, and 32,061,510 Gaia stars.
- Validation: residuals of the G→V conversion (Tycho V < 10.5, 13 color bins) are −0.009–+0.031 mag for the bin medians, with MAD 0.014–0.052 mag. The J2000 position difference between Gaia and Tycho-2 is 0.08″ median and 0.58″ at the 99th percentile. The pack cells passed a decode check. With the pack installed, the share of starlight subtracted from the Milky Way averages 31% at limiting magnitude 13 and 67% at 15.

#### 5.6.4 WDS close double stars

- Source: Mason B.D. et al. (2001), AJ 122, 3466, The Washington Visual Double Star Catalog. VizieR B/wds.
- License: CREDITS states no separate terms of use. The project follows source attribution.
- Original: `wds/wds.tsv`, about 24MB, 158,047 rows (including comments).
- Processing (`make_wds.py`): uses only rows whose component field is empty, two capital letters (AB, CD, etc.), or of the form `A,BC`, with the primary at magnitude 9.5 or brighter, the companion at 13 or brighter, and a most recent separation of 0.5–30″. A match to a HYG star requires a position within 15″ of the WDS position (plus proper motion × 30 years) and a magnitude within 1 mag of the primary or combined magnitude. If HYG has the combined light, it is split into component magnitudes. The companion position is computed from the primary position with the position angle and separation by spherical trigonometry. A companion is not added if it already exists in HYG (within separation + 8″) or in deep_stars (within 2.5″). If an existing HYG companion differs from the most recent WDS value by more than 1″, it is moved to the WDS position. The original star list is kept in `_data_raw/stars_base_1.7/` and the output is rebuilt from that original every time.
- Result: 7,459 added companions in stars.bin (measured as the difference between the total star count and the number of stars with HIP 0). 105 HYG companions moved.
- Validation: Castor 1.93 + 2.85 mag, Porrima 3.47 + 3.52 mag, Mizar 2.23 + 3.95 mag, and Almach 2.19 + 4.9 mag are included without duplicates. Separations of Sirius B 11.1″, Castor 5.4″, Porrima 3.4″, Mizar 14.4″, Acrux 3.5″, and Rigil Kentaurus 8.1″ match the most recent WDS values.

#### 5.6.5 GCVS variable stars, SIMBAD

- GCVS: Samus et al. (2017), Astronomy Reports 61, 80, VizieR B/gcvs. Used only for light curve parameters of Algol (period 2.8673 days, magnitude 2.1–3.4) and Mira (magnitude 3.4–9.3). No data is packaged as a file.
- 18 well-known stars (famous_stars.json): positions, magnitudes, and distances are from HYG v4.1. Tabby's Star (KIC 8462852) and UY Scuti, which are not in HYG, use SIMBAD ICRS coordinates. The one-line descriptions (Korean and English) were written by this project and are CC BY-SA 4.0.

### 5.7 Constellation data

#### 5.7.1 d3-celestial constellation lines and boundaries

- Source: Olaf Frohn, https://github.com/ofrohn/d3-celestial. BSD 3-Clause License (full text at the end of CREDITS).
- Original: `constellations.json` 50,581 bytes, `constellations.lines.json` 27,136 bytes, `constellations.bounds.json` 40,714 bytes.
- Processing: `build_data.py` builds constellations.json from the lines and name positions and adds Korean names. `make_bounds.py` keeps each edge shared by neighboring constellations only once and subdivides the boundaries at 1° intervals so they follow lines of right ascension and declination.
- Result: constellations.json with 89 entries (88 constellations, Serpens split into Caput and Cauda), bounds.json with 104 line groups.

#### 5.7.2 Stellarium "modern" constellation art

- Source: Stellarium sky culture "modern", art by Johan Meuris. The art is under the Free Art License 1.3 and the data under CC BY-SA 4.0.
- Original: `stellarium_art/` (about 2.4MB).
- Processing: `make_art.py` packs the 85 images into one image 4096 px wide and records 3 anchor stars (HYG coordinates) per image in art.json. The images themselves were not modified.
- Result: art_atlas.png 4096×2816, art.json with 85 entries.

#### 5.7.3 Cheonsang Yeolcha Bunyajido asterisms

- Source: Stellarium sky culture "chinese" (traditional Chinese asterisms, "Text and line: CC BY-SA", no version given). The Unicode Unihan Database (Unihan_Readings, Unihan_Variants, Unicode License) was used to check the Hanja readings. Unihan data is not included in the release.
- Original: `stellarium_chinese_index.json` 240,478 bytes (318 asterisms), `unihan/` about 9.2MB, and a list in the Bocheonga system compiled by this project, `cheonsang_list_20261114.md` (302 asterism entries).
- Processing: `make_korean_sky2.py` selects only the asterisms in the list and matches them to the Stellarium lines by Hanja name (compared after simplified/traditional conversion). The Korean names are checked against the Unihan kHangul readings and follow the Hanja reading when they differ (the list spelling is kept if the only difference is the initial-sound rule). Star positions use HYG v4.1 J2000 coordinates looked up by HIP number. Romanized names for the English display follow the Revised Romanization of Korean (with liaison, nasalization, and lateralization applied).
- Result: korean_constellations.json with 289 asterisms: the 283 asterisms of the Bocheonga plus Cheonchang (天槍), Taejon (太尊), and Togongri (土公吏). The 23 asterisms near the south celestial pole added in the Ming dynasty and the 5 asterisms with no data matching them to modern stars (Cheonjik, Cheonmyo, Donggu, Gunmun, Gibu) are excluded.
- Validation (`korean_sky_report.md`, the 1.9.0 Cheonsang Yeolcha validation report): 7 names were corrected (Cheongu→Cheonju, Hwangwan→Hwanja, Cheoljil→Bujil, Chuho→Chugo, Jeoksigi→Jeoksi, 天紀→天記, Jugwi→Jugi). 6 asterisms differ from the list by more than 3 stars (Gugam, Sibiguk, Oeju, Eonyeo, Ik, Jin). 3 HIP numbers are not in HYG (55203, 78727, 115125). Entries listed twice were included only once.

### 5.8 Deep-sky data

#### 5.8.1 OpenNGC, Messier and Caldwell

- Source: OpenNGC, Mattia Verga, CC BY-SA 4.0. Caldwell numbers, distances, and some galaxy morphologies are from Wikipedia ("Messier object", "Caldwell catalogue", articles on individual galaxies), CC BY-SA 4.0.
- Original: `NGC.csv` 3,942,946 bytes (13,970 rows), `addendum.csv` 17,496 bytes, `messier_wiki.txt` 63,352 bytes, `caldwell_wiki.txt` 34,646 bytes.
- Processing:
  - `build_data.py`: 110 Messier, 109 Caldwell, and the 2 Magellanic Clouds. M102 is taken as NGC 5866, and C9, C41, and C99 are entered by hand. Leftover wiki markup is removed.
  - `make_dso_dist.py`: distances from Wikipedia tables. `make_galaxy_types.py`: de Vaucouleurs morphology for 77 galaxies.
  - `add_magellan_dso.py`: 69 nebulae and clusters within 6° of the LMC center or 3° of the SMC center, with V (B if V is missing) of 11.0 or brighter.
  - `add_galaxies_110.py`: 264 OpenNGC Type G galaxies with V (B if V is missing) of 11.0 or brighter. Hubble types use the OpenNGC values. For radial velocities above 500 km/s, distance is v/70 Mpc.
- Result: dso.json with 554 objects, including 110 Messier and 109 Caldwell. By type: 341 galaxies, 83 open clusters, 67 globular clusters, 43 emission and reflection nebulae, 17 planetary nebulae, 3 other. dso_dist.json has 531 entries and dso_morph.json 340.

#### 5.8.2 DSS eyepiece sketch atlas

- Source: Digitized Sky Surveys, STScI (NASA grant NAG W-2166). Plates from POSS-II (Caltech), POSS-I (National Geographic Society–Palomar), and UK Schmidt (AAO). Obtained via MAST through NASA SkyView.
- License: CC BY-NC-SA 4.0 (https://archive.stsci.edu/dss/copyright.html). Commercial use is prohibited, and the derived galaxy_atlas.png is also CC BY-NC-SA 4.0.
- Original: 466 FITS files in `dss_galaxies/` (about 73MB, `fetch_galaxy_dss.py`, square of major axis ×1.9, 192 px). Additional plates for nebulae in `neb_extra/` (about 55MB, `fetch_neb_extra.py`, major axis ×3.8, DSS2 Red, DSS2 Blue, DSS1 Red).
- Processing (`make_galaxy_atlas.py`): each object gets a 128×128 cell. Plate density D is converted to brightness with I = 10^((D−background)/γ) − 1, γ = (brightest value − background)/1.3, and stored as a log value. Star grains (points 5σ brighter than the 7 px median) are removed, and noise below 3σ is set to 0. Nebulae are composited as DSS2 Blue 0.65 + Red 0.35; if the shape correlation between Blue and Red is below 0.5, DSS1 Red is mixed in half and half. For saturated bright HII regions (M42, M43, M8, M17, M20, C103, C92), the central brightness is restored from depth. Globular clusters are drawn as smooth disks with a King profile (tidal radius 1.1R, exponent 1.3). Core radii for 13 clusters use the measurements of Harris (1996, 2010 edition) (0.035–0.3R); the rest use 0.12R. M57 is drawn with a ring formula (hole 35%, ring 0.62R) because of plate saturation.
- Result: galaxy_atlas.png 4096×2048 grayscale, 462 cells (339 galaxies, 123 nebulae, planetary nebulae, and globular clusters). The 2 galaxies without photographs (Large and Small Magellanic Clouds) are drawn with the morphology model.
- Validation: at cells with multiples of 32, the shader interpolating the cell index read 64 as 63.999 and produced horizontal stripes, so the cell index is rounded to an integer when read.

### 5.9 Milky Way data

- Source: NASA/Goddard Space Flight Center Scientific Visualization Studio, Deep Star Maps 2020 (https://svs.gsfc.nasa.gov/4851). An all-sky map computed from about 1.7 billion stars in Hipparcos-2, Tycho-2, and Gaia DR2. As a NASA image, it has no copyright in the United States.
- Original: linear EXR `milkyway_2020_4k.exr` 36,436,668 bytes, `milkyway_2020_8k.exr` 137,307,727 bytes, `milkyway_2020_32k.exr` 1,509,917,679 bytes.
- Processing scripts: `make_milkyway.py` (4K), `make_milkyway_real.py` (color removal for the naked-eye version), `make_milkyway_8k.py` (8K), `make_base_160.py` (default quality), `make_mw_12k.py` (12K), `make_mw_patches.py` (LMC, SMC, M24, and C99 patches), `make_ultra_161.py` (16K and 32K tiles). The shared tone mapping subtracts a 35% floor, maps the 99.8% value to 1, and applies gamma 1/1.6. The naked-eye version uses 20% saturation, half contrast against a large blur, and gray. In the 32K tiles, points left at the positions of stars that Cheonsang draws (HYG to magnitude 9.5) are filled with the surrounding median, and each tile has a 2 px margin on all sides. All resolutions share the floor and ceiling values derived from the 8K original, so brightness stays continuous when the magnification level changes.
- Result: main mod textures are listed in Section 5.4 and expansion pack tiles in Section 5.5.
- Handling of duplicated starlight: with the expansion pack installed, the light of the newly drawn Gaia stars is computed per HEALPix cell and subtracted from the Milky Way image (Section 6.2).

### 5.10 Moon data

#### 5.10.1 LROC textures

- Source: NASA's Scientific Visualization Studio, CGI Moon Kit (LRO data), https://svs.gsfc.nasa.gov/4720. As a NASA image, it has no copyright in the United States.
- Original: `lroc_color_2k.jpg` 457,942 bytes, `lroc_color_poles_4k.tif` 13,095,388 bytes, `lroc_color_poles_8k.tif` 50,641,970 bytes.
- Processing: `make_moon_map.py` (2048×1024), `make_base_160.py` (4096, 2048, 1024, and 256 px levels from the 4K original), `make_ultra_161.py` and `make_extra_pack.py` (8K).
- Result: 4 levels of moon_map plus the low-magnification maps in the main mod (Section 5.4), and moon_map_8k.png in the expansion pack (Section 5.5).

#### 5.10.2 Lunar feature names

- Source: Gazetteer of Planetary Nomenclature, IAU WGPSN / USGS Astrogeology Science Center. A U.S. government work in the public domain.
- Original: `MOON_nomenclature_center_pts.zip` 23,845,070 bytes (downloaded 2026-11-11).
- Processing (`make_moon_names.py`): within the near side plus the limb visible through libration (longitude ±100°), selects all maria, oceans, lakes, bays, and marshes, craters of 20 km diameter or more (excluding satellite craters), and mountains, ranges, valleys, scarps, and promontories of 50 km or more. The Korean names were written by this project.
- Result: moon_names.json with 787 features (121 with Korean names).

### 5.11 Planet and small body data

#### 5.11.1 Planet textures

- Source: Solar System Scope (INOVE), https://www.solarsystemscope.com/textures/, CC BY 4.0 (based on NASA data).
- Original: `8k_mars.jpg` 8,396,951 bytes, `8k_jupiter.jpg` 3,080,980 bytes, `8k_saturn.jpg` 1,098,204 bytes, `8k_saturn_ring_alpha.png` 64,790 bytes.
- Processing (`make_base_160.py`): Mars is reduced to 2048×1024. For Jupiter and Saturn, averaging along longitude keeps only the band colors by latitude, and the disks are drawn with reduced contrast and saturation (the Great Red Spot is drawn separately at its longitude for the current time). Saturn's rings are drawn face-on from the actual radial profile (C ring, B ring, Cassini Division, A ring, Encke Gap) scaled in units of Saturn's radius.
- Result: mars_map.png, jupiter.png, saturn.png, saturn_ring.png, saturn_ring_front.png (Section 5.4). The phase images phase_00–15 were generated by the project with `make_planet_textures.py` and use no external material.

#### 5.11.2 JPL Horizons

- Source: NASA/JPL Horizons System, https://ssd.jpl.nasa.gov/horizons/. U.S. government data.
- Original (`fetch_horizons.py`): 7 moons of Saturn (Titan 606, Rhea 605, Mimas 601, Enceladus 602, Tethys 603, Dione 604, Iapetus 608), Saturn-centered ICRF equatorial, 1900–2050 at 1-day steps, about 4.77MB per file. Ceres (1) and Vesta (4), heliocentric J2000 ecliptic, 1900–2050 at 4-day steps, about 1.19MB per file.
- Processing: `fit_saturn_moons.py` (Titan and Rhea) and `fit_saturn_moons2.py` (the other 5) fit orbit formulas with mean longitude, eccentricity terms, resonant libration, and out-of-plane height, and only the fitted coefficients go into the code. `make_asteroids.py` writes the Ceres and Vesta position tables to asteroids.bin.
- Result: asteroids.bin (2 bodies, 4-day step). The raw Saturn moon data is not included in the release.
- Validation: Titan mean 0.73″ and max 1.53″, Rhea mean 0.29″ and max 0.50″ (`saturn_moons_fit.txt`). Mimas 0.66″, Enceladus 0.07″, Tethys 0.52″, Dione 0.02″, Iapetus 1.02″ (mean; max 2.6″). Results for the Mars central meridian longitude and Jupiter System III are in Section 4.6.

#### 5.11.3 VSOP87

- Source: Bretagnon P., Francou G. (1988), A&A 202, 309, IMCCE. Coefficients were read from the VSOP87D tables included in the PyMeeus package. CREDITS lists it under the METHODS section.
- Original: the full VSOP87D coefficients in PyMeeus (Mercury through Neptune, and Earth).
- Processing (`make_vsop87.py`): keeps only terms with amplitude of at least 3×10⁻⁶ rad and generates Vsop87Data.kt. Results referred to the ecliptic and equinox of date are converted to the J2000 ecliptic, and light-time correction is applied. Earth's position also comes from VSOP87.
- Result: 1,259 terms, Vsop87Data.kt 46,744 bytes. One computation for all 7 planets takes 28 µs.
- Validation (`vsop_check.py`, against PyEphem): maximum errors for 1800–2050 are as in the table in Section 4.6. Over the full range from 1000 BCE to 3000 CE, the maximum is about 2′.

#### 5.11.4 Lunar position coefficients

- Source: J. Meeus, Astronomical Algorithms 2nd ed., Chapter 47, Tables 47.A and 47.B. Coefficients were read from `moonposition.js` in the astronomia library (MIT, 8,325 bytes).
- Processing: `gen_moon_terms.py` generates MoonTerms.kt (60 terms for longitude and distance, 60 terms for latitude), and `astro_check.py` reads the same tables directly to validate them.
- Validation: against PyEphem at 400 epochs, the Moon's maximum error is 30.0″ and distance maximum 9.8 km.

### 5.12 Meteor showers, aurora, and computational references

- IMO meteor showers: uses the solar longitude of maximum, radiant, ZHR, and velocity (factual data) from the International Meteor Organization calendar. `build_data.py` builds meteors.json. There are 7 showers (Quadrantids, Lyrids, Eta Aquariids, Perseids, Orionids, Leonids, Geminids) plus sporadic meteors at 10 per hour.
- Aurora: no external data is used. The occurrence latitude (|φ| > 60°, maximum at 67–75°), the 80–320 km altitude layer, and the 557.7 nm and 630 nm emission colors are computed by a shader model, and the activity level for the day is set by a random number.
- JUPOS: uses the Great Red Spot longitude trend (factual values only).
- References for computational methods (no data copied): Meeus, Krisciunas & Schaefer (1991), Schaefer (1990), Kasten & Young (1989), Sæmundsson (1986), Bennett (1982), IAU WGCCRE (Archinal et al. 2011/2018), Espenak and Meeus ΔT polynomials.

### 5.13 Quality validation summary

| Item | Method | Result |
| --- | --- | --- |
| Batch validation of astronomical computation | `verify_all.py` (astro_check 41, planet_check 24, eclipse_check 11, eclipse_lat_check 6) | 82 checks passed (run before the switch of planets to VSOP87) |
| Planet positions | `vsop_check.py`, PyEphem 1800–2050 | Max 2.4″–12.6″ |
| Sun and Moon positions | `astro_check.py`, PyEphem 400 epochs (1900–2100) | Sun max 43.7″, Moon max 30.0″, distance max 9.8 km |
| G→V conversion | Comparison with Tycho-2 cross-matched stars | Bin medians −0.009–+0.031 mag |
| Gaia–Tycho positions | J2000 position difference | Median 0.08″, 99% 0.58″ |
| WDS double stars | Separations of representative pairs compared with the most recent WDS values | Match (Sirius B 11.1″, etc.) |
| Saturn moon orbit formulas | Checked against Horizons 1900–2050 | Mean 0.02″–1.02″, max 2.6″ |
| Solar and lunar eclipses | Checked against NASA data | 2026-08-12 total solar eclipse greatest point within 0.1°, 2028-12-31 total lunar eclipse 6 contacts ±1 min |
| Cheonsang Yeolcha Bunyajido | Three-way comparison of the list, Stellarium, and Unihan | 289 asterisms, 7 names corrected, 6 star count differences recorded |
| Licenses | CREDITS.txt compared with the original license pages | DSS non-commercial condition confirmed |

The known data limitations are as follows. Stars use J2000 coordinates with precession only and do not include position changes from nutation, aberration, or proper motion, so they differ from Stellarium by about 1′. The Moon excludes physical libration and nutation, with an error of around 0.04°. Ceres, Vesta, and Saturn's moons are fitted to data for 1900–2050. faint_stars.bin is included in the main mod but is not read because deep_stars.bin is present.

### 5.14 License summary

| Material | License | Commercial use | Conditions |
| --- | --- | --- | --- |
| Mod source code | MIT | Allowed | Copyright notice |
| Original textures and icons, Korean names | CC BY-SA 4.0 | Allowed | Attribution, ShareAlike |
| HYG v4.1 and derived star files | CC BY-SA 4.0 | Allowed | Attribution, ShareAlike |
| Gaia DR3 and derived files, expansion pack stars | CC BY-SA 3.0 IGO | Allowed | ESA/DPAC notice, ShareAlike |
| Tycho-2, WDS, GCVS (VizieR) | Not stated in CREDITS | Not stated | Attribution, VizieR notice |
| 2 SIMBAD coordinates | Not stated in CREDITS | Not stated | SIMBAD notice |
| d3-celestial | BSD 3-Clause | Allowed | Copyright notice, full license text |
| Stellarium "modern" art | Free Art License 1.3 | Allowed | Same license |
| Stellarium "chinese" lines and names | CC BY-SA (version not stated) | Allowed | ShareAlike (derived file CC BY-SA 4.0) |
| Unicode Unihan | Unicode License | Not applicable | Used for validation only, not included |
| OpenNGC | CC BY-SA 4.0 | Allowed | Attribution, ShareAlike |
| Wikipedia (Caldwell numbers, distances, morphology) | CC BY-SA 4.0 | Allowed | Attribution, ShareAlike |
| DSS sketch atlas (galaxy_atlas.png) | CC BY-NC-SA 4.0 | Not allowed | Non-commercial, attribution, ShareAlike |
| Harris globular cluster catalog | Measurements cited | Allowed | Citation |
| NASA SVS Milky Way and Moon maps | NASA media guidelines (no U.S. copyright) | Allowed | Attribution |
| IAU WGPSN / USGS lunar names | Public domain | Allowed | None |
| Solar System Scope textures | CC BY 4.0 | Allowed | Attribution |
| JPL Horizons | U.S. government data | Allowed | None |
| IMO meteor showers, JUPOS | Factual data | Allowed | Attribution |
| astronomia moonposition.js (lunar coefficients) | MIT | Allowed | Copyright notice |
| VSOP87D coefficients (as included in PyMeeus) | CREDITS METHODS section (classified as a method) | Not stated | Citation |

The release jar is a collection, and each file follows the license of its source. ShareAlike licenses apply only to files made from the corresponding material. The main mod contains one file under a non-commercial condition (galaxy_atlas.png), so the main mod jar as a whole cannot be redistributed commercially. The expansion pack contains no files under a non-commercial condition. The license summary at the top of CREDITS.txt (LICENSING AT A GLANCE) lists the CC BY-NC-SA 4.0 condition of the DSS sketch atlas as a separate item.

---

## 6. Rendering

### 6.1 Stars

Stars are drawn as 4-vertex quads and filled as Gaussian points in the fragment shader (`stars.vsh`, `stars.fsh`). The vertex shader computes the following for each star.

* Refraction: moves the vertex toward the zenith with the Sæmundsson formula of Section 4.10
* Extinction: m ← m + k(X − 1) with airmass X
* Opacity: α = clamp((limit + 0.5 − m)/5, 0, 1)^1.4
* Size: the desired size 0.22 · 10^(−0.1(m − limit)) is converted to screen pixels, with a minimum radius of 1.5 px. Intensity is raised by the amount the size falls below the minimum radius, so total brightness is preserved
* Telescope star image: the minimum radius includes the diffraction and seeing size (Section 6.4)
* Horizon color: reddening by the ratio R 0.09, V 0.172, B 0.30 mag/airmass, clamped at airmass 10
* Twinkling: clamped at airmass 3, up to ±15% toward the horizon, 0 at high magnification
* Lunar occultation: stars inside the Moon's disk are not drawn

Star color is the blackbody color at the effective temperature converted from B−V with the Ballesteros (2012) formula. Stars much brighter than the limiting magnitude get higher saturation (up to 0.55).

Telescope stars are taken from sky cells near the field of view in order of brightness, up to 150,000, and built into a buffer (`DeepStars`). Gaia cells are read by one background thread, and cells not yet read are drawn with the main mod's cells instead.

### 6.2 Milky Way

| Item | Specification |
| --- | --- |
| Original | NASA SVS Deep Star Maps 2020 (about 1.7 billion stars from Hipparcos-2, Tycho-2, and Gaia DR2; linear EXR) |
| Main mod | Naked eye 4096×2048, telescope 12288×6144. Grayscale images are single-channel R8 textures |
| Expansion pack | 16K (16×8) and 32K (32×16) tiles, 1024 px per tile + 2 px border |
| Tile streaming | Read on a 3-thread pool, up to 4 tiles uploaded per frame, up to 12 queued, 0.3-second crossfade. Over budget, the tiles not viewed for the longest time are unloaded first |
| Interpolation | Bicubic (Mitchell) + dithering shader (`milky.fsh`) |
| Expansion pack starlight subtraction | From Gaia starlight totals over 12,288 HEALPix cells × 0.5 mag steps, each cell's share of "light already drawn as stars" is computed and subtracted from the Milky Way brightness. Mean share: 31% at limiting magnitude 13, 67% at 15 |
| Magnification dimming | (exit pupil ratio)^0.33 × (1 − share resolved into stars), minimum 0.10 |

### 6.3 Sun, Moon, and planets

* Sun: drawn at its apparent radius, with vertical compression from refraction. The corona region is shown during a total solar eclipse (`sun.fsh`).
* Moon: phase, earthshine, and optical libration are applied in the shader (`moon.fsh`). Maps of 256 → 1K → 2K → 4K → 8K (expansion pack) are used according to the magnification level.
* Mars: a 2048×1024 surface map rotated with the IAU rotation model.
* Jupiter: banded disk + Great Red Spot (System II longitude).
* Saturn: disk + two ring images (front and back), deformed into an ellipse by the tilt B.
* Planets, moons, and asteroids that appear small: drawn as points with the same shader as stars.

### 6.4 Telescope optics model

| Item | Values and formulas |
| --- | --- |
| Aperture D | 50, 60, 70, 80, 90, 102, 114, 127, 150, 200, 254, 305mm (12 steps, default 80mm) |
| Eyepiece f | 40, 32, 25, 20, 15, 12, 9, 7, 5, 4, 3mm (11 steps) |
| Focal ratio | Fixed at f/7, objective focal length F = 7D |
| Magnification | M = F / f, up to ×712 (305mm with 3mm) |
| Effective aperture | D_eff = min(D, 7mm × M) |
| Point source gain | Δm = 5 log₁₀(D_eff / 7mm) + 0.43 (equal to 7.7 + 5 log₁₀(D/cm) for a naked-eye limit of 6.5) |
| Limiting magnitude (naked-eye 6.0 baseline) | 80mm 11.7, 305mm 14.6 |
| Limiting magnitude cap | Main mod 13.0, expansion pack 15.2 |
| Exit pupil | D / M, pupil 7mm |
| Extended source brightness ratio | min(1, (D/M/7)²) |
| Star image radius | r = 0.6 √((138″/D)² + (1.5″)²); first Airy minimum 138″/D(mm), seeing 1.5″ |
| Reference maximum magnification | 2D (magnification is not limited beyond it; star images grow and blur) |
| Integrated magnitude threshold for extended sources | 5.5 + Δm (fades out gradually over +0.5) |

The lower left of the telescope view shows aperture, focal length, eyepiece, and magnification on one line (`TelescopeHud`). The default placement is 6 px from the left and 6 px above the bottom in GUI coordinates, at a text scale of 0.75. To avoid overlapping the hotbar, the placement is chosen in the following order.

| Step | Condition | Placement |
| --- | --- | --- |
| Free width on the left | room = screen width/2 − 91 − 29 − 4 − 6 | 91 is half the hotbar width of 182, 29 is the offhand slot, 4 and 6 are margins |
| ① | line width × 0.75 ≤ room | Scale 0.75, 6 px above the bottom |
| ② | line width × 0.75 > room, line width × 0.5 ≤ room | Scale reduced to room / line width (minimum 0.5), 6 px above the bottom |
| ③ | line width × 0.5 > room | Scale 0.75 kept, raised to 64 px above the bottom (above the health and armor bars) |

### 6.5 Deep-sky sketches

| Item | Specification |
| --- | --- |
| Atlas | `galaxy_atlas.png` 4096×2048 grayscale, 128×128 cell per object, 462 cells used (339 galaxies, 123 nebulae and clusters). 8 MiB as R8 on the GPU |
| Plate density → brightness | I = 10^((D − background)/γ) − 1, γ = (maximum density − background)/1.3 |
| Star removal | 7 px median 5σ, noise below 3σ set to 0, attenuation outside the D25 ellipse. 2 px blur for nebulae, 3 px for globular clusters |
| Nebula compositing | DSS2 blue 0.65 + red 0.35. If the shape correlation between blue and red is below 0.5, half and half with DSS1 red |
| Galaxies without photographs | 7 morphologies (elliptical, lenticular, Sa, Sb, Sc, Sd, irregular) + bar: Sérsic (n = 2) bulge + exponential disk + two-armed logarithmic spiral + dust lane for edge-on spirals |
| Globular clusters | King profile (tidal radius 1.1R, exponent 1.3). Core radii for 13 clusters from the Harris catalog (0.035–0.3R), others 0.12R |
| Planetary nebulae | Brightness exponent 1.3 (M27 2.4). M57 uses the ring formula (hole 35%, ring 0.62R) |
| Display threshold | The integrated magnitude is spread over the whole shape to find the peak surface brightness, which is compared with the thresholds of Sections 6.4 and 4.12. Only the parts brighter than the threshold are drawn in gray |
| Structure threshold | Arms, bars, and dust lanes only where brightness is at least 2–8 times the threshold. The structure threshold is lowered by 0.3 as the on-screen size goes from 150 to 900 px |

### 6.6 Aurora

| Item | Specification |
| --- | --- |
| Conditions | Absolute latitude above 60°, solar altitude below −10°, solar activity for the day (random) > 0.4. Strongest at 67–75°, weakened by moonlight |
| Oval position | Latitude 69° − 3° × intensity |
| Rendering | Volumetric ray marching in the fragment shader (`aurora.vsh`, `aurora.fsh`): layer at 80–320 km altitude, including Earth's curvature |
| Structure | 3 curtains (5 km thick, folds of ±125 km), 64 steps per curtain + 12 steps for a broad diffuse glow |
| Color | 557.7 nm green below, 630 nm red above |
| Brightness | Side-on cap about 4 times the face-on value, compression 1 − e^(−1.4k) |
| Noise | Integer hash (same pattern regardless of graphics card) |

### 6.7 Meteors

| Shower | Solar longitude of maximum | ZHR | σ (°) |
| --- | --- | --- | --- |
| Quadrantids QUA | 283.15° | 110 | 0.6 |
| Lyrids LYR | 32.32° | 18 | 1.0 |
| Eta Aquariids ETA | 45.5° | 50 | 4.0 |
| Perseids PER | 140.0° | 100 | 2.5 |
| Orionids ORI | 208.0° | 20 | 4.0 |
| Leonids LEO | 235.27° | 15 | 1.5 |
| Geminids GEM | 262.2° | 150 | 1.5 |

Activity is ZHR × exp(−½(Δλ/σ)²) × sin(radiant altitude) + 10 sporadic meteors per hour. To fit the length of a game night, 450 per hour is converted to 1 per second.

Meteors run on the computer clock. `System.currentTimeMillis()` divided by 50 (20 per second) is used as the tick unit, which is divided into slots of 4 ticks (0.2 seconds), and a random number is generated from the slot number. Each slot decides whether a meteor occurs, which shower it belongs to, its starting altitude (20–75°) and azimuth, duration, length, and brightness. Since a meteor lives at most 1 second, only the last 6 slots are checked. Meteors therefore run in real time regardless of the sky time compression rate or pauses, and computers with the same clock see the same meteor at the same moment.

---

## 7. Performance

### 7.1 Load control by design

| Feature | Method |
| --- | --- |
| Telescope stars | Index of 41,252 sky cells, only cells near the field of view selected, up to 150,000 stars |
| Gaia cell cache | 1 background thread, cache cap of 3 million stars (14 bytes per star in memory, about 42MB). When exceeded, the least recently used cells are evicted until 3/4 remains |
| GPU memory budget | 1/4 of graphics card memory, minimum 256MB (`GL_NVX_gpu_memory_info`, `GL_ATI_meminfo`). Tiles : photos = 4 : 3. 512MB + 384MB if memory cannot be read |
| Large textures | Uploaded in advance when the telescope is held, released after 2 minutes without use |
| Planets | Two epochs 1 second apart computed + interpolation, 28 µs per pass over 7 planets |
| Info card | 150 ms period |
| Light pollution | 289 points read 40 per tick, recomputed every 2 seconds or after an 8-block move |
| Aurora and sketches | Computed in the fragment shader, not drawn when conditions are not met |
| `/cwhen` | 1 separate thread, a new request cancels the previous computation. 1–150ms (eclipses far in the future take longer) |
| Constellation lines and names | Previous results reused when camera and sky are unchanged, only near the field of view |

### 7.2 Measured results (`/cbench`)

`/cbench` runs at night with a telescope held. It measures 5 scenes, each for 10 seconds after 2 seconds of settling, and saves the results and a screenshot per scene in `cheonsang_bench/date_time/` in the game folder.

The test environment was Intel Core i7-1255U (12 threads), NVIDIA GeForce RTX 2050 (driver 610.91), 15.7GB system memory, Java max 4096MB, 1920×1080, render distance 16, max FPS 260, VSync off. Measured on 2026-11-08.

| Scene | Avg FPS (photo mode / naked-eye view) | 1% low FPS | Longest frame |
| --- | --- | --- | --- |
| Ground (baseline) | 357.0 / 362.0 | 202.0 / 197.5 | 8.3 / 8.4ms |
| Naked-eye sky | 721.1 / 726.3 | 321.2 / 313.6 | 9.9 / 6.6ms |
| Telescope ×7 Milky Way | 534.8 / 246.2 | 244.8 / 128.4 | 8.8 / 15.0ms |
| Telescope ×75 M31 | 359.0 / 250.4 | 198.4 / 158.6 | 10.7 / 11.1ms |
| Telescope ×300 Jupiter | 281.2 / 380.5 | 151.5 / 189.7 | 11.1 / 11.8ms |

Java memory use was 733–992MB. This measurement was made on a build before the telescope optics model, Gaia stars, deep-sky sketches, and shader aurora were added, and there are no values remeasured with the current configuration. The performance targets are set at an average of 200 FPS or more per scene, 1% low of 120 FPS or more, and a graphics memory increase of 300MB or less.

---

## 8. Commands, keys, and settings

### 8.1 Server command (`/cele`)

| Command | Permission | Action |
| --- | --- | --- |
| `/cele` | Anyone | Sky date and time, compression rate, world default location, rise and set of the Sun and Moon |
| `/cele set <year> <month> <day> <hour> <minute> <latitude> <longitude>` | OP (game master) | Sky date, time (local mean time), and world default location. Trailing arguments can be omitted, `~` keeps the current value, and a city name can go in the latitude position. Years −2999–2999 |
| `/cele set today` | OP | Sets today's date (time and location kept) |
| `/cele rate <0.01~1000>` | OP | Compression rate (1 = real speed) |
| `/cele reset` | OP | Defaults (2026-08-11 epoch, 0°, 0°) |
| `/cele realtime [city]` · `/cele realtime off` | OP | Real-time mode (default seoul), corrected to real-world time every 5 seconds (100 ticks). set, rate, and reset are blocked while it is on |

The 13 real-time cities are longyearbyen, reykjavik, london, newyork, seoul, cairo, hongkong, singapore, lima, sydney, buenosaires, ushuaia, and mcmurdo.

### 8.2 Client commands

| Command | Action |
| --- | --- |
| `/cplace` · `/cplace <latitude> <longitude>` · `/cplace <city>` · `/cplace reset` | Show, set, or clear the personal observing location (your own sky only) |
| `/cfind <name> [true\|false]` | Find an object (altitude and azimuth, direction arrow for 60 seconds). `true`: turns the view to it + locks telescope tracking |
| `/cwhen` | What to see tonight |
| `/cwhen <object>` | Best night (range: fixed objects 1 year; planets, asteroids, and moons 3 years; Moon 60 days; lunar features 120 days) |
| `/cwhen solar total\|annular\|partial` | Next solar eclipse (within 60 years) |
| `/cwhen lunar total\|partial\|penumbral` | Next lunar eclipse |
| `/cwhen meteor [QUA\|LYR\|ETA\|PER\|ORI\|LEO\|GEM]` | Next meteor shower maximum |
| `/cwhen full` · `new` · `conj` · `next` | Next full moon, new moon, planetary conjunction, list of upcoming events |
| `/clog` · `/clog list` | Open the observing notebook, logbook summary |
| `/cbench` · `/cbench stop` | Performance benchmark |
| `/cvis` · `/cvis <effect> on\|off` | Status of visibility effects and turning them on or off (not saved). Effects: twilight, moon, airglow, extinction, refraction, colorloss, light, dark, zodiacal, twinkle, aurora |

The `/cwhen` criteria for objects (solar altitude, object altitude) are −18° and 25° for deep-sky objects, −12° and 15° for naked-eye stars, −15° and 20° for faint stars, and −6° and 8° for planets (−3° and 5° for Mercury and Venus). Each night is scored at 20-minute steps and refined at 5-minute steps.

### 8.3 Keys and telescope controls

| Action | Default key |
| --- | --- |
| Telescope on and off | Right-click with the item |
| Change eyepiece | Mouse wheel |
| Change aperture | Left Shift + wheel |
| Cycle display (All → Lines only → Names only → Off) | N |
| Select object | Left click |
| Lock tracking | Left Alt + left click |
| Angular distance | Ctrl + left click |
| Clear selection | Shift + left click |
| Cheonsang settings · Cheonsang observing notebook | Not bound (assign under "Miscellaneous" in Controls) |

The telescope item `cheonsang:telescope` stacks to 1 and is placed after the spyglass in the creative "Tools & Utilities" tab. The recipe is a glass pane at top center, a spyglass in the center, and 3 iron ingots in the bottom row.

### 8.4 Settings screen items

The settings screen has items for Constellation lines, Names, object display, and Crosshair; the constellation system (Western 88 / Cheonsang Yeolcha Bunyajido); Equatorial grid (celestial equator, ecliptic, Moon's path, nodes); Alt-az grid; Boundaries; Constellation art; Info card (on/off, position, opacity 25–100%, displayed lines); Text size (×0.75–×1.5); Day/night visuals = Cheonsang Sun; observing location input; and pack status.

---

## 9. Multiplayer synchronization and abuse prevention

### 9.1 Synchronization

| Target | Method |
| --- | --- |
| Sky clock and location | The server stores them per dimension and sends `sky4` on the tick after joining, on dimension change, and when a value changes |
| Personal location | Stored only on the client. When the world default location (latitude and longitude) changes, the server's `#gen` counter increases by 1, and a client receiving `placegen` clears `/cplace` if the counter differs (players who were not online do this on their next join). Kept if only the date, time, or compression rate changes |
| Observing logbook | The client decides visibility and sends `log_add`, and the server stores it with the sky Julian Day at that moment. The list is resent in batches at most once per second (20 ticks) |
| Advancements | Decided from the logbook; the server grants criteria with `adv:<id>/<criterion>` keys |
| Telescope pose | `pose_set` is sent only when the state changes, and the server relays `pose` to other players. New players receive the list of players currently zoomed in; cleared on leave. Not saved |
| Meteors | No packet. Each client generates the same random numbers from the 0.2-second slot number of the computer clock (`System.currentTimeMillis`), so clients with matching clocks see the same meteors |
| Server without Cheonsang | The client computes on its own with defaults (2026-08-11, 0°, 0°, compression rate 1) or settings screen values |

### 9.2 Client → server packet limits

A per-player token bucket is used. Up to burst packets are allowed at once, then perSec are refilled each second, and packets beyond that are dropped. The bucket is removed when the player leaves.

| Packet | burst | Per second | Notes |
| --- | --- | --- | --- |
| `log_add` (logbook, advancements) | 300 | 20 | Larger than the amount sent in bulk right after joining |
| `pose_set` (telescope pose) | 6 | 2 | Off (on = false) is accepted without limit |

### 9.3 Input validation

| Item | Rule |
| --- | --- |
| Logbook key | `^[A-Za-z]+:[^\t\n\|]{1,80}$` or `sun` / `moon` |
| Name | Tabs, line breaks, and `\|` replaced, truncated to 80 characters |
| Magnification | Clamped to 1–1000 |
| Entry count | Up to 15,000 per player; existing keys are ignored (only the first discovery is recorded) |
| Advancement ID and criterion | `^[a-z0-9_]{1,32}$` · `^[A-Za-z0-9_]{1,32}$`, only `cheonsang:` advancements that exist on the server |
| String length | In the packet codec, key and name 96 bytes; list strings 256 bytes, up to 20,000 lines |
| Server config | `rate` clamped to 0.01–1000, `lat` to −90–90, `lon` to −180–180 |

---

## 10. Fault isolation

Client features are split into units (sky, telescope, find, keys, tonight, notebook, logbook), each wrapped in `Safety.run`. If one feature throws an exception, only that feature is turned off for the current session and falls back to the default sky or vanilla behavior. The full stack trace is written once to `latest.log`, and an error with the feature name is shown on screen. `VirtualMachineError` is rethrown. All features are turned back on when you rejoin the server. Where shaders cannot be used, galaxy sketches are replaced with a simple glow.

---

## 11. Known limitations

| Area | Details |
| --- | --- |
| Compatibility | Cannot be used with Iris (shader mod) (`breaks` declaration) |
| Position computation | Nutation, aberration, and proper motion not applied to stars (about 1′ against Stellarium). Physical libration and nutation of the Moon excluded (around 0.04°) |
| Time range | Planets: max 12.6″ for 1800–2050, max about 2′ for 1000 BCE–3000 CE. Saturn's moons, Ceres, and Vesta are based on data for 1900–2050; asteroids are not shown outside that range. ΔT uses the long-term parabola before 1900 and after 2150 |
| Jupiter's moons and Great Red Spot | Low-accuracy method for Jupiter's moons (about 0.5 Jupiter radii). The Great Red Spot longitude is a linear approximation, so it drifts further off the further the date is from 2026 |
| Sky brightness | The value at the single zenith point applies to the whole sky. No weather, clouds, or changes in atmospheric transparency. Seeing 1.5″ and extinction coefficients are fixed |
| Optics | No aberrations, central obstruction, or transmission losses. The 30-second dark adaptation recovery is shortened for the game |
| Display | Stars fainter than magnitude 15.2 appear only in the Milky Way image |
| Meteors | Based on the computer clock, so clients whose clocks differ see different meteors |
| Sketches | The atlas has no mipmaps, so objects may flicker when they appear very small. DSS core saturation makes bright galaxy cores look flat. With 128 px cells, large objects blur at high magnification. The DSS atlas is under a non-commercial condition (CC BY-NC-SA 4.0) |
| `/cwhen` | Judges only altitude, darkness, and moonlight (light pollution and weather excluded). Moons are computed from the position of their parent planet. Lunar features ignore libration. Displayed times may be off in worlds with a compression rate other than 1 |
| Location | When an OP changes latitude or longitude with `/cele set`, every player's `/cplace` is cleared. In real-time mode, changing the longitude with `/cplace` shifts the sky's Universal Time from the real world by the longitude difference |
| Cheonsang Yeolcha Bunyajido | The 5 asterisms that cannot be matched to modern stars (Cheonjik, Cheonmyo, Donggu, Gunmun, Gibu) are not included. Boundaries, art, and star symbols exist only for the Western system |
| Game rules | Mob spawning and sleeping follow the vanilla 10-minute day (`vanillaNight` in `server.json` can switch to a solar altitude −6° criterion) |
| Performance measurement | No `/cbench` values remeasured with the current configuration. Minimum specs on low-end hardware (integrated graphics) and multi-player load on dedicated servers not measured |
