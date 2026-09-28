package com.dalim.datalimit.data

import com.dalim.datalimit.core.Period
import com.dalim.datalimit.core.Schedule
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.UsagePrefsPatch
import com.dalim.datalimit.core.WindowStyle

/**
 * Hand-rolled JSON serializer / parser for the config export/import feature.
 *
 * Format:
 * {
 *   "format": "dalim-config", "version": 1,
 *   "limitMb": 5120, "windowStyle": "monthly", "periodType": "fixed",
 *   "monitoringEnabled": true, "gateEnabled": true, "notificationsEnabled": true,
 *   "firewall": { "enabled": false, "blocked": ["pkg"], "budgetsMb": { "pkg": 256 } },
 *   "battery": { "floor": 10, "alerts": true },
 *   "schedule": { "style": "off", "start": 0, "end": 1439 },
 *   "language": "en"
 * }
 *
 * Importer rules (see PLAN-NEXT M2):
 * - Unknown / future fields are ignored.
 * - Missing fields fall back to defaults.
 * - Invalid JSON or a wrong "format" => Result.failure; nothing is applied.
 */
object ConfigExchange {

    const val FORMAT = "dalim-config"
    const val VERSION = 1

    // ---- Export ----

    fun toJson(prefs: UsagePrefs): String =
        toJson(
            UsagePrefsPatch(
                limitMb = prefs.limitMb,
                period = prefs.period,
                windowStyle = prefs.windowStyle,
                gateEnabled = prefs.gateEnabled,
                notificationsEnabled = prefs.notificationsEnabled,
                language = prefs.language,
                firewallEnabled = prefs.firewallEnabled,
                blockedApps = prefs.blockedApps,
                appBudgetsMb = prefs.budgetedPackages(),
                batteryFloor = prefs.batteryBudgetFloor,
                batteryAlertsEnabled = prefs.batteryAlertsEnabled,
                scheduleStyle = prefs.scheduleWindowStyle,
                scheduleStartMin = prefs.scheduleStartMin,
                scheduleEndMin = prefs.scheduleEndMin
            ),
            monitoring = prefs.monitoringEnabled
        )

    fun toJson(patch: UsagePrefsPatch, monitoring: Boolean = false): String {
        val sb = StringBuilder("{")
        field(sb, "format", FORMAT, quote = true, isFirst = true)
        field(sb, "version", VERSION.toLong(), quote = false)
        field(sb, "limitMb", patch.limitMb, quote = false)
        field(sb, "windowStyle", periodCode(patch.period), quote = true)
        field(sb, "periodType", styleCode(patch.windowStyle), quote = true)
        field(sb, "monitoringEnabled", monitoring, quote = false)
        field(sb, "gateEnabled", patch.gateEnabled, quote = false)
        field(sb, "notificationsEnabled", patch.notificationsEnabled, quote = false)
        field(sb, "language", patch.language, quote = true)

        sb.append(",\"firewall\":{")
        field(sb, "enabled", patch.firewallEnabled, quote = false, isFirst = true)
        buildStringArrayField(sb, "blocked", patch.blockedApps.toList())
        buildNumberMapField(sb, "budgetsMb", patch.appBudgetsMb)
        sb.append("}")

        sb.append(",\"battery\":{")
        field(sb, "floor", patch.batteryFloor.toLong(), quote = false, isFirst = true)
        field(sb, "alerts", patch.batteryAlertsEnabled, quote = false)
        sb.append("}")

        sb.append(",\"schedule\":{")
        field(sb, "style", patch.scheduleStyle.code, quote = true, isFirst = true)
        field(sb, "start", patch.scheduleStartMin.toLong(), quote = false)
        field(sb, "end", patch.scheduleEndMin.toLong(), quote = false)
        sb.append("}")

        sb.append("}")
        return sb.toString()
    }

    // ---- Import ----

