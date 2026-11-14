# Cheonsang 2.0.0 Scientific Implementation Analysis

Cheonsang 2.0.0 (frozen release) · 2026-11-15

Astronomical Computation and Visibility Modelling in Cheonsang 2.0.0: Implementation and Accuracy Assessment

## Contents

- Abstract
- 1. Introduction
- 2. Methods
  - 2.1 Time scales
  - 2.2 Coordinate systems and precession
  - 2.3 Sun
  - 2.4 Moon
  - 2.5 Planets
  - 2.6 Satellites
  - 2.7 Asteroids
  - 2.8 Solar and lunar eclipses
  - 2.9 Atmospheric refraction
  - 2.10 Airmass and atmospheric extinction
  - 2.11 Sky brightness
  - 2.12 Light pollution, dark adaptation, and limiting magnitude
  - 2.13 Telescope optics
  - 2.14 Star data processing
  - 2.15 Star display: from magnitude to screen brightness
  - 2.16 Milky Way rendering
- 3. Results
  - 3.1 Validation method
  - 3.2 Calendar, Sun, Moon, and precession
  - 3.3 Planetary positions
  - 3.4 Satellites and planetary disk orientation
  - 3.5 Comparison of solar and lunar eclipses with NASA
  - 3.6 Lunar libration
  - 3.7 Computed values of the optical model
  - 3.8 Consistency of star positions
- 4. Discussion
  - 4.1 Rationale for omitting nutation, stellar aberration, and proper motion
  - 4.2 Omission of lunar physical libration
  - 4.3 Range of the solar theory and ΔT
  - 4.4 Range of validity of the truncated VSOP87D
  - 4.5 Fixed values in the atmosphere and sky brightness models
  - 4.6 Simplifications in the optical model
  - 4.7 Limits of the validation coverage
- 5. Conclusions
- References

---

## Abstract

Cheonsang 2.0.0 is software that reproduces the sky for an arbitrary date and observing site in real time inside Minecraft 26.3 (Fabric). This paper describes its system of astronomical computation and its model of observing conditions, and assesses their accuracy. Time is taken from the game tick as a Universal Time (UT) Julian Day and converted to Terrestrial Time (TT) by adding ΔT from the Espenak–Meeus polynomials. Stars receive only the precession matrix of Meeus Chapter 21, applied to their J2000.0 equatorial coordinates. The Sun uses the low-precision theory of Meeus Chapter 25 with aberration included, and the Moon uses the periodic terms of Meeus Chapter 47 (60 terms for longitude and distance, 60 terms for latitude) with observer parallax. The planets use VSOP87D truncated to the 1,259 terms with amplitude at or above $`3\times10^{-6}`$ rad, with light-time correction. Compared with PyEphem over 1800–2050, the maximum planetary errors range from 2.4″ (Neptune) to 12.6″ (Mars); over the full span from 1000 BCE to 3000 CE they are about 2′. Under the same conditions, approximate Keplerian elements (Standish & Williams 1992) gave errors of 1.1′ for Mars, 7.9′ for Jupiter, 11.5′ for Saturn, and 17.1′ for Uranus. In a comparison at 400 random epochs in 1900–2100, the maximum position errors of the Sun and Moon are 43.7″ and 30.0″, respectively. For the total solar eclipse of 2026 August 12, the point of greatest eclipse differs from the NASA value by 0.1° in both latitude and longitude, and the duration of totality differs by 1 second. Visibility is decided by a limiting-magnitude model that adds twilight, light pollution, and dark adaptation terms to Kasten–Young airmass, the Krisciunas–Schaefer moonlight model, and Schaefer's luminance conversion. The telescope is modelled with, among other terms, an aperture gain of $`5\log_{10}(D_\mathrm{eff}/7\,\mathrm{mm})+0.43`$ and a diffraction radius of $`138''/D`$. The grounds for leaving nutation, stellar aberration, and proper motion out of the computation are examined quantitatively, and the limits of the visibility model, which still rests on empirical constants, are discussed.

Keywords: ephemeris approximation, VSOP87, ΔT, precession, atmospheric refraction, airmass, sky brightness, limiting magnitude, real-time rendering

---

## 1. Introduction

Reproducing the sky inside a game engine imposes two requirements at once. One is positional accuracy that can be checked numerically against the real sky. The other is real-time performance while drawing from several hundred thousand to tens of millions of point sources every frame. Cheonsang set its accuracy targets with Stellarium as the primary reference and PyEphem as the cross-check reference. The target scale is about 1′, corresponding to naked-eye resolution. At high telescope magnification, the errors that become noticeable are limited to the relative arrangement of stars and the positions of planets and satellites.

Bundling a high-precision ephemeris (the JPL DE series) was not adopted because of its large constraints on download size and time span. The Sun, Moon, and planets are computed from analytical theories. The satellites and asteroids, for which no analytical theory is used, are computed from fitted formulas and position tables built from JPL Horizons values. The positions of all bodies are recomputed every frame from the absolute time (Julian Day), so integration errors do not accumulate. The server and the client share the same computation code, and the server sends only the reference time, compression rate, latitude, and longitude.

On the observing-conditions side, sky drawing and observation-log decisions were designed to call the same visibility function. Atmospheric extinction, sky brightness, observer state, and equipment gain are each applied once, in a fixed order, so that the same effect never enters twice.

Section 2 describes the models and formulas of each computation stage, from time scales to rendering. Section 3 presents the results of comparisons with external references. Section 4 discusses the simplifications in the models, their justification, and the remaining limits. All formulas and constants were checked against the 2.0.0 source code and data-processing scripts. File layout and commands are covered in the technical specification (technical-spec.md).

---

## 2. Methods

### 2.1 Time scales

#### 2.1.1 Game ticks and the Julian Day

A Minecraft day is 24,000 ticks. Cheonsang converts the world clock tick $`t`$ to a Julian Day on the UT scale.

```math
JD_\mathrm{UT} = JD_0 + \frac{t - t_0}{24000}\,k
```

$`t_0`$ and $`JD_0`$ are the server reference point, and $`k`$ is the compression rate (days of sky date advanced per game day; default 1.0, range 0.01–1000). When the compression rate is changed, the reference point is moved to the current time so that the sky does not jump discontinuously. Conversion between calendar dates and Julian Days uses the formulas of Meeus Chapter 7, and the Gregorian calendar applies from 1582 October 15.

#### 2.1.2 Terrestrial Time and ΔT

The theories of motion for the Sun, Moon, and planets take Terrestrial Time (TT), a uniform time scale, as their argument. The Julian century is

```math
T = \frac{JD_\mathrm{UT} + \Delta T/86400 - 2451545.0}{36525}
```

and $`\Delta T = \mathrm{TT} - \mathrm{UT}`$ (seconds) is obtained from the piecewise polynomials of Espenak and Meeus (2006). The year arguments are $`y = 2000 + (JD - 2451545.0)/365.25`$ and $`u = (y-1820)/100`$.

```math
\Delta T =
\begin{cases}
-20 + 32u^2 & y < 1900 \;\text{or}\; y \ge 2150 \\
-2.79 + 1.494119\,t - 0.0598939\,t^2 + 0.0061966\,t^3 - 0.000197\,t^4, \; t = y-1900 & 1900 \le y < 1920 \\
21.20 + 0.84493\,t - 0.076100\,t^2 + 0.0020936\,t^3, \; t = y-1920 & 1920 \le y < 1941 \\
29.07 + 0.407\,t - t^2/233 + t^3/2547, \; t = y-1950 & 1941 \le y < 1961 \\
45.45 + 1.067\,t - t^2/260 - t^3/718, \; t = y-1975 & 1961 \le y < 1986 \\
63.86 + 0.3345\,t - 0.060374\,t^2 + 0.0017275\,t^3 + 0.000651814\,t^4 + 0.00002373599\,t^5, \; t = y-2000 & 1986 \le y < 2005 \\
62.92 + 0.32217\,t + 0.005589\,t^2, \; t = y-2000 & 2005 \le y < 2050 \\
-20 + 32u^2 - 0.5628\,(2150 - y) & 2050 \le y < 2150
\end{cases}
```

ΔT has its largest effect on the position of the Moon. As recorded in a code comment, omitting the 2019 value of ΔT (about 69 seconds) displaces the Moon by about 35″. This equals the product of the Moon's mean angular rate (about 0.55″/s) and ΔT.

#### 2.1.3 Local mean time and local sidereal time

