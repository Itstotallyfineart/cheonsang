package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import com.google.gson.JsonParser
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.client.sky.CheonsangSky
import java.io.InputStreamReader
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 1.2.3 달 지명 좌클릭 (×200↑): 화면 가운데가 달 원판 위면 그 자리의 셀레노 위도·경도 → 그 자리를 덮는 가장 작은 지형.
 * 자료: sky/moon_names.json (IAU 지명, USGS Gazetteer — _tools/make_moon_names.py). 칭동·자전축은 셰이더(moon.fsh)와 같은 변환.
 */
internal object MoonNames {
    /** 이 배율부터 지명 (1.7.0: 단계 번호 → 배율, 전 4단계 = ×200) */
    const val MIN_ZOOM = 150.0
    private const val MOON_R_KM = 1737.4

    private val TYPE_EN = mapOf("크레이터" to "Crater", "산" to "Mountain", "바다" to "Mare", "호수" to "Lacus", "만" to "Sinus", "계곡" to "Vallis",
        "절벽" to "Rupes", "곶" to "Promontorium", "늪" to "Palus", "대양" to "Oceanus")

    class Feature(val ko: String, val name: String, val type: String, val lat: Double, val lon: Double, val diamKm: Double) {
        val title get() = if (ko.isEmpty() || !L.ko) name else "$ko ($name)"
        /** 1.9.0-dev2: 종류 화면 이름 (자료는 한국어) */
        val typeLabel: String get() = if (L.ko) type else TYPE_EN[type] ?: type
    }

    /** 2.0.2-dev1: /cfind 검색에 내놓는 유명 지명 53곳 (좌클릭 이름 표시는 787곳 그대로) */
    private val FAMOUS = setOf(
        "Oceanus Procellarum", "Mare Imbrium", "Mare Serenitatis", "Mare Tranquillitatis", "Mare Crisium", "Mare Fecunditatis", "Mare Nectaris",
        "Mare Nubium", "Mare Humorum", "Mare Frigoris", "Mare Vaporum", "Mare Cognitum", "Mare Insularum", "Mare Orientale",
        "Sinus Iridum", "Sinus Medii", "Sinus Aestuum", "Lacus Somniorum",
        "Tycho", "Copernicus", "Kepler", "Aristarchus", "Plato", "Clavius", "Ptolemaeus", "Alphonsus", "Arzachel", "Theophilus", "Cyrillus",
        "Catharina", "Grimaldi", "Gassendi", "Eratosthenes", "Archimedes", "Langrenus", "Petavius", "Posidonius", "Maginus", "Schickard",
        "Aristoteles", "Eudoxus", "Hipparchus", "Albategnius", "Proclus", "Messier", "Bailly", "Pythagoras",
        "Montes Apenninus", "Montes Alpes", "Montes Caucasus", "Vallis Alpes", "Rupes Recta", "Vallis Schröteri")
    val famous: List<Feature> by lazy { list.filter { it.name in FAMOUS } }

    val list: List<Feature> by lazy {
        val s = MoonNames::class.java.getResourceAsStream("/assets/cheonsang/sky/moon_names.json") ?: return@lazy emptyList()
        InputStreamReader(s, Charsets.UTF_8).use { r ->
            JsonParser.parseReader(r).asJsonArray.map { e ->
                val a = e.asJsonArray
                Feature(a[0].asString, a[1].asString, a[2].asString, a[3].asDouble, a[4].asDouble, a[5].asDouble)
            }
        }
    }

