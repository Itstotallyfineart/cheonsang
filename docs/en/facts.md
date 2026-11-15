# Cheonsang 2.0.1 fact table

This is the source of the numbers used in the documents. Numbers in other documents are copied from this table.
At each version update, `_tools/make_facts.py` is run to refresh it. Values whose "Source" is a file name are counted directly by the script. The other values are copied from the results of comparison scripts.

| Area | Item | Value | Source |
|---|---|---|---|
| Version | Cheonsang | 2.0.1 | gradle.properties |
| Version | cheonsang-extra | 1.0.1 | Release file name |
| Environment | Minecraft | 26.3 | gradle.properties |
| Environment | Fabric Loader | 0.20.1 or later | gradle.properties |
| Environment | Fabric API | 0.165.0+26.3 | gradle.properties |
| Environment | Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 | gradle.properties |
| Files | cheonsang-2.0.1.jar | 111.4 MB | build/libs |
| Files | cheonsang-extra-1.0.1.jar | 434.0 MB | Release file |
| Stars | Naked-eye stars (stars.bin) | 90,938 | stars.bin header count |
| Stars | Gaia stars (extra pack) | 32,061,510, to V magnitude 15.2 | CREDITS.txt |
| Stars | Named famous stars (stars without proper names) | 18 | famous_stars.json |
| Stars | Telescope stars (deep_stars.bin) | 2,409,260, to magnitude 15.37 | make_deep_stars.py output (measured from the 2.0.1 jar) |
| Stars | WDS close double-star companions | 7,459 (HYG to magnitude 9: 83,479 + companions = naked-eye stars) | Measured from the 2.0.1 jar |
| Constellations | Western constellation lines | 88 (+ 1 for the split of Serpens) | constellations.json |
| Constellations | Cheonsang Yeolcha Bunyajido constellations | 289 | korean_constellations.json |
| Deep-sky objects | Total | 554 | dso.json |
| Deep-sky objects | Messier / Caldwell | 110 / 109 | dso.json id, caldwell |
| Moon | Lunar feature names | 787 | moon_names.json |
| Astronomical events | Meteor showers | 7 + 10 sporadic per hour | meteors.json |
| Computation | Planetary VSOP87 term count | 1,259 terms (amplitude of at least 3×10⁻⁶ rad) | Vsop87Data.kt |
| Accuracy | Maximum planetary error 1800–2050 (compared with PyEphem) | Mercury 4.3″ · Venus 5.4″ · Mars 12.6″ · Jupiter 4.3″ · Saturn 3.8″ · Uranus 4.0″ · Neptune 2.4″ | _tools/vsop_check.py |
| Accuracy | Maximum planetary error 1000 BC–AD 3000 | About 2′ | _tools/vsop_check.py |
| Accuracy | Maximum Sun and Moon position error 1900–2100 (400 instants) | Sun 43.7″ · Moon 30.0″ | verify_all.py |
| Accuracy | Stars | J2000 + precession only (no nutation, aberration, or proper motion), about 1′ relative to Stellarium | Development decision notes |
| Accuracy | Error from omitting lunar physical libration and nutation | Around 0.04° | Moon.kt |
| Optics | Diffraction radius | 138″/D(mm) | Optics.kt |
| Optics | Limiting magnitude (naked-eye 6.0) | 80mm mag 11.7 · 305mm mag 14.6 | Optics.kt |
| Optics | Aperture / eyepiece | 50–305 mm / 40–3 mm | TelescopeView.kt |
| Performance | One computation of 7 planets | 28µs | Measured in 2.0.1 (average of 2000 runs) |
| License | Code | MIT | LICENSE |
| License | DSS sketch plates | CC BY-NC-SA 4.0 (non-commercial) | STScI |
