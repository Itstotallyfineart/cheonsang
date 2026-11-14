package kr.inf.cheonsang

import kr.inf.cheonsang.astro.Frames
import kr.inf.cheonsang.astro.Moon
import kr.inf.cheonsang.astro.Sun
import kr.inf.cheonsang.astro.asinD
import kr.inf.cheonsang.astro.norm360
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.attribute.EnvironmentAttribute
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.level.Level
import net.minecraft.world.level.MoonPhase
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * 바닐라 맞추기 (서버, 오버월드).
 * - 달 위상 (`moon` 기본 켬): 바닐라 달 위상·지표 슬라임 확률을 천상 달에 맞춤. 슬라임 확률 = 0.5 × 밝은 비율
 * - 밤 (`night` 기본 끔): 하늘 빛 밝기(몹 생성·바닐라 밤 판정)를 실제 해 고도로 맞춤 — 해 고도 0° 이상 15, −6°(시민박명 끝) 이하 4, 사이는 직선.
 *   해가 [POLAR_DAYS] 일째 −6° 아래나 0° 위로 안 움직이면(고위도 백야·극야) 바닐라 규칙으로 돌아감.
 * 서버 설정 server.json: vanillaMoon · vanillaNight
 */
object VanillaSync {
    var moonOn = true
    var nightOn = false
    const val NIGHT_ALT = -6.0
    const val POLAR_DAYS = 3.0

    private class State(val level: ServerLevel) {
        var tick = Long.MIN_VALUE
        var phase: MoonPhase = MoonPhase.FULL_MOON
        var slime = 0.5f
        var skyLight: Float? = null
        var lastBelowJd = Double.NaN
        var lastAboveJd = Double.NaN
        var lastJd = Double.NaN
    }

    private val bound = WeakHashMap<Any, State>()

    fun register() {
        ServerTickEvents.START_LEVEL_TICK.register { level ->
            if (level.dimension() != Level.OVERWORLD) return@register
            val sys = level.environmentAttributes()
            if (sys !in bound) bound[sys] = State(level)
        }
    }

    /** 믹스인에서 부름 — 바꿀 값이면 그 값, 아니면 null */
    @JvmStatic
    fun override(system: Any, attribute: EnvironmentAttribute<*>): Any? {
        if (!moonOn && !nightOn) return null
        val st = bound[system] ?: return null
        return try {
            update(st)
            when {
                moonOn && attribute === EnvironmentAttributes.MOON_PHASE -> st.phase
                moonOn && attribute === EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE -> st.slime
                nightOn && attribute === EnvironmentAttributes.SKY_LIGHT_LEVEL -> st.skyLight
                else -> null
            }
        } catch (e: Exception) {
            Cheonsang.LOG.error("Cheonsang: vanilla sync calculation failed — disabled", e)
            moonOn = false; nightOn = false
            null
        }
    }

    /** 한 틱에 한 번만 계산 */
    private fun update(st: State) {
        val level = st.level
        val tick = level.defaultClockTime
        if (tick == st.tick) return
        st.tick = tick
        val s = SkyServer.get(level)
        val clock = s.clock
        val jd = clock.jd(tick.toDouble())
        val sun = Sun.position(jd)
        val moon = Moon.position(jd)
        val ph = Moon.phase(moon, sun)
        // 바닐라 순서: 0 보름 → 1 하현 전 … 4 삭 … 6 상현 → 7 상현 뒤
        val idx = (((norm360(ph.elongation + 180.0)) / 45.0).roundToInt()) % 8
        st.phase = MoonPhase.entries[idx]
        st.slime = (0.5 * ph.illuminated).toFloat()
        if (!nightOn) { st.skyLight = null; return }
        val horizon = Frames.horizon(clock.lst(tick.toDouble()), s.lat)
        val alt = asinD((horizon * sun.dir).normalized().y)
        // 시간 점프(하루 넘게)면 고위도 기록 새로 시작
        if (st.lastJd.isNaN() || kotlin.math.abs(jd - st.lastJd) > 1.0) { st.lastBelowJd = jd; st.lastAboveJd = jd }
        st.lastJd = jd
        if (alt < NIGHT_ALT) st.lastBelowJd = jd
        if (alt > 0.0) st.lastAboveJd = jd
        val polar = jd - st.lastBelowJd > POLAR_DAYS || jd - st.lastAboveJd > POLAR_DAYS
        st.skyLight = if (polar) null else (4.0 + 11.0 * ((alt - NIGHT_ALT) / -NIGHT_ALT).coerceIn(0.0, 1.0)).toFloat()
    }
}
