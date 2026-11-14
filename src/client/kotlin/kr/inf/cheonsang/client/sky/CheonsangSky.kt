package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.L
import kr.inf.cheonsang.client.Safety
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.vertex.VertexFormat
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.client.TelescopeFilter
import kr.inf.cheonsang.client.TelescopeView
import kr.inf.cheonsang.astro.Eclipse
import kr.inf.cheonsang.astro.Frames
import kr.inf.cheonsang.astro.Mat3
import kr.inf.cheonsang.astro.Moon
import kr.inf.cheonsang.astro.Planet
import kr.inf.cheonsang.astro.PlanetPos
import kr.inf.cheonsang.astro.Planets
import kr.inf.cheonsang.astro.SkyClock
import kr.inf.cheonsang.astro.Sun
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.asinD
import kr.inf.cheonsang.astro.cosD
import kr.inf.cheonsang.astro.sinD
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.level.SkyRenderState
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector4f
import kotlin.math.acos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/**
 * 천상 하늘 (클라이언트). 실제 별(전용 셰이더, 망원경 배율에 따라 9등까지), 은하수·심원천체 빛무리·유성우, 행성 5개(맨눈 빛점, 망원경 확대 시 원판·줄무늬·위성·고리),
 * 해·달 원판(실제 시직경 × 배율), 달 위상·회전, 일식·월식, 박명에 따른 별 밝기를 그린다.
 *
 * 별은 J2000 적도좌표 반지름 100 구면에 한 번만 쌓아 두고 프레임마다 [지평 × 세차] 행렬로 돌린다.
 * 해·달은 단위 사각형 하나를 프레임마다 위치·크기 행렬로 옮겨 그린다.
 */
object CheonsangSky {
    /** 반지름 (바닐라 별과 같은 100) */
    private const val R = 100f

    /** 맨눈 한계등급 (설정 화면에서 바꿈) */
    var nakedLimit = 6.0f

    /** 위도·경도 (서버가 보냄, 서버 없으면 기본 0°·0°) — 경도는 시계(SkyClock.lon)에도 들어 있음 */
    var latitude = SkyClock.DEFAULT_LAT
    /** 서버가 보낸 하늘 (위도 이동 기준 Z·블록 수 포함, 서버에 천상 없으면 null) */
    var serverSkyData: kr.inf.cheonsang.SkyServer.Sky? = null
    val longitude get() = clock.lon

    /** 해·달 원판 배율 — 실제 크기(1) 고정. 설정에서 안 바꿈 */
    const val discScale = 1.0

    var clock = SkyClock()
    /** 서버(천상 있음)가 하늘을 보냈는지 — false 면 클라이언트 단독 (설정 화면에서 날짜·위치) */
    var serverSky = false
    /** 서버가 실시간 모드면 그 도시 (카드·/ctonight 에 그 도시 시계 시각) */
    var realtimeCity: kr.inf.cheonsang.RealTime.City? = null

    /** 실제 하늘 사용 여부 — 문제 생기면 false 로 바닐라 복귀 */
    var enabled = true

    private fun pipeline(name: String, shader: String, format: VertexFormat, blend: BlendFunction): RenderPipeline =
        pipeline(name, Identifier.withDefaultNamespace(shader), format, blend)

    private fun pipeline(name: String, shader: Identifier, format: VertexFormat, blend: BlendFunction): RenderPipeline =
        pipeline(name, shader, shader, format, blend)

    private fun pipeline(name: String, vertex: Identifier, fragment: Identifier, format: VertexFormat, blend: BlendFunction): RenderPipeline =
        RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withLocation(Cheonsang.id("pipeline/$name"))
            .withVertexShader(vertex)
            .withFragmentShader(fragment)
            .withColorTargetState(ColorTargetState(blend))
            .withCull(false)
            .withVertexBinding(0, format)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build()

    /** 별: 전용 셰이더 (assets/cheonsang/shaders/core/stars) — 화면에서 늘 같은 크기, 한계등급은 ColorModulator.g */
    private val STARS_PIPELINE = pipeline("stars", Cheonsang.id("core/stars"), DefaultVertexFormat.POSITION_TEX_COLOR, BlendFunction.OVERLAY)

    /** 심원천체 빛무리: 텍스처 × 정점색 · 더하기 합성 */
    private val TINT_PIPELINE = pipeline("deep_sky", "core/position_tex_color", DefaultVertexFormat.POSITION_TEX_COLOR, BlendFunction.OVERLAY)

    /** 해·빛무리·달 밝은 면: 더하기 합성 */
    private val GLOW_PIPELINE = pipeline("celestial_glow", "core/position_tex", DefaultVertexFormat.POSITION_TEX, BlendFunction.OVERLAY)
    /** 한 채널(R8) 흑백 그림용 — 현실 은하수 ([GrayTexture]) */
    private val GRAY_PIPELINE = pipeline("celestial_gray", Identifier.withDefaultNamespace("core/position_tex"), Cheonsang.id("core/gray"), DefaultVertexFormat.POSITION_TEX, BlendFunction.OVERLAY)

    /** 은하수 화질: 바이큐빅 보간 + 디더링 (shaders/core/milky.fsh) — TextureMat[0].x = 1 이면 R8 흑백 */
    private val MILKY_PIPELINE = pipeline("milky", Identifier.withDefaultNamespace("core/position_tex"), Cheonsang.id("core/milky"), DefaultVertexFormat.POSITION_TEX, BlendFunction.OVERLAY)
    private val MILKY_GRAY = Matrix4f().zero().m00(1f)
    private val MILKY_RGBA = Matrix4f().zero()

    /** 달 가림막: 하늘색 원판으로 뒤의 별·해를 덮음 (일식) */
    private val MASK_PIPELINE = pipeline("celestial_mask", "core/position_tex", DefaultVertexFormat.POSITION_TEX, BlendFunction.TRANSLUCENT)

    private val TEX_STAR = Cheonsang.id("textures/sky/star.png")
    /** 해: 원판·주연감광·빛무리를 셰이더가 계산 — 더하기 합성 */
    private val SUN_PIPELINE = pipeline("sun", Cheonsang.id("core/disc"), Cheonsang.id("core/sun"), DefaultVertexFormat.POSITION_TEX, BlendFunction.OVERLAY)

    /** 달: ① 가림막(반투명 — 밤 검게, 일식 때 해 가림, 낮엔 안 그림) → ② 달빛(더하기 — 낮엔 하늘보다 밝게) */
    private val MOON_MASK_PIPELINE = pipeline("moon_mask", Cheonsang.id("core/disc"), Cheonsang.id("core/moon"), DefaultVertexFormat.POSITION_TEX, BlendFunction.TRANSLUCENT)
    private val MOON_PIPELINE = pipeline("moon", Cheonsang.id("core/disc"), Cheonsang.id("core/moon"), DefaultVertexFormat.POSITION_TEX, BlendFunction.OVERLAY)

    private val TEX_DISC = Cheonsang.id("textures/sky/disc.png")
    /** 달 지도 (NASA LRO, 정거원통) — 작게 보일 때는 작은 지도 (밉맵이 없어 큰 지도를 작게 그리면 반짝임) */
    private val TEX_MOON_MAP = Cheonsang.id("textures/sky/moon_map.png")
    private val TEX_MOON_SMALL = Cheonsang.id("textures/sky/moon_map_small.png")
    /** 달 지도 4단계 (256·1K·2K·4K) — 화면 반지름이 지도 폭/4 보다 작으면 한 단계 작은 것 (밉맵 없음 → 반짝임 방지) */
    private val TEX_MOON_1K = Cheonsang.id("textures/sky/moon_map_1k.png")
    private val TEX_MOON_4K = Cheonsang.id("textures/sky/moon_map_4k.png")
    /** 초고화질팩 달 8K (LRO) — 달 반지름이 화면 1024px 넘을 때 (×200 이상) */
    private val TEX_MOON_8K = Cheonsang.id("textures/sky/moon_map_8k.png")
    /** 맨눈용 흐린 지도 — 바다 무늬만 은은하게 */
    private val TEX_MOON_SOFT = Cheonsang.id("textures/sky/moon_map_soft.png")
    /**
     * 은하수: 맨눈·×1 = 4096 (화면 픽셀보다 커서 반짝임 없음), 확대 = 8192 (NASA 8K 원본, 맨눈엔 반짝여서 안 씀).
     * 장노출(photo on) / 현실(기본, 회색) 두 벌
     */
    private val TEX_MILKY = Cheonsang.id("textures/sky/milkyway.png")
    private val TEX_MILKY_8K = Cheonsang.id("textures/sky/milkyway_8k.png")
    private val TEX_MILKY_REAL = Cheonsang.id("textures/sky/milkyway_real.png")
    /** 현실 은하수 확대용은 기본에 (고화질팩 흡수) · 8K → 12K (32K 원본을 줄임, R8 72MiB) */
    private val TEX_MILKY_REAL_HI = Cheonsang.id("textures/sky/milkyway_eye_12k.png")
    /** 별자리 그림 (Stellarium, 85장 한 장 아틀라스) — /cfilter art */
    private val TEX_ART = Cheonsang.id("textures/sky/art_atlas.png")
    private var artBuffer: GpuBuffer? = null
    private var artIndexCount = 0
    private var artLoaded = false

    /**
     * 망원경용 큰 텍스처 중 올라간 것 — 장노출 은하수 8K(RGBA 128MiB)·현실 은하수 8K(R8 32MiB)·사진.
     * 망원경을 손에 들면 미리 올림 (켜는 순간 멈칫 없게), 들지도 켜지도 않은 채 [BIG_KEEP_NANOS] 지나면 내림
     */
    private val loadedBig = HashSet<Identifier>()
    /** R8 로 등록한 텍스처 (내릴 때 등록도 지움 — 다시 쓸 때 GrayTexture 로 새로 등록) */
    private val grayRegistered = HashSet<Identifier>()
    private var lastBigUse = 0L
    private const val BIG_KEEP_NANOS = 120_000_000_000L
    private val TEX_GLOW = Cheonsang.id("textures/sky/glow.png")
    /** 은하 관측 스케치 판 (DSS2, R8 4096×2048 = 8MiB, _tools/make_galaxy_atlas.py) */
    private val TEX_GALAXY = Cheonsang.id("textures/sky/galaxy_atlas.png")
    private val TEX_METEOR = Cheonsang.id("textures/sky/meteor.png")
    private val TEX_PHASE = Array(16) { Cheonsang.id("textures/sky/phase_%02d.png".format(it)) }
    private val TEX_JUPITER = Cheonsang.id("textures/sky/jupiter.png")
    private val TEX_SATURN = Cheonsang.id("textures/sky/saturn.png")
    private val TEX_RING = Cheonsang.id("textures/sky/saturn_ring.png")
    private val TEX_RING_FRONT = Cheonsang.id("textures/sky/saturn_ring_front.png")
    /** 대적점 (흰 타원, 색은 그릴 때) · 화성 지도 (Solar System Scope, CC BY 4.0) */
    private val TEX_GRS = Cheonsang.id("textures/sky/grs.png")
    private val TEX_MARS = Cheonsang.id("textures/sky/mars_map.png")

    // 실사 사진 목록·텍스처는 PhotoSky (초고화질팩 pack/photos.json, 없으면 기본 13장)

    /** 장노출(사진) 모드가 실제로 켜졌는지 — 사진팩이 있어야 함 */
    @JvmStatic
    val photoOn get() = TelescopeFilter.photo && Packs.photoOk

    /**
     * 은하수 그림 속 천체 조각 (NASA 32K 에서 잘라낸 것, _tools/make_mw_patches.py) — 값 = 한 변 (°).
     * 확대로 은하수 그림이 빠지는 만큼 이 조각이 채워 색·모양·진하기가 이어짐. 마젤란은 톤이 달라 사진 대신 이것을 씀.
     */
    private val MW_PATCHES = mapOf("LMC" to 14.0, "SMC" to 7.5, "M24" to 3.0, "C99" to 9.0)
    private class MwPatch(val o: Dso, val frame: Double, val real: Identifier, val lx: Identifier)
    private val mwPatches by lazy {
        SkyData.dso.filter { it.id in MW_PATCHES }.map {
            val base = "textures/sky/dso/mw_${it.id.lowercase()}"
            MwPatch(it, MW_PATCHES.getValue(it.id), Cheonsang.id("$base.png"), Cheonsang.id("${base}_lx.png"))
        }
    }

    /** 토성 고리 바깥 반지름 / 본체 반지름 */
    private const val RING_RATIO = 2.27

    /** 맨눈 심원천체 빛무리 기준 등급 (이보다 밝을수록 진하게) */
    private const val DSO_NAKED_MAG = 5.5

    var catalog: StarCatalog? = null
        private set
    private var starBuffer: GpuBuffer? = null
    private var starIndexCount = 0
    private var quadBuffer: GpuBuffer? = null
    private var milkyBuffer: GpuBuffer? = null
    private var milkyIndexCount = 0
    /** 심원천체 빛무리: 맨눈(-1)·망원경 배율 단계마다 한 벌 (그 배율 한계등급까지) */
    private class DsoMesh(val buffer: GpuBuffer, val count: Int)
    private val dsoMeshes = HashMap<Int, DsoMesh>()
    /** 은하 관측 스케치 (배율 칸마다, 빛무리와 같이 다시 만듦) */
    private val galaxyMeshes = HashMap<Int, DsoMesh>()
    private val GALAXY_PIPELINE = pipeline("galaxy", Cheonsang.id("core/galaxy"), DefaultVertexFormat.POSITION_TEX_COLOR, BlendFunction.OVERLAY)
    /** 스케치로 그리는 천체: 은하 전부(사진 없으면 형태 그림) + 사진 판이 있는 성운·행성상성운·구상성단 */
    private fun sketched(o: Dso) = o.type == "galaxy" || (o.type in SKETCH_TYPES && o.id in SkyData.galaxySketch)
    private val SKETCH_TYPES = setOf("nebula", "planetary", "globular")
    /** 은하 스케치 셰이더가 컴파일됐으면 은하는 빛무리 대신 스케치로 */
    private fun sketchOn() = RenderSystem.getCompiledPipelineNullable(GALAXY_PIPELINE) != null
    private var twilightBuffer: GpuBuffer? = null
    private var twilightIndexCount = 0
    private var failed = false

    // ── 프레임 상태 (extract 에서 계산, render 에서 사용) ──
    private var valid = false
    /** J2000 적도 → 월드 (별·행성) */
    private val skyMatrix = Matrix4f()
    /** J2000 적도 → 월드 (망원경 화면 표시·이름 찾기에서도 씀) */
    var skyMat3 = Mat3.IDENTITY
        private set