    /** 월드 방향 → 셀레노 (위도, 경도) 도. 달 원판 밖이면 null */
    private fun seleno(v: Vec3): Pair<Double, Double>? {
        val c = CheonsangSky.moonWorld.normalized()
        val vn = v.normalized()
        val dc = vn dot c
        if (dc <= 0) return null
        val tr = tan(Math.toRadians(CheonsangSky.moonRadius))
        val x = (vn dot CheonsangSky.moonRight) / dc / tr
        val y = (vn dot CheonsangSky.moonUp) / dc / tr
        val r2 = x * x + y * y
        if (r2 >= 1.0) return null
        val z = sqrt(1.0 - r2)
        val lib = CheonsangSky.moonLib
        val cP = cos(Math.toRadians(lib.p)); val sP = sin(Math.toRadians(lib.p))
        val xm = cP * x + sP * y; val ym = -sP * x + cP * y
        val cb = cos(Math.toRadians(lib.b)); val sb = sin(Math.toRadians(lib.b))
        val cl = cos(Math.toRadians(lib.l)); val sl = sin(Math.toRadians(lib.l))
        val bx = cb * z - sb * ym; val bz = sb * z + cb * ym
        val bxX = cl * bx - sl * xm; val bxY = sl * bx + cl * xm
        return Math.toDegrees(asin(bz.coerceIn(-1.0, 1.0))) to Math.toDegrees(atan2(bxY, bxX))
    }

    /** 셀레노 (위도, 경도) → 월드 방향. 지금 안 보이는 쪽이면 null */
    fun world(lat: Double, lon: Double): Vec3? {
        val X = cos(Math.toRadians(lat)) * cos(Math.toRadians(lon))
        val Y = cos(Math.toRadians(lat)) * sin(Math.toRadians(lon))
        val Z = sin(Math.toRadians(lat))
        val lib = CheonsangSky.moonLib
        val cl = cos(Math.toRadians(lib.l)); val sl = sin(Math.toRadians(lib.l))
        val bx = cl * X + sl * Y; val xm = -sl * X + cl * Y
        val cb = cos(Math.toRadians(lib.b)); val sb = sin(Math.toRadians(lib.b))
        val z = cb * bx + sb * Z; val ym = -sb * bx + cb * Z
        if (z < 0) return null
        val cP = cos(Math.toRadians(lib.p)); val sP = sin(Math.toRadians(lib.p))
        val x = cP * xm - sP * ym; val y = sP * xm + cP * ym
        val tr = tan(Math.toRadians(CheonsangSky.moonRadius))
        return (CheonsangSky.moonWorld.normalized() + (CheonsangSky.moonRight * x + CheonsangSky.moonUp * y) * tr).normalized()
    }

    /** 두 셀레노 점 사이 거리 (km) */
    private fun distKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = Vec3.fromSpherical(lon1, lat1); val b = Vec3.fromSpherical(lon2, lat2)
        return Math.toRadians(a.angleTo(b)) * MOON_R_KM
    }

    /** 가운데 방향이 덮고 있는 가장 작은 지형 (없으면 null) */
    fun at(center: Vec3): Pair<Feature, Pair<Double, Double>>? {
        if (!TelescopeView.active || TelescopeView.zoom < MIN_ZOOM) return null
        val (lat, lon) = seleno(center) ?: return null
        var best: Feature? = null
        for (f in list) {
            if (distKm(lat, lon, f.lat, f.lon) <= f.diamKm / 2 && (best == null || f.diamKm < best.diamKm)) best = f
        }
        return best?.let { it to (lat to lon) }
    }

    fun latText(lat: Double) = "%s %.1f°".format(if (lat >= 0) L.t("cheonsang.moonnames.2") else L.t("cheonsang.moonnames.1"), kotlin.math.abs(lat))
    fun lonText(lon: Double) = "%s %.1f°".format(if (lon >= 0) L.t("cheonsang.moonnames.4") else L.t("cheonsang.moonnames.3"), kotlin.math.abs(lon))

    fun target(f: Feature): Target = Target("moonf:${f.name}", f.title, L.t("cheonsang.moonnames.5", f.title, f.typeLabel).format(f.diamKm),
        { world(f.lat, f.lon) }, { CheonsangSky.moonRadius * f.diamKm / 2 / MOON_R_KM }) {
        val lib = CheonsangSky.moonLib
        listOf(
            f.title,
            L.t("cheonsang.moonnames.6", f.typeLabel).format(f.diamKm),
            L.t("cheonsang.moonnames.7", latText(f.lat), lonText(f.lon)),
            L.t("cheonsang.moonnames.8").format(lib.l, lib.b),
            if (world(f.lat, f.lon) == null) L.t("cheonsang.moonnames.10") else L.t("cheonsang.moonnames.9"),
        )
    }
}
