package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.L

import com.google.gson.JsonParser
import java.io.InputStreamReader

/** 심원천체 하나 (dso.json — 메시에·칼드웰·마젤란은하) */
data class Dso(
    val id: String,
    val ngc: String,
    val ko: String,
    val en: String,
    /** galaxy · open · globular · nebula · planetary · dark · star · other */
    val type: String,
    /** J2000 적경·적위 (도) */
    val ra: Double,
    val dec: Double,
    /** 긴 지름·짧은 지름 (각분) */
    val major: Double,
    val minor: Double,
    /** 긴 축 위치각 (북→동, 도) */
    val pa: Double,
    val mag: Double,
)

/**
 * 이름 없는 유명 별 (famous_stars.json, HYG v4.1 좌표 J2000). hip 이 있으면 맨눈 목록 별과 HIP 로, 없으면 위치(1′ 안)로 맞춤.
 * fixMag = 자료 등급이 부정확한 별의 보정값 (예: 용골자리 에타 6.21 → 4.3)
 */
class Famous(val id: String, val ko: String, val en: String, val alt: List<String>, val ra: Double, val dec: Double, val hip: Int,
             val mag: Double, val ly: Double, val fixMag: Double?, private val noteKo: String, private val noteEn: String) {
    val dirJ = kr.inf.cheonsang.astro.Vec3.fromSpherical(ra, dec)
    val label: String get() = L.name(ko, en)
    val note: String get() = if (L.ko) noteKo else noteEn
}

/** 유성우 하나 (meteors.json, 국제유성기구 IMO 달력 기준) */
data class Shower(
    val id: String,
    val ko: String,
    /** 영어 이름 (Perseids) · 단수 (Perseid, "Perseid meteor shower") */
    val en: String,
    val en1: String,
    /** 극대 때 해의 황경 (도) */
    val peakLon: Double,
    /** 복사점 J2000 적경·적위 (도) */
    val ra: Double,
    val dec: Double,
    /** 극대 때 시간당 최대 유성 수 */
    val zhr: Double,
    /** 활동 폭 (해 황경, 도) */
    val sigma: Double,
    /** 대기 진입 속도 (km/s) */
    val speed: Double,
) {
    /** 화면 이름: 한국어 "페르세우스자리" / 영어 "Perseid" (뒤에 "meteor shower" 가 붙음) */
    val label: String get() = if (kr.inf.cheonsang.L.ko) ko else en1
}

/** 별자리 하나 (constellations.json): 이름 위치·선 (J2000 적경·적위, 도) */
class Constellation(val id: String, val ko: String, val en: String, val labelRa: Double, val labelDec: Double, val lines: List<DoubleArray>)

/** 모드 안 하늘 데이터 읽기 (assets/cheonsang/sky/) */
object SkyData {
    private fun json(name: String) =
        InputStreamReader(SkyData::class.java.getResourceAsStream("/assets/cheonsang/sky/$name") ?: error("$name missing"), Charsets.UTF_8)
            .use { JsonParser.parseReader(it) }

    val dso: List<Dso> by lazy {
        json("dso.json").asJsonArray.map { e ->
            val o = e.asJsonObject
            Dso(
                o["id"].asString, o["ngc"].asString, o["ko"].asString, o["en"].asString, o["type"].asString,
                o["ra"].asDouble, o["dec"].asDouble, o["maj"].asDouble, o["min"].asDouble, o["pa"].asDouble, o["mag"].asDouble,
            )
        }
    }

    /** 산발 유성 (시간당) */
    var sporadicPerHour = 10.0
        private set

    val showers: List<Shower> by lazy {
        val root = json("meteors.json").asJsonObject
        sporadicPerHour = root["sporadic_per_hour"].asDouble
        root["showers"].asJsonArray.map { e ->
            val o = e.asJsonObject
            Shower(
                o["id"].asString, o["ko"].asString, o["en"]?.asString ?: o["ko"].asString, o["en1"]?.asString ?: o["ko"].asString, o["lon"].asDouble, o["ra"].asDouble, o["dec"].asDouble,
                o["zhr"].asDouble, o["sigma"].asDouble, o["v"].asDouble,
            )
        }
    }

