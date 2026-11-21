package kr.inf.cheonsang.client

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kr.inf.cheonsang.L
import net.minecraft.network.chat.Component
import java.util.Locale
import java.util.concurrent.CompletableFuture

/**
 * 2.0.3-dev1 `/cwhen <천체> <밝은 비율%>` 의 값: 숫자 + 끝에 + (차오를 때) 또는 - (기울 때), 없으면 둘 다.
 * 범위 밖이면 입력하는 도중 입력창 아래에 "값은 A~B 이어야 합니다" (Brigadier 해석 오류).
 */
class PhaseArg(private val min: Double, private val max: Double = 100.0) : ArgumentType<PhaseArg.Value> {
    /** pct 0~100, dir +1 차오름 · -1 기욺 · 0 둘 다 */
    data class Value(val pct: Double, val dir: Int)

    private fun fmt(v: Double) = if (v == Math.floor(v)) "%.0f".format(Locale.ROOT, v) else "%.1f".format(Locale.ROOT, v)

    override fun parse(reader: StringReader): Value {
        val start = reader.cursor
        while (reader.canRead() && (reader.peek().isDigit() || reader.peek() == '.')) reader.skip()
        val num = reader.string.substring(start, reader.cursor).toDoubleOrNull()
        if (num == null) {
            reader.cursor = start
            throw SimpleCommandExceptionType(Component.literal(L.t("cheonsang.cwhen.phase_expect"))).createWithContext(reader)
        }
        var dir = 0
        if (reader.canRead() && (reader.peek() == '+' || reader.peek() == '-')) dir = if (reader.read() == '+') 1 else -1
        if (num < min - 1e-9 || num > max + 1e-9) {
            reader.cursor = start
            throw SimpleCommandExceptionType(Component.literal(L.t("cheonsang.cwhen.phase_range", fmt(min), fmt(max)))).createWithContext(reader)
        }
        return Value(num, dir)
    }

    override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val opts = if (min <= 0.0) listOf("0", "25", "50", "75", "100", "50+", "50-") else listOf(fmt(min), "100")
        opts.filter { it.startsWith(builder.remaining) }.forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    override fun getExamples(): Collection<String> = listOf("50", "50+", "100")

    companion object {
        fun get(c: CommandContext<*>, name: String): Value = c.getArgument(name, Value::class.java)
    }
}