    /** 지금 하늘의 행성 (망원경·이름 표시에서도 씀) */
    var planets: List<PlanetPos> = emptyList()
        private set
    /** 소행성 (세레스·베스타) */
    var asteroids: List<AsteroidPos> = emptyList()
        private set
    var sunWorld = Vec3(0.0, 1.0, 0.0)
        private set
    private var sunRadius = 0.0
    /** 지구-해 거리 (AU) — 화성 위상 */
    private var sunDistAu = 1.0
    /** 해 고도 (도, 굴절 없음) — 가시성 판정에서도 씀 */
    var sunAlt = 0.0
        private set
    var moonWorld = Vec3(0.0, 1.0, 0.0)
        private set
    /** 달 사각형 위(천구 북쪽)·오른쪽(서쪽) 월드 방향 — 지명 좌클릭에서도 씀 */
    var moonUp = Vec3(0.0, 0.0, -1.0)
        private set
    var moonRight = Vec3(1.0, 0.0, 0.0)
        private set
    /** 칭동 (l, b, P 도) */
    var moonLib = kr.inf.cheonsang.astro.Libration(0.0, 0.0, 0.0)
        private set
    /** 그려지는 달 반지름 (도, 배율 포함) */
    var moonRadius = 0.0
        private set
    /** 관측자에서 달까지 거리 (km) */
    var moonTopoKm = 384400.0
        private set
    /** 달 밝은 비율 0~1 */
    var moonIllum = 0.0
        private set
    private val moonTint = Vector4f(1f, 1f, 1f, 1f)
    private var skyR = 0f; private var skyG = 0f; private var skyB = 0f
    private var clear = 1f
    private var tickNow = 0.0
    var sunLon = 0.0
        private set
    /** 달빛 0~1 (밝은 비율 × 달이 떠 있는 정도) — 은하수·유성이 흐려짐 */
    var moonLight = 0f
        private set
    /** 월식 본영이 달을 덮은 비율 0~1 (도감 "월식") */
    var lunarUmbra = 0f
        private set
    /** 일식: 해 원판이 가려진 넓이 비율 0~1 */
    var eclipseObsc = 0f
        private set
    /** 일식 어둠 0~1 — 90% 가려질 때까지는 살짝, 개기 직전에 확 (하늘·안개·땅 밝기·별에 씀) */
    @JvmStatic
    var eclipseDim = 0f
        private set
    /** 하늘이 깜깜한 정도 0~1 (바닐라 하늘색 밝기 기준) — 달 가림막 진하기 */
    private var skyDark = 1f

    @JvmStatic
    fun isActive() = enabled && !failed && valid && Safety.ok(Safety.SKY)

    /** 밤 안개색 = 하늘색 (바닐라 밤 안개는 파래서 지평선 아래·먼 땅만 파랗게 뜸). 해 고도 -2°부터 섞기 시작, -12°에서 하늘색 그대로 */
    @JvmStatic
    fun nightFog(c: org.joml.Vector3fc): org.joml.Vector3fc {
        val n = smooth(0f, 1f, ((-sunAlt - 2.0) / 10.0).coerceIn(0.0, 1.0).toFloat())
        if (n <= 0.001f) return c
        return org.joml.Vector3f(lerp(c.x(), skyR, n), lerp(c.y(), skyG, n), lerp(c.z(), skyB, n))
    }

    /**
     * 화면 낮밤용 가상 하루 틱 (0~23999, 안 쓸 땐 −1) — VisualDay.
     * 바닐라 해 고도(0틱 = 동쪽 지평선, 6000 = 천정, 12000 = 서쪽 지평선)가 천상 해 고도와 같은 시각. 오전·오후는 해가 동(+X)·서 어느 쪽인지로.
     * 바닐라는 해 −25° 쯤(13670틱)에 캄캄해짐 → 지평선 아래는 ×25/16 (천상 −16° = 바닐라 캄캄, 시민박명 −6° = 반쯤)
     */
    @JvmStatic
    fun visualDayTick(): Long {
        if (!isActive()) return -1
        val h = sunAlt
        val av = (if (h >= 0) h else h * 25.0 / 16.0).coerceIn(-90.0, 90.0)
        val off = (90.0 - av) * 24000.0 / 360.0
        val t = if (sunWorld.x > 0) 6000.0 - off else 6000.0 + off
        return Math.floorMod(Math.round(t), 24000L)
    }

    /** 프레임 시작: 시계 → 해·달·별 위치 */
    @JvmStatic
    fun extract(level: ClientLevel, partialTick: Float, state: SkyRenderState) {
        Safety.run(Safety.SKY, Unit) { extractSky(level, partialTick, state) }
        if (!Safety.ok(Safety.SKY)) valid = false
    }

    private fun extractSky(level: ClientLevel, partialTick: Float, state: SkyRenderState) {
        if (!enabled || failed) { valid = false; return }
        valid = state.skybox == net.minecraft.world.level.dimension.DimensionType.Skybox.OVERWORLD
        if (!valid) return
        prepare()
        val tick = clockTick(level, partialTick)
        val jd = clock.jd(tick)
        val sun = Sun.position(jd)
        val moon = Moon.position(jd)
        val lst = clock.lst(tick)
        val horizon = Frames.horizon(lst, latitude)
        skyMat3 = horizon * Frames.precession(jd)
        setMatrix(skyMatrix, skyMat3)
        planets = planetsAt(jd, tick, level)
        asteroids = Asteroids.all(jd)

        // 해
        val sunEq = sun.dir
        sunLon = sun.lon
        tickNow = tick
        sunWorld = horizon * sunEq
        sunDistAu = sun.distAu
        sunAlt = asinD(sunWorld.y)
        // 망원경을 켜면 해·달 실제 크기
        val scale = if (TelescopeView.magnifying) 1.0 else discScale
        sunRadius = Sun.angularRadius(sun.distAu) * scale

        // 달: 늘 관측자 기준 방향·거리 (그 위도·경도, 시차 최대 약 1°) — 일식은 그 장소에서 실제로 가릴 때만
        val moonEq = moon.dir
        val obs = Vec3.fromSpherical(lst, latitude) * Moon.EARTH_RADIUS_KM
        val topoVec = moonEq * moon.distKm - obs
        val shown = topoVec.normalized()
        moonTopoKm = topoVec.length()
        moonRadius = Moon.angularRadius(moonTopoKm) * scale
        val phase = Moon.phase(moon, sun)
        moonIllum = phase.illuminated
        val ncp = Vec3(0.0, 0.0, 1.0)
        val north = (ncp - shown * (shown dot ncp)).let { if (it.length() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalized() }
        val east = north cross shown
        moonWorld = horizon * shown
        // 달 사각형: 위 = 천구 북쪽, 오른쪽 = 서쪽 (달 지도 앞면이 그대로 보이는 방향). 위상은 셰이더가 해 방향으로
        moonUp = horizon * north
        moonRight = horizon * (east * -1.0)
        moonLight = (phase.illuminated * (asinD(moonWorld.y) / 10.0).coerceIn(0.0, 1.0)).toFloat()
        moonLib = Moon.libration(jd, moon)

        // 월식: 반영은 살짝 어둡게, 본영은 붉고 어둡게 (블러드문)
        val shadow = Eclipse.lunar(moon, sun)
        val pen = 1f - 0.35f * shadow.penumbraCover.toFloat()
        val u = shadow.umbraCover.toFloat()
        lunarUmbra = u
        moonTint.set(pen * lerp(1f, 0.55f, u), pen * lerp(1f, 0.16f, u), pen * lerp(1f, 0.08f, u), 1f)

        // 일식: 그려지는 해·달 자리로 가린 넓이 → 하늘색 어둡게 (안개·땅 밝기는 믹스인에서 eclipseDim 으로)
        eclipseObsc = if (sunAlt > -2 * sunRadius - 1) discOverlap(sunRadius, moonRadius, Math.toDegrees(acos((sunWorld.normalized() dot moonWorld.normalized()).coerceIn(-1.0, 1.0)))).toFloat() else 0f
        val tot = smooth(0.9f, 1f, eclipseObsc)
        eclipseDim = 0.25f * eclipseObsc + 0.75f * tot
        if (eclipseDim > 0.001f) {
            val k = 1f - 0.92f * eclipseDim
            state.skyColor = org.joml.Vector3f(state.skyColor.x() * k, state.skyColor.y() * k, state.skyColor.z() * k)
        }
        val sky = state.skyColor
        skyR = sky.x(); skyG = sky.y(); skyB = sky.z()
        skyDark = 1f - smooth(0.02f, 0.25f, 0.3f * skyR + 0.55f * skyG + 0.15f * skyB)
        clear = state.rainBrightness
        // 빛공해 하늘: 밤하늘이 주황빛 회색으로 밝아짐 (보틀 지수, 최대 +0.035)
        if (kr.inf.cheonsang.client.Visibility.on(kr.inf.cheonsang.client.Visibility.Fx.LIGHTPOLLUTION) && sunAlt < -2.0) {
            val a = 0.035f * kr.inf.cheonsang.client.AmbientLight.glow() * smooth(2f, 10f, (-sunAlt).toFloat())
            if (a > 0.0005f) {
                state.skyColor = org.joml.Vector3f(state.skyColor.x() + 1.0f * a, state.skyColor.y() + 0.8f * a, state.skyColor.z() + 0.6f * a)
                skyR = state.skyColor.x(); skyG = state.skyColor.y(); skyB = state.skyColor.z()
            }
        }
        // 달빛 하늘: 달이 밝게 떠 있으면 밤하늘이 푸르스름하게 밝아짐 (천정 달빛 밝기의 로그)
        if (sunAlt < -2.0) {
            val ratio = kr.inf.cheonsang.client.Visibility.moonNl(Vec3(0.0, 1.0, 0.0)) / kr.inf.cheonsang.client.Visibility.nl(kr.inf.cheonsang.client.Visibility.DARK_SKY)
            val a = (log10(1.0 + ratio) / 2.5).coerceIn(0.0, 1.0).toFloat() * 0.06f * smooth(2f, 8f, (-sunAlt).toFloat())
            if (a > 0.0005f) {
                state.skyColor = org.joml.Vector3f(state.skyColor.x() + 0.55f * a, state.skyColor.y() + 0.70f * a, state.skyColor.z() + 1.0f * a)
                skyR = state.skyColor.x(); skyG = state.skyColor.y(); skyB = state.skyColor.z()
            }
        }
    }

    /**
     * 추적 떨림 방지: 이번 프레임 시각의 하늘 행렬 (J2000 → 월드). 마우스 처리(추적)는 하늘 그리기(extract)보다 먼저라
     * 지난 프레임 행렬이 아니라 같은 시계·같은 프레임 보간값으로 새로 계산한다 (한 프레임씩 늦게 보이지 않도록)
     */
    fun skyMatNow(): Mat3? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val tick = clockTick(level, mc.deltaTracker.getGameTimeDeltaPartialTick(false))
        return Frames.horizon(clock.lst(tick), latitude) * Frames.precession(clock.jd(tick))
    }

    /** 지난 프레임 월드 방향 → 이번 프레임 월드 방향 (하늘에 붙은 것만 — 행성 고유 움직임은 한 프레임 무시) */
    fun toNow(w: Vec3, now: Mat3): Vec3 {
        val m = skyMat3.m
        val j = Vec3(m[0] * w.x + m[3] * w.y + m[6] * w.z, m[1] * w.x + m[4] * w.y + m[7] * w.z, m[2] * w.x + m[5] * w.y + m[8] * w.z)
        return now * j
    }

    /** 늘 쓰는 텍스처 (기본 모드 안) — 현실 은하수 4K 는 R8 로 따로 ([loadGray]) */
    private val allTextures: List<Identifier> by lazy {
        listOf(TEX_STAR, TEX_DISC, TEX_MOON_MAP, TEX_MOON_SMALL, TEX_MOON_1K, TEX_MOON_4K, TEX_MOON_SOFT, TEX_GLOW, TEX_METEOR, TEX_JUPITER, TEX_SATURN, TEX_RING, TEX_RING_FRONT, TEX_GRS, TEX_MARS) +
            TEX_PHASE + mwPatches.map { it.real }
    }

    /** R8 텍스처로 등록·업로드 (이미 했으면 그대로) */
    private fun loadGray(id: Identifier) {
        if (grayRegistered.add(id)) Minecraft.getInstance().textureManager.registerAndLoad(id, GrayTexture(id))
    }

    /** 큰 텍스처 올리기 (이미 올라가 있으면 그대로) */
    private fun loadBig(id: Identifier, gray: Boolean = false) {
        if (id in loadedBig) return
        if (gray) loadGray(id) else Minecraft.getInstance().textureManager.getTexture(id)
        loadedBig.add(id)
    }

    /** 큰 텍스처 전부 내림 */
    private fun releaseBig() {
        val tm = Minecraft.getInstance().textureManager
        for (id in loadedBig) { tm.release(id); grayRegistered.remove(id) }
        loadedBig.clear()
        TileSky.releaseAll()
        PhotoSky.releaseAll()
    }

    /**
     * 그리기 전에 텍스처·버퍼 준비. 26.3 은 하늘 렌더 패스가 열린 동안 텍스처 업로드를 막아서, 처음 쓰는 텍스처를 그리는 중에 불러오면 튕긴다.
     * 프레임마다 불러도 이미 올라간 텍스처는 찾기만 하므로 가볍다.
     */
    private fun prepare() {
        val tm = Minecraft.getInstance().textureManager
        for (id in allTextures) tm.getTexture(id)
        loadGray(TEX_MILKY_REAL)
        loadGray(TEX_GALAXY)   // 그리는 중(렌더 패스 안)에 올리면 튕김 → 여기서 미리
        val photo = photoOn
        if (photo) { tm.getTexture(TEX_MILKY); for (p in mwPatches) tm.getTexture(p.lx) }
        // 별자리 그림: 켤 때 처음 한 번 올림
        if (TelescopeFilter.art) { tm.getTexture(TEX_ART); artLoaded = true; if (artBuffer == null) buildArt() }
        // 망원경을 손에 들면 (켜기 전에) 지금 모드의 큰 그림을 미리 올림 / 2분 안 쓰면 내림
        val now = System.nanoTime()
        if (TelescopeView.active || TelescopeView.held()) {
            lastBigUse = now
            if (photo) {
                loadBig(TEX_MILKY_8K)
            } else {
                loadBig(TEX_MILKY_REAL_HI, gray = true)
            }
            if (Packs.hiOk && TelescopeView.magnifying) loadBig(TEX_MOON_8K)
        } else if (loadedBig.isNotEmpty() && now - lastBigUse > BIG_KEEP_NANOS) {
            releaseBig()
        }
        prepareTiles(now, photo)
        if (starBuffer == null) buildStars()
        if (quadBuffer == null) buildQuad()
        if (milkyBuffer == null) buildMilkyWay()
        for (k in -1 until TelescopeView.LEVELS.size) if (k !in dsoMeshes) buildDso(k)
        if (twilightBuffer == null) buildTwilight()
    }

    /** 이 프레임에 그릴 은하수 조각 (null = 기본 그림 그대로) */
    private var tileDraw: List<TileSky.Tile>? = null
    private var tileNow = 0L