    /**
     * Parse and validate a config export. Returns a patch (with defaults for
     * any missing field) or a failure that must not be applied to prefs.
     */
    fun fromJson(raw: String): Result<UsagePrefsPatch> = runCatching {
        val root = JsonParse.parse(raw) as? Json.Object
            ?: throw IllegalArgumentException("root is not an object")
        val format = (root.value("format") as? Json.String)?.value
        if (format == null || format != FORMAT) {
            throw IllegalArgumentException("unknown format")
        }
        val version = (root.value("version") as? Json.Number)?.toLong() ?: 0L
        if (version != VERSION.toLong()) {
            throw IllegalArgumentException("unsupported version $version")
        }

        val firewall = root.value("firewall") as? Json.Object ?: Json.Object(emptyMap())
        val battery = root.value("battery") as? Json.Object ?: Json.Object(emptyMap())
        val schedule = root.value("schedule") as? Json.Object ?: Json.Object(emptyMap())

        UsagePrefsPatch(
            limitMb = (root.value("limitMb") as? Json.Number)?.toLong()?.coerceAtLeast(0L) ?: 200L,
            period = periodFromCode(root.string("windowStyle")) ?: Period.DAILY,
            windowStyle = styleFromCode(root.string("periodType")) ?: WindowStyle.FIXED,
            gateEnabled = (root.value("gateEnabled") as? Json.Bool)?.value ?: true,
            notificationsEnabled = (root.value("notificationsEnabled") as? Json.Bool)?.value ?: true,
            language = root.string("language") ?: "en",
            firewallEnabled = (firewall.value("enabled") as? Json.Bool)?.value ?: false,
            blockedApps = (firewall.value("blocked") as? Json.Array)
                ?.items.orEmpty().mapNotNull { (it as? Json.String)?.value }.toSet(),
            appBudgetsMb = (firewall.value("budgetsMb") as? Json.Object)
                ?.value.orEmpty().entries
                .mapNotNull { (pkg, v) -> (v as? Json.Number)?.toLong()?.takeIf { it > 0L }?.let { pkg to it } }
                .toMap(),
            batteryFloor = (battery.value("floor") as? Json.Number)?.toLong()?.toInt()?.coerceIn(0, 100) ?: 20,
            batteryAlertsEnabled = (battery.value("alerts") as? Json.Bool)?.value ?: true,
            scheduleStyle = Schedule.WindowStyle.from(schedule.string("style")),
            scheduleStartMin = (schedule.value("start") as? Json.Number)?.toLong()?.toInt() ?: 0,
            scheduleEndMin = (schedule.value("end") as? Json.Number)?.toLong()?.toInt() ?: 1439
        )
    }

    private fun periodCode(p: Period): String = p.name.lowercase()
    private fun styleCode(s: WindowStyle): String = s.name.lowercase()

    private fun periodFromCode(code: String?): Period? =
        code?.let { runCatching { Period.valueOf(it.uppercase()) }.getOrNull() }

    private fun styleFromCode(code: String?): WindowStyle? =
        code?.let { runCatching { WindowStyle.valueOf(it.uppercase()) }.getOrNull() }

    // ---- JSON writer primitives ----

    private fun field(sb: StringBuilder, name: String, value: Any?, quote: Boolean, isFirst: Boolean = false) {
        if (!isFirst) sb.append(',')
        sb.append('"').append(name).append("\":")
        if (quote) sb.append('"').append(escape(value.toString())).append('"')
        else sb.append(value.toString())
    }

    private fun buildStringArrayField(sb: StringBuilder, name: String, items: List<String>) {
        if (items.isEmpty()) return
        sb.append(",\"").append(name).append("\":[")
        for ((i, item) in items.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append('"').append(escape(item)).append('"')
        }
        sb.append(']')
    }