The Minecraft clock shows local mean time at the observing longitude $`\lambda`$, with tick 0 at 06:00. The hour angle of the mean Sun, $`H_\odot`$, is set from the tick, and the local sidereal time (LST) is obtained by adding it to the right ascension of the mean Sun.

```math
H_\odot = \frac{t}{24000}\times 360^\circ - 90^\circ, \qquad \alpha_\mathrm{m} = 280.46061837^\circ + 0.98564736629^\circ\,(JD - 2451545.0)
```

```math
\mathrm{LST} = \alpha_\mathrm{m}(JD) + H_\odot(t)
```

$`\alpha_\mathrm{m}`$ is the noon (UT) value taken from the Greenwich mean sidereal time of Meeus Eq. 12.4, $`\theta_0 = 280.46061837^\circ + 360.98564736629^\circ d + 0.000387933^\circ T^2 - T^3/38710000`$. For $`k = 1`$ the LST above equals $`\theta_0 + \lambda`$, and the difference consists only of terms of order $`T^2`$ and higher. With this construction, the times of day and night follow the Minecraft clock even when the compression rate is changed. The time of the Sun's transit moves with the season by about ±16 minutes, following the equation of time.

When the observing site is changed, the Minecraft clock is kept and the reference Julian Day is shifted to $`JD_0' = JD_0 + (\lambda_\mathrm{world} - \lambda_\mathrm{obs})/360`$. Universal Time shifts by 1 hour per 15° of longitude.

### 2.2 Coordinate systems and precession

#### 2.2.1 Transformation chain

All star, deep-sky object, planet, and asteroid data are stored in J2000.0 mean equatorial coordinates. The world direction vector is obtained through the following chain.

```math
\hat{\mathbf{s}}_\mathrm{world} = M(\varphi)\,R_z(-\mathrm{LST})\,P(T)\,\hat{\mathbf{s}}_\mathrm{J2000}
```

$`P`$ is the precession matrix from the J2000 equator to the mean equator of date, $`R_z(-\mathrm{LST})`$ is the rotation into hour-angle coordinates, and $`M(\varphi)`$ is the matrix that places the horizontal coordinates for latitude $`\varphi`$ on the Minecraft world axes (east $`+X`$, up $`+Y`$, north $`-Z`$). The Sun and Moon are already computed in mean equatorial coordinates of date, so $`P`$ is skipped for them. This 3×3 matrix is built once per frame on the CPU, and the per-star multiplication is done by the GPU vertex shader.

#### 2.2.2 Precession (Meeus Chapter 21)

The precession matrix is built from three angles $`\zeta`$, $`z`$, and $`\theta`$ ($`T`$ is Julian centuries from J2000.0).

```math
\zeta = 2306.2181''T + 0.30188''T^2 + 0.017998''T^3
```
```math
z = 2306.2181''T + 1.09468''T^2 + 0.018203''T^3
```
```math
\theta = 2004.3109''T - 0.42665''T^2 - 0.041833''T^3
```
```math
P = R_z(z)\,R_y(-\theta)\,R_z(\zeta)
```

The rotations follow the active-rotation convention. Nutation is not included, so the result is the mean equator of date (Section 4.1).

#### 2.2.3 Horizontal transformation

Equatorial Cartesian coordinates $`(x, y, z)`$ (x: vernal equinox, z: north celestial pole) are rotated into hour-angle coordinates by $`R_z(-\mathrm{LST})`$ and then placed on the world axes by the following matrix.

```math
M(\varphi) = \begin{pmatrix} 0 & 1 & 0 \\ \cos\varphi & 0 & \sin\varphi \\ \sin\varphi & 0 & -\cos\varphi \end{pmatrix}, \qquad (X, Y, Z) = (\text{east},\ \text{up},\ -\text{north})
```

In the northern hemisphere the north celestial pole lies in the $`-Z`$ direction at altitude $`\varphi`$ above the horizon. Conversion between ecliptic and equatorial coordinates uses the mean obliquity of the ecliptic from Meeus Eq. 22.2,

```math
\varepsilon_0 = 23^\circ 26' 21.448'' - 46.8150''T - 0.00059''T^2 + 0.001813''T^3
```

### 2.3 Sun

The Sun is computed with the low-precision theory of Meeus Chapter 25 (precision about 0.01° according to the code comment).

```math
L_0 = 280.46646^\circ + 36000.76983^\circ T + 0.0003032^\circ T^2
```
```math
M = 357.52911^\circ + 35999.05029^\circ T - 0.0001537^\circ T^2
```
```math
e = 0.016708634 - 0.000042037\,T - 0.0000001267\,T^2
```
```math
C = (1.914602^\circ - 0.004817^\circ T - 0.000014^\circ T^2)\sin M + (0.019993^\circ - 0.000101^\circ T)\sin 2M + 0.000289^\circ \sin 3M
```
```math
R = \frac{1.000001018\,(1-e^2)}{1 + e\cos(M + C)}\ \mathrm{AU}, \qquad \lambda_\odot = L_0 + C - 0.00569^\circ
```

The last term, $`-0.00569^\circ`$ (−20.5″), is the annual aberration correction. The nutation term of Meeus's apparent longitude ($`-0.00478^\circ\sin\Omega`$) is not included, so the result is the ecliptic longitude referred to the mean equinox of date, with aberration included. The latitude is set to 0. The apparent radius is $`\arcsin(696000\,\mathrm{km}/R)`$.

### 2.4 Moon

#### 2.4.1 Periodic terms

The Moon uses Table 47.A (60 terms for longitude and distance) and Table 47.B (60 terms for latitude) of Meeus Chapter 47. The fundamental arguments are fourth-degree polynomials in $`T`$ for the mean longitude $`L'`$, the mean elongation $`D`$, the Sun's mean anomaly $`M`$, the Moon's mean anomaly $`M'`$, and the argument of latitude $`F`$. The arguments of the additional terms for the perturbations by Venus and Jupiter and for the flattening of the Earth, $`A_1 = 119.75^\circ + 131.849^\circ T`$, $`A_2 = 53.09^\circ + 479264.290^\circ T`$, and $`A_3 = 313.45^\circ + 481266.484^\circ T`$, are used together with the eccentricity factor $`E = 1 - 0.002516\,T - 0.0000074\,T^2`$.

```math
\Sigma l = \sum_i l_i\,E^{|m_i|}\sin(d_i D + m_i M + m'_i M' + f_i F) + 3958\sin A_1 + 1962\sin(L'-F) + 318\sin A_2
```
```math
\Sigma r = \sum_i r_i\,E^{|m_i|}\cos(d_i D + m_i M + m'_i M' + f_i F)
```
```math
\Sigma b = \sum_j b_j\,E^{|m_j|}\sin(\cdots) - 2235\sin L' + 382\sin A_3 + 175\sin(A_1 - F) + 175\sin(A_1+F) + 127\sin(L'-M') - 115\sin(L'+M')
```
```math
\lambda = L' + \Sigma l/10^6, \qquad \beta = \Sigma b/10^6, \qquad \Delta = 385000.56 + \Sigma r/1000\ \mathrm{km}
```

The coefficient tables were read by a script from a public implementation (astronomia, MIT) to generate the Kotlin source automatically. The validation scripts also read the tables directly from this source, which rules out transcription errors. The theoretical precision stated in the code comment is about 10″.

#### 2.4.2 Observer parallax

The Moon lies at about 60 Earth radii, so its direction as seen from the Earth's center and from the surface differs by up to about 1°. The Moon on screen is always drawn in the topocentric direction.

```math
\mathbf{r}_\mathrm{topo} = \Delta\,\hat{\mathbf{m}} - R_\oplus\,\hat{\mathbf{o}}(\mathrm{LST}, \varphi), \qquad R_\oplus = 6378.14\ \mathrm{km}
```

Screen rendering approximates the Earth as a sphere, while eclipse prediction (Section 2.8.2) uses an ellipsoid.

#### 2.4.3 Phase and libration

The phase follows Meeus Chapter 48. From the geocentric Sun–Moon elongation $`\psi`$, the phase angle $`i = \operatorname{atan2}(R\sin\psi,\ \Delta - R\cos\psi)`$, the illuminated fraction $`k = (1+\cos i)/2`$, and the position angle of the bright limb $`\chi`$ are obtained.

For libration, only the optical libration and the position angle of the axis from Meeus Chapter 53 are computed. From the inclination of the lunar equator $`I = 1.54242^\circ`$, the mean longitude of the ascending node $`\Omega = 125.0445479^\circ - 1934.1362891^\circ T + 0.0020754^\circ T^2`$, and $`W = \lambda - \Omega`$, the quantities