    /** 초고화질팩 + 확대 중 + 기본 8K 보다 고운 단계가 필요하면 보이는 조각 준비 (렌더 패스 밖) */
    private fun prepareTiles(now: Long, photo: Boolean) {
        tileDraw = null
        tileNow = now
        val mc = Minecraft.getInstance()
        val cam = mc.gameRenderer.mainCamera()
        val fov = cam.getFov().toDouble()
        val h = mc.window.height.coerceAtLeast(1)
        val half = TileSky.halfDiag(fov, mc.window.width.toDouble() / h)
        val f = cam.forwardVector()
        val m = skyMat3.m
        // 월드 → 하늘(J2000): skyMat3 의 전치
        val view = Vec3(m[0] * f.x() + m[3] * f.y() + m[6] * f.z(), m[1] * f.x() + m[4] * f.y() + m[7] * f.z(), m[2] * f.x() + m[5] * f.y() + m[8] * f.z()).normalized()
        // 실사 사진 — 장노출 모드에서 확대 중일 때 화면 근처 것만
        PhotoSky.update(view.takeIf { photo && TelescopeView.magnifying }, half, TelescopeView.zoom, now)
        if (!TelescopeView.active || !Packs.hiOk || RenderSystem.getCompiledPipelineNullable(MILKY_PIPELINE) == null) return
        val lv = TileSky.levelFor(photo, fov / h) ?: return
        tileDraw = TileSky.update(view, half, lv, now)
    }

    /** 조각으로 은하수 — 조각마다 (올라온 조각) 또는 (한 단계 낮은 조각 → 없으면 기본 그림), 올라온 직후엔 둘을 섞어 부드럽게 */
    private fun drawMilkyTiles(pass: RenderPass, tiles: List<TileSky.Tile>, k: Float, photo: Boolean) {
        val base = milkyTexture()
        val baseParams = if (photo) MILKY_RGBA else MILKY_GRAY
        for (t in tiles) {
            val mesh = t.mesh ?: continue
            val a = TileSky.fade(t, tileNow)
            if (a < 1f) {
                val p = TileSky.parentOf(t)?.takeIf { it.state == 2 }
                val kk = k * (1f - a)
                if (p != null) draw(pass, "milky_tile", MILKY_PIPELINE, skyTransform(Vector4f(kk, kk, kk, kk), TileSky.params(p)), p.id, mesh, t.meshCount)
                else draw(pass, "milky_tile", MILKY_PIPELINE, skyTransform(Vector4f(kk, kk, kk, kk), baseParams), base, mesh, t.meshCount)
            }
            if (a > 0f) {
                val kk = k * a
                draw(pass, "milky_tile", MILKY_PIPELINE, skyTransform(Vector4f(kk, kk, kk, kk), t.params), t.id, mesh, t.meshCount)
            }
        }
    }

    /** 망원경용 큰 텍스처가 하나라도 올라가 있는지 */
    fun bigTexturesLoaded() = loadedBig.isNotEmpty()

    /** 지금 쓸 은하수 그림 — 확대 중이고 큰 그림(장노출 8K·현실 12K)이 올라가 있으면 그것 */
    private fun milkyTexture(): Identifier {
        val (small, big) = if (photoOn) TEX_MILKY to TEX_MILKY_8K else TEX_MILKY_REAL to TEX_MILKY_REAL_HI
        return if (TelescopeView.magnifying && big in loadedBig) big else small
    }

    /**
     * 하늘 시각 = 마크 시계 자체 값 (총 틱 + 시계 보간 + 멈춤 아니면 속도 × 프레임 보간).
     * 게임 프레임 보간값을 그대로 더하면 시계가 멈추거나 실시간 속도일 때 하늘이 1틱씩 떨림
     */
    @JvmStatic
    fun clockTick(level: ClientLevel, framePartial: Float): Double {
        val holder = level.dimensionType().defaultClock().orElse(null) ?: return level.defaultClockTime + framePartial.toDouble()
        val c = level.clockManager().getInstance(holder)
        return c.totalTicks() + c.partialTick().toDouble() + if (c.isPaused) 0.0 else c.rate() * framePartial.toDouble()
    }

    // ── 행성은 1초(실제 시간)마다 계산 + 그 사이 보간 ──
    /** 계산해 둔 두 시점 A·B (B = A + 하늘 시간 1초 분량) */
    private var planA: List<PlanetPos>? = null
    private var planB: List<PlanetPos>? = null
    private var planJdA = 0.0
    private var planJdB = 0.0
    /** 보간을 안 하는 한 칸 길이 (일) — 1초에 이보다 많이 가면 매 프레임 계산 */
    private const val PLANET_MAX_SPAN = 0.5

    /** 서버 시각·위치가 바뀌면 행성 보간 버림 */
    fun invalidate() { planA = null; planB = null }

    /** 시계 속도 (틱/틱, 멈춤 0) */
    private fun clockRate(level: ClientLevel): Double {
        val holder = level.dimensionType().defaultClock().orElse(null) ?: return 1.0
        val c = level.clockManager().getInstance(holder)
        return if (c.isPaused) 0.0 else c.rate().toDouble()
    }

    /**
     * jd 의 행성 위치. A~B 사이면 방향만 보간 (나머지 값은 A), B 를 지나면 B → A 로 넘기고 새 B 계산.
     * 시간 점프(A 보다 앞, B 한 칸 넘어 뒤)나 시계 속도가 바뀌면 보간 버리고 즉시 다시 계산
     */
    private fun planetsAt(jd: Double, tick: Double, level: ClientLevel): List<PlanetPos> {
        val span = clock.jd(tick + 20.0 * clockRate(level)) - jd
        if (span <= 0.0 || span > PLANET_MAX_SPAN) {
            if (span <= 0.0 && planA != null && jd == planJdA) return planA!!
            planA = Planets.all(jd); planJdA = jd; planB = null; planJdB = jd
            return planA!!
        }
        var a = planA; var b = planB
        val cur = planJdB - planJdA
        val sameRate = b != null && kotlin.math.abs(cur - span) <= span * 0.05
        if (a == null || b == null || !sameRate || jd < planJdA || jd > planJdB + span) {
            a = Planets.all(jd); planJdA = jd
            b = Planets.all(jd + span); planJdB = jd + span
        } else if (jd > planJdB) {
            a = b; planJdA = planJdB
            b = Planets.all(planJdA + span); planJdB = planJdA + span
        }
        planA = a; planB = b
        val t = ((jd - planJdA) / (planJdB - planJdA)).coerceIn(0.0, 1.0)
        return List(a.size) { i ->
            val p = a[i]; val q = b[i]
            p.copy(dir = (p.dir * (1 - t) + q.dir * t).normalized())
        }
    }

    /** 바닐라 해돋이·노을 부채꼴 대신 천상 박명 빛을 쓰므로 바닐라 것은 끔 */
    @JvmStatic
    fun replacesSunrise() = isActive()

    /**
     * 박명 빛: 해 방위의 지평선 쪽이 붉게 물듦. 해 고도 -1° 근처에서 가장 진하고 +10°·-12° 쯤이면 사라짐.
     * 모양은 buildTwilight 의 고정 그물(+X 방향 기준)을 해 방위로 돌려 씀. 가장자리 없이 점점 옅어짐.
     */
    private fun drawTwilight(pass: RenderPass) {
        val k = (0.6 * kotlin.math.exp(-((sunAlt + 1.0) / 5.5).pow(2)) * clear).toFloat()
        val hl = kotlin.math.hypot(sunWorld.x, sunWorld.z)
        if (k >= 0.005f && hl >= 1e-4) drawTwilightAt(pass, k, (sunWorld.x / hl).toFloat(), (sunWorld.z / hl).toFloat())
        // 개기일식: 지평선 사방이 노을빛 (그림자 바깥 먼 하늘이 비춤)
        val ring = 0.3f * smooth(0.97f, 1f, eclipseObsc) * clear
        if (ring >= 0.005f) for (q in 0 until 6) {
            val a = q * Math.PI / 3
            drawTwilightAt(pass, ring, kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
        }
    }

    private fun drawTwilightAt(pass: RenderPass, k: Float, hx: Float, hz: Float) {
        val buf = twilightBuffer ?: buildTwilight() ?: return
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix()
        stack.mul(Matrix4f().set(hx, 0f, hz, 0f, 0f, 1f, 0f, 0f, -hz, 0f, hx, 0f, 0f, 0f, 0f, 1f))
        val transform = RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(stack), Vector4f(k, k, k, k))
        stack.popMatrix()
        draw(pass, "twilight", TINT_PIPELINE, transform, TEX_DISC, buf, twilightIndexCount)
    }

    /** 바닐라 별 대신 그림. 그렸으면 true (바닐라 취소) */
    @JvmStatic
    fun renderStars(pass: RenderPass): Boolean = Safety.run(Safety.SKY, false) { renderStarsSky(pass) }

    private fun renderStarsSky(pass: RenderPass): Boolean {
        if (!isActive()) return false
        val buffer = starBuffer ?: buildStars() ?: return false
        val b = starFactor()
        // 통합 가시성 모델: 한계 등급·면광원 진하기 = 지금 보는 방향의 하늘 밝기 (박명·달빛·대기광 …)
        val center = viewCenter()
        val limit = kr.inf.cheonsang.client.Visibility.drawLimit(center)
        // 은하수·빛무리: 하늘이 밝으면 묻힘 (보름달 하늘 약 0.15, 낮 0)
        val faint = (kr.inf.cheonsang.client.Visibility.skyWash(center) * clear).toFloat()
        if (limit < -4f && faint < 0.002f) return true
        if (faint > 0.01f) {
            val milky = milkyBuffer ?: buildMilkyWay()
            if (milky != null) {
                // 배율을 올리면 은하수 그림을 뺌 (×7 그대로 → ×25 약 0.27 → ×40 이상 0). 대신 9등 넘는 배경 별이 드러남
                // 장노출(사진) 모드는 안 줄임 — 사진은 확대해도 은하수 빛이 그대로
                val z = if (TelescopeView.active) TelescopeView.zoom else 1.0
                val photo = photoOn
                // 배율 감쇄 = 출사동 기준 표면 밝기 (0 안 됨, Visibility.milkyFactor). 그림은 ×7→×40 사이에 은하수 → 조각으로 넘김 (해상도)
                // 단 은하수는 최소 MW_FLOOR 만큼 남김 (조각 밖 은하수가 사라지지 않게). 장노출(사진)은 안 줄임
                val zoomK = if (photo) 1f else kr.inf.cheonsang.client.Visibility.milkyFactor(TelescopeView.opticalZoom.takeIf { TelescopeView.active } ?: 1.0).toFloat()
                val cross = (1.0 - kotlin.math.ln(z.coerceAtLeast(7.0) / 7.0) / kotlin.math.ln(40.0 / 7.0)).coerceIn(0.0, 1.0).toFloat()
                val mw = if (photo) 1f else max(cross, MW_FLOOR)
                // Gaia 별 팩이 있으면 그 별빛(그림 속 별과 같은 별)만큼 그림을 줄임 — 팩 없으면 1
                val packKeep = if (TelescopeView.magnifying) DeepStars.milkyKeep(viewCenterJ2000(), limit) else 1f
                val k = 0.75f * faint * zoomK * mw * packKeep
                // 바이큐빅 + 디더링 셰이더 (컴파일 실패하면 다른 방식으로)
                val hq = RenderSystem.getCompiledPipelineNullable(MILKY_PIPELINE) != null
                val tiles = tileDraw
                if (hq && tiles != null) drawMilkyTiles(pass, tiles, k, photo)
                else if (hq) draw(pass, "milkyway", MILKY_PIPELINE, skyTransform(Vector4f(k, k, k, k), if (photo) MILKY_RGBA else MILKY_GRAY), milkyTexture(), milky, milkyIndexCount)
                else draw(pass, "milkyway", if (photo) GLOW_PIPELINE else GRAY_PIPELINE, skyTransform(Vector4f(k, k, k, k)), milkyTexture(), milky, milkyIndexCount)
                // 은하수가 빠지는 만큼 은하수 속 천체 조각이 채움
                // 은하수와 같은 배율 감쇄 적용 (대·소마젤란이 고배율에서도 옅게 남음)
                if (TelescopeView.active && mw < 0.999f) drawMwPatches(pass, 0.75f * faint * zoomK * (1f - mw))
            }
            if (dsoVersion != kr.inf.cheonsang.client.Visibility.version) {
                dsoMeshes.values.forEach { it.buffer.close() }; dsoMeshes.clear()
                galaxyMeshes.values.forEach { it.buffer.close() }; galaxyMeshes.clear()
                dsoVersion = kr.inf.cheonsang.client.Visibility.version
            }
            drawZodiacal(pass)
            val dm = dsoMeshes[dsoKey()] ?: buildDso(dsoKey())
            if (dm != null && dm.count > 0) {
                draw(pass, "deep_sky", TINT_PIPELINE, skyTransform(Vector4f(faint, faint, faint, faint)), TEX_GLOW, dm.buffer, dm.count)
            }
            if (sketchOn()) {
                val gm = galaxyMeshes[dsoKey()] ?: buildGalaxies(dsoKey())
                if (gm != null && gm.count > 0 && TEX_GALAXY in grayRegistered) {
                    draw(pass, "galaxy", GALAXY_PIPELINE, skyTransform(Vector4f(faint, faint, faint, faint)), TEX_GALAXY, gm.buffer, gm.count)
                }
            }
            if (TelescopeView.magnifying && photoOn) drawPhotos(pass, faint)
            // 별자리 그림 — 망원경 켠 동안, 은은하게 (더하기 합성, 검은 바탕은 안 보임)
            if (TelescopeView.active && TelescopeView.level < 1 && TelescopeFilter.art && artLoaded) artBuffer?.let {  // ×1 만 (×7부터 끔)
                val k = 0.32f * b  // 달빛과 무관 (밤 정도만)
                draw(pass, "art", GLOW_PIPELINE, skyTransform(Vector4f(k, k, k, k)), TEX_ART, it, artIndexCount)
            }
        }
        val count = (catalog?.countBrighterThan(limit + 0.5f) ?: 0) * 6
        if (count > 0) {
            val occ = starParams(twinkleAmount())
            val starTr = skyTransform(Vector4f(clear, limit, 1f, starBlurPx()), occ)
            draw(pass, "stars", STARS_PIPELINE, starTr, TEX_STAR, buffer, minOf(count, starIndexCount))
            drawDeep(pass, starTr, limit)
            drawVariables(pass, clear, limit, occ)
        }
        return true
    }

