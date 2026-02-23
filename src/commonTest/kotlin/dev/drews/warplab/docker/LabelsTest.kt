package dev.drews.warplab.docker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LabelsTest {
    @Test
    fun `parseLabels returns null when no warplab labels`() {
        val labels = mapOf("traefik.enable" to "true")
        assertNull(parseLabels(labels))
    }

    @Test
    fun `parseLabels returns null for empty map`() {
        assertNull(parseLabels(emptyMap()))
    }

    @Test
    fun `parseLabels extracts hostname with default non-direct`() {
        val labels = mapOf(Labels.WARP_DNS to "my-service.your.homelab")
        val info = parseLabels(labels)
        assertNotNull(info)
        assertEquals("my-service.your.homelab", info.hostname)
        assertFalse(info.direct)
    }

    @Test
    fun `parseLabels extracts direct flag when true`() {
        val labels = mapOf(
            Labels.WARP_DNS to "ijc.your.homelab",
            Labels.WARP_DIRECT to "true",
        )
        val info = parseLabels(labels)
        assertNotNull(info)
        assertEquals("ijc.your.homelab", info.hostname)
        assertTrue(info.direct)
    }

    @Test
    fun `parseLabels handles case-insensitive direct flag`() {
        val labels = mapOf(
            Labels.WARP_DNS to "test.your.homelab",
            Labels.WARP_DIRECT to "True",
        )
        val info = parseLabels(labels)
        assertNotNull(info)
        assertTrue(info.direct)
    }

    @Test
    fun `parseLabels treats non-true direct as false`() {
        val labels = mapOf(
            Labels.WARP_DNS to "test.your.homelab",
            Labels.WARP_DIRECT to "false",
        )
        val info = parseLabels(labels)
        assertNotNull(info)
        assertFalse(info.direct)
    }

    @Test
    fun `parseLabels ignores extra labels`() {
        val labels = mapOf(
            Labels.WARP_DNS to "svc.your.homelab",
            "traefik.http.routers.svc.rule" to "Host(`svc.your.homelab`)",
            Labels.COMPOSE_SERVICE to "my-svc",
        )
        val info = parseLabels(labels)
        assertNotNull(info)
        assertEquals("svc.your.homelab", info.hostname)
        assertFalse(info.direct)
    }
}