```math
A = \operatorname{atan2}(\sin W\cos\beta\cos I - \sin\beta\sin I,\ \cos W\cos\beta), \qquad l = A - F
```
```math
b = \arcsin(-\sin W\cos\beta\sin I - \sin\beta\cos I)
```

are obtained, and the position angle $`P`$ uses Meeus Eq. 53.3. Physical libration and nutation are omitted; their size is around 0.04° (Section 4.2).

### 2.5 Planets

#### 2.5.1 Truncated VSOP87D

Planetary positions use the heliocentric spherical-coordinate series of VSOP87D (Bretagnon & Francou 1988).

```math
X = \sum_{\alpha} \tau^\alpha \sum_k A_k \cos(B_k + C_k\,\tau), \qquad X \in \{L, B, R\}, \qquad \tau = T/10
```

$`\tau`$ is Julian millennia (TT) from J2000.0. From the series for Mercury through Neptune and for the Earth, only terms with amplitude $`A_k \ge 3\times10^{-6}`$ (rad for L and B; the same number in AU for R) were kept, giving 1,259 terms in total in the source. The generation script writes the coefficients as strings, which are parsed once at run time into arrays.

#### 2.5.2 Coordinate transformation

VSOP87D is referred to the ecliptic and equinox of date. To bring positions into the same J2000 equatorial coordinates as the stars, the following sequence is applied.

```math
\mathbf{h}_\mathrm{J2000\,ecl} = R_x(-\varepsilon_\mathrm{J2000})\;P(T)^{\mathsf{T}}\;R_x(\varepsilon_0(T))\;\mathbf{h}_\mathrm{date\,ecl}, \qquad \varepsilon_\mathrm{J2000} = 23.43928^\circ
```

The ecliptic of date is rotated to the equator of date, returned to the J2000 equator by the transpose (inverse) of the precession matrix, and then moved to the J2000 ecliptic. The Earth is computed from the same series.

#### 2.5.3 Light-time

The geocentric position is the difference between the planet's position at the time the light left the planet and the Earth's position at the time of observation.

```math
\mathbf{g} = \mathbf{h}_p(t - \tau_\mathrm{lt}) - \mathbf{h}_\oplus(t), \qquad \tau_\mathrm{lt} = 0.0057755\ \mathrm{day\,AU^{-1}} \times |\mathbf{g}|
```

$`\tau_\mathrm{lt}`$ is updated by two iterations. The resulting direction is placed on the J2000 equator by $`R_x(\varepsilon_\mathrm{J2000})\,\mathbf{g}`$; these are the same astrometric coordinates as those of the star catalog.

#### 2.5.4 Brightness and disk

The phase angle $`i`$ is obtained from the Sun–planet distance $`r`$, the Earth–planet distance $`\Delta`$, and the Sun–Earth distance $`R`$ as $`\cos i = (r^2 + \Delta^2 - R^2)/(2r\Delta)`$. The apparent magnitude uses the approximate formula of Meeus Chapter 41.

```math
m = m_0 + 5\log_{10}(r\Delta) + f(i)
```

$`m_0`$ is −0.42 for Mercury, −4.40 for Venus, −1.52 for Mars, −9.40 for Jupiter, −7.19 for Uranus, and −6.87 for Neptune. The phase term $`f(i)`$ is $`0.0380i - 0.000273i^2 + 0.000002i^3`$ for Mercury, $`0.0009i + 0.000239i^2 - 0.00000065i^3`$ for Venus, $`0.016i`$ for Mars, and $`0.005i`$ for Jupiter. Saturn is computed with the ring tilt $`B`$ as $`m = -8.88 + 5\log_{10}(r\Delta) - 2.60\sin|B| + 1.25\sin^2 B`$. $`B`$ is obtained from the dot product of the IAU north pole of rotation (J2000 right ascension 40.589°, declination 83.537°) and the line of sight. The apparent diameter is the diameter at 1 AU (Meeus Chapter 55) divided by $`\Delta`$.

The central longitude and latitude of the disk and the position angle of the axis for Mars and Jupiter are computed from the IAU rotation models (pole $`\alpha_0 = a_0 + a_1 T`$, $`\delta_0 = d_0 + d_1 T`$; prime meridian $`W = w_0 + w_1 d`$) with light-time applied. Mars uses the IAU 2009 constants, and Jupiter uses System II (rotation rate 870.27°/day), which is referenced to the Great Red Spot. The System II longitude of the Great Red Spot is a linear approximation of the JUPOS trend: 91° on 2026-06-01, increasing by 1.75° per month.

#### 2.5.5 Computational cost

One computation of the seven planets takes 28µs. Rendering computes only two epochs 1 second of sky time (20 ticks) apart and interpolates the direction between them. When the compression rate is so high that more than 0.5 day passes per second of sky time, the positions are computed every frame.

### 2.6 Satellites

#### 2.6.1 The four Galilean satellites of Jupiter

The Galilean satellites are computed with the low-precision formulas of Meeus Chapter 44. These formulas give $`X`$ (positive west) and $`Y`$ (positive north) in units of Jupiter's equatorial radius, with the center of Jupiter as origin. From each satellite's corrected argument $`u`$, its distance $`r`$, and the jovicentric latitude of the Earth on Jupiter's equatorial plane, $`D_E`$,

```math
X = r\sin u, \qquad Y = -r\cos u\,\sin D_E
```

are obtained and then rotated by the position angle $`P`$ of Jupiter's axis into east and north offsets on the sky. A satellite is judged to be in front of Jupiter when $`\cos u > 0`$. The precision stated in the code comment is about 0.5 Jupiter radius.

#### 2.6.2 Seven satellites of Saturn

For the satellites of Saturn (Mimas, Enceladus, Tethys, Dione, Rhea, Titan, and Iapetus), no analytical theory is included. Instead, formulas fitted to JPL Horizons position vectors at 1-day intervals over 1900–2050 are used. In Saturn's equatorial plane (fixed pole), the formulas are as follows.

```math
\lambda = \lambda_0 + n\,t, \qquad M = \lambda - \dot\varpi\,t
```
```math
u = \lambda + c_1\sin M + c_2\cos M + c_3\sin 2M + c_4\cos 2M + \delta u_\mathrm{lib}
```
```math
r = a_0 + a_1\sin M + a_2\cos M
```
```math
h = r\left[(k_0 + k_2 t + k_4 t^2)\sin u + (k_1 + k_3 t + k_5 t^2)\cos u\right]
```

Here $`t = JD_\mathrm{TT} - 2451545.0 - \tau_\mathrm{lt}`$ (days), and the constant term of the longitude of pericenter is absorbed into $`\lambda_0`$. $`\delta u_\mathrm{lib}`$ is the long-period libration term $`(l_0 + l_2 t)\sin\phi + (l_1 + l_3 t)\cos\phi`$ ($`\phi = 360^\circ t/p`$), and $`h`$ is the height above the equatorial plane. For Iapetus, terms with period $`2\lambda`$ are added to $`u`$ and $`r`$. The offset from the center of Saturn is added to Saturn's position to give the direction. A satellite that lies behind Saturn along the line of sight, at a distance perpendicular to the line of sight smaller than Saturn's radius (60,268 km), is treated as occulted. Magnitudes are fixed at their mean values near opposition.

### 2.7 Asteroids

For Ceres and Vesta, tables of heliocentric J2000 ecliptic Cartesian coordinates obtained from JPL Horizons (1900–2050, 4-day intervals) are interpolated with 4-point Lagrange interpolation.

```math
\mathbf{h}(x) = \sum_{k=0}^{3} w_k(\tau)\,\mathbf{h}_{i_0+k}, \qquad w_0 = -\tfrac{\tau(\tau-1)(\tau-2)}{6},\; w_1 = \tfrac{(\tau+1)(\tau-1)(\tau-2)}{2},\; w_2 = -\tfrac{(\tau+1)\tau(\tau-2)}{2},\; w_3 = \tfrac{(\tau+1)\tau(\tau-1)}{6}
```

The Earth's position comes from the VSOP87 series, and light-time is corrected with two iterations. Magnitudes use the IAU H,G system (Bowell et al. 1989).

```math
m = H + 5\log_{10}(r\Delta) - 2.5\log_{10}\left[(1-G)\,\Phi_1 + G\,\Phi_2\right], \quad \Phi_1 = e^{-3.33\tan^{0.63}(\alpha/2)}, \; \Phi_2 = e^{-1.87\tan^{1.22}(\alpha/2)}
```