    /** 별자리 89개. lines 는 선 하나당 [ra0, dec0, ra1, dec1, ...] */
    val constellations: List<Constellation> by lazy {
        json("constellations.json").asJsonArray.map { e ->
            val o = e.asJsonObject
            val label = o["label"].asJsonArray
            Constellation(
                o["id"].asString, o["ko"].asString, o["en"].asString, label[0].asDouble, label[1].asDouble,
                o["lines"].asJsonArray.map { line ->
                    val pts = line.asJsonArray
                    DoubleArray(pts.size() * 2) { k -> pts[k / 2].asJsonArray[k % 2].asDouble }
                },
            )
        }
    }

    /**
     * 천상열차분야지도 별자리 289개 (korean_constellations.json, _tools/make_korean_sky2.py — 보천가 목록 + Stellarium 중국 전통 별자리 선, Unihan 한자음 검증).
     * 이름은 한글 (영어 화면은 음역, 한자 없음)
     */
    val korean: List<Constellation> by lazy {
        json("korean_constellations.json").asJsonArray.map { e ->
            val o = e.asJsonObject
            val label = o["label"].asJsonArray
            Constellation(
                o["id"].asString, o["ko"].asString, o["en"].asString, label[0].asDouble, label[1].asDouble,
                o["lines"].asJsonArray.map { line ->
                    val pts = line.asJsonArray
                    DoubleArray(pts.size() * 2) { k -> pts[k / 2].asJsonArray[k % 2].asDouble }
                },
            )
        }
    }

    /** 천상열차 발전과제용: 28수 가장 밝은 별(각→진 순) · 삼원 별들 · 북두 일곱 별 · 노인성 (HIP) */
    class KoreanMeta(val mansions: List<Int>, val enclosures: Map<String, Set<Int>>, val ladle: List<Int>, val noin: Int)
    val koreanMeta: KoreanMeta by lazy {
        val arr = json("korean_constellations.json").asJsonArray.map { it.asJsonObject }
        fun hips(o: com.google.gson.JsonObject) = o["hips"]?.asJsonArray?.map { it.asInt } ?: emptyList()
        val enc = linkedMapOf("jami" to "자미원", "taemi" to "태미원", "cheonsi" to "천시원").mapValues { (_, g) ->
            arr.filter { it["group"]?.asString?.endsWith(g) == true }.flatMap { hips(it) }.toSet()
        }
        KoreanMeta(
            arr.filter { it["mansion"]?.asBoolean == true }.map { it["bright"].asInt },
            enc,
            arr.firstOrNull { it["hz"]?.asString == "北斗" }?.let { hips(it) } ?: emptyList(),
            arr.firstOrNull { it["hz"]?.asString == "老人" }?.let { hips(it).firstOrNull() } ?: 30438,
        )
    }

    /** 망원경 화면에 그릴 별자리 — 설정 "별자리: 서양 / 천상열차" (발전과제·별 부호는 늘 서양 88) */
    val drawnConstellations: List<Constellation> get() = if (kr.inf.cheonsang.client.TelescopeFilter.korean) korean else constellations

    /** 심원천체 거리 (광년) — 위키백과 메시에·캘드웰 표 (_tools/make_dso_dist.py) */
    val dsoDist: Map<String, Double> by lazy {
        json("dso_dist.json").asJsonObject.entrySet().associate { (k, v) -> k to v.asDouble }
    }

    /** 별자리 그림 (Stellarium) — 아틀라스 uv, 그림 크기, 맞출 별 3개 [x, y, ra, dec] */
    class ArtPiece(val id: String, val uv: DoubleArray, val w: Double, val h: Double, val anchors: List<DoubleArray>)
    val art: List<ArtPiece> by lazy {
        json("art.json").asJsonArray.map { e ->
            val o = e.asJsonObject
            fun arr(x: com.google.gson.JsonElement) = x.asJsonArray.let { a -> DoubleArray(a.size()) { a[it].asDouble } }
            ArtPiece(o["id"].asString, arr(o["uv"]), o["w"].asDouble, o["h"].asDouble, o["anchors"].asJsonArray.map { arr(it) })
        }
    }

    /** 은하 드보클레르 형태 — 위키백과 정보상자 (_tools/make_galaxy_types.py) */
    val dsoMorph: Map<String, String> by lazy {
        json("dso_morph.json").asJsonObject.entrySet().associate { (k, v) -> k to v.asString }
    }

