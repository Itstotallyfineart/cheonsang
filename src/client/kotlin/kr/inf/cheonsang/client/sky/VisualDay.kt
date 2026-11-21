package kr.inf.cheonsang.client.sky

import net.minecraft.client.Minecraft
import net.minecraft.world.attribute.EnvironmentAttribute
import net.minecraft.world.attribute.EnvironmentAttributes

/**
 * 1.10.1-dev4 화면 낮밤 = 천상 해 (11-14 13:57 지율님 "숫자적인 부분(몹 생성·잠자기)은 바닐라 그대로, 나머지 비주얼은 다 고쳐서").
 * 바닐라 26.3 은 하늘색·안개색·구름색·땅에 비치는 하늘빛·별 밝기를 "day" 타임라인(24000틱)으로 정함.
 * 그 화면용 트랙만 "천상 해 고도와 같은 바닐라 해 고도의 시각"으로 읽힘 → 고위도 백야·극야, 계절별 낮 길이가 화면에 그대로.
 * gameplay/sky_light_level(몹 생성)·잠자기·몹 타기 등은 바닐라 시계 그대로.
 */
object VisualDay {
    @JvmStatic
    fun isVisual(a: EnvironmentAttribute<*>): Boolean =
        a === EnvironmentAttributes.SKY_COLOR || a === EnvironmentAttributes.FOG_COLOR || a === EnvironmentAttributes.CLOUD_COLOR ||
            a === EnvironmentAttributes.SKY_LIGHT_COLOR || a === EnvironmentAttributes.SKY_LIGHT_FACTOR ||
            a === EnvironmentAttributes.STAR_BRIGHTNESS || a === EnvironmentAttributes.SUNRISE_SUNSET_COLOR

    /** 바닐라 시계 틱 → 화면용 틱 (같은 날 안에서 하루 위치만 바꿈). 천상 안 켜졌거나 렌더 스레드가 아니면(내장 서버) 그대로 */
    @JvmStatic
    fun ticks(real: Long): Long {
        if (!Minecraft.getInstance().isSameThread || !kr.inf.cheonsang.client.TelescopeFilter.visualDay) return real
        val v = CheonsangSky.visualDayTick()
        if (v < 0) return real
        return real - Math.floorMod(real, 24000L) + v
    }
}