$`(H, G)`$ is (3.34, 0.12) for Ceres and (3.20, 0.32) for Vesta. The asteroids are not drawn for dates outside the table range.

### 2.8 Solar and lunar eclipses

#### 2.8.1 Eclipses in sky rendering

Eclipses on screen are not created as separate events; they arise on their own from the computed positions and sizes. A solar eclipse occurs when the topocentric lunar disk covers the solar disk, and the sky is darkened according to the overlapping area fraction $`o`$.

```math
d_\mathrm{ecl} = 0.25\,o + 0.75\,\mathrm{smoothstep}(0.9, 1, o), \qquad \text{sky color} \times (1 - 0.92\,d_\mathrm{ecl})
```

Lunar eclipses use the shadow radii of Meeus Chapter 54 with the conventional factor of 1.02 for enlargement of the shadow by the Earth's atmosphere. From the Moon's horizontal parallax $`\pi_\mathrm{m} = \arcsin(R_\oplus/\Delta)`$, the Sun's apparent radius $`s`$, and the Sun's horizontal parallax $`\pi_\odot = 8.794''/R`$,

```math
\rho_\mathrm{u} = 1.02\,(0.998340\,\pi_\mathrm{m} - s + \pi_\odot), \qquad \rho_\mathrm{p} = 1.02\,(0.998340\,\pi_\mathrm{m} + s + \pi_\odot)
```

are obtained. From the angular distance $`\sigma`$ between the Moon's center and the antisolar point and the Moon's radius $`r_\mathrm{m}`$, the magnitude $`(\rho + r_\mathrm{m} - \sigma)/(2r_\mathrm{m})`$, clipped to [0, 1], is used as the umbral or penumbral coverage fraction. The Moon is tinted red in proportion to the umbral coverage, and the moonlit sky brightness is reduced by up to 98%.

#### 2.8.2 Eclipse prediction

For solar eclipse prediction, the central line is found at each new moon as the intersection of the Sun→Moon shadow axis with the Earth ellipsoid of flattening $`f = 1/298.257`$. Greatest eclipse is taken at the instant when the axis passes closest to the Earth's center, and the eclipse is classified as total, annular, or hybrid from the apparent radii of the Moon and Sun, with parallax applied, at points on the central line. Obscuration at the observing site is evaluated at 2-minute intervals over ±3 hours from greatest eclipse. For lunar eclipses, the penumbral and umbral contacts (P1, U1, U2, U3, U4, P4) are searched at 1-minute intervals and then refined to 1 second. The search range is 60 years.

### 2.9 Atmospheric refraction

Refraction uses the formula of Sæmundsson (1986) for a pressure of 1010 hPa and a temperature of 10 °C.

```math
R = \frac{1.02'}{\tan\!\left(h + \dfrac{10.3}{h + 5.11}\right)}
```

$`h`$ is the geometric altitude (degrees). Values below −0.9° are clamped to −0.9°, and no refraction is applied below −1.5°. Refraction at the horizon is about 0.5°. For stars the vertex shader applies the formula per vertex; for the Sun, Moon, and planets the CPU applies the same formula. When a body is picked on screen, the drawn (apparent) position must be converted back to the geometric position, so the formula of Bennett (1982), which takes apparent altitude as its argument, is used.

```math
R = \frac{1'}{\tan\!\left(h_a + \dfrac{7.31}{h_a + 4.4}\right)}
```

The vertical flattening of the Sun and Moon at the horizon comes from the difference in refraction between the upper and lower limbs of the disk. Rising and setting times are found by sampling at 10-minute intervals followed by bisection. The reference altitude is −0°50′ for the Sun and −0°34′ for stars and planets; for the Moon, parallax is corrected.

### 2.10 Airmass and atmospheric extinction

Airmass uses the approximation of Kasten and Young (1989).

```math
X(h) = \frac{1}{\sin h + 0.50572\,(h + 6.07995^\circ)^{-1.6364}}
```

Values of $`h`$ below −0.5° are set to −0.5°, and $`X`$ is capped at 40 (about 38 at the horizon). Extinction in the V band, relative to the zenith, is as follows.

```math
\Delta m = k_V\,(X - 1), \qquad k_V = 0.172\ \mathrm{mag\,airmass^{-1}}
```

Extinction is added only at this stage of the visibility computation. For the color change of stars near the horizon, the extinction differences of the R and B channels relative to V, 0.082 and 0.128 mag per airmass, are applied at half perceptual strength ($`10^{\pm0.2\,\Delta k\,(X-1)}`$) and held fixed beyond $`X = 10`$ (altitude about 5.5°).

### 2.11 Sky brightness

#### 2.11.1 Unit conversion

To add the components of sky brightness, the surface brightness $`S`$ in mag/arcsec² must be converted to linear luminance. The conversion of Schaefer (1990),

```math
B\,[\mathrm{nL}] = 34.08\,\exp(20.7233 - 0.92104\,S)
```

is used. The zenith brightness of a dark natural sky is set to $`S_\mathrm{dark} = 22.0`$ mag/arcsec².

#### 2.11.2 Components

```math
B = B_\mathrm{nat} + B_\mathrm{tw} + B_\mathrm{moon} + B_\mathrm{LP} + B_\mathrm{zod}
```

The natural sky (including airglow) is multiplied by a van Rhijn-type brightening toward the horizon and by half of the extinction.

```math
B_\mathrm{nat} = B_\mathrm{dark}\,\frac{1}{\sqrt{1 - 0.96\sin^2 z}}\;10^{-0.2\,k_V (X - 1)}
```

The twilight and daytime sky are linearly interpolated from a table of zenith brightness versus solar altitude.

| Solar altitude | −24° | −18° | −15° | −12° | −9° | −6° | −3° | 0° | 5° | 10° | 20° | 40° | 90° |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Zenith brightness (mag/arcsec²) | 30.0 | 24.0 | 21.0 | 18.2 | 15.2 | 12.2 | 9.5 | 7.3 | 5.5 | 4.6 | 3.9 | 3.4 | 3.0 |

During a solar eclipse, the twilight component is multiplied by $`(1 - 0.995\,d_\mathrm{ecl})`$.

The moonlit sky uses the model of Krisciunas and Schaefer (1991).

```math
B_\mathrm{moon} = f(\rho)\,I^*\,10^{-0.4\,k_V X(Z_\mathrm{m})}\left[1 - 10^{-0.4\,k_V X(Z)}\right]
```
```math
f(\rho) = 10^{5.36}\,(1.06 + \cos^2\rho) + 10^{6.15 - \rho/40}, \qquad I^* = 10^{-0.4\,(3.84 + 0.026\,|\alpha| + 4\times10^{-9}\alpha^4)}
```

$`\alpha`$ is the lunar phase angle and $`\rho`$ is the angular distance between the Moon and the viewing direction (values below 0.5° are set to 0.5°). The airmass in this term is the K&S form $`X(Z) = (1 - 0.96\sin^2 Z)^{-1/2}`$. For umbral coverage $`u`$ during a lunar eclipse the term is multiplied by $`(1 - 0.98\,u)`$, and it is switched on linearly as the Moon rises from 1° below the horizon to the horizon.

Sky brightness is evaluated at a single point, the zenith, regardless of viewing direction, and that value is applied to the whole sky. This choice removes the change in the number of stars across the whole sky that would otherwise occur when the player turns their head; its cost is discussed in Section 4.5.

### 2.12 Light pollution, dark adaptation, and limiting magnitude

#### 2.12.1 From block light to the Bortle scale

Light pollution is computed as a Bortle scale value from the surrounding block light sources. The area within ±64 blocks of the observer is sampled every 8 blocks (17×17 = 289 points), and only the block light $`\ell`$ (0–15) of cells whose sky light value is 15 (cells open to the sky) is summed, weighted by distance $`d`$.

```math
L = \sum \frac{\ell^2}{1 + (d/16)^2}, \qquad \mathrm{Bortle} = 1 + 8\,\left(1 - e^{-L/800}\right)
```

Sampling is spread over ticks at 40 points per tick, and a round is discarded if the observer moves more than 8 blocks or the chunk changes. The Bortle value is converted to $`B_\mathrm{LP}`$ through a table of zenith brightness (rough estimates).

| Bortle | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|
| Zenith brightness (mag/arcsec²) | 21.9 | 21.6 | 21.4 | 20.8 | 20.0 | 19.1 | 18.5 | 18.0 | 17.5 |