    /** 은하 관측 스케치 판 (galaxy_sketch.json — _tools/make_galaxy_atlas.py): id → (판 칸 번호, log10 빛 면적) */
    val galaxySketch: Map<String, Pair<Int, Double>> by lazy {
        runCatching {
            json("galaxy_sketch.json").asJsonObject["tiles"].asJsonObject.entrySet().associate { (k, v) -> k to (v.asJsonArray[0].asInt to v.asJsonArray[1].asDouble) }
        }.getOrDefault(emptyMap())
    }

    /** 별자리 경계선 (d3-celestial, J2000) — 다각형마다 [ra0, dec0, ra1, dec1, ...] */
    val bounds: List<DoubleArray> by lazy {
        json("bounds.json").asJsonArray.map { e -> val a = e.asJsonArray; DoubleArray(a.size()) { a[it].asDouble } }
    }

    /** 별 정보 (stars.bin 과 같은 순서, 실제 별만) — 거리(광년, 0 = 모름) · 절대등급(NaN = 모름) · HD · 분광형 */
    class StarInfo(val distLy: FloatArray, val absMag: FloatArray, val hd: IntArray, val spect: Array<String>)
    val starInfo: StarInfo? by lazy {
        val input = SkyData::class.java.getResourceAsStream("/assets/cheonsang/sky/star_info.bin") ?: return@lazy null
        java.io.DataInputStream(input.buffered()).use { d ->
            val n = d.readInt()
            val dist = FloatArray(n); val abs = FloatArray(n); val hd = IntArray(n)
            val sp = Array(n) {
                dist[it] = d.readFloat()
                val a = d.readShort().toInt(); abs[it] = if (a == -32768) Float.NaN else a / 100f
                hd[it] = d.readInt()
                val len = d.readUnsignedByte(); val b = ByteArray(len); d.readFully(b); String(b, Charsets.US_ASCII)
            }
            StarInfo(dist, abs, hd, sp)
        }
    }

    /** 별 이름: HIP 번호 → 한국어(없으면 영어) */
    val starNames: Map<Int, String> by lazy {
        json("star_names.json").asJsonObject.entrySet().associate { (k, v) ->
            val o = v.asJsonObject
            val ko = o["ko"]?.asString.orEmpty()
            k.toInt() to ko.ifEmpty { o["en"]?.asString.orEmpty() }
        }
    }

    /** 화면에 쓸 별 이름 (한국어 화면 = 한국어, 아니면 영어). 이름 없는 유명 별은 그 이름 */
    fun starLabel(hip: Int): String? = (if (kr.inf.cheonsang.L.ko) starNames[hip] else starNamesEn[hip] ?: starNames[hip])
        ?: famousByHip[hip]?.label

    val famous: List<Famous> by lazy {
        json("famous_stars.json").asJsonArray.map { e ->
            val o = e.asJsonObject
            Famous(o["id"].asString, o["ko"].asString, o["en"].asString, o["alt"]?.asJsonArray?.map { it.asString } ?: emptyList(),
                o["ra"].asDouble, o["dec"].asDouble, o["hip"]?.asInt ?: 0, o["mag"].asDouble, o["ly"].asDouble, o["fixMag"]?.asDouble,
                o["note_ko"].asString, o["note_en"].asString)
        }
    }
    val famousByHip: Map<Int, Famous> by lazy { famous.filter { it.hip > 0 }.associateBy { it.hip } }

    /** 방향(J2000 단위 벡터) 가까이 (arcmin 안) 있는 유명 별 */
    fun famousNear(x: Double, y: Double, z: Double, arcmin: Double = 1.0, mag: Double? = null): Famous? {
        val cosLim = kotlin.math.cos(Math.toRadians(arcmin / 60.0))
        return famous.firstOrNull { f -> f.dirJ.x * x + f.dirJ.y * y + f.dirJ.z * z > cosLim && (mag == null || kotlin.math.abs(mag - f.mag) < 1.5) }
    }

    /** /cfind: 별 영어 이름 (HIP → en) */
    val starNamesEn: Map<Int, String> by lazy {
        json("star_names.json").asJsonObject.entrySet().mapNotNull { (k, v) ->
            v.asJsonObject["en"]?.asString?.takeIf { it.isNotBlank() }?.let { k.toInt() to it }
        }.toMap()
    }
}
