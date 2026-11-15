# Cheonsang 2.0.1 overview

Cheonsang 2.0.1 · 2026-11-16

Cheonsang: an astronomical simulation mod for Minecraft  
Astronomical Sky Simulation for Minecraft: Java Edition · Minecraft 26.3 · Fabric · Kotlin · itsnotfineart

## Contents

1. [Project Overview](#1-project-overview)
2. [Astronomical Database](#2-astronomical-database)
3. [Celestial Mechanics](#3-celestial-mechanics)
4. [Telescope Simulation](#4-telescope-simulation)
5. [Observational Environment](#5-observational-environment)
6. [Deep-Sky Observation](#6-deep-sky-observation)
7. [Astronomical Events](#7-astronomical-events)
8. [Technical Specifications](#8-technical-specifications)
9. [Educational and Scientific Value](#9-educational-and-scientific-value)
10. [Data Sources and Credits](#10-data-sources-and-credits)
11. [Related Documents](#11-related-documents)

---

## 1. Project Overview

Cheonsang (天象, "celestial phenomena") is a Fabric mod that turns the sky of Minecraft Java Edition into a real celestial sphere. Once the observing location (latitude and longitude), date, and time are set, the mod computes the positions and brightness of stars, the Sun, the Moon, planets, and deep-sky objects from astronomical catalogs and the equations of celestial mechanics. It then applies atmospheric, sky-brightness, and telescope-optics conditions to decide whether each object is actually visible, and draws it on screen.

### Purpose of the project

The aim is to make the night sky in the game follow the same rules as real observation. At the same moment, a different observing location shows different stars. The same object looks different depending on aperture, magnification, and sky brightness. Every star and object drawn on screen is a catalog entry with a recorded source, and no randomly generated stars are included. Random numbers are used only for the timing of meteors and auroras.

### Comparison with the default Minecraft sky

| Item | Default Minecraft | Cheonsang 2.0.1 |
| --- | --- | --- |
| Stars | Decorative dots unrelated to real stars | Real positions, magnitudes, and colors from the HYG, Tycho-2, and Gaia DR3 catalogs |
| Sun and Moon | The same path every day, driven by game time | Real positions from Meeus's formulas, altitude changes with season and latitude, lunar phases and libration |
| Planets | None | Mercury to Neptune (VSOP87 planetary theory), the 4 Galilean moons, 7 moons of Saturn, Ceres and Vesta |
| Observing location | The same sky everywhere | A sky for each latitude and longitude (including the southern hemisphere and the midnight sun and polar night of the polar regions) |
| Visibility | Always the same brightness at night | Limiting magnitude computed from atmospheric extinction, twilight, moonlight, light pollution, and dark adaptation |
| Observing equipment | A spyglass that magnifies the view at a fixed power | Magnification, limiting magnitude, resolution, and exit pupil set by aperture, focal length, and eyepiece |
| Astronomical events | None | Solar and lunar eclipses, meteor showers, auroras, and planetary conjunctions reproduced at computed positions |

### Main figures

| Figure | Description |
| --- | --- |
| 90,938 | Naked-eye stars (to magnitude 9.0, including double-star companions) |
| 2,409,260 | Telescope stars (Tycho-2, corrected with Gaia DR3) |
| 32,061,510 | Gaia DR3 stars (optional extension pack, to V magnitude 15.2) |
| 554 | Deep-sky objects (including 110 Messier and 109 Caldwell objects) |
| 88 + 289 | Western constellations + Cheonsang Yeolcha Bunyajido constellations |
| 50–305 mm | 12 telescope aperture steps, up to 712× |
| 12.6″ | Maximum planetary position error, 1800–2050 (compared with PyEphem, Mars) |
| 462 | Eyepiece sketch plates based on DSS images |

---

## 2. Astronomical Database

The sky in Cheonsang is built from public astronomical catalogs. Stars visible to the naked eye are included in the main mod. Fainter stars seen through the telescope are loaded in stages according to the observing limit.

| Category | Source catalog | Count | Range and content |
| --- | --- | --- | --- |
| Naked-eye stars (main mod) | HYG Database v4.1 (merged Hipparcos, Yale Bright Star, and Gliese) | 90,938 | 83,479 stars to magnitude 9.0 + 7,459 WDS double-star companions. Position (J2000), magnitude, color index, spectral type, distance |
| Telescope stars (main mod) | Tycho-2 (Høg et al. 2000) + Gaia DR3 cross-match table | 2,409,260 | To magnitude 15.37. Stars with a Gaia match use the Gaia magnitude, color, and position |
| Gaia stars (extension pack) | ESA Gaia DR3 gaia\_source | 32,061,510 | 9 < V ≤ 15.2, all sky |
| Close double stars | Washington Double Star Catalog (WDS) | 7,459 | Separations of 0.5–30″. Separation, position angle, and component magnitudes from the latest measurement |
| Variable stars | General Catalogue of Variable Stars (GCVS) | 2 | Light-curve models of Algol (eclipsing binary, period 2.8673 days, magnitude 2.1–3.4) and Mira (magnitude 3.4–9.3) |
| Named famous stars | HYG v4.1, SIMBAD | 18 | Famous objects without proper names, such as Proxima Centauri, Barnard's Star, and Cygnus X-1 |

Tycho-2 and Gaia DR3 are linked star by star through the cross-match table published with Gaia DR3, and stars with a match use the Gaia magnitude, color, and position. Gaia G magnitudes are converted to Johnson V magnitudes with the transformation of Riello et al. (2021). Close double stars that HYG merges into a single point are split into two stars using WDS data, so they separate into two stars when the telescope's resolution is sufficient (for example, Castor at 5.4″ and Epsilon Lyrae at 2.1″ and 2.4″). The processing steps and verification results for catalog merging, magnitude and color conversion, and double-star splitting are described in [analysis-2.0.1.md](analysis-2.0.1.md).

### Star colors and brightness

The color index (B−V) is converted to an effective temperature with the Ballesteros (2012) formula, and each star is drawn in the blackbody color of that temperature. Magnitude is converted to brightness and on-screen size. Atmospheric extinction, refraction, and scintillation are computed per star in a shader.

### Sky spatial index and observing limits

The sky is divided into 41,252 cells of about 1°² each, and the stars in each cell are sorted by brightness. Only the cells near the telescope's field of view are read, so tens of millions of stars do not have to be checked every frame. With only the main mod installed, the telescope's limiting magnitude stops at 13.0. Installing the extension pack adds Gaia stars down to magnitude 15.2. The light of stars fainter than that is contained in the Milky Way image.

### 88 Western constellations

The mod shows the names, boundaries, and connecting lines of the 88 constellations defined by the International Astronomical Union (IAU) (Serpens is split into two parts, the head and the tail). The lines and boundaries come from d3-celestial, and the 85 constellation drawings come from Stellarium's "modern" sky culture. 188 bright stars have Korean names.

### 289 Cheonsang Yeolcha Bunyajido constellations

Following the system of the Joseon-era star map Cheonsang Yeolcha Bunyajido (天象列次分野之圖), the mod shows 289 constellations, including the Three Enclosures (三垣) and the 28 lunar mansions (二十八宿). The set consists of the 283 asterisms of the Bocheonga (步天歌, "Song of Pacing the Heavens") plus Cheonchang, Taejon, and Togongni. Constellations near the south celestial pole that were added in the Ming dynasty, and constellations that cannot be matched to modern stars, are excluded. The connecting lines were taken from Stellarium's Chinese traditional sky culture and matched by their Chinese-character names, and the Korean names were checked against the Korean readings in Unicode Unihan. In the settings screen you can choose either the Western constellations or the Cheonsang Yeolcha Bunyajido constellations.

### Solar System Bodies

| Object | Computation and data | Rendering |
| --- | --- | --- |
| Sun | Meeus chapter 25, including aberration correction | Disk flattened near the horizon by atmospheric refraction |
| Moon | All terms of Meeus chapter 47 | Phases, earthshine, optical libration, NASA LRO map, 787 IAU lunar feature names |
| 7 planets | Truncated VSOP87D (Bretagnon & Francou 1988, 1,259 terms) + light-time correction | Phases of Venus, tilt of Saturn's rings, rotation of Mars and Jupiter (IAU WGCCRE), Jupiter's Great Red Spot |
| 4 moons of Jupiter | Meeus chapter 44 | Io, Europa, Ganymede, and Callisto, with in-front-of and behind-Jupiter tests |
| 7 moons of Saturn | Orbital formulas fitted to JPL Horizons positions for 1900–2050 | Titan at magnitude 8.3 to Mimas at magnitude 12.9 |
| 2 asteroids | JPL Horizons position tables (1900–2050, interpolated at 4-day intervals) | Ceres, Vesta |

### Deep-Sky Objects

| List | Count |
| --- | --- |
| Messier catalog | 110 |
| Caldwell catalog | 109 |
| Objects in the Large and Small Magellanic Clouds | 69 |
| Large and Small Magellanic Clouds | 2 |
| Galaxies to V magnitude 11 (OpenNGC) | 264 |
| Total (of which 341 are galaxies) | 554 |

Position, size, orientation, magnitude, Hubble type, and radial-velocity-based distance use OpenNGC values. Open clusters are filled with their real member stars from Gaia DR3, and the core radii of 13 globular clusters use measured values from the Harris (1996, 2010 edition) catalog. Every object can be found with the find command (`/cfind`) by its Korean or English name, Bayer designation, Flamsteed number, HIP or HD number, Messier or Caldwell number, lunar feature name, and so on.

---

## 3. Celestial Mechanics

### Coordinate systems and time scales

The direction of an object is first computed in equatorial coordinates (right ascension α, declination δ) and then converted to horizontal coordinates (altitude, azimuth) using the observer's latitude and local sidereal time. Star catalog coordinates refer to the J2000.0 equinox and are carried to the equator of the observing date with the precession matrix of Meeus chapter 21. All computations are based on the Julian Day. The motions of celestial bodies are computed in Terrestrial Time (TT), and the Earth's rotation in Universal Time (UT). The difference between the two, ΔT, comes from the polynomials of Espenak and Meeus (NASA), and the game clock shows the local mean time of the observing location. The coordinate transformations and computation steps are in [technical-spec.md](technical-spec.md).

### Accuracy of Sun, Moon, and planet positions

| Object | Algorithm | Comparison with an independent ephemeris (PyEphem) |
| --- | --- | --- |
| Sun | Meeus chapter 25 (including aberration) | 400 random instants in 1900–2100, maximum 43.7″ |
| Moon | All terms of Meeus chapter 47 | Same conditions, maximum 30.0″ |
| Planets | Truncated VSOP87D (1,259 terms with amplitude of at least 3×10⁻⁶ rad) + light-time | Maximum error in 1800–2050: Mercury 4.3″, Venus 5.4″, Mars 12.6″, Jupiter 4.3″, Saturn 3.8″, Uranus 4.0″, Neptune 2.4″. Maximum over the whole range 1000 BC–AD 3000 about 2′ |

Planet positions are computed as heliocentric ecliptic coordinates of date from the VSOP87D series and converted to the J2000 ecliptic. The Earth's position, computed from the same series, is subtracted to give the direction as seen from the Earth. A light-time correction is included: the planet's position is taken at the earlier time when the light left it. One computation of all 7 planets takes 28µs.

The formulas were reimplemented in Python and compared with PyEphem, the worked examples in Meeus's textbook, and NASA eclipse data, and all 82 test items passed. This verification covers the formulas only and does not include measurements taken directly from the game screen. The verification methods and per-item results are in [analysis-2.0.1.md](analysis-2.0.1.md).

### Observing location and the passage of time

The server sets the world's default observing location and the date and time of the sky, and each player can set their own latitude and longitude separately. A location can also be given by city name. Changing the longitude applies the local mean time of that place, so events that happen in Universal Time, such as eclipses, appear at the local time of that place. Three time modes are available: real-time mode, where time runs at the same speed as real time; sync, which matches the current time of a real city; and the compressed calendar, which compresses a year so that the seasonal change of constellations can be seen quickly. Command usage is in [manual.md](manual.md).

### Rising, transit, and atmospheric refraction

The times when an object rises, crosses the meridian at its highest point (transit), and sets are computed and shown on the info card. Rising and setting use a standard altitude that includes atmospheric refraction, and the midnight sun and polar night of the polar regions are also computed. Atmospheric refraction comes from the formulas of Sæmundsson (1986) and Bennett (1982). Near the horizon, refraction reaches about 0.5°, and the vertically flattened look of the Sun and Moon on the horizon comes from this computation.

### Effects not computed and range of verification

Star positions use J2000 coordinates and precession only. Nutation, annual aberration, and proper motion are not included, and the resulting difference in star positions is about 1′ compared with Stellarium. The Sun's position includes aberration. The lunar libration computation leaves out physical libration and nutation, and the difference is around 0.04°.

Sun and Moon positions were verified for 1900–2100, and eclipse computations for 1900–2050. Ceres, Vesta, and the moons of Saturn use tables and formulas fitted to 1900–2050 data, so accuracy is not guaranteed for other years.

### Solar and lunar eclipses

Solar and lunar eclipses appear on screen according to the computed positions and apparent sizes of the Sun and Moon. Solar eclipses include the Moon's parallax for the observer's location, so the fraction covered differs from place to place. Lunar eclipses are computed from the fraction of the Moon covered by the Earth's umbra and penumbra, and the Moon inside the umbra is drawn red.

| Item compared with NASA | Cheonsang | NASA |
| --- | --- | --- |
| 2026-08-12 total solar eclipse, point of greatest eclipse | 65.1°N 25.1°W | 65.2°N 25.2°W |
| Same eclipse, duration of totality · path width | 2 min 19 s · 301 km | 2 min 18 s · 294 km |
| 2026-08-28 partial lunar eclipse, umbral magnitude | 0.935 | 0.930 |
| 2028-12-31 total lunar eclipse, 6 contacts | Agree within ±1 minute | |

---

## 4. Telescope Simulation

The Cheonsang telescope defines its optical system by the combination of objective aperture, focal length, and eyepiece, and computes magnification, limiting magnitude, resolution, and exit pupil from that combination. Apertures come in 12 steps from 50 to 305mm, eyepieces range from 40 to 3mm, and the objective's focal ratio is fixed at f/7.

| Item | Computation |
| --- | --- |
| Magnification | M = objective focal length / eyepiece focal length = 7D / f. Maximum 712× (305mm, 3mm eyepiece) |
| Limiting magnitude | Naked-eye limiting magnitude plus the light-gathering gain from the aperture, minus losses from sky brightness and dark adaptation |
| Resolution | The star image size is set by combining the radius of the first dark ring of the Airy disk, 138″/D(mm), with 1.5″ seeing |
| Exit pupil | D/M. If larger than the eye's pupil (7mm), the effective aperture is reduced |

| Aperture | Objective focal length | Magnification range | Limiting magnitude (naked-eye 6.0) | Airy radius |
| --- | --- | --- | --- | --- |
| 50mm | 350mm | ×8.8–×117 | 10.7 | 2.76″ |
| 80mm (default) | 560mm | ×14–×187 | 11.7 | 1.73″ |
| 127mm | 889mm | ×22–×296 | 12.7 | 1.09″ |
| 305mm | 2,135mm | ×53–×712 | 14.6 (extension pack required) | 0.45″ |

The limiting magnitudes assume a dark sky and a fully dark-adapted eye. Without the extension pack, the limit stops at magnitude 13.0. Raising the magnification makes the exit pupil smaller, so extended sources such as nebulae and galaxies get dimmer. The basis and derivation of the limiting-magnitude and resolution formulas are in [technical-spec.md](technical-spec.md).

### Telescope view by aperture and magnification

M13, the globular cluster in Hercules, appears only as a faint patch of light at its center at 50mm aperture and 117×. At 305mm aperture the greater light grasp resolves individual stars in the outer region. The positions of Jupiter's Galilean moons are computed with Meeus chapter 44, and the tilt of Saturn's rings from the positions of the Earth and Saturn on the observing date. At high magnification the Moon shows a surface based on the NASA LRO lunar map with optical libration applied.

### Observing features

- Circular field: the eyepiece field is limited to a circle, and lower magnification shows a wider area.
- Object tracking: the telescope follows diurnal motion and keeps the target at the center of the field. An object selected with the find command can be locked on directly.
- Info card: selecting an object in the field shows its magnitude, distance, spectral type, right ascension and declination, altitude and azimuth, and rise and set times.
- Guide overlays: equatorial and horizontal coordinate grids, constellation boundaries, and angular distance measurement.
- Telescope item: a 3D refracting telescope model. In multiplayer, other players also see the holder in a pose with the eyepiece held to the eye.

Controls are described in [manual.md](manual.md).

### Scope of the optical model

Lens aberrations, central obstruction, and transmission are not included. Seeing is fixed at 1.5″ and does not change with altitude or weather. The limiting-magnitude formula is empirical, and differences between individual observers are reflected only through the naked-eye limiting magnitude setting (4.0–6.5).

---

## 5. Observational Environment

The limiting magnitude in the viewing direction is computed by a unified visibility model that applies the six stages below in a fixed order. Star display and the intensity of the Milky Way and nebulae follow its result.

| Stage | Content |
| --- | --- |
| ① Intrinsic brightness | Point-source magnitude, extended-source surface brightness |
| ② Atmospheric extinction | k = 0.172 mag/airmass (V) |
| ③ Sky brightness | Airglow, twilight, moonlight, light pollution, zodiacal light |
| ④ Dark adaptation | Loss of up to 3 magnitudes |
| ⑤ Equipment | Aperture gain, exit pupil |
| ⑥ Decision | Limiting magnitude, surface-brightness threshold |

### Sky brightness

Sky brightness is computed as the sum of airglow, twilight, moonlight, light pollution, and zodiacal light and gegenschein, in units of mag/arcsec². The zenith of a dark natural sky is set to 22.0 mag/arcsec². Moonlight is computed with the Krisciunas & Schaefer (1991) model from the Moon's phase, altitude, and angular distance, and twilight is taken from a table by solar altitude. The loss in limiting magnitude due to sky brightness is about 1.7 magnitudes on a full-moon night and about 5.4 magnitudes in twilight with the Sun at −6°.

### Atmospheric extinction, scintillation, and dark adaptation

The closer an object is to the horizon, the thicker the atmosphere its light passes through. Airmass is computed with the Kasten & Young (1989) formula. Because of the wavelength-dependent extinction coefficients (B 0.30, V 0.172, R 0.09 mag/airmass), stars near the horizon look fainter and redder. Scintillation grows stronger toward the horizon and disappears at high magnification. Dark adaptation causes a loss of up to 3 magnitudes depending on nearby block light and a light source held in the hand, and recovers over 30 seconds, which is shorter than for the real eye. Extended sources with low surface brightness are drawn with their color faded to gray to match the characteristics of rod vision.

### Light pollution and the Bortle scale

The Bortle scale divides sky darkness from 1 (darkest) to 9 (inner city). The mod samples light-emitting blocks such as torches and lanterns within ±64 blocks, estimates a Bortle class from them, and adds it to the zenith sky brightness. Lights in caves and closed interiors are excluded.

| Bortle | Zenith brightness (mag/arcsec²) | Naked-eye limit (default 6.0) |
| --- | --- | --- |
| 1 | 21.9 | 6.0 |
| 3 | 21.4 | 5.8 |
| 5 | 20.0 | 5.2 |
| 7 | 18.5 | 4.3 |
| 9 | 17.5 | 3.7 |

### Scope of the model

Sky brightness uses the value at a single point, the zenith, for the whole sky. As a result, light-pollution domes toward the horizon and the bright sky near the Moon are not reflected in the limiting magnitude for each direction. Weather, clouds, and day-to-day changes in atmospheric transparency are not computed.

---

## 6. Deep-Sky Observation

### The Milky Way

The Milky Way uses Deep Star Maps 2020 from the Scientific Visualization Studio (SVS) at NASA's Goddard Space Flight Center. It is an all-sky map computed from 1.7 billion stars in Hipparcos-2, Tycho-2, and Gaia DR2. The main mod contains a 4K image for the naked eye and a 12K image for the telescope. The extension pack splits 16K and 32K images into tiles and loads only the visible part. Where the extension pack's Gaia stars are drawn, their starlight is subtracted from the Milky Way image so that the same light is not added twice.

### Eyepiece sketches based on DSS

Galaxies, nebulae, and globular clusters are drawn to match what the eye sees through an eyepiece. The base data are Digitized Sky Survey (DSS2, DSS1) plate images, and the set consists of 462 plates (339 galaxies, 123 nebulae and clusters). Stars are removed from the plates so that only the shape of the object remains, and the real stars are drawn by the star catalog. For nebulae, the blue plate is given more weight in the mix, because the dark-adapted eye is sensitive to blue-green light ([O III], Hβ) and insensitive to the red light of Hα. Galaxies without a DSS image are drawn with a computed bulge, disk, and spiral arms according to their Hubble type. The plate production steps are in [analysis-2.0.1.md](analysis-2.0.1.md).

### Visibility under telescope conditions

The surface brightness of the brightest part of an object is computed from its integrated magnitude and size, and only the parts brighter than a threshold set by aperture, exit pupil, magnification, and sky brightness are drawn. Structures such as spiral arms, bars, and dust lanes appear only where they are 2–8 times brighter than the threshold. So with an 80mm telescope under a bright sky only a central patch is visible, while with a 305mm telescope under a dark sky the arms and dark lanes can be seen. Stars inside nebulae, such as the Trapezium in M42, the Orion Nebula, are drawn as catalog stars.

### Nebulae and clusters

- Open clusters: filled with their real member stars from Gaia DR3.
- Globular clusters: brightness is drawn with a King profile, and the core radii of 13 clusters use measured values from the Harris catalog.
- Planetary nebulae: M57 is saturated on the DSS plates, so it is drawn as a ring with measured proportions.
- Emission nebulae: for saturated bright HII regions (M42, M8, M17, and others), the central brightness is restored.

### Limits of the data

Each sketch plate is 128×128 pixels per object, so large objects such as M31 look blurred at high magnification. Saturation on the DSS plates makes the cores of some bright galaxies look flat, and structures that are faint in the photographs are also faint in the sketches. DSS images are subject to non-commercial use terms (CC BY-NC-SA 4.0).

---

## 7. Astronomical Events

Astronomical events appear on screen according to computed object positions and observing conditions. The forecast command (`/cwhen`) finds the date of the next event and the place where it is best seen.

### Solar and lunar eclipses

Solar eclipses are classified as total, annular, hybrid, or partial. A forecast gives the latitude and longitude of the point of greatest eclipse, the local time and solar altitude there, the duration of totality, the width of the shadow path, and the fraction covered at the current observing location. For example, the total solar eclipse of 2027-08-02 is forecast with greatest eclipse at 25.5°N 33.1°E, a duration of 6 min 25 s, and a path width of 258 km, and the observing location can be moved to that point directly from the result. Lunar eclipses are classified as total, partial, or penumbral, and the times are computed from the start of the penumbral eclipse (P1) through the umbral contacts (U1–U4) to the end of the penumbral eclipse (P4).


### Planets, moons, and occultations

The mod computes the phases of Venus, the tilt of Saturn's rings, and the changing positions of Jupiter's Galilean moons and Saturn's moons. When a moon passes behind its planet, it is marked "Currently hidden". The date and angular separation of conjunctions, when two planets come close in the sky, are forecast. Stars, planets, and moons behind the lunar disk are not drawn, so occultations by the Moon appear according to the computed positions, but there is no feature that forecasts occultations.

### Meteor showers

Based on the International Meteor Organization (IMO) calendar, the mod reproduces 7 meteor showers (the Quadrantids, Lyrids, Eta Aquariids, Perseids, Orionids, Leonids, and Geminids) and sporadic meteors (10 per hour). Meteors spread out from the radiant, and the expected hourly count is computed from the zenithal hourly rate (ZHR), taking into account the altitude of the radiant and the moonlight.

### Auroras

Auroras appear at latitudes of 60° and above at night (Sun below −10°) and are strongest at latitudes of 67–75°. For each sky pixel, the shader follows the line of sight through an aurora layer at heights of 80–320 km and accumulates the light. The lower part is drawn in the 557.7 nm green light of atomic oxygen and the upper part in 630 nm red light. The strength of solar activity is set at random each night.

### Other events

- Forecasts of full and new moon dates, and computation of the "best night" for each object (based on altitude, sky darkness, and moonlight; weather and light pollution are not considered)
- Eclipses of Algol and the long-period brightness changes of Mira
- Zodiacal light and gegenschein, earthshine
- The midnight sun and polar night at high latitudes, the southern hemisphere sky

---

## 8. Technical Specifications

### Supported environment

| Item | Requirement |
| --- | --- |
| Minecraft | Java Edition 26.3 |
| Mod loader | Fabric Loader 0.20.1 or later |
| Java | 25 or later |
| Required mods | Fabric API (0.165.0+26.3), Fabric Language Kotlin (1.14.2+kotlin.2.4.21) |
| Optional mods | Mod Menu (access to the settings screen) |
| Not compatible | Iris (shader mod) |
| Languages | Korean, English (follows the game language setting) |
| Implementation languages | Kotlin, Java (mixins), GLSL shaders |

### Run configurations

- Single player and client only: the sky works on the client even if the server does not have Cheonsang.
- Multiplayer: the server sets the date and time of the sky and the world's default location and syncs them to connected players. The server and client use the same version.
- Personal observing location: each player can set their own latitude and longitude. Only that player's own sky changes, so no operator permission is needed.
- Game rules preserved: mob spawning, sleeping, and crop growth follow the default Minecraft clock. Cheonsang changes only the sky on screen and the day and night colors.

### Main mod and optional extension pack

| Item | Main mod cheonsang 2.0.1 | Extension pack cheonsang-extra 1.0.1 |
| --- | --- | --- |
| File size | 111.4 MB | 434.0 MB |
| Stars | 90,938 naked-eye + 2,409,260 telescope | + 32,061,510 Gaia DR3 |
| Telescope limit | Magnitude 13.0 | Magnitude 15.2 |
| Milky Way | 4K naked-eye, 12K telescope | 16K and 32K naked-eye tiles |
| Moon map | 4K | 8K |
| Deep-sky objects and sketches | All included in the main mod (554 objects, 462 plates) | |

The extension pack is an optional component installed as a separate file. All features of the main mod work without it.

### Performance design

- Spatial index: the sky is divided into 41,252 cells of about 1°² each, and only cells near the field of view are processed.
- Asynchronous loading and cache: a background thread reads Gaia star cells, and the render thread does not wait. Up to 3 million stars are cached, and each star is compressed to 6 bytes.
- Texture streaming: only the visible Milky Way tiles are uploaded, and the GPU memory budget is set automatically to 1/4 of the graphics card's memory.
- GPU computation: star extinction and scintillation, auroras, and deep-sky sketches are computed in shaders.
- Update interval: planet positions are computed once per second and interpolated in between. Forecast computations run on a separate thread.
- Fault isolation: errors are contained per feature (sky, telescope, find, and so on). When an error occurs, only that feature is turned off and the sky falls back to the default.

The internal structure and data formats are in [technical-spec.md](technical-spec.md).

### Computational accuracy and known limitations

| Area | Details |
| --- | --- |
| Position accuracy | Sun maximum 43.7″, Moon maximum 30.0″ (1900–2100). Planets maximum 2.4″–12.6″ for 1800–2050, maximum about 2′ for 1000 BC–AD 3000. Stars differ from Stellarium by about 1′ |
| Ceres, Vesta, moons of Saturn | Tables and formulas fitted to 1900–2050 data are used, so for years outside that range accuracy drops or the objects are not shown |
| Sky brightness | The zenith value is applied to the whole sky; no weather or clouds. Seeing 1.5″, fixed extinction coefficients |
| Optics | No aberrations, central obstruction, or transmission. The 30-second dark-adaptation recovery is shortened for gameplay |
| Display | Cannot be used together with a shader mod (Iris). Stars fainter than magnitude 15.2 appear only in the Milky Way image |

---

## 9. Educational and Scientific Value

In Cheonsang, astronomy concepts from textbooks can be observed directly and checked under changing conditions. All of the uses below can be carried out with features implemented in 2.0.1.

| Field | Related concepts | Activities in Cheonsang |
| --- | --- | --- |
| Astronomy · Earth science | Diurnal motion, annual motion, seasonal constellations, change of the sky with latitude, lunar phases, solar and lunar eclipses | Move the observing location between the equator, mid-latitudes, polar regions, and southern hemisphere and compare the sky on the same day. Use the compressed calendar to watch the constellations change over a year. Use the forecast command to find the date of a real solar eclipse and observe its progress from that place. |
| Physics · Optics | Lenses and magnification, light grasp, the diffraction limit, exit pupil, atmospheric refraction and scattering, wavelength and color of light | Compare the same object at 50mm and 305mm aperture to see the difference in light grasp and resolution. Watch whether close double stars split depending on aperture. Observe the extinction that makes stars near the horizon fainter and redder, and the 557.7 nm and 630 nm emission of auroras. |
| Mathematics · Coordinate systems | Spherical coordinates, coordinate transformations, trigonometric functions, logarithmic scales (magnitudes), interpolation, series | Compare the right ascension and declination with the altitude and azimuth on the info card, and work out how they change with observing time. Check the logarithmic relation between magnitude and brightness through the limiting-magnitude formula. Look at the structure of the VSOP87 series, which expresses planet positions as sums of trigonometric terms. |
| Computer science | Large-scale data processing, spatial indexing, compression, asynchronous processing, GPU shaders | Use as case studies the methods for handling data on more than 30 million stars in real time (sky cell index, cache, streaming) and the methods for verifying numerical approximations. |
| Scientific inquiry | Control of variables, comparison of models with observation, error and approximation | Change only one of light pollution, moonlight, or aperture and compare how the number of visible stars changes. Discuss the limits of a model through the accuracy range of the computational model and the effects it leaves out (nutation, proper motion). |

### Repeated observation under changed conditions

Real observation is limited by weather, time, equipment, and location. In Cheonsang the same object can be observed repeatedly under different conditions. For example, the same night sky can be compared under a Bortle class 1 sky and next to village lights, or the 2027 total solar eclipse can be observed from the center line of the eclipse. The stars and objects on screen follow real catalog data, so the arrangement of constellations and objects learned in the game is the same as in the real night sky.


### Notes on use

Observation results in Cheonsang are simulations produced by computational models and cannot be treated as equivalent to real telescope observation. For uses that need precise coordinates, professional astronomy software or an official ephemeris should be used.

---

## 10. Data Sources and Credits

The Cheonsang release files are a compilation of several data sets, and each file follows the license of its original data. The full list is in CREDITS.txt in the release files. The providers of third-party data do not endorse this mod.

| Data | Use in Cheonsang | License |
| --- | --- | --- |
| HYG Database v4.1 (D. Nash) | Naked-eye stars, coordinates of named stars | CC BY-SA 4.0 |
| Tycho-2 (Høg et al. 2000, CDS VizieR I/259) | Telescope stars | CDS public data |
| ESA Gaia DR3 (Gaia Collaboration 2023) | Telescope star corrections, stars of open clusters and the Magellanic Clouds, extension pack stars | CC BY-SA 3.0 IGO |
| WDS (Mason et al. 2001) · GCVS (Samus et al. 2017) | Close double stars · variable stars | VizieR public data |
| OpenNGC (M. Verga) | Deep-sky object positions, sizes, types, and distances | CC BY-SA 4.0 |
| Digitized Sky Survey (STScI; POSS-II Caltech, UK Schmidt AAO) | Sketch plates of galaxies, nebulae, and clusters | CC BY-NC-SA 4.0 (non-commercial) |
| Harris (1996, 2010 edition) | Globular cluster core radii | Measured values cited |
| d3-celestial (O. Frohn) | Constellation lines and boundaries | BSD 3-Clause |
| Stellarium "modern" (drawings: J. Meuris) | 85 constellation drawings | Free Art License 1.3 |
| Stellarium "chinese" sky culture | Cheonsang Yeolcha Bunyajido constellation lines | CC BY-SA |
| Unicode Unihan Database | Verification of Chinese-character readings (data not included) | Unicode License |
| NASA SVS Deep Star Maps 2020 · CGI Moon Kit | Milky Way images · Moon map | NASA imagery (no copyright in the United States) |
| Finkbeiner (2003), NASA LAMBDA | Hydrogen-alpha layer of the long-exposure Milky Way image | NASA public data |
| IAU WGPSN / USGS | 787 lunar feature names | Public domain |
| NASA/JPL Horizons | Orbital formulas for Saturn's moons, Ceres and Vesta, rotation verification | U.S. government data |
| Solar System Scope (INOVE) | Surfaces of Mars, Jupiter, and Saturn, and Saturn's rings | CC BY 4.0 |
| International Meteor Organization | Meteor shower dates, radiants, and rates | Factual data |
| JUPOS (H.-J. Mettig et al.) | Longitude trend of Jupiter's Great Red Spot | Factual data |

Computational methods: J. Meeus, *Astronomical Algorithms* 2nd ed. · Bretagnon & Francou (1988), A&A 202, 309 (VSOP87) · Krisciunas & Schaefer (1991), PASP 103, 1033 · Schaefer (1990), PASP 102, 212 · Kasten & Young (1989), Appl. Opt. 28, 4735 · Sæmundsson (1986) · Bennett (1982) · Riello et al. (2021), A&A 649, A3 · IAU WGCCRE (Archinal et al.) · Espenak & Meeus (ΔT)

This work has made use of data from the European Space Agency (ESA) mission Gaia, processed by the Gaia Data Processing and Analysis Consortium (DPAC). This research has made use of the VizieR catalogue access tool and the SIMBAD database, CDS, Strasbourg, France.

### License summary

- Mod source code: MIT License
- Original textures and icons: CC BY-SA 4.0
- Data files made from CC BY-SA data: CC BY-SA 4.0
- DSS-based sketch plates: CC BY-NC-SA 4.0 (non-commercial)
- Minecraft is a trademark of Mojang Synergies AB. Cheonsang is not affiliated with Mojang or Microsoft.

---

## 11. Related Documents

- [manual.md](manual.md): installation, full command list, controls
- [technical-spec.md](technical-spec.md): formulas and derivations, internal structure, data formats
- [analysis-2.0.1.md](analysis-2.0.1.md): data processing steps, accuracy verification results
- [facts.md](facts.md): source table for the figures used in the documents

## Credits

Cheonsang 2.0.1, itsnotfineart.  
The author handled the planning, design, and verification. The source code and this document were written in collaboration with a generative AI (Claude).