#### 2.12.2 Dark adaptation

The adaptation ceiling is set by $`E`$ (0–15), the larger of the block light at eye position and the light source held in hand. If the solar altitude is above −4° and the sky is visible, $`E = 15`$.

```math
a_\mathrm{max} = 1 - (E/15)^{0.7}, \qquad \Delta L_\mathrm{dark} = 3\,(1 - a)
```

The adaptation level $`a`$ drops to $`a_\mathrm{max}`$ immediately when the surroundings brighten, and recovers linearly over 30 seconds when they darken.

#### 2.12.3 Point-source limiting magnitude

The order of application is fixed: (1) intrinsic brightness of the object, (2) atmospheric extinction, (3) sky brightness, (4) observer state, (5) equipment, (6) decision. The loss of limiting magnitude due to sky brightness is

```math
\Delta L_\mathrm{sky} = \max\!\left(0,\ 1.5\log_{10}\frac{1 + B/B_\mathrm{dark}}{2}\right)
```

and the point-source limiting magnitude and the decision are as follows.

```math
L = \min(L_0 + G,\ L_\mathrm{max}) - \Delta L_\mathrm{sky} - \Delta L_\mathrm{dark}, \qquad m + k_V(X - 1) \le L
```

$`L_0`$ is the naked-eye limit (default 6.0 mag, configurable 4.0–6.5), $`G`$ is the telescope gain (Section 2.13; 0 for the naked eye), and $`L_\mathrm{max}`$ is 13.0 mag (15.2 mag if the Gaia star extension data are present). According to the code comment, $`\Delta L_\mathrm{sky}`$ is about 1.7 mag on a full-moon night, about 5.4 mag in twilight with the Sun at −6°, and about 10.7 mag in daytime. With only the Bortle zenith brightness entered in this formula (no Moon, twilight, or dark adaptation), the naked-eye limit is 6.0 mag at Bortle 1, 5.2 mag at Bortle 5, and 3.7 mag at Bortle 9.

Extended sources (the Milky Way and the glow of nebulae and galaxies) are washed out by the sky separately from stars, by a factor of $`(B_\mathrm{dark}/B)^{0.6}`$ (about 0.15 under a full-moon sky). Their surface brightness, corrected for exit pupil, is compared with a threshold surface brightness that depends on apparent size,

```math
S_\mathrm{thr}(a) = 26.5 - 2.5\log_{10}\!\left[1 + \left(\frac{15'}{a\,M}\right)^2\right]
```

($`a`$: major axis (′), $`M`$: magnification).

### 2.13 Telescope optics

#### 2.13.1 Configuration and magnification

The objective aperture $`D`$ has 12 steps, 50, 60, 70, 80, 90, 102, 114, 127, 150, 200, 254, and 305 mm (default 80 mm). The eyepiece focal length $`f_e`$ has 11 steps, 40, 32, 25, 20, 15, 12, 9, 7, 5, 4, and 3 mm. The focal ratio is fixed at f/7.

```math
F = 7D, \qquad M = \frac{F}{f_e} = \frac{7D}{f_e}, \qquad d_\mathrm{exit} = \frac{D}{M}
```

The maximum magnification is about 712×, with the 305 mm and 3 mm combination. There is no electronic zoom.

#### 2.13.2 Limiting magnitude

The point-source gain comes from the area ratio of the effective aperture to the dark-adapted pupil (7 mm).

```math
G = 5\log_{10}\frac{D_\mathrm{eff}}{7\ \mathrm{mm}} + 0.43, \qquad D_\mathrm{eff} = \min(D,\ 7\,\mathrm{mm}\times M)
```

$`D_\mathrm{eff}`$ accounts for the case in which the exit pupil is larger than the eye's pupil, so that part of the light cannot enter the eye. With f/7 fixed and the longest eyepiece at 40 mm, the exit pupil is at most $`40/7 \approx 5.7`$ mm, so this limit does not come into play with the current set of steps. The constant 0.43 makes the result agree with a widely used approximation at $`L_0 = 6.5`$.

```math
6.5 + 5\log_{10}\frac{D_\mathrm{mm}}{7} + 0.43 = 7.705 + 5\log_{10} D_\mathrm{cm}
```

At the default $`L_0 = 6.0`$, the limiting magnitude is 11.7 mag for an 80 mm aperture and 14.6 mag for 305 mm (dark sky, full adaptation; for 305 mm, with the extension data).

#### 2.13.3 Surface brightness of extended sources

Through the telescope, the surface brightness of an extended source drops by the area ratio when the exit pupil is smaller than the eye's pupil.

```math
\frac{S_\mathrm{tel}}{S_\mathrm{eye}} = \min\!\left[1,\ \left(\frac{d_\mathrm{exit}}{7\ \mathrm{mm}}\right)^2\right]
```

This term is why the Milky Way and nebulae grow fainter as magnification increases.

#### 2.13.4 Resolution and star images

The radius of the first dark ring of the Airy disk for a circular aperture is $`1.22\lambda/D`$, which at $`\lambda \approx 550`$ nm becomes $`138''/D_\mathrm{mm}`$. Cheonsang combines this value with a seeing of 1.5″ to set the minimum radius of a star image.

```math
r_\star = 0.6\sqrt{\left(\frac{138''}{D_\mathrm{mm}}\right)^2 + (1.5'')^2}
```

$`r_\star`$ is converted to screen pixels, and if it exceeds 1.5 px the star is spread out by that amount. The maximum useful magnification is set at $`2D_\mathrm{mm}`$, but higher magnification is not blocked; above it the image grows larger and blurs. Close double stars receive no separate resolution test; they appear as two once the screen separation of the components exceeds the star image size.

### 2.14 Star data processing

#### 2.14.1 Catalog composition

| Catalog | Coverage | Original photometry and epoch | Result |
|---|---|---|---|
| HYG v4.1 | Down to V 9.0, names, constellations, Bayer designations | V, J2000 | 83,479 stars; with 7,459 WDS companions added, 90,938 naked-eye stars |
| Tycho-2 | Telescopic stars fainter than V 9 | $`V_T`$, $`B_T`$, J2000 mean positions | 2,409,260 stars, down to 15.37 mag (including Gaia cluster and Magellanic Cloud stars) |
| Gaia DR3 | Replacement of values for Tycho matches; extension data 9 < V ≤ 15.2 | $`G`$, $`G_{BP}-G_{RP}`$, J2016.0 | 32,061,510 stars |
| WDS | Splitting close doubles merged into one point in HYG | Component magnitudes, position angle, separation | 7,459 companions |

All stars are brought to a single system of J2000.0 positions, Johnson V magnitudes, and B−V color indices.

#### 2.14.2 Photometric transformation

Tycho photometry uses the standard transformations.

```math
V = V_T - 0.090\,(B_T - V_T), \qquad B - V = 0.850\,(B_T - V_T)
```

Gaia $`G`$ is converted to V with the polynomial of Riello et al. (2021) ($`x = G_{BP} - G_{RP}`$; values outside −0.5–5.0 take the boundary value).

```math
V = G - \left(-0.02704 + 0.01424\,x - 0.2156\,x^2 + 0.01426\,x^3\right)
```

Without color information, $`V = G + 0.1`$ and $`B-V = 0.65`$ are used. For Gaia B−V, matches with precise Tycho colors (V < 10.5) are binned at 0.1 intervals of $`G_{BP}-G_{RP}`$, the median B−V is computed for each bin (30 or more stars), and the resulting table, forced to be monotonically increasing, is interpolated. The base data and the extension data use the same transformation function, so the same star never has different magnitudes in the two files.

#### 2.14.3 Epoch transformation

Gaia positions (J2016.0) are moved back 16 years by proper motion (linear approximation).

```math
\delta_{2000} = \delta_{2016} - 16\,\mu_\delta, \qquad \alpha_{2000} = \alpha_{2016} - \frac{16\,\mu_{\alpha*}}{\cos\delta}
```

#### 2.14.4 Cross-matching

If the same star is drawn twice, its brightness is doubled and a double star appears as three. The following criteria identify the same star across catalogs.

| Catalogs matched | Same-star criterion and handling |
|---|---|
| Tycho-2 ↔ Gaia | Official Gaia match table (tycho2tdsc_merge_best_neighbour). If the two V values differ by more than 0.75 mag, the Tycho value is kept |
| Tycho-2 ↔ HYG | Excluded if within 5″ of a HYG star and V ≤ 9.8; V ≤ 9.0 is also excluded |
| Gaia extension ↔ base | In addition to the rules above, excluded if within 3″ of a HYG star with magnitude difference < 1, or within 2″ of a base star with magnitude difference < 0.75 |
| WDS ↔ HYG | Within 15″ in position (+ proper motion × 30 years), magnitude difference within 1. Primary ≤ 9.5 mag, companion ≤ 13 mag, separation 0.5–30″ |

