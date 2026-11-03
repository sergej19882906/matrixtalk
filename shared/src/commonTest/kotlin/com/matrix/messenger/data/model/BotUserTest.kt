package com.matrix.messenger.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotUserTest {

    @Test
    fun bridgeBotsDetected() {
        assertTrue(isBotUser("@telegram_bot:example.org", null))
        assertTrue(isBotUser("@_whatsapp_bot:example.org", "WhatsApp bridge bot"))
        assertTrue(isBotUser("@_discord_bot:example.org", "Discord bridge"))
        assertTrue(isBotUser("@mautrix_bot:server", "Мост Telegram"))
    }

    @Test
    fun displayNameCounts() {
        assertTrue(isBotUser("@alice:example.org", "My Bot Helper"))
        assertTrue(isBotUser("@alice:example.org", "BOT"))
    }

    @Test
    fun regularUsersNotBots() {
        assertFalse(isBotUser("@alice:example.org", "Алиса"))
        assertFalse(isBotUser("@bob_matrix:example.org", "Bob"))
        assertFalse(isBotUser("@max123:example.org", null))
        assertFalse(isBotUser("@bob_matrix:example.org", null))
    }

    @Test
    fun caseInsensitive() {
        assertTrue(isBotUser("@Telegram_BOT:example.org", null))
        // BOTANIST содержит 'bot' — эвристика Element тоже сработает, это осознанный компромисс
        assertTrue(isBotUser("@BOTANIST:example.org", null))
    }
}