    private fun buildNumberMapField(sb: StringBuilder, name: String, map: Map<String, Long>) {
        if (map.isEmpty()) return
        sb.append(",\"").append(name).append("\":{")
        for ((i, entry) in map.entries.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append('"').append(escape(entry.key)).append("\":").append(entry.value)
        }
        sb.append('}')
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append(String.format("\\u%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    // ---- Minimal JSON AST + parser (no dependencies) ----

    private sealed class Json {
        object Null : Json()
        data class Bool(val value: Boolean) : Json()
        data class Number(val raw: kotlin.String) : Json() {
            fun toLong(): Long = runCatching { raw.toLong() }.getOrElse { raw.toDouble().toLong() }
        }
        data class String(val value: kotlin.String) : Json()
        data class Array(val items: List<Json>) : Json()
        data class Object(val value: Map<kotlin.String, Json>) : Json() {
            fun value(key: kotlin.String): Json? = value[key]
            fun string(key: kotlin.String): kotlin.String? =
                (value[key] as? Json.String)?.value
        }
    }

    private object JsonParse {
        fun parse(text: String): Json {
            val p = P(text)
            val v = p.value()
            p.ws()
            if (!p.atEnd()) throw IllegalArgumentException("trailing data")
            return v
        }
    }

    private class P(private val t: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= t.length

        fun ws() {
            while (i < t.length && (t[i] == ' ' || t[i] == '\n' || t[i] == '\r' || t[i] == '\t')) i++
        }

        fun value(): Json {
            ws()
            if (i >= t.length) throw IllegalArgumentException("unexpected end")
            return when (t[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> Json.String(str())
                't' -> { expect("true"); Json.Bool(true) }
                'f' -> { expect("false"); Json.Bool(false) }
                'n' -> { expect("null"); Json.Null }
                else -> number()
            }
        }

        private fun obj(): Json {
            i++ // consume '{'
            val map = HashMap<String, Json>()
            ws()
            if (i < t.length && t[i] == '}') { i++; return Json.Object(map) }
            while (true) {
                ws()
                if (i >= t.length || t[i] != '"') throw IllegalArgumentException("expected key")
                val key = str()
                ws()
                if (i >= t.length || t[i] != ':') throw IllegalArgumentException("expected ':'")
                i++
                map[key] = value()
                ws()
                if (i >= t.length) throw IllegalArgumentException("unterminated object")
                when (t[i]) {
                    ',' -> i++
                    '}' -> { i++; return Json.Object(map) }
                    else -> throw IllegalArgumentException("expected ',' or '}'")
                }
            }
        }

        private fun arr(): Json {
            i++ // consume '['
            val items = ArrayList<Json>()
            ws()
            if (i < t.length && t[i] == ']') { i++; return Json.Array(items) }
            while (true) {
                items.add(value())
                ws()
                if (i >= t.length) throw IllegalArgumentException("unterminated array")
                when (t[i]) {
                    ',' -> i++
                    ']' -> { i++; return Json.Array(items) }
                    else -> throw IllegalArgumentException("expected ',' or ']'")
                }
            }
        }

        private fun str(): String {
            i++ // consume opening quote
            val sb = StringBuilder()
            while (i < t.length) {
                val c = t[i]
                when {
                    c == '"' -> { i++; return sb.toString() }
                    c == '\\' -> {
                        i++
                        if (i >= t.length) throw IllegalArgumentException("bad escape")
                        when (val e = t[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 >= t.length) throw IllegalArgumentException("bad unicode")
                                val hex = t.substring(i + 1, i + 5)
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                        i++
                    }
                    c.code < 0x20 -> throw IllegalArgumentException("control char in string")
                    else -> { sb.append(c); i++ }
                }
            }
            throw IllegalArgumentException("unterminated string")
        }

        private fun expect(word: String) {
            if (!t.startsWith(word, i)) throw IllegalArgumentException("expected $word")
            i += word.length
        }

        private fun number(): Json {
            val start = i
            if (i < t.length && t[i] == '-') i++
            while (i < t.length && (t[i].isDigit() || t[i] == '.' || t[i] == 'e' || t[i] == 'E' || t[i] == '+' || t[i] == '-')) i++
            if (i == start) throw IllegalArgumentException("expected value")
            return Json.Number(t.substring(start, i))
        }
    }
}