    /** 별 셰이더 TextureMat (stars.vsh) — 달 가림 · 화면 높이 · 월드 위쪽 · 소광 · 굴절 · 색 빠짐 · 반짝임 */
    private fun starParams(twinkle: Float): Matrix4f {
        // 달 원판 안의 별은 안 그림 (TextureMat[0] = 달 방향 J2000, cos(달 반지름)) — 가림막 없이 별이 가려짐
        val m = skyMat3.m; val w = moonWorld.normalized()
        val mj = Vec3(m[0] * w.x + m[3] * w.y + m[6] * w.z, m[1] * w.x + m[4] * w.y + m[7] * w.z, m[2] * w.x + m[5] * w.y + m[8] * w.z)
        return Matrix4f().zero().m00(mj.x.toFloat()).m01(mj.y.toFloat()).m02(mj.z.toFloat()).m03(kotlin.math.cos(Math.toRadians(moonRadius * 1.03)).toFloat())
            .m10(Minecraft.getInstance().window.height.toFloat())  // 별 반지름을 화면 픽셀로 (stars.vsh)
            // 월드 위쪽(J2000) · 소광 계수 / 굴절·색 빠짐 켜짐 · 반짝임 세기·시각
            .m20(m[3].toFloat()).m21(m[4].toFloat()).m22(m[5].toFloat()).m23(if (kr.inf.cheonsang.client.Visibility.on(kr.inf.cheonsang.client.Visibility.Fx.EXTINCTION)) kr.inf.cheonsang.client.Visibility.K_V.toFloat() else 0f)
            .m30(if (kr.inf.cheonsang.client.Visibility.on(kr.inf.cheonsang.client.Visibility.Fx.REFRACTION)) 1f else 0f).m31(if (kr.inf.cheonsang.client.Visibility.on(kr.inf.cheonsang.client.Visibility.Fx.COLORLOSS)) 1f else 0f).m32(twinkle).m33(twinkleTime())
    }

    /** 별 상 최소 반지름 (px) = 회절·시상 (″) × 화면 px/″ — 배율이 구경×2 를 넘으면 커져 흐려짐 */
    private fun starBlurPx(): Float = if (TelescopeView.magnifying) {
        val mc = Minecraft.getInstance()
        val fovV = mc.options.fov().get().toDouble() / TelescopeView.zoom
        (kr.inf.cheonsang.client.Optics.starRadiusArcsec() * mc.window.height / (fovV * 3600.0)).toFloat()
    } else 1f

    // ── 위성·소행성·작은 행성 빛점: star.png 사각형(반지름 약 1px, 시야를 옮기면 깜빡이거나 사라짐) 대신 별 셰이더로 — 별과 같은 최소 1.5px · 가우스 · 등급 진하기 ──
    private const val MAX_POINTS = 64
    private val pointDir = FloatArray(MAX_POINTS * 3)
    private val pointMag = FloatArray(MAX_POINTS)
    private val pointRgb = IntArray(MAX_POINTS)
    private var pointCount = 0
    private var pointBuffer: GpuBuffer? = null

    /** dirJ: J2000 방향 (굴절 전 — 굴절·소광은 별 셰이더가 함) */
    private fun addPoint(dirJ: Vec3, mag: Double, bv: Float) {
        if (pointCount >= MAX_POINTS) return
        val d = dirJ.normalized()
        pointDir[pointCount * 3] = d.x.toFloat(); pointDir[pointCount * 3 + 1] = d.y.toFloat(); pointDir[pointCount * 3 + 2] = d.z.toFloat()
        pointMag[pointCount] = mag.toFloat(); pointRgb[pointCount] = StarCatalog.color(bv)
        pointCount++
    }

