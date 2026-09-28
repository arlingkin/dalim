package com.dalim.datalimit.data

import com.dalim.datalimit.core.Period
import com.dalim.datalimit.core.Schedule
import com.dalim.datalimit.core.UsagePrefsPatch
import com.dalim.datalimit.core.WindowStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigExchangeTest {

    @Test
    fun `round trip preserves every field`() {
        val patch = UsagePrefsPatch(
            limitMb = 5120,
            period = Period.MONTHLY,
            windowStyle = WindowStyle.FIXED,
            gateEnabled = true,
            notificationsEnabled = false,
            language = "in",
            firewallEnabled = true,
            blockedApps = setOf("com.a", "com.b"),
            appBudgetsMb = mapOf("com.a" to 256L, "com.b" to 1024L),
            batteryFloor = 10,
            batteryAlertsEnabled = true,
            scheduleStyle = Schedule.WindowStyle.CUSTOM,
            scheduleStartMin = 22 * 60,
            scheduleEndMin = 6 * 60
        )
        val json = ConfigExchange.toJson(patch, monitoring = true)
        assertTrue(json.contains("\"format\":\"dalim-config\""))
        assertTrue(json.contains("\"version\":1"))
        assertTrue(json.contains("\"budgetsMb\":{\"com.a\":256,\"com.b\":1024}"))

        val parsed = ConfigExchange.fromJson(json).getOrThrow()
        assertEquals(patch, parsed)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val parsed = ConfigExchange.fromJson(
            """{"format":"dalim-config","version":1}"""
        ).getOrThrow()
        assertEquals(UsagePrefsPatch(), parsed)
    }

    @Test
    fun `unknown fields are ignored`() {
        val parsed = ConfigExchange.fromJson(
            """{"format":"dalim-config","version":1,"futureThing":["x"],"limitMb":42}"""
        ).getOrThrow()
        assertEquals(42L, parsed.limitMb)
        assertEquals(UsagePrefsPatch().period, parsed.period)
    }

    @Test
    fun `invalid json fails`() {
        assertTrue(ConfigExchange.fromJson("{not json").isFailure)
        assertTrue(ConfigExchange.fromJson("[]").isFailure)
        assertTrue(ConfigExchange.fromJson("").isFailure)
        assertTrue(ConfigExchange.fromJson("42").isFailure)
    }

    @Test
    fun `wrong format or version fails`() {
        assertTrue(ConfigExchange.fromJson("""{"format":"other","version":1}""").isFailure)
        assertTrue(ConfigExchange.fromJson("""{"format":"dalim-config","version":99}""").isFailure)
    }

    @Test
    fun `minimal example from plan parses`() {
        val raw = """
            {
              "format": "dalim-config", "version": 1,
              "limitMb": 5120, "windowStyle": "monthly", "periodType": "fixed",
              "monitoringEnabled": true,
              "firewall": { "enabled": false, "budgetsMb": { "pkg": 256 } },
              "battery": { "floor": 10, "alerts": true },
              "schedule": { "style": "off", "start": 0, "end": 1439 },
              "language": "en"
            }
        """.trimIndent()
        val parsed = ConfigExchange.fromJson(raw).getOrThrow()
        assertEquals(5120L, parsed.limitMb)
        assertEquals(Period.MONTHLY, parsed.period)
        assertEquals(WindowStyle.FIXED, parsed.windowStyle)
        assertEquals(mapOf("pkg" to 256L), parsed.appBudgetsMb)
        assertEquals(false, parsed.firewallEnabled)
        assertEquals(10, parsed.batteryFloor)
        assertEquals(true, parsed.batteryAlertsEnabled)
        assertEquals(Schedule.WindowStyle.OFF, parsed.scheduleStyle)
        assertEquals(1439, parsed.scheduleEndMin)
        assertEquals("en", parsed.language)
    }

    @Test
    fun `escaped strings survive round trip`() {
        val patch = UsagePrefsPatch(blockedApps = setOf("pkg \"quoted\"", "line\nbreak"))
        val parsed = ConfigExchange.fromJson(ConfigExchange.toJson(patch)).getOrThrow()
        assertEquals(patch.blockedApps, parsed.blockedApps)
    }

    @Test
    fun `negative budget entries are dropped on import`() {
        val raw = """{"format":"dalim-config","version":1,"firewall":{"budgetsMb":{"a":-5,"b":0,"c":7}}}"""
        val parsed = ConfigExchange.fromJson(raw).getOrThrow()
        assertEquals(mapOf("c" to 7L), parsed.appBudgetsMb)
    }

    @Test
    fun `overnight schedule range round trips`() {
        val patch = UsagePrefsPatch(
            scheduleStyle = Schedule.WindowStyle.CUSTOM,
            scheduleStartMin = 22 * 60,
            scheduleEndMin = 6 * 60
        )
        val parsed = ConfigExchange.fromJson(ConfigExchange.toJson(patch)).getOrThrow()
        assertEquals(22 * 60, parsed.scheduleStartMin)
        assertEquals(6 * 60, parsed.scheduleEndMin)
    }

    @Test
    fun `monitoring flag not applied by patch`() {
        val patch = UsagePrefsPatch(limitMb = 100)
        assertFalse(patch.firewallEnabled)
        assertTrue(patch.gateEnabled)
    }
}