#### 2.14.5 Sky cells

To read only the stars near the telescope field quickly, the sky is divided into cells of about 1 deg². For the 180 declination bands of 1°,

```math
n_\alpha(b) = \max\!\left(1,\ \mathrm{round}(360\cos\delta_b)\right), \qquad \delta_b = -89.5^\circ + b
```

giving 41,252 cells in total. Stars within a cell are stored in order of brightness, so the number of stars down to the limiting magnitude is found by binary search and only that many are drawn from the start. Star records in the Gaia extension data have a quantization precision of about 0.055″ in position and 0.04 mag in magnitude. Details of the storage format are covered in the technical specification.

### 2.15 Star display: from magnitude to screen brightness

Stars are drawn without textures; the vertex shader computes a circular Gaussian point. From the magnitude including extinction, $`m`$, and the limiting magnitude $`L`$, the opacity $`\alpha`$ and size $`s`$ are set.

```math
\alpha = \mathrm{clamp}\!\left(\frac{L + 0.5 - m}{5},\ 0,\ 1\right)^{1.4}, \qquad s = \mathrm{clamp}\!\left(0.22\times10^{-0.1(m - L)},\ 0.02,\ 1.3\right)
```

If the screen radius $`r_\mathrm{want}`$ corresponding to $`s`$ is smaller than the minimum radius $`r_\mathrm{min} = \max(1.5\ \mathrm{px},\ r_\star)`$, the radius is enlarged to $`r_\mathrm{min}`$ and the opacity is multiplied by $`(r_\mathrm{want}/r_\mathrm{min})^2`$, which conserves the total light $`\alpha r^2`$. The light on screen is therefore

```math
\Phi_\mathrm{disp} \propto \alpha(m)\,s^2 \propto \alpha(m)\,10^{-0.2\,(m - L)}
```

Compared with the true flux ratio $`10^{-0.4\,\Delta m}`$, the logarithmic slope is halved, so displayed brightness is compressed to the square root of the true brightness ratio. Size reaches its cap of 1.3 for stars about 7.7 mag brighter than the limit. This compression fits both first-magnitude stars and stars near the limit into the finite dynamic range of a monitor.

Star colors are obtained by computing the effective temperature from B−V with the formula of Ballesteros (2012), converting it to RGB with a blackbody color approximation, and mixing in 22% white.

```math
T_\mathrm{eff} = 4600\ \mathrm{K}\left(\frac{1}{0.92\,(B-V) + 1.7} + \frac{1}{0.92\,(B-V) + 0.62}\right)
```

Color saturation is set by how much brighter the star is than the limit, as $`0.55\,\mathrm{clamp}((L - m - 1.5)/4,\ 0.12,\ 1)`$, so stars near the limit look almost white. This approximates the loss of cone response at low light levels.

### 2.16 Milky Way rendering

The Milky Way source is NASA SVS Deep Star Maps 2020. The base data include a 12K grayscale all-sky image for the naked eye and an 8K color all-sky image for long exposures; the extension data include 16K and 32K naked-eye images split into tiles. Each frame, the center of the view and the half-angle of the screen diagonal are computed, and the coarsest level whose texel angular size is at most 1.25 times a screen pixel is selected. If the base image is sufficient, no tiles are used. Only tiles within the view are loaded, nearest first, and cross-faded over a short time; sampling uses a Mitchell bicubic (4×4) filter with dithering. Details of tile loading and memory management are covered in the technical specification.

The opacity of the naked-eye image is as follows.

```math
k_\mathrm{MW} = 0.75\;W_\mathrm{sky}\;Z\;\max(C,\ 0.35)\;(1 - f_\mathrm{Gaia})
```

$`W_\mathrm{sky} = (B_\mathrm{dark}/B)^{0.6}`$ is the degree of washing out by the sky; $`Z`$ is the magnification attenuation, the 0.33 power of the exit pupil ratio with the fraction resolved into stars removed; and $`C`$ is a term that falls from 1 to 0 between magnifications of 7× and 40×.

$`f_\mathrm{Gaia}`$ prevents the same starlight from being added twice, once in the image and once as points, when Gaia stars are drawn individually in the telescope. When the extension data were built, the Gaia starlight in each HEALPix cell (nside 32, 12,288 cells) was accumulated by G magnitude bin, and the fraction of the total starlight fainter than 11.5 mag contributed by stars down to limiting magnitude V was stored at 0.5 mag steps (11.5–15.5).

```math
f(\text{cell}, V) = \frac{\sum_{G < V - 0.3} F_G}{\sum_{G \ge 11.5} F_G}
```

At run time, the value for the cell at the center of the screen is linearly interpolated for the current limiting magnitude.

---

## 3. Results

### 3.1 Validation method

The astronomical computations are checked with test scripts that port the Kotlin formulas to Python. The lunar periodic terms and the planetary coefficient tables are read directly from the Kotlin source, which rules out transcription errors. The references are PyEphem (an independent ephemeris library), worked examples from Meeus, records of actual solar and lunar eclipses together with NASA predictions, and JPL Horizons. The 82 items in the four test scripts (41, 24, 11, and 6 items) all passed in the run of 2026-11-14. Planetary positions were compared with the astrometric J2000 positions of PyEphem in a separate script. This validation matches a Python implementation of the same formulas against the references; it does not include tests that measure the game screen directly.

### 3.2 Calendar, Sun, Moon, and precession

Table 1. Results of the basic ephemeris comparison

| Item tested (number of items) | Reference | Tolerance | Result |
|---|---|---|---|
| Calendar, leap years, JD (10) | Meeus Examples 7.a and 7.b; daily round trip 1890–2110 | JD $`10^{-6}`$ day, 0 round-trip errors | All match |
| Sun, Moon, and precession examples (7) | Meeus Examples 25.a, 47.a, 21.b | Sun 0.002°; Moon 0.0005° and 0.5 km; precession 0.0002° | Solar longitude difference 0.00001°, lunar distance difference 0.02 km |
| Sun and Moon at 400 random epochs (4) | PyEphem, 1900–2100 | Position 60″, lunar distance 30 km, phase 0.5% | Max Sun 43.7″, Moon 30.0″, distance 9.8 km, phase exceedances 0/200 |
| Lunar eclipse type (4) | Actual lunar eclipses of 2019 and 2020 | Total, partial, penumbral, or none must match | 4/4 |

The maximum solar error of 43.7″ is of the same size as the nominal precision of the low-precision theory of Meeus Chapter 25 (0.01° = 36″). The maximum lunar position error of 30.0″ is half of the naked-eye resolution of 1′ and 1/60 of the Moon's apparent diameter (about 30′).

### 3.3 Planetary positions

Table 2. Maximum errors of planetary positions compared with PyEphem (1800–2050)

| Planet | Truncated VSOP87D (1,259 terms) | Approximate Keplerian elements (Standish & Williams 1992) |
|---|---|---|
| Mercury | 4.3″ | |
| Venus | 5.4″ | |
| Mars | 12.6″ | 1.1′ |
| Jupiter | 4.3″ | 7.9′ |
| Saturn | 3.8″ | 11.5′ |
| Uranus | 4.0″ | 17.1′ |
| Neptune | 2.4″ | |

Blank cells indicate that the source data have no value under the same conditions. The approximate Keplerian elements do not include the great inequality of Jupiter and Saturn, which leaves errors of order arcminutes for Jupiter, Saturn, and Uranus. The truncated VSOP87D reduces these errors by one to two orders of magnitude, bringing all planets within 13″.

Over a wider time range, the maximum error of the truncated VSOP87D is about 2′ over the whole span from 1000 BCE to 3000 CE. The values by interval recorded in the code comment are 4–7″ for Mercury, Venus, Jupiter, Saturn, and Uranus, 13″ for Mars, and 4″ for Neptune in 1000–3000 CE; 1–2′ from 1000 BCE to 1000 CE; and 2–15′ from 3000 BCE to 1000 BCE. One computation of the seven planets costs 28µs.

### 3.4 Satellites and planetary disk orientation

Table 3. Comparison of satellite positions and disk orientation