    private fun flushPoints(pass: RenderPass) {
        pointBuffer?.close(); pointBuffer = null  // 지난 프레임 것
        val n = pointCount
        pointCount = 0
        if (n == 0) return
        val buf = guard(L.t("cheonsang.cheonsangsky.1")) {
            val bytes = ByteBufferBuilder.exactlySized(n * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
            bytes.use {
                val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                for (i in 0 until n) {
                    val x = pointDir[i * 3] * R; val y = pointDir[i * 3 + 1] * R; val z = pointDir[i * 3 + 2] * R
                    val rgb = pointRgb[i]
                    repeat(4) { k -> bb.addVertex(x, y, z).setUv(pointMag[i], k.toFloat()).setColor(rgb shr 16 and 255, rgb shr 8 and 255, rgb and 255, 255) }
                }
                bb.buildOrThrow().use { mesh ->
                    RenderSystem.getDevice().createBuffer({ "Cheonsang points" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                }
            }
        }.also { if (it == null) failed = false } ?: return  // 실패해도 하늘 전체는 안 끔
        pointBuffer = buf
        val limit = kr.inf.cheonsang.client.Visibility.drawLimit(viewCenter())
        draw(pass, "points", STARS_PIPELINE, skyTransform(Vector4f(clear, limit, 1f, starBlurPx()), starParams(0f)), TEX_STAR, buf, n * 6)
    }

    /**
     * 지구조(지구빛): 달에서 본 지구의 밝은 비율 ≈ 1 − 달 밝은 비율 → 초승달·그믐달 때 어두운 쪽이 가장 밝고 보름 무렵 0.
     * 세기 = 0.06 × (1 − 달 밝은 비율)^1.5
     */
    private fun earthshine(): Float = (0.06 * (1.0 - moonIllum).coerceIn(0.0, 1.0).pow(1.5)).toFloat()

    /** 은하수 그림 최소 비율 (조각으로 넘어가도 은하수가 남는 몫) */
    private const val MW_FLOOR = 0.35f
    private var dsoVersion = 0

    /** 화면 가운데 방향 (월드) — 하늘 밝기를 이 방향으로 잼 */
    fun viewCenter(): Vec3 {
        val f = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector()
        return Vec3(f.x().toDouble(), f.y().toDouble(), f.z().toDouble()).normalized()
    }

    /** 반짝임 세기·시각 (셰이더) */
    @JvmStatic var twinkleAmount: () -> Float = { 0f }
    private fun twinkleTime(): Float = ((System.nanoTime() / 1_000_000L) % 600_000L).toFloat() / 1000f

    /** 박명: 해 고도 -2° 에서 별이 보이기 시작, -12° 에서 최대 (비 오면 줄어듦) */
    fun starFactor(): Float = max(((-sunAlt - 2.0) / 10.0).coerceIn(0.0, 1.0).toFloat(), 0.85f * smooth(0.97f, 1f, eclipseObsc)) * clear

    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /** 반지름 rs 원(해)이 반지름 rm 원(달)과 중심거리 d 일 때 해가 가려진 넓이 비율 (단위: 도, 작은 각이라 평면 근사) */
    private fun discOverlap(rs: Double, rm: Double, d: Double): Double {
        if (d >= rs + rm) return 0.0
        if (d <= rm - rs) return 1.0
        if (d <= rs - rm) return rm * rm / (rs * rs)
        val a1 = rs * rs * acos(((d * d + rs * rs - rm * rm) / (2 * d * rs)).coerceIn(-1.0, 1.0))
        val a2 = rm * rm * acos(((d * d + rm * rm - rs * rs) / (2 * d * rm)).coerceIn(-1.0, 1.0))
        val a3 = 0.5 * kotlin.math.sqrt(((-d + rs + rm) * (d + rs - rm) * (d - rs + rm) * (d + rs + rm)).coerceAtLeast(0.0))
        return ((a1 + a2 - a3) / (Math.PI * rs * rs)).coerceIn(0.0, 1.0)
    }

    /** 별 구면(J2000) 그대로 돌려 그리는 변환 */
    private fun skyTransform(color: Vector4f, params: Matrix4f? = null): com.mojang.renderpearl.api.buffers.GpuBufferSlice {
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix()
        stack.mul(skyMatrix)
        val transform = if (params == null) RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(stack), color)
            else RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(stack), color, org.joml.Vector3f(), params)
        stack.popMatrix()
        return transform
    }

    /** 해(빛무리 → 원판) → 달(가림막 → 밝은 면). 별보다 나중이라 달이 별·해를 가린다 */
    @JvmStatic
    fun renderSunMoon(pass: RenderPass) = Safety.run(Safety.SKY, Unit) { renderSunMoonSky(pass) }

    private fun renderSunMoonSky(pass: RenderPass) {
        if (!isActive()) return
        val quad = quadBuffer ?: buildQuad() ?: return
        if (clear <= 0.002f) return
        val c = clear
        drawTwilight(pass)
        drawAirglow(pass)
        drawLightPollution(pass)
        drawAurora(pass)
        // 행성: 별보다 밝아 박명 초기(해 고도 +1°~-6°)부터 보임. 달보다 먼저 그려서 달에 가려짐(엄폐)
        // 행성·소행성은 가시성 모델(그 방향 하늘 밝기·소광)로 — 박명 곡선 대신
        jovianShown = false
        saturnianShown = false
        for (p in planets) drawPlanet(pass, quad, p, c)
        for (a in asteroids) drawAsteroid(pass, quad, a, c)
        flushPoints(pass)  // 위성·소행성·작은 행성 빛점 (달보다 먼저 → 달에 가려짐)
        // 해 (지평선 아래면 생략)
        if (sunAlt > -2 * sunRadius - 1) {
            // 사각형 = 해 반지름 × 4 (빛무리 자리). 색: 높이 뜨면 흰빛, 지평선 쪽은 주황 → 붉게
            val rs = (R * tan(Math.toRadians(sunRadius))).toFloat()
            val t = (sunAlt / 12.0).coerceIn(0.0, 1.0).pow(0.6).toFloat()
            val col = Vector4f(1f, lerp(0.5f, 0.97f, t), lerp(0.25f, 0.92f, t), c)
            // 가장자리 부드럽게 + 넓은 빛번짐 (높이 뜰수록 눈부시게)
            val open = 1f - eclipseObsc
            val prm = Matrix4f().zero().m00(0.6f).m01(0.3f * open).m02(2.5f).m03(SUN_QUAD)
                .m10(lerp(0.03f, 0.10f, t)).m11(lerp(0.10f, 0.22f, t) * open).m12(0.45f)
            // 지평선: 굴절만큼 올려 그림 + 아래위 굴절 차이로 납작
            val sunDraw = kr.inf.cheonsang.client.Visibility.refractWorld(sunWorld)
            val (sRight, sUp) = flatAxes(sunDraw, sunAlt, sunRadius)
            drawQuad(pass, "sun", SUN_PIPELINE, TEX_DISC, quad, sunDraw, sRight, sUp, rs * SUN_QUAD, col, prm)
            // 코로나 (개기 순간만): 해 둘레 하얀 빛 — 가운데는 다음의 달 가림막이 덮음
            val corona = smooth(0.97f, 1f, eclipseObsc) * c
            if (corona > 0.002f) {
                val k = 0.75f * corona
                drawQuad(pass, "corona", GLOW_PIPELINE, TEX_GLOW, quad, sunDraw, null, null, rs * 4.5f, Vector4f(k * 0.92f, k * 0.95f, k, k))
            }
        }
        // 달: 해 쪽 방향을 달 사각형 기준(오른쪽·위·지구 쪽)으로 셰이더에 넘김. 낮에는 옅게 (하늘빛 위에 더해지는 정도)
        val rm = (R * tan(Math.toRadians(moonRadius))).toFloat()
        val day = ((sunAlt + 6.0) / 12.0).coerceIn(0.0, 1.0).toFloat()
        val toSun = (sunWorld - moonWorld * 0.0026).normalized()
        // 지평선: 굴절 위치 · 납작 · 붉게 (소광 색 — R 0.09 · V 0.172 · B 0.30 등/대기량) · 어두워짐(지각 0.33제곱)
        val moonAlt = asinD(moonWorld.normalized().y)
        val moonDraw = kr.inf.cheonsang.client.Visibility.refractWorld(moonWorld)
        val sq = flatSquash(moonAlt, moonRadius)
        val vert = (Vec3(0.0, 1.0, 0.0) - moonDraw * moonDraw.y).let { if (it.length() < 1e-6) null else it.normalized() }
        fun squash(v: Vec3): Vec3 = if (vert == null) v else v - vert * ((v dot vert) * (1.0 - sq))
        val mRight = squash(moonRight); val mUp = squash(moonUp)
        val (er, eg, eb, edim) = horizonTint(moonAlt)
        val prm = Matrix4f().zero()
            .m00((toSun dot moonRight).toFloat()).m01((toSun dot moonUp).toFloat()).m02(-(toSun dot moonWorld.normalized()).toFloat()).m03(lerp(1f, 0.6f, day))
            .m10(moonTint.x * er * edim).m11(moonTint.y * eg * edim).m12(moonTint.z * eb * edim).m13(earthshine() * (1f - day))
            .m30(Math.toRadians(moonLib.l).toFloat()).m31(Math.toRadians(moonLib.b).toFloat()).m32(Math.toRadians(moonLib.p).toFloat())
        // 하늘색을 더하지 않음. 가림막 — 밤은 검게, 낮은 없음(빛 안 받는 쪽 투명), 일식 때는 하늘색으로 해를 가림
        val sepDeg = Math.toDegrees(acos((sunWorld.normalized() dot moonWorld.normalized()).coerceIn(-1.0, 1.0)))
        val eclipse = if (sunAlt > -2 * sunRadius - 1) (1.0 - ((sepDeg - (sunRadius + moonRadius)) / 1.0)).coerceIn(0.0, 1.0).toFloat() else 0f
        val back = max(skyDark, eclipse)
        // 맨눈: 흐린 지도(바다 무늬만) + 명암 낮춤 + 밤엔 둘레 빛. 망원경: 자세한 지도
        val scope = TelescopeView.magnifying
        val moonTex = if (!scope) TEX_MOON_SOFT else moonScreenPixels().let { px -> if (px < 64) TEX_MOON_SMALL else if (px < 256) TEX_MOON_1K else if (px < 512) TEX_MOON_MAP else if (px < 1024 || TEX_MOON_8K !in loadedBig) TEX_MOON_4K else TEX_MOON_8K }
        prm.m21(if (scope) 1f else 0.8f)
        if (back * c > 0.002f) drawQuad(pass, "moon_mask", MOON_MASK_PIPELINE, moonTex, quad, moonDraw, mRight, mUp, rm,
            Vector4f(skyR * eclipse, skyG * eclipse, skyB * eclipse, back * c), Matrix4f(prm).m22(0f))
        // 달빛 번짐: 대기에 퍼진 달빛 — 가림막 위에 더해서 어두운 쪽도 둘레 하늘과 같은 밝기, 달 근처 은하수는 씻겨 보임
        val g = 0.10f * moonLight * skyDark * c
        if (g > 0.002f) drawQuad(pass, "moon_glow", GLOW_PIPELINE, TEX_GLOW, quad, moonDraw, null, null, rm * 9f, Vector4f(g * 0.92f, g * 0.95f, g, g))
        drawQuad(pass, "moon", MOON_PIPELINE, moonTex, quad, moonDraw, mRight, mUp, rm, Vector4f(1f, 1f, 1f, c), Matrix4f(prm).m22(1f))
        // 유성 (대기 속이라 달보다 앞)
        val dark = starFactor() * (1f - 0.5f * moonLight)
        val view = viewCenter()
        // 유성은 실제 시간으로 흐름 (시간 정지·압축률과 무관). 기준은 컴퓨터 시계라 멀티에서 같은 시각이면 같은 유성
        for (st in Meteors.visible(System.currentTimeMillis() / 50.0, sunLon, skyMat3, dark)) {
            val mid = (st.tail + st.head).normalized()
            // 발전과제 "별똥별 보기": 화면 가운데에서 30° 안을 지나간 별똥별
            if (st.alpha > 0.2f && (mid dot view) > 0.866) meteorSeenNanos = System.nanoTime()
            val along = (st.head - st.tail) * (R / 2.0)
            val width = (mid cross along).normalized() * 0.07
            drawQuad(pass, "meteor", GLOW_PIPELINE, TEX_METEOR, quad, mid, along, width, 1f, Vector4f(1f, 1f, 1f, st.alpha))
        }
    }

    /**
     * 행성 하나. 맨눈·저배율은 빛점, 망원경으로 원판이 빛점보다 커지면 원판(실제 겉보기 지름).
     * 방향 벡터는 J2000 적도 기준으로 만들고 skyMat3 으로 월드로 보냄.
     */
    private fun drawPlanet(pass: RenderPass, quad: GpuBuffer, p: PlanetPos, c: Float) {
        val world0 = skyMat3 * p.dir
        // 그 방향 한계 등급 · 소광 (가시성 모델)
        val lim = kr.inf.cheonsang.client.Visibility.pointLimit(world0).toFloat()
        val m = kr.inf.cheonsang.client.Visibility.effMag(p.mag, world0).toFloat()
        if (m > lim + 0.5f) return
        val pb = ((lim + 1.5f - m) / 2f).coerceIn(0f, 1f) * c
        val zoom = if (TelescopeView.active) TelescopeView.zoom.toFloat() else 1f
        val pointR = (0.20f * 10f.pow(-0.1f * (p.mag.toFloat() - 6f))).coerceIn(0.18f, 1.1f) / 2 / zoom
        val alpha = ((lim + 0.5f - m) / 5.0f).coerceIn(0.04f, 1f).pow(1.6f) * c
        val rgb = StarCatalog.color(p.planet.bv.toFloat())
        val (er, eg, eb, _) = horizonTint(kr.inf.cheonsang.client.Visibility.altitude(world0))
        tintR = er; tintG = eg; tintB = eb
        val cr = (rgb shr 16 and 255) / 255f * er; val cg = (rgb shr 8 and 255) / 255f * eg; val cb = (rgb and 255) / 255f * eb
        val world = kr.inf.cheonsang.client.Visibility.refractWorld(world0)
        val discR = (R * tan(Math.toRadians(p.diameterArcsec / 7200.0))).toFloat()
        if (discR < pointR * 0.8f) {
            addPoint(p.dir, p.mag, p.planet.bv.toFloat())  // 원판이 안 보일 만큼 작으면 별 셰이더 빛점 (반짝임 없음)
            return
        }
        // 원판 기준축: 천구 북쪽 위, 동쪽 (J2000)
        val d = p.dir
        val ncp = Vec3(0.0, 0.0, 1.0)
        val north = (ncp - d * (d dot ncp)).normalized()
        val east = north cross d
        fun axes(pa: Double): Pair<Vec3, Vec3> { // (오른쪽, 위) — 위쪽을 위치각 pa 로
            val up = north * cosD(pa) + east * sinD(pa)
            val right = north * sinD(pa) - east * cosD(pa)
            return skyMat3 * right to skyMat3 * up
        }
        val bright = (pb * 1.2f).coerceAtMost(1f)
        when (p.planet) {
            Planet.JUPITER -> {
                val (rt, up) = axes(Planets.jupiterAxis(d))
                drawQuad(pass, "jupiter", GLOW_PIPELINE, TEX_JUPITER, quad, world, rt * discR.toDouble(), up * (discR * 0.935).toDouble(), 1f, Vector4f(bright * er, bright * eg, bright * eb, bright))
                drawGrs(pass, quad, p, world, rt, up, discR, bright)
                drawJovianMoons(pass, quad, p, pb)
            }
            Planet.SATURN -> {
                val rings = Planets.rings(d)
                val (rt, up) = axes(rings.axisAngle)
                val ringR = discR * RING_RATIO
                val squash = kotlin.math.abs(sinD(rings.tilt)).coerceAtLeast(0.01)
                // 지구가 고리면 남쪽(tilt<0)이면 앞쪽 고리가 위로 → 위아래 뒤집어 '앞 절반' 텍스처를 맞춤
                val flip = if (rings.tilt >= 0) 1.0 else -1.0
                val col = Vector4f(bright * er, bright * eg, bright * eb, bright)
                drawQuad(pass, "saturn_ring", GLOW_PIPELINE, TEX_RING, quad, world, rt * ringR.toDouble(), up * (ringR * squash * flip), 1f, col)
                drawQuad(pass, "saturn", MASK_PIPELINE, TEX_SATURN, quad, world, rt * discR.toDouble(), up * (discR * 0.90).toDouble(), 1f, col)
                drawQuad(pass, "saturn_ring_front", MASK_PIPELINE, TEX_RING_FRONT, quad, world, rt * ringR.toDouble(), up * (ringR * squash * flip), 1f, col)
                drawSaturnMoons(pass, quad, p, pb)
            }
            Planet.MARS -> drawMars(pass, quad, p, world, discR, bright)
            else -> {
                // 수성·금성·화성: 위상 원판, 밝은 쪽(텍스처 오른쪽 = 위치각 270°)을 해 쪽으로
                val (rt, up) = axes(p.brightLimb - 270.0)
                val k = (p.phaseAngle / 12.0).let { kotlin.math.round(it).toInt() }.coerceIn(0, 15)
                // 천왕성·해왕성: 청록·파랑 원판
                val tint = when (p.planet) {
                    Planet.URANUS -> floatArrayOf(0.70f * er, 0.90f * eg, 0.92f * eb)
                    Planet.NEPTUNE -> floatArrayOf(0.50f * er, 0.66f * eg, 0.98f * eb)
                    else -> floatArrayOf(cr, cg, cb)
                }
                drawQuad(pass, "planet_disc", GLOW_PIPELINE, TEX_PHASE[k], quad, world, rt * discR.toDouble(), up * discR.toDouble(), 1f,
                    Vector4f(tint[0] * bright, tint[1] * bright, tint[2] * bright, bright))
            }
        }
    }

    /**
     * 망원경으로 충분히 확대하면 실사 사진이 서서히 나타남.
     * 기준: 천체 긴 지름 × 배율 이 4° → 12° (보통 시야로 환산한 화면 크기) 사이에서 0 → 1.
     */
    private fun drawPhotos(pass: RenderPass, faint: Float) {
        val quad = quadBuffer ?: buildQuad() ?: return
        val zoom = TelescopeView.zoom
        // ×7 → ×25 사이에서 나타남 (×7 은 옅은 빛무리만)
        val stage = (kotlin.math.ln(zoom / 7.0) / kotlin.math.ln(25.0 / 7.0)).coerceIn(0.0, 1.0).toFloat()
        val ncp = Vec3(0.0, 0.0, 1.0)
        for (ph in PhotoSky.photos()) {
            val f = PhotoSky.fade(ph, tileNow)
            if (f <= 0f) continue
            val apparent = ph.size / 60.0 * zoom
            val a = (((apparent - 4.0) / 8.0).coerceIn(0.0, 1.0)).toFloat() * stage * f
            if (a <= 0.01f) continue
            val d = ph.dir
            val north = (ncp - d * (d dot ncp)).normalized()
            val east = north cross d
            // 사진 위쪽 = 위치각 rot (북 → 동), 오른쪽 = 서쪽 (하늘을 안에서 보므로 동쪽이 왼쪽)
            val c = kotlin.math.cos(Math.toRadians(ph.rot)); val sn = kotlin.math.sin(Math.toRadians(ph.rot))
            val upDir = north * c + east * sn
            val rightDir = east * -c + north * sn
            val hw = (R * tan(Math.toRadians(ph.w / 2))).toDouble()
            val hh = (R * tan(Math.toRadians(ph.h / 2))).toDouble()
            // replace: 그 자리 은하수를 덮음 (반투명, 알파 = 가장자리) · 나머지: 더하기
            if (ph.replace) drawQuad(pass, "photo", MASK_PIPELINE, ph.texId, quad, skyMat3 * d, skyMat3 * (rightDir * hw), skyMat3 * (upDir * hh), 1f, Vector4f(faint, faint, faint, a))
            else drawQuad(pass, "photo", GLOW_PIPELINE, ph.texId, quad, skyMat3 * d, skyMat3 * (rightDir * hw), skyMat3 * (upDir * hh), 1f, Vector4f(a * faint, a * faint, a * faint, a))
        }
    }

    /** 은하수 속 천체 조각 — 은하수 그림과 같은 합성·같은 진하기 기준. 은하수와 같은 바이큐빅 셰이더 */
    private fun drawMwPatches(pass: RenderPass, k: Float) {
        if (k <= 0.002f) return
        val quad = quadBuffer ?: buildQuad() ?: return
        val photo = photoOn
        for (p in mwPatches) {
            if (photo && PhotoSky.replaces(p.o.id)) continue
            val d = Vec3.fromSpherical(p.o.ra, p.o.dec)
            val ncp = Vec3(0.0, 0.0, 1.0)
            val north = (ncp - d * (d dot ncp)).normalized()
            val east = north cross d
            val half = (R * tan(Math.toRadians(p.frame / 2))).toDouble()
            val hq = RenderSystem.getCompiledPipelineNullable(MILKY_PIPELINE) != null
            drawQuad(pass, "mw_patch", if (hq) MILKY_PIPELINE else GLOW_PIPELINE, if (photo) p.lx else p.real, quad, skyMat3 * d, skyMat3 * (east * -half), skyMat3 * (north * half), 1f, Vector4f(k, k, k, k),
                if (hq) MILKY_RGBA else null)
        }
    }

    /**
     * 대적점: System II 가운데 경도와 대적점 경도 차로 원판 위 자리 (보이는 쪽만).
     * 오른쪽(서쪽) = sin(가운데 경도 − 대적점 경도)·cos 위도, 위 = 축 북쪽. 가장자리로 갈수록 좁고 어둡게
     */
    private fun drawGrs(pass: RenderPass, quad: GpuBuffer, p: PlanetPos, world: Vec3, rt: Vec3, up: Vec3, discR: Float, bright: Float) {
        val v = Planets.jupiterView2(p, clock.jd(tickNow))
        val dl = v.cmWest - Planets.grsLongitude(clock.jd(tickNow))
        val la = Planets.GRS_LAT; val b = v.lat
        val z = sinD(la) * sinD(b) + cosD(la) * cosD(dl) * cosD(b)
        if (z <= 0.05) return
        val x = cosD(la) * sinD(dl)
        val y = sinD(la) * cosD(b) - cosD(la) * cosD(dl) * sinD(b)
        val c = world.normalized() * 1.0
        val center = c * R.toDouble() + (rt * (x * discR) + up * (y * discR * 0.935))
        val k = kotlin.math.sqrt(z).toFloat() * bright
        // 크기: 동서 반지름 0.10 · 남북 0.08 (목성 반지름 단위, 동서는 가장자리에서 좁아짐)
        // 화면에서 동서 반지름이 4픽셀보다 작으면 키움 (최대 2.5배) — ×300 에서 목성 40~50px 라 실제 비율로는 4~5px
        val discPx = tan(Math.toRadians(Math.toDegrees(kotlin.math.atan((discR / R).toDouble())))) / tan(Math.toRadians(Minecraft.getInstance().gameRenderer.mainCamera().getFov().toDouble() / 2)) * Minecraft.getInstance().window.height / 2
        val grow = (4.0 / (0.10 * discPx).coerceAtLeast(0.1)).coerceIn(1.0, 2.5)
        // 진한 벽돌색 · 진하기 85%
        drawQuad(pass, "grs", MASK_PIPELINE, TEX_GRS, quad, center.normalized(), rt * (0.10 * grow * discR * kotlin.math.sqrt(1 - x * x)), up * (0.08 * grow * discR * 0.935), 1f,
            Vector4f(0.62f * k * tintR, 0.28f * k * tintG, 0.18f * k * tintB, 0.85f * kotlin.math.sqrt(z).toFloat()))
    }

    /**
     * 화성: 달과 같은 셰이더로 지도를 공에 입힘 — 그 시각 가운데 경도(IAU 자전)·위도·축 방향 + 해 쪽 위상.
     * 위 = 천구 북쪽 (축 기울기는 셰이더가 P 로 돌림)
     */
    private fun drawMars(pass: RenderPass, quad: GpuBuffer, p: PlanetPos, world: Vec3, discR: Float, bright: Float) {
        val d = p.dir
        val ncp = Vec3(0.0, 0.0, 1.0)
        val north = (ncp - d * (d dot ncp)).normalized()
        val east = north cross d
        val rt = skyMat3 * (east * -1.0); val up = skyMat3 * north
        val w = world.normalized()
        val toSun = (sunWorld.normalized() * sunDistAu - w * p.distAu).normalized()
        val v = Planets.marsView(p, clock.jd(tickNow))
        val prm = Matrix4f().zero()
            .m00((toSun dot rt).toFloat()).m01((toSun dot up).toFloat()).m02(-(toSun dot w).toFloat()).m03(bright)
            .m10(tintR).m11(tintG).m12(tintB).m13(0f)
            .m21(1f).m22(1f).m23(1f)
            .m30(Math.toRadians(-v.cmWest).toFloat()).m31(Math.toRadians(v.lat).toFloat()).m32(Math.toRadians(v.p).toFloat())
        drawQuad(pass, "mars", MOON_PIPELINE, TEX_MARS, quad, world, rt * discR.toDouble(), up * discR.toDouble(), 1f, Vector4f(1f, 1f, 1f, 1f), prm)
    }

    /** 이번 프레임에 토성 위성을 그렸는지 (좌클릭 탐지 조건) */
    @JvmStatic
    var saturnianShown = false
        private set

    /** 토성 위성 (이름·번호·등급, 월드 방향) — 토성 뒤에 숨은 것·지금 한계등급보다 어두운 것 뺌 */
    fun saturnMoonsWorld(): List<Pair<kr.inf.cheonsang.astro.SaturnMoon, Vec3>> {
        if (!saturnianShown) return emptyList()
        val sat = planets.firstOrNull { it.planet == Planet.SATURN } ?: return emptyList()
        val lim = kr.inf.cheonsang.client.Visibility.pointLimit(skyMat3 * sat.dir) - kr.inf.cheonsang.client.Visibility.extinction(kr.inf.cheonsang.client.Visibility.altitude(skyMat3 * sat.dir)) + 0.5
        return Planets.saturnMoons(clock.jd(tickNow), sat).filter { !it.hidden && it.mag <= lim }.map { it to skyMat3 * it.dir }
    }

    private fun drawSaturnMoons(pass: RenderPass, quad: GpuBuffer, sat: PlanetPos, pb: Float) {
        saturnianShown = true
        for ((m, _) in saturnMoonsWorld()) addPoint(m.dir, m.mag, 1.0f)  // 별 셰이더 (굴절·소광·지평선 색도 별과 같게)
    }

    /** 목성 4대 위성: 작은 빛점. 목성 뒤에 숨은 위성은 안 그림 */
    /** 이번 프레임에 목성 위성을 그렸는지 (원판이 보일 만큼 확대했을 때만) — 좌클릭 탐지 조건 */
    @JvmStatic
    var jovianShown = false
        private set

    /** 목성 위성 (이름, 월드 방향) — 목성 뒤에 숨은 것은 뺌 (좌클릭 탐지용) */
    fun jovianMoonsWorld(): List<Pair<String, Vec3>> {
        if (!jovianShown) return emptyList()
        val jup = planets.firstOrNull { it.planet == Planet.JUPITER } ?: return emptyList()
        val d = jup.dir
        val ncp = Vec3(0.0, 0.0, 1.0)
        val north = (ncp - d * (d dot ncp)).normalized()
        val east = north cross d
        val rJup = Math.toRadians(jup.diameterArcsec / 7200.0)
        return Planets.jovianMoons(clock.jd(tickNow), jup)
            .filter { !(it.east * it.east + it.north * it.north < 1.0 && !it.inFront) }
            .map { it.name to skyMat3 * (d + (east * it.east + north * it.north) * rJup).normalized() }
    }

    /** /cfind: 지금 토성·목성 위성 — 그렸는지와 무관. dirJ = J2000, hidden = 행성 뒤 */
    class MoonNow(val ko: String, val num: String, val planet: Planet, val dirJ: Vec3, val mag: Double, val hidden: Boolean)
    fun moonsNow(): List<MoonNow> {
        val out = ArrayList<MoonNow>()
        val jd = clock.jd(tickNow)
        planets.firstOrNull { it.planet == Planet.SATURN }?.let { sat ->
            for (m in Planets.saturnMoons(jd, sat)) out += MoonNow(m.name, m.num, Planet.SATURN, m.dir, m.mag, m.hidden)
        }
        planets.firstOrNull { it.planet == Planet.JUPITER }?.let { jup ->
            val d = jup.dir
            val ncp = Vec3(0.0, 0.0, 1.0)
            val north = (ncp - d * (d dot ncp)).normalized()
            val east = north cross d
            val rJup = Math.toRadians(jup.diameterArcsec / 7200.0)
            for ((k, mn) in Planets.jovianMoons(jd, jup).withIndex()) {
                val inside = mn.east * mn.east + mn.north * mn.north < 1.0
                out += MoonNow(mn.name, "ⅠⅡⅢⅣ"[k].toString(), Planet.JUPITER, (d + (east * mn.east + north * mn.north) * rJup).normalized(),
                    (JOVIAN_MAG[mn.name] ?: 5.3) + (jup.mag + 2.7).coerceAtLeast(0.0), inside && !mn.inFront)
            }
        }
        return out
    }

    private val JOVIAN_MAG = mapOf("이오" to 5.0, "유로파" to 5.3, "가니메데" to 4.6, "칼리스토" to 5.7)

    private fun drawJovianMoons(pass: RenderPass, quad: GpuBuffer, jup: PlanetPos, pb: Float) {
        jovianShown = true
        val d = jup.dir
        val ncp = Vec3(0.0, 0.0, 1.0)
        val north = (ncp - d * (d dot ncp)).normalized()
        val east = north cross d
        val rJup = Math.toRadians(jup.diameterArcsec / 7200.0) // 목성 반지름 (라디안)
        for (mn in Planets.jovianMoons(clock.jd(tickNow), jup)) {
            val inside = mn.east * mn.east + mn.north * mn.north < 1.0
            if (inside && !mn.inFront) continue
            val dir = (d + (east * mn.east + north * mn.north) * rJup).normalized()
            // 별 셰이더 — 충 무렵 평균 등급 (이오 5.0 · 유로파 5.3 · 가니메데 4.6 · 칼리스토 5.7) + 목성이 충보다 어두운 만큼
            addPoint(dir, (JOVIAN_MAG[mn.name] ?: 5.3) + (jup.mag + 2.7).coerceAtLeast(0.0), 0.9f)
        }
    }

    /** 소행성: 행성 빛점과 같은 방식 (원판 없음) — 별 셰이더 */
    private fun drawAsteroid(pass: RenderPass, quad: GpuBuffer, a: AsteroidPos, c: Float) {
        val w = skyMat3 * a.dir
        val lim = kr.inf.cheonsang.client.Visibility.pointLimit(w).toFloat()
        val m = kr.inf.cheonsang.client.Visibility.effMag(a.mag, w).toFloat()
        if (m > lim + 0.5f) return
        addPoint(a.dir, a.mag, 0.75f)  // 별 셰이더 빛점
    }

    // ── 변광성: 큰 별 버퍼에서는 빼고 (등급 99), 별 하나짜리 버퍼를 따로 — 등급 0 으로 굽고 한계등급을 (한계 − 지금 등급) 으로 넘겨 그림 ──
    private class VarMesh(val star: VariableStars.Star, val buffer: GpuBuffer)
    private var varMeshes: List<VarMesh>? = null

    private fun buildVariables(cat: StarCatalog): List<VarMesh> {
        val out = ArrayList<VarMesh>()
        for (v in VariableStars.list) {
            val i = (0 until cat.realCount).firstOrNull { cat.hip[it] == v.hip } ?: continue
            val buf = guard(L.t("cheonsang.cheonsangsky.6")) {
                val bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
                bytes.use {
                    val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                    addStar(bb, cat, i, 0f)
                    bb.buildOrThrow().use { mesh ->
                        RenderSystem.getDevice().createBuffer({ "Cheonsang variable ${v.hip}" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    }
                }
            } ?: continue
            out += VarMesh(v, buf)
        }
        return out
    }

    private fun drawVariables(pass: RenderPass, b: Float, limit: Float, occ: Matrix4f) {
        val cat = catalog ?: return
        val list = varMeshes ?: buildVariables(cat).also { varMeshes = it }
        val jd = clock.jd(tickNow)
        for (vm in list) {
            val lim = limit - vm.star.mag(jd).toFloat()
            draw(pass, "variable", STARS_PIPELINE, skyTransform(Vector4f(b, lim, 1f, 1f), occ), TEX_STAR, vm.buffer, 6)
        }
    }

    /** 해·달 납작 비율 = 1 − (아래 가장자리 굴절 − 위 가장자리 굴절) / 지름 */
    private fun flatSquash(altDeg: Double, radiusDeg: Double): Double {
        val r = radiusDeg.coerceAtLeast(1e-4)
        return (1.0 - (kr.inf.cheonsang.client.Visibility.refraction(altDeg - r) - kr.inf.cheonsang.client.Visibility.refraction(altDeg + r)) / (2 * r)).coerceIn(0.5, 1.0)
    }

    /** 둥근 원판의 (오른쪽, 위) 축 — 위(지평 수직)를 납작하게 */
    private fun flatAxes(d0: Vec3, altDeg: Double, radiusDeg: Double): Pair<Vec3?, Vec3?> {
        val d = d0.normalized()
        if (kotlin.math.abs(d.y) >= 0.99) return null to null
        val right = (Vec3(0.0, 1.0, 0.0) cross d).normalized()
        val up = d cross right
        return right to up * flatSquash(altDeg, radiusDeg)
    }

    private data class Tint(val r: Float, val g: Float, val b: Float, val dim: Float)
    /** 지금 그리는 행성의 지평선 색 — 원판·고리·위성·대적점에도 같게 */
    private var tintR = 1f
    private var tintG = 1f
    private var tintB = 1f
    /** 지평선 소광 색 (천정 대비, 가장 밝은 채널 1) + 지각 밝기 (V 소광의 0.33제곱, 최소 0.25) */
    private fun horizonTint(altDeg: Double): Tint {
        if (!kr.inf.cheonsang.client.Visibility.on(kr.inf.cheonsang.client.Visibility.Fx.EXTINCTION)) return Tint(1f, 1f, 1f, 1f)
        val x = kr.inf.cheonsang.client.Visibility.airmass(altDeg) - 1.0
        // 색은 별 셰이더(stars.vsh)와 같은 곡선 — 대기량 10 에서 멈춤 · 절반 세기
        val xc = x.coerceAtMost(9.0)
        val r = 10.0.pow(0.2 * 0.082 * xc); val b = 10.0.pow(-0.2 * 0.128 * xc)
        val mx = max(r, max(1.0, b))
        val g = 10.0.pow(-0.4 * 0.172 * x)  // 어두워짐(지각)은 그대로
        return Tint((r / mx).toFloat(), (1.0 / mx).toFloat(), (b / mx).toFloat(), g.pow(0.33).coerceIn(0.25, 1.0).toFloat())
    }

    /** 대기광: 어두운 밤 지평선 둘레 옅은 초록빛 (높이 약 10° 쪽이 가장 밝음, 달빛·박명에 묻힘) */
    private fun drawAirglow(pass: RenderPass) {
        if (!kr.inf.cheonsang.client.Visibility.on(kr.inf.cheonsang.client.Visibility.Fx.AIRGLOW)) return
        val k = 0.035f * starFactor() * (1f - moonLight)
        if (k < 0.003f) return
        val buf = ringBuffer ?: buildRing() ?: return
        val transform = RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(RenderSystem.getModelViewStack()), Vector4f(0.55f * k, 0.80f * k, 0.55f * k, k))
        draw(pass, "airglow", TINT_PIPELINE, transform, TEX_DISC, buf, ringIndexCount)
    }

    /** 빛공해: 빛 몰린 방향 지평선 주황 돔(박명 그물 재사용) + 둘레 띠 — 밤에만 (해 −4°→−12°) */
    private fun drawLightPollution(pass: RenderPass) {
        if (!kr.inf.cheonsang.client.Visibility.on(kr.inf.cheonsang.client.Visibility.Fx.LIGHTPOLLUTION)) return
        val night = ((-sunAlt - 4.0) / 8.0).coerceIn(0.0, 1.0).toFloat() * clear
        if (night < 0.01f) return
        val (dx, dz, k) = kr.inf.cheonsang.client.AmbientLight.dome()
        if (k > 0.01f) drawTwilightAt(pass, 0.35f * k * night, dx, dz)
        val g = 0.05f * kr.inf.cheonsang.client.AmbientLight.glow() * night
        if (g > 0.003f) {
            val buf = ringBuffer ?: buildRing() ?: return
            val transform = RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(RenderSystem.getModelViewStack()), Vector4f(0.9f * g, 0.7f * g, 0.5f * g, g))
            draw(pass, "light_ring", TINT_PIPELINE, transform, TEX_DISC, buf, ringIndexCount)
        }
    }

    /**
     * 오로라 셰이더(aurora.fsh): 시선을 따라 오로라 층 속을 걸으며 커튼 3장 + 옅은 빛 장막의 빛을 모아 원근·수렴·입체감이 저절로 나옴.
     * 오로라 띠 위도 = 69° − 3°×세기, 내 위도와 차이 × 111km 가 띠까지 거리. 반구 그물은 한 번만 만들고 시각·세기·거리는 TextureMat[0] 으로 넘김
     */
    private fun drawAurora(pass: RenderPass) {
        val a = kr.inf.cheonsang.client.AmbientLight.auroraStrength(clock.jd(tickNow)).toFloat() * clear
        if (a < 0.01f) return
        val buf = auroraBuffer ?: buildAuroraDome() ?: return
        val st = a.coerceAtMost(1f)
        val ovalLat = 69.0 - 3.0 * st
        val distKm = ((ovalLat - kotlin.math.abs(latitude)) * 111.0).toFloat()
        // 1시간마다 0 으로 돌아가면 정각마다 오로라 모양이 툭 바뀜 → 약 28시간 주기 (float 정밀도 0.01초 안쪽)
        val t = ((System.nanoTime() % 100_000_000_000_000L) / 1e9).toFloat()
        // 넓은 현상이라 망원경 배율이 오를수록 옅게
        val zoomFade = if (TelescopeView.active) (1.0 / (1.0 + (TelescopeView.zoom - 1.0) * 0.15)).toFloat() else 1f
        val k = (0.15f + 0.85f * st) * zoomFade
        val prm = Matrix4f().zero().m00(t).m01(st).m02(distKm).m03(if (latitude < 0) -1f else 1f)
        val transform = RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(RenderSystem.getModelViewStack()), Vector4f(1f, 1f, 1f, k), org.joml.Vector3f(), prm)
        draw(pass, "aurora", AURORA_PIPELINE, transform, TEX_DISC, buf, auroraIndexCount)
    }

    private val AURORA_PIPELINE = pipeline("aurora", Cheonsang.id("core/aurora"), DefaultVertexFormat.POSITION_TEX, BlendFunction.OVERLAY)
    private var auroraBuffer: GpuBuffer? = null
    private var auroraIndexCount = 0

    /** 지평 좌표 반구 (고도 −3°~90°, 방위 64 × 고도 16 칸) — 북 = −Z, 동 = +X */
    private fun buildAuroraDome(): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.7")) {
        val cols = 64; val rows = 16
        val bytes = ByteBufferBuilder.exactlySized(cols * rows * 4 * DefaultVertexFormat.POSITION_TEX.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX)
            fun vtx(az: Double, alt: Double) {
                val x = cosD(alt) * sinD(az); val y = sinD(alt); val z = -cosD(alt) * cosD(az)
                bb.addVertex((x * R).toFloat(), (y * R).toFloat(), (z * R).toFloat()).setUv(0.5f, 0.5f)
            }
            for (i in 0 until cols) for (j in 0 until rows) {
                val a0 = 360.0 * i / cols; val a1 = 360.0 * (i + 1) / cols
                val e0 = -3.0 + 93.0 * j / rows; val e1 = -3.0 + 93.0 * (j + 1) / rows
                vtx(a0, e0); vtx(a1, e0); vtx(a1, e1); vtx(a0, e1)
            }
            bb.buildOrThrow().use { mesh ->
                auroraIndexCount = mesh.drawState().indexCount()
                RenderSystem.getDevice().createBuffer({ "Cheonsang aurora dome" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    .also { auroraBuffer = it }
            }
        }
    }

    /**
     * 황도광·대일조: 황도 띠 그물 (J2000) — 해 황경이 0.5° 넘게 바뀌면 다시 만듦. 정점 진하기 = AmbientLight.zodiacalRel.
     * 아주 어두운 하늘만 (해 −15°→−19°, 달빛 없음, 보틀 4 아래)
     */
    private var zodiBuffer: GpuBuffer? = null
    private var zodiIndexCount = 0
    private var zodiSunLon = Double.NaN
    /** 별똥별이 화면 가까이 지나간 마지막 시각 (도감) */
    @JvmStatic var meteorSeenNanos = 0L