| Target | Reference | Result |
|---|---|---|
| Positions of the four Galilean satellites | PyEphem, 40 random epochs in 2000–2030 × 4 satellites | Max 0.13 Jupiter radius |
| Jupiter satellite in front/behind decision | Same sample | 160/160 match |
| Fitted formulas for Saturn's satellites | Horizons 1900–2050 at 1-day intervals (mean fit residual) | Dione 0.02″, Enceladus 0.1″, Rhea 0.3″, Tethys 0.5″, Titan 0.7″, Mimas 0.7″, Iapetus 1.0″ (max 1.5″ for Titan and Rhea) |
| Mars disk (2026-11-11 0h) | Horizons | Central longitude 1.27° (1.28°), central latitude 21.2° (21.4°), axis position angle 5.4° (5.5°) |
| Jupiter System III central longitude (same time) | Horizons | 45.41° (45.41°) |

Values in parentheses are from Horizons. The maximum error of the Jupiter satellites, 0.13 Jupiter radius, is smaller than the nominal precision of the low-precision formulas (about 0.5 radius). The figures for Saturn's satellites are residuals against the fitting sample, so they do not guarantee the accuracy of extrapolation outside the sample (before 1900 or after 2050).

### 3.5 Comparison of solar and lunar eclipses with NASA

Table 4. Comparison of eclipse predictions with NASA

| Item | Cheonsang | NASA |
|---|---|---|
| Point of greatest eclipse, total solar eclipse of 2026-08-12 | 65.1°N 25.1°W | 65.2°N 25.2°W |
| Duration of totality, same eclipse | 2 min 19 s | 2 min 18 s |
| Width of central path, same eclipse | 301 km | 294 km |
| Umbral magnitude, partial lunar eclipse of 2026-08-28 | 0.935 | 0.930 |
| Six contacts, total lunar eclipse of 2028-12-31 | Within ±1 minute of NASA times | |
| Solar eclipse type classification | 2027-02-06 annular, 2027-08-02 total, 2023 hybrid, 2014 non-central annular | Match |

The 0.1° difference in latitude of the point of greatest eclipse is about 11 km on the ground. The path width differs by 7 km (2.4%) and the duration by 1 second. The causes of these differences were not separated; candidates are the position errors of the solar and lunar theories (Sun max 43.7″, Moon max 30.0″) and the assumption of a smooth disk that ignores lunar limb topography. One prediction took 1–150 ms to compute.

### 3.6 Lunar libration

Table 5. Comparison of libration with Meeus Example 53.a (1992-04-12)

| Quantity | Cheonsang | Meeus |
|---|---|---|
| Libration in longitude $`l`$ | −1.20° | −1.206° |
| Libration in latitude $`b`$ | +4.19° | +4.194° |
| Position angle of axis $`P`$ | 15.07° | 15.08° |

The differences are at the 0.01° level and correspond to the omitted physical libration and nutation.

### 3.7 Computed values of the optical model

Table 6. Optical values by aperture (dark sky, full adaptation)

| Aperture | Objective focal length | Magnification range (eyepiece 40→3 mm) | Limiting magnitude ($`L_0`$ = 6.0) | Limiting magnitude ($`L_0`$ = 6.5) | Airy radius 138″/D | Star image radius $`r_\star`$ | Maximum useful magnification $`2D`$ |
|---|---|---|---|---|---|---|---|
| 50 mm | 350 mm | ×8.8–×117 | 10.7 | 11.2 | 2.76″ | 1.88″ | ×100 |
| 80 mm | 560 mm | ×14–×187 | 11.7 | 12.2 | 1.73″ | 1.37″ | ×160 |
| 127 mm | 889 mm | ×22–×296 | 12.7 | 13.0\* | 1.09″ | 1.11″ | ×254 |
| 305 mm | 2,135 mm | ×53–×712 | 13.0\* (extension 14.6) | 13.0\* (extension 15.1) | 0.45″ | 0.94″ | ×610 |

\* Without the Gaia extension data, the value stops at the 13.0 mag cap (15.2 mag with the extension data).

The default is $`L_0`$ = 6.0; the $`L_0`$ = 6.5 column is the case with the naked-eye limit set to its maximum. At 305 mm, $`r_\star`$ exceeds twice the Airy radius because the 1.5″ seeing dominates. At apertures of 127 mm and above, seeing rather than diffraction sets the size of the star image.

### 3.8 Consistency of star positions

Star positions differ from Stellarium by about 1′. This difference comes from the omission of nutation, aberration, and proper motion analyzed in Section 4.1, and the relative arrangement of stars within the field is preserved.

---

## 4. Discussion

### 4.1 Rationale for omitting nutation, stellar aberration, and proper motion

#### 4.1.1 Nutation

Nutation is the rotation from the mean equator of date to the true equator of date. Its amplitude is about 17″ in longitude and about 9″ in obliquity (Meeus Chapter 22). Because this rotation is a rigid rotation of the whole celestial sphere, all stars and planets shift together by the same angle about one axis, and the angular distances between bodies and the arrangement of constellations are preserved exactly. Omitting nutation only rotates the entire horizontal frame by less than 20″. This is below naked-eye resolution (about 1′), and even in a high-magnification telescope it does not affect the relative arrangement within the field. Including nutation would require evaluating the IAU nutation series every frame, and the result would be a rotation of the whole sky that the observer cannot distinguish.

#### 4.1.2 Annual aberration of stars

Annual aberration is the effect by which stars are displaced by up to $`\kappa \approx 20.5''`$ toward the direction of the Earth's motion (the apex) because of the Earth's orbital velocity. The displacement is $`\kappa\sin\theta`$ ($`\theta`$: angular distance from the apex) and depends on direction, so unlike nutation it is not strictly a rigid rotation. Between two stars within a field of angular diameter $`\Phi`$, the difference in displacement is at most about $`\kappa\Phi`$ ($`\Phi`$ in radians). For a 1° field this is $`20.5'' \times 0.0175 \approx 0.36''`$, smaller than the star image radii in Table 6. The arrangement within a telescope field is therefore effectively unchanged, and the all-sky distortion does not exceed 20.5″.

This omission is applied consistently to all bodies that share the star coordinate system. Planets and asteroids are astrometric positions corrected only for light-time, so stars and planets both lack the same observer-velocity aberration and their positions relative to each other agree. Only the Sun includes the −20.5″ of aberration. The Sun needs its apparent position for its relation to the Moon (eclipses), and stars near the Sun are not visible except during a total solar eclipse, so this inconsistency does not show in observation. The aberration effect for the Moon is under 1″.

#### 4.1.3 Proper motion

While the game runs, stars are fixed at their J2000.0 positions; only Gaia positions were moved back from J2016.0 to J2000.0 at the data-processing stage. For a star with proper motion $`\mu`$, the position error in 2026 is $`26\,\mu`$, about 26″ for a star moving 1″ per year. Most naked-eye stars move less than this and stay within the 1′ criterion. However, this error differs in direction and size from star to star, so it changes the star arrangement itself, and it grows linearly with distance from J2000. A few nearby stars with large proper motions can exceed 1′ even in 2026, and in the sky of 1000 BCE or 3000 CE, which the planetary computation allows, even a star moving 1″ per year is off by more than 15′. This project places proper motion and the reproduction of the distant past and future sky outside its scope, and the guaranteed accuracy of the star arrangement is limited to a few decades around J2000.

### 4.2 Omission of lunar physical libration

Physical libration is the oscillation caused by actual irregularities in the Moon's rotation; at around 0.04° it is small compared with optical libration (several degrees). A 0.04° rotation of selenographic coordinates moves limb features by about 1.2 km for a lunar radius of 1,737 km, which is about 0.6″ as seen from the Earth. This is smaller than the star image radii in Table 6 (0.94–1.88″), so even at high magnification it stays below the resolution of the lunar map texture. The difference in the comparison with the Meeus example in Section 3.6 (0.01°) shows the actual size of this omission.

### 4.3 Range of the solar theory and ΔT

Even with the low-precision theory, the Sun shows a maximum error of 43.7″ over 1900–2100. This is small compared with the radius of the solar disk (about 16′) and has no effect on sunrise, sunset, or twilight. In eclipse prediction the solar position error carries over directly into the position of the central line, but in the comparison of Section 3.5 the difference in the point of greatest eclipse was within 0.1°.

ΔT uses the piecewise polynomials only for 1900–2150 and the long-term parabola $`-20 + 32u^2`$ outside that range. Espenak and Meeus (2006) also give piecewise polynomials before 1900, so for dates before 1900 this simplification adds error to the lunar position and to eclipse paths. ΔT in the distant future cannot in principle be predicted, and the long-term parabola is an extrapolation.