    /** 황도광 보이는 정도 0~1 (해 −15°→−19°, 달빛 없음, 보틀 4 아래) — 그리기·도감 같은 기준 */
    fun zodiacalDark(): Float = ((-sunAlt - 15.0) / 4.0).coerceIn(0.0, 1.0).toFloat() * (1f - moonLight).coerceAtLeast(0f) *
        ((4.0 - kr.inf.cheonsang.client.AmbientLight.bortle) / 2.0).coerceIn(0.0, 1.0).toFloat() * clear

    private fun drawZodiacal(pass: RenderPass) {
        if (!kr.inf.cheonsang.client.Visibility.on(kr.inf.cheonsang.client.Visibility.Fx.ZODIACAL)) return
        val dark = zodiacalDark()
        if (dark < 0.01f) return
        if (zodiBuffer == null || kotlin.math.abs(kr.inf.cheonsang.astro.norm180(sunLon - zodiSunLon)) > 0.5) {
            zodiBuffer?.close(); zodiBuffer = null
            buildZodiacal(sunLon)
        }
        val buf = zodiBuffer ?: return
        val k = 0.05f * dark
        draw(pass, "zodiacal", TINT_PIPELINE, skyTransform(Vector4f(k, k * 0.95f, k * 0.85f, k)), TEX_DISC, buf, zodiIndexCount)
    }

    private fun buildZodiacal(lonSun: Double): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.8")) {
        val cols = 90; val rows = 18
        val eps0 = 23.4392911
        val bytes = ByteBufferBuilder.exactlySized(cols * rows * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            fun vtx(lam: Double, beta: Double) {
                val e = Math.toDegrees(kotlin.math.acos((cosD(beta) * cosD(lam - lonSun)).coerceIn(-1.0, 1.0)))
                val w = (kr.inf.cheonsang.client.AmbientLight.zodiacalRel(e, beta) / 1.6).coerceIn(0.0, 1.0)
                val p = kr.inf.cheonsang.astro.Frames.eclipticToEquatorial(lam, beta, eps0) * R.toDouble()
                bb.addVertex(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()).setUv(0.5f, 0.5f).setColor(255, 255, 255, (w * 255).toInt().coerceIn(0, 255))
            }
            for (i in 0 until cols) for (j in 0 until rows) {
                val l0 = 360.0 * i / cols; val l1 = 360.0 * (i + 1) / cols
                val b0 = -36.0 + 72.0 * j / rows; val b1 = -36.0 + 72.0 * (j + 1) / rows
                vtx(l0, b0); vtx(l1, b0); vtx(l1, b1); vtx(l0, b1)
            }
            bb.buildOrThrow().use { mesh ->
                zodiIndexCount = mesh.drawState().indexCount()
                zodiSunLon = lonSun
                RenderSystem.getDevice().createBuffer({ "Cheonsang zodiacal" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    .also { zodiBuffer = it }
            }
        }
    }

    /** 빛공해 돔에서도 씀: 방위 한 바퀴 · 고도 −6°~40° 띠, 흰색 × 세기 exp(−((고도 − 6°)/12°)²) */
    private var ringBuffer: GpuBuffer? = null
    private var ringIndexCount = 0
    private fun buildRing(): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.9")) {
        val cols = 72; val rows = 16
        val bytes = ByteBufferBuilder.exactlySized(cols * rows * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            fun vtx(az: Double, alt: Double) {
                val w = kotlin.math.exp(-((alt - 6.0) / if (alt >= 6.0) 12.0 else 5.0).pow(2))
                val x = cosD(alt) * cosD(az); val y = sinD(alt); val z = cosD(alt) * sinD(az)
                bb.addVertex((x * R).toFloat(), (y * R).toFloat(), (z * R).toFloat()).setUv(0.5f, 0.5f).setColor(255, 255, 255, (w * 255).toInt().coerceIn(0, 255))
            }
            for (i in 0 until cols) for (j in 0 until rows) {
                val a0 = 360.0 * i / cols; val a1 = 360.0 * (i + 1) / cols
                val e0 = -6.0 + 46.0 * j / rows; val e1 = -6.0 + 46.0 * (j + 1) / rows
                vtx(a0, e0); vtx(a1, e0); vtx(a1, e1); vtx(a0, e1)
            }
            bb.buildOrThrow().use { mesh ->
                ringIndexCount = mesh.drawState().indexCount()
                RenderSystem.getDevice().createBuffer({ "Cheonsang ring" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    .also { ringBuffer = it }
            }
        }
    }

    /** 해 셰이더 사각형 반지름 ÷ 해 반지름 (빛무리가 들어갈 자리) */
    private const val SUN_QUAD = 12f

    /** 달 반지름이 화면에서 몇 픽셀인지 (작은 지도로 바꿀지 판단) */
    private fun moonScreenPixels(): Double {
        val mc = Minecraft.getInstance()
        val fov = mc.gameRenderer.mainCamera().getFov().toDouble()
        return tan(Math.toRadians(moonRadius)) / tan(Math.toRadians(fov / 2)) * mc.window.height / 2
    }

    /** 단위 사각형(-1~1)을 dir 방향 하늘(반지름 100)에 radius 크기로. params = 셰이더 값 (TextureMat 자리로 넘김) */
    private fun drawQuad(
        pass: RenderPass, label: String, pipeline: RenderPipeline, texture: Identifier, quad: GpuBuffer,
        dir: Vec3, right0: Vec3?, up0: Vec3?, radius: Float, color: Vector4f, params: Matrix4f? = null,
    ) {
        val d = dir.normalized()
        val right: Vec3
        val up: Vec3
        if (right0 != null && up0 != null) { right = right0; up = up0 } else {
            // 둥근 원판이라 방향 무관 — 월드 위쪽 기준으로 축만 잡음
            val ref = if (kotlin.math.abs(d.y) < 0.99) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
            right = (ref cross d).normalized()
            up = d cross right
        }
        val m = Matrix4f().set(
            (right.x * radius).toFloat(), (right.y * radius).toFloat(), (right.z * radius).toFloat(), 0f,
            (up.x * radius).toFloat(), (up.y * radius).toFloat(), (up.z * radius).toFloat(), 0f,
            d.x.toFloat(), d.y.toFloat(), d.z.toFloat(), 0f,
            (d.x * R).toFloat(), (d.y * R).toFloat(), (d.z * R).toFloat(), 1f,
        )
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix()
        stack.mul(m)
        val transform = if (params == null) RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(stack), color)
            else RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(stack), color, org.joml.Vector3f(), params)
        stack.popMatrix()
        draw(pass, label, pipeline, transform, texture, quad, 6)
    }

    private fun draw(
        pass: RenderPass, label: String, pipeline: RenderPipeline,
        transform: com.mojang.renderpearl.api.buffers.GpuBufferSlice, texture: Identifier, vertices: GpuBuffer, indexCount: Int,
    ) {
        // 셰이더 컴파일 실패 등으로 파이프라인이 없으면 그 부분만 건너뜀 (튕기지 않게)
        val compiled = RenderSystem.getCompiledPipelineNullable(pipeline) ?: run { warnOnce(label); return }
        val tex = Minecraft.getInstance().textureManager.getTexture(texture)
        val indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        val indexBuffer = indices.getBuffer(indexCount)
        pass.pushDebugGroup { "Cheonsang $label" }
        pass.setPipeline(compiled)
        RenderSystem.bindDefaultUniforms(pass)
        pass.setUniform("DynamicTransforms", transform)
        pass.setUniform("Sampler0", tex.getTextureView(), tex.getSampler())
        pass.setVertexBuffer(0, vertices.slice())
        pass.setIndexBuffer(indexBuffer, indices.type())
        pass.drawIndexed(indexCount, 1, 0, 0, 0)
        pass.popDebugGroup()
    }

    /**
     * 은하수: J2000 적도 격자 구 (64×32 칸). 텍스처는 가운데가 적경 0°, 왼쪽으로 갈수록 적경 증가.
     * 적경 180° 에서 텍스처 가장자리가 만나므로 그 선에서 u 를 끊어 이음매가 생기지 않게 한다.
     */
    /**
     * 별자리 그림 그물: 그림 픽셀 (x, y, 1) → 하늘 방향 = 정규화(M · (x, y, 1)), M = 기준 별 3개로 푼 3×3 (Stellarium 과 같은 방식).
     * 큰 별자리도 휘어진 하늘에 맞게 8×8 칸으로 나눔. J2000 좌표 → 은하수처럼 skyTransform 으로 그림
     */
    private fun buildArt(): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.10")) {
        val arts = SkyData.art
        val n = 8
        val bytes = ByteBufferBuilder.exactlySized(maxOf(1, arts.size) * n * n * 4 * DefaultVertexFormat.POSITION_TEX.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX)
            for (a in arts) {
                val p = DoubleArray(9); val d = DoubleArray(9)
                for (k in 0 until 3) {
                    val an = a.anchors[k]
                    p[k] = an[0]; p[3 + k] = an[1]; p[6 + k] = 1.0
                    val v = Vec3.fromSpherical(an[2], an[3])
                    d[k] = v.x; d[3 + k] = v.y; d[6 + k] = v.z
                }
                val m = mul3(d, inv3(p))
                fun vtx(i: Int, j: Int) {
                    val x = a.w * i.toDouble() / n; val y = a.h * j.toDouble() / n
                    var vx = m[0] * x + m[1] * y + m[2]; var vy = m[3] * x + m[4] * y + m[5]; var vz = m[6] * x + m[7] * y + m[8]
                    val len = kotlin.math.sqrt(vx * vx + vy * vy + vz * vz); vx /= len; vy /= len; vz /= len
                    val u = a.uv[0] + (a.uv[2] - a.uv[0]) * i / n; val v = a.uv[1] + (a.uv[3] - a.uv[1]) * j / n
                    bb.addVertex((vx * R).toFloat(), (vy * R).toFloat(), (vz * R).toFloat()).setUv(u.toFloat(), v.toFloat())
                }
                for (i in 0 until n) for (j in 0 until n) { vtx(i, j); vtx(i + 1, j); vtx(i + 1, j + 1); vtx(i, j + 1) }
            }
            bb.buildOrThrow().use { mesh ->
                artIndexCount = mesh.drawState().indexCount()
                Cheonsang.LOG.info("Cheonsang: {} constellation art images", arts.size)
                RenderSystem.getDevice().createBuffer({ "Cheonsang constellation art" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    .also { artBuffer = it }
            }
        }
    }

    private fun mul3(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { k -> val r = k / 3; val c = k % 3; a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c] }

    private fun inv3(m: DoubleArray): DoubleArray {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        return doubleArrayOf(e * i - f * h, c * h - b * i, b * f - c * e, f * g - d * i, a * i - c * g, c * d - a * f, d * h - e * g, b * g - a * h, a * e - b * d).map { it / det }.toDoubleArray()
    }

    private fun buildMilkyWay(): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.11")) {
        // 1° 칸 — 더 큰 칸이면 극 둘레 부채꼴에서 그림이 비틀려 소용돌이처럼 보임
        val cols = 360
        val rows = 180
        val bytes = ByteBufferBuilder.exactlySized(cols * rows * 4 * DefaultVertexFormat.POSITION_TEX.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX)
            fun u(ra: Double, firstHalf: Boolean) = (if (firstHalf) 0.5 - ra / 360.0 else 1.5 - ra / 360.0).toFloat()
            fun vtx(ra: Double, dec: Double, uu: Float) {
                val p = Vec3.fromSpherical(ra, dec) * R.toDouble()
                bb.addVertex(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()).setUv(uu, (0.5 - dec / 180.0).toFloat())
            }
            for (i in 0 until cols) for (j in 0 until rows) {
                val ra0 = 360.0 * i / cols
                val ra1 = 360.0 * (i + 1) / cols
                val d0 = -90.0 + 180.0 * j / rows
                val d1 = -90.0 + 180.0 * (j + 1) / rows
                val half = ra0 < 180.0
                // 극 꼭짓점은 한 점이라 u 를 칸 가운데 하나로 (두 꼭짓점 u 가 다르면 조각 안에서 그림이 비틀림)
                val um = u((ra0 + ra1) / 2, half)
                val ua0 = if (j == 0) um else u(ra0, half); val ua1 = if (j == 0) um else u(ra1, half)
                val ub0 = if (j == rows - 1) um else u(ra0, half); val ub1 = if (j == rows - 1) um else u(ra1, half)
                vtx(ra0, d0, ua0); vtx(ra1, d0, ua1); vtx(ra1, d1, ub1); vtx(ra0, d1, ub0)
            }
            bb.buildOrThrow().use { mesh ->
                milkyIndexCount = mesh.drawState().indexCount()
                RenderSystem.getDevice().createBuffer({ "Cheonsang milky way" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    .also { milkyBuffer = it }
            }
        }
    }

    /**
     * 심원천체 빛무리: 실제 크기·납작함·기울기의 타원, 종류별 색. 진하기 = 면 밝기(등급+넓이) 기준, 넓고 흐린 성운은 옅게, 납작함 하한 0.5.
     * 맨눈(5.5등)과 달리 망원경은 배율 단계마다 그 한계등급까지(최대 11등). 은하수 조각(마젤란·M24·C99)·암흑성운은 제외, 별 천체(M40·M73)는 작은 빛점.
     */
    private fun dsoKey() = if (TelescopeView.active) TelescopeView.level else -1
    private fun dsoLimit(key: Int) =
        if (key < 0) DSO_NAKED_MAG else minOf(DSO_NAKED_MAG + kr.inf.cheonsang.client.Optics.gain(TelescopeView.LEVELS[key]), TelescopeView.MAX_LIMIT.toDouble())

    private fun buildDso(key: Int): DsoMesh? {
        var count = 0
        val zoom = if (key < 0) 1.0 else TelescopeView.LEVELS[key].coerceAtMost(TelescopeView.OPTICAL_MAX)
        val buf = buildDsoBuffer(dsoLimit(key), zoom) { count = it } ?: return null
        return DsoMesh(buf, count).also { dsoMeshes[key] = it }
    }

    private fun buildDsoBuffer(limit: Double, zoom: Double, setCount: (Int) -> Unit): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.12")) {
        val sketch = sketchOn()
        val list = SkyData.dso.filter { it.type != "dark" && it.id !in MW_PATCHES && it.mag < limit + 0.5 && !(sketch && sketched(it)) }
        val bytes = ByteBufferBuilder.exactlySized(maxOf(1, list.size) * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            for (o in list) {
                val (strength, rgb) = when (o.type) {
                    "galaxy" -> 0.55f to Triple(255, 242, 217)
                    "globular" -> 0.6f to Triple(255, 240, 210)
                    "nebula" -> 0.6f to Triple(220, 190, 215)
                    "planetary" -> 0.6f to Triple(160, 255, 230)
                    else -> 0.12f to Triple(215, 228, 255) // 산개성단: 별이 따로 보이므로 은은하게 (둘레를 Gaia 실제 별로 채움)
                }
                val minorArcmin = maxOf(o.minor, o.major * 0.5)
                val surface = o.mag + 2.5 * log10(Math.PI / 4 * o.major * minorArcmin * 3600)
                // 면광원 대비 (표면 밝기 × 출사동 vs 겉보기 크기 문턱)
                val alpha = kr.inf.cheonsang.client.Visibility.diffuseIntensity(o.mag, surface, o.major, zoom).toFloat() * strength
                // 색 빠짐: 어두운 빛무리는 회색
                val gray = kr.inf.cheonsang.client.Visibility.colorLoss(kr.inf.cheonsang.client.Visibility.surfaceEff(surface, zoom))
                val lum = 0.3 * rgb.first + 0.59 * rgb.second + 0.11 * rgb.third
                val rgbC = Triple((rgb.first + (lum - rgb.first) * gray).toInt(), (rgb.second + (lum - rgb.second) * gray).toInt(), (rgb.third + (lum - rgb.third) * gray).toInt())
                val d = Vec3.fromSpherical(o.ra, o.dec)
                val ncp = Vec3(0.0, 0.0, 1.0)
                val north = (ncp - d * (d dot ncp)).let { if (it.length() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalized() }
                val east = north cross d
                val major = north * cosD(o.pa) + east * sinD(o.pa)
                val minor = north * -sinD(o.pa) + east * cosD(o.pa)
                // 빛무리 텍스처는 가운데 절반쯤만 밝음 → 실제 지름만큼 보이도록 반지름 = 지름
                val a = R * tan(Math.toRadians(o.major / 60.0))
                val b = R * tan(Math.toRadians(minorArcmin / 60.0))
                val c = d * R.toDouble()
                val col = (alpha * 255).toInt()
                fun v(x: Double, y: Double, uu: Float, vv: Float) {
                    val p = c + major * (x * a) + minor * (y * b)
                    bb.addVertex(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()).setUv(uu, vv).setColor(rgbC.first, rgbC.second, rgbC.third, col)
                }
                v(-1.0, -1.0, 0f, 1f); v(1.0, -1.0, 1f, 1f); v(1.0, 1.0, 1f, 0f); v(-1.0, 1.0, 0f, 0f)
            }
            bb.buildOrThrow().use { mesh ->
                setCount(if (list.isEmpty()) 0 else mesh.drawState().indexCount())
                Cheonsang.LOG.info("Cheonsang: {} deep-sky glows (to mag {})", list.size, "%.1f".format(limit))
                RenderSystem.getDevice().createBuffer({ "Cheonsang deep sky" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
            }
        }
    }

    private fun buildGalaxies(key: Int): DsoMesh? {
        var count = 0
        val zoom = if (key < 0) 1.0 else TelescopeView.LEVELS[key].coerceAtMost(TelescopeView.OPTICAL_MAX)
        val buf = buildGalaxyBuffer(dsoLimit(key), zoom) { count = it } ?: return null
        return DsoMesh(buf, count).also { galaxyMeshes[key] = it }
    }

    /**
     * 은하 관측 스케치: 은하마다 긴 축·짧은 축 방향 사각형 (반폭 = D25 긴 반지름 × 1.3), 정점색 = 모양·문턱 (GalaxySketch.colors).
     * 그림은 galaxy.fsh 가 픽셀마다 형태별 표면 밝기를 계산해 문턱보다 밝은 곳만 회색으로
     */
    private fun buildGalaxyBuffer(limit: Double, zoom: Double, setCount: (Int) -> Unit): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.12")) {
        val list = SkyData.dso.filter { sketched(it) && it.id !in MW_PATCHES && it.mag < limit + 0.5 }
        val bytes = ByteBufferBuilder.exactlySized(maxOf(1, list.size) * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            for (o in list) {
                val c4 = GalaxySketch.colors(o, zoom, 0.75)
                // UV.x = 칸 번호 × 2 + 사각형 안 위치 (칸 없으면 −2 + 위치 → 형태 그림)
                val tileOff = GalaxySketch.shape(o).tile.let { if (it >= 0) it * 2.0 else -2.0 }
                val q = c4[0] / 255.0
                val d = Vec3.fromSpherical(o.ra, o.dec)
                val ncp = Vec3(0.0, 0.0, 1.0)
                val north = (ncp - d * (d dot ncp)).let { if (it.length() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalized() }
                val east = north cross d
                val major = north * cosD(o.pa) + east * sinD(o.pa)
                val minor = north * -sinD(o.pa) + east * cosD(o.pa)
                val half = o.major / 2.0 * GalaxySketch.EXT / 60.0
                val a = R * tan(Math.toRadians(half))
                val b = R * tan(Math.toRadians(half * maxOf(q, 0.15)))
                val c = d * R.toDouble()
                fun v(x: Double, y: Double) {
                    val p = c + major * (x * a) + minor * (y * b)
                    bb.addVertex(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()).setUv((tileOff + (x + 1) / 2).toFloat(), ((y + 1) / 2).toFloat()).setColor(c4[0], c4[1], c4[2], c4[3])
                }
                v(-1.0, -1.0); v(1.0, -1.0); v(1.0, 1.0); v(-1.0, 1.0)
            }
            bb.buildOrThrow().use { mesh ->
                setCount(if (list.isEmpty()) 0 else mesh.drawState().indexCount())
                Cheonsang.LOG.info("Cheonsang: {} galaxy sketches (to mag {})", list.size, "%.1f".format(limit))
                RenderSystem.getDevice().createBuffer({ "Cheonsang galaxies" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
            }
        }
    }

    /**
     * 박명 빛 그물: 방위 ±120° · 고도 -8°~40° 구면 띠, +X 가 해 쪽. 정점색 = 세기(가운데·지평선이 진함) × 색(지평선 주황 → 위로 분홍).
     * 텍스처는 disc.png 가운데(흰색)만 찍어 정점색 그대로 씀.
     */
    private fun buildTwilight(): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.13")) {
        val cols = 48
        val rows = 24
        val bytes = ByteBufferBuilder.exactlySized(cols * rows * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            fun vtx(az: Double, alt: Double) {
                val w = kotlin.math.exp(-(az / 50.0).pow(2)) * kotlin.math.exp(-(alt / if (alt >= 0) 11.0 else 3.0).pow(2))
                val t = (alt / 15.0).coerceIn(0.0, 1.0)
                val r = 1.0; val g = 0.45 + 0.05 * t; val b = 0.2 + 0.4 * t
                val x = cosD(alt) * cosD(az); val y = sinD(alt); val z = cosD(alt) * sinD(az)
                bb.addVertex((x * R).toFloat(), (y * R).toFloat(), (z * R).toFloat()).setUv(0.5f, 0.5f)
                    .setColor((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), (w * 255).toInt().coerceIn(0, 255))
            }
            for (i in 0 until cols) for (j in 0 until rows) {
                val a0 = -120.0 + 240.0 * i / cols; val a1 = -120.0 + 240.0 * (i + 1) / cols
                val e0 = -8.0 + 48.0 * j / rows; val e1 = -8.0 + 48.0 * (j + 1) / rows
                vtx(a0, e0); vtx(a1, e0); vtx(a1, e1); vtx(a0, e1)
            }
            bb.buildOrThrow().use { mesh ->
                twilightIndexCount = mesh.drawState().indexCount()
                RenderSystem.getDevice().createBuffer({ "Cheonsang twilight" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    .also { twilightBuffer = it }
            }
        }
    }

    private val warned = HashSet<String>()
    private fun warnOnce(label: String) {
        if (warned.add(label)) Cheonsang.LOG.error("Cheonsang: pipeline '{}' unavailable, skipped (see shader errors above)", label)
    }

    /** 해·달용 단위 사각형 (-1~1, 텍스처 위쪽 = +y) */
    private fun buildQuad(): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.14")) {
        val bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX)
            bb.addVertex(-1f, -1f, 0f).setUv(0f, 1f)
            bb.addVertex(1f, -1f, 0f).setUv(1f, 1f)
            bb.addVertex(1f, 1f, 0f).setUv(1f, 0f)
            bb.addVertex(-1f, 1f, 0f).setUv(0f, 0f)
            bb.buildOrThrow().use { mesh ->
                RenderSystem.getDevice().createBuffer({ "Cheonsang quad" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    .also { quadBuffer = it }
            }
        }
    }

    /** 별 정점 버퍼를 한 번 만든다 (렌더 스레드). 9등까지 전부 — 밝은 순이라 앞에서 n 개만 그리면 됨 */
    private fun buildStars(): GpuBuffer? = guard(L.t("cheonsang.cheonsangsky.15")) {
        VramBudget.apply()  // 그래픽 메모리 예산 1/4 자동
        val cat = catalog ?: StarCatalog.load().also { catalog = it }
        val n = cat.size
        val bytes = ByteBufferBuilder.exactlySized(n * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            for (i in 0 until n) addStar(bb, cat, i)
            bb.buildOrThrow().use { mesh ->
                starIndexCount = mesh.drawState().indexCount()
                Cheonsang.LOG.info("Cheonsang: built vertex buffer for {} stars", n)
                RenderSystem.getDevice().createBuffer({ "Cheonsang stars" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                    .also { starBuffer = it }
            }
        }
    }

    // ── 깊은 별 (DeepStars): 시야 근처 칸만 작은 버퍼로, 시야가 옮겨지거나 한계가 바뀌면 다시 만듦 ──
    private var deepBuffer: GpuBuffer? = null
    private var deepIndexCount = 0
    private var deepCenter: Vec3? = null
    private var deepRadius = 0.0
    private var deepLimit = 0f
    private var deepVersion = -1
    private var deepBuiltAt = 0L

    /** 화면 가운데 방향 (J2000 단위벡터) */
    private fun viewCenterJ2000(): Vec3 {
        val w = viewCenter().normalized(); val m = skyMat3.m
        return Vec3(m[0] * w.x + m[3] * w.y + m[6] * w.z, m[1] * w.x + m[4] * w.y + m[7] * w.z, m[2] * w.x + m[5] * w.y + m[8] * w.z).normalized()
    }

    private fun drawDeep(pass: RenderPass, tr: com.mojang.renderpearl.api.buffers.GpuBufferSlice, limit: Float) {
        if (!TelescopeView.magnifying || limit <= 9.0f || !DeepStars.available()) return
        val mc = Minecraft.getInstance()
        val fovV = mc.options.fov().get().toDouble() / TelescopeView.zoom
        val aspect = mc.window.width.toDouble() / mc.window.height.coerceAtLeast(1)
        val half = Math.toDegrees(kotlin.math.atan(kotlin.math.tan(Math.toRadians(fovV / 2)) * kotlin.math.hypot(1.0, aspect)))
        val cj = viewCenterJ2000()
        val prev = deepCenter
        val now = System.nanoTime()
        // Gaia 팩 칸이 새로 읽혔으면 0.25초마다 다시 고름
        val arrived = DeepStars.version != deepVersion && now - deepBuiltAt > 250_000_000L
        val stale = prev == null || deepBuffer == null || Math.toDegrees(prev.angleTo(cj)) > deepRadius - half * 1.05 ||
            half > deepRadius * 0.8 || half < deepRadius * 0.3 || kotlin.math.abs(limit - deepLimit) > 0.15f || arrived
        if (stale) {
            val r = half * 1.6 + 0.05
            deepVersion = DeepStars.version; deepBuiltAt = now
            // 별이 상한을 넘으면 한계를 0.25등씩 낮춰 다시 (칸 순서로 잘려 한쪽만 비는 것 방지)
            var lim = limit + 0.5f
            var n = DeepStars.collect(cj, r, lim)
            var tries = 0
            while (DeepStars.truncated && tries++ < 12) { lim -= 0.25f; n = DeepStars.collect(cj, r, lim) }
            deepBuffer?.close(); deepBuffer = null; deepIndexCount = 0
            deepCenter = cj; deepRadius = r; deepLimit = limit
            if (n > 0) guard(L.t("cheonsang.cheonsangsky.16")) {
                val bytes = ByteBufferBuilder.exactlySized(n * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.vertexSize)
                bytes.use {
                    val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                    for (i in 0 until n) {
                        val x = DeepStars.outDir[i * 3] * R; val y = DeepStars.outDir[i * 3 + 1] * R; val z = DeepStars.outDir[i * 3 + 2] * R
                        val rgb = StarCatalog.color(DeepStars.outBv[i])
                        val cr = rgb shr 16 and 255; val cg = rgb shr 8 and 255; val cb = rgb and 255
                        val mg = DeepStars.outMag[i]
                        repeat(4) { k -> bb.addVertex(x, y, z).setUv(mg, k.toFloat()).setColor(cr, cg, cb, 255) }
                    }
                    bb.buildOrThrow().use { mesh ->
                        deepIndexCount = mesh.drawState().indexCount()
                        RenderSystem.getDevice().createBuffer({ "Cheonsang deep stars" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
                            .also { deepBuffer = it }
                    }
                }
            }.also { if (it == null) failed = false }  // 깊은 별 실패는 하늘 전체를 끄지 않음
        }
        val buf = deepBuffer ?: return
        if (deepIndexCount > 0) draw(pass, "deep_stars", STARS_PIPELINE, tr, TEX_STAR, buf, deepIndexCount)
    }

    private inline fun guard(what: String, block: () -> GpuBuffer): GpuBuffer? =
        try { block() } catch (e: Exception) {
            failed = true
            Cheonsang.LOG.error("Cheonsang: failed to create {} buffer — falling back to vanilla sky", what, e)
            null
        }

    /** 별 하나 = 정점 4개 모두 별 중심 (UV = 등급, 모서리 번호). 모서리·크기·진하기는 셰이더가 정함 */
    private fun addStar(bb: BufferBuilder, cat: StarCatalog, i: Int, magOverride: Float? = null) {
        val x = cat.dir[i * 3] * R; val y = cat.dir[i * 3 + 1] * R; val z = cat.dir[i * 3 + 2] * R
        // 변광성은 큰 버퍼에서 안 보이게 (따로 그림)
        val m = magOverride ?: if (i < cat.realCount && cat.hip[i] in VariableStars.byHip) 99f else cat.mag[i]
        val rgb = StarCatalog.color(cat.bv[i])
        val r = rgb shr 16 and 255; val g = rgb shr 8 and 255; val b = rgb and 255
        repeat(4) { k -> bb.addVertex(x, y, z).setUv(m, k.toFloat()).setColor(r, g, b, 255) }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun setMatrix(out: Matrix4f, m: Mat3) {
        val a = m.m
        // JOML set 은 열 우선: (열0: m00 m01 m02 m03), ...
        out.set(
            a[0].toFloat(), a[3].toFloat(), a[6].toFloat(), 0f,
            a[1].toFloat(), a[4].toFloat(), a[7].toFloat(), 0f,
            a[2].toFloat(), a[5].toFloat(), a[8].toFloat(), 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