### 4.4 Range of validity of the truncated VSOP87D

VSOP87 is an analytical theory designed for a range of several thousand years, but amplitude truncation discards small coefficients of the higher powers of $`\tau`$, so the error grows with distance from J2000. Table 2 and the interval values in Section 3.3 show this trend. The value of within 13″ for 1800–2050 means that the planets are placed correctly relative to the stars even in the field of a high-magnification telescope (a few arcminutes at ×712). The 2–15′ for 3000 BCE to 1000 BCE is acceptable for naked-eye viewing. In that era, however, the star arrangement is already off because of proper motion (Section 4.1.3), so the accuracy of planetary positions does not determine the accuracy of the sky as a whole.

### 4.5 Fixed values in the atmosphere and sky brightness models

Refraction is fixed at 1010 hPa and 10 °C and does not represent changes with weather (several arcminutes at the horizon). The extinction coefficient $`k_V = 0.172`$ is also a single value, with no effect from season, humidity, or aerosols.

In the sky brightness model, the twilight table, the Bortle table, the extended-source threshold exponent 0.6, the coefficient 1.5 in the sky loss formula, and the maximum dark adaptation loss of 3 mag with 30-second recovery are marked in the code as rough values subject to tuning; they were not fitted to observations. Real dark adaptation takes tens of minutes, but it was shortened to 30 seconds for gameplay. No comparison of this model's output (for example, the limiting magnitude computed from the zenith brightness for each Bortle class) with measured limiting magnitudes or SQM measurements exists, so the accuracy of the visibility model has not been assessed quantitatively. Unlike the position computations, this part should be regarded as a qualitative reproduction.

Evaluating sky brightness at the single zenith point removes the unphysical change in the number of stars across the whole sky when the view is turned. However, it also means that a light-pollution dome toward the horizon or the bright sky near the Moon is not reflected in a direction-dependent limiting magnitude. The Krisciunas–Schaefer model supports direction-dependent computation, so the method can be extended to evaluate sky brightness per star. The formula from block light to the Bortle scale is likewise a correspondence chosen for the game environment and does not involve a physical radiative transfer computation.

### 4.6 Simplifications in the optical model

The optical model does not include lens aberrations, central obstruction, or transmission. Seeing is fixed at a single value of 1.5″ and does not vary with altitude or weather. The star image radius formula adds the radius of the first dark ring of the Airy pattern and the seeing FWHM in quadrature and multiplies by a coefficient of 0.6; this differs from a rigorous convolution of point-spread functions and is an approximation for matching on-screen size. Only the diffraction radius $`138''/D`$ is used as the resolution measure; the Dawes limit is not used. The limiting-magnitude formula is empirical, and individual observer differences are represented only by the single naked-eye limit setting.

### 4.7 Limits of the validation coverage

The 82-item validation compares a Python port of the same formulas with the references, so it cannot detect errors in the single-precision arithmetic of the shaders or in the rendering stage. The ellipsoidal shadow computation for eclipse prediction, the out-of-sample accuracy of the fitted formulas for Saturn's satellites, asteroid interpolation, and libration are not part of the 82 items; each was checked only by the individual comparisons in Sections 3.4–3.6. Some of the test scripts (20 items on the reference date and clock, 13 items on eclipse display) target default values and decision methods that differ from the current local-mean-time clock and topocentric eclipses, so they need to be supplemented with items that test the current clock system directly. The range of guaranteed accuracy is 1900–2100 for the stars, Sun, and Moon, where comparisons were made, and 1800–2050 for the planets (more broadly, 1000 BCE to 3000 CE).

---

## 5. Conclusions

Cheonsang 2.0.0 computes the real-time sky using only analytical ephemerides and a small amount of fitted data. Using a 1,259-term truncation of VSOP87D as the planetary theory reduced the planetary position errors over 1800–2050 from the arcminute level of approximate Keplerian elements to 2.4–12.6″, at a cost of 28µs for seven planets. The Sun and Moon, from the low- and medium-precision theories of Meeus, are within 43.7″ and 30.0″, respectively, and the eclipse prediction agrees with NASA to 0.1° in the point of greatest eclipse and 1 second in the duration of totality. Nutation is a rigid rotation of the whole celestial sphere, and the annual aberration of stars leaves only differential displacements below 0.4″ within a 1° field, so omitting these two effects does not change the star arrangement within the field. Omitting proper motion meets the 1′ criterion for most stars within a few decades of J2000, but it becomes the main source of error in the star arrangement for more distant eras.

Observational visibility is built on literature models (Kasten–Young airmass, Krisciunas–Schaefer moonlight, and the Schaefer luminance conversion) with twilight, light pollution, and dark adaptation terms added as rough values. Unlike the position computations, this part has not been compared with observations. Direction-dependent sky brightness evaluation and comparison with measured limiting magnitudes are the next tasks.

---

## References

Archinal, B. A., A'Hearn, M. F., Bowell, E., et al. (2011). Report of the IAU Working Group on Cartographic Coordinates and Rotational Elements: 2009. *Celestial Mechanics and Dynamical Astronomy*, 109, 101–135.

Ballesteros, F. J. (2012). New insights into black bodies. *EPL (Europhysics Letters)*, 97, 34008.

Bennett, G. G. (1982). The calculation of astronomical refraction in marine navigation. *Journal of Navigation*, 35, 255–259.

Bowell, E., Hapke, B., Domingue, D., et al. (1989). Application of photometric models to asteroids. In R. P. Binzel, T. Gehrels, & M. S. Matthews (Eds.), *Asteroids II* (pp. 524–556). University of Arizona Press.

Bretagnon, P., & Francou, G. (1988). Planetary theories in rectangular and spherical variables: VSOP87 solutions. *Astronomy and Astrophysics*, 202, 309–315.

Espenak, F., & Meeus, J. (2006). *Five Millennium Canon of Solar Eclipses: −1999 to +3000* (NASA Technical Publication TP-2006-214141). NASA Goddard Space Flight Center.

Gaia Collaboration, Vallenari, A., Brown, A. G. A., et al. (2023). Gaia Data Release 3: Summary of the content and survey properties. *Astronomy and Astrophysics*, 674, A1.

Górski, K. M., Hivon, E., Banday, A. J., et al. (2005). HEALPix: A framework for high-resolution discretization and fast analysis of data distributed on the sphere. *The Astrophysical Journal*, 622, 759–771.

Høg, E., Fabricius, C., Makarov, V. V., et al. (2000). The Tycho-2 catalogue of the 2.5 million brightest stars. *Astronomy and Astrophysics*, 355, L27–L30.

JPL Solar System Dynamics Group. *Horizons On-Line Ephemeris System*. https://ssd.jpl.nasa.gov/horizons/

Kasten, F., & Young, A. T. (1989). Revised optical air mass tables and approximation formula. *Applied Optics*, 28, 4735–4738.

Krisciunas, K., & Schaefer, B. E. (1991). A model of the brightness of moonlight. *Publications of the Astronomical Society of the Pacific*, 103, 1033–1039.

Mason, B. D., Wycoff, G. L., Hartkopf, W. I., Douglass, G. G., & Worley, C. E. (2001). The 2001 US Naval Observatory Double Star CD-ROM. I. The Washington Double Star Catalog. *The Astronomical Journal*, 122, 3466–3471.

Meeus, J. (1998). *Astronomical Algorithms* (2nd ed.). Willmann-Bell.

Nash, D. *The HYG Database*, version 4.1. https://github.com/astronexus/HYG-Database

NASA Scientific Visualization Studio. (2020). *Deep Star Maps 2020*. NASA Goddard Space Flight Center.

Rhodes, B. C. *PyEphem: Astronomy library for Python*. https://rhodesmill.org/pyephem/

Riello, M., De Angeli, F., Evans, D. W., et al. (2021). Gaia Early Data Release 3: Photometric content and validation. *Astronomy and Astrophysics*, 649, A3.

Sæmundsson, Þ. (1986). Astronomical refraction. *Sky and Telescope*, 72, 70.

Schaefer, B. E. (1990). Telescopic limiting magnitudes. *Publications of the Astronomical Society of the Pacific*, 102, 212–229.

Standish, E. M., & Williams, J. G. (1992). Orbital ephemerides of the Sun, Moon, and planets. In P. K. Seidelmann (Ed.), *Explanatory Supplement to the Astronomical Almanac* (pp. 279–323). University Science Books.
