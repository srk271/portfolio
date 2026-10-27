package dev.bidu.app.ui

import dev.bidu.app.assistant.AssistantUiState
import dev.bidu.app.settings.BiduSettings
import dev.bidu.app.voice.ModelState
import dev.bidu.app.voice.TtsVoiceInfo
import dev.bidu.app.voice.VoiceMode
import dev.bidu.app.voice.VoiceState
import dev.bidu.core.agent.AgentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiLogicTest {
    private val phrases = BiduSettings.DEFAULT_WAKE_PHRASES

    @Test
    fun `listening shows display names of wake phrases`() {
        val ui = AssistantUiState(
            agentState = AgentState.LISTENING_WAKE,
            voice = VoiceState(serviceRunning = true, mode = VoiceMode.WAKE_LISTENING, wakeWordAvailable = true),
        )
        assertEquals(HomeStatus.Listening(listOf("Hey Bidu", "Bhai")), homeStatus(ui, phrases))
    }

    @Test
    fun `service off means push-to-talk only`() {
        assertEquals(HomeStatus.ListeningOff, homeStatus(AssistantUiState(), phrases))
    }

    @Test
    fun `running service without model means push-to-talk only`() {
        val ui = AssistantUiState(voice = VoiceState(serviceRunning = true, mode = VoiceMode.PAUSED, wakeWordAvailable = false))
        assertEquals(HomeStatus.WakeWordUnavailable, homeStatus(ui, phrases))
    }

    @Test
    fun `engine states and capture are working`() {
        val executing = AssistantUiState(agentState = AgentState.EXECUTING, busy = true)
        assertEquals(HomeStatus.Working(AgentState.EXECUTING), homeStatus(executing, phrases))
        val capturing = AssistantUiState(voice = VoiceState(serviceRunning = true, mode = VoiceMode.CAPTURING, wakeWordAvailable = true))
        assertEquals(HomeStatus.Working(AgentState.CAPTURING_COMMAND), homeStatus(capturing, phrases))
        val busyOnly = AssistantUiState(agentState = AgentState.LISTENING_WAKE, busy = true)
        assertEquals(HomeStatus.Working(null), homeStatus(busyOnly, phrases))
    }

    @Test
    fun `error state carries its reason`() {
        val ui = AssistantUiState(agentState = AgentState.ERROR, lastError = "Recognizer crashed")
        assertEquals(HomeStatus.Error("Recognizer crashed"), homeStatus(ui, phrases))
        val voiceError = AssistantUiState(voice = VoiceState(serviceRunning = true, error = "Mic busy"))
        assertEquals(HomeStatus.Error("Mic busy"), homeStatus(voiceError, phrases))
    }

    @Test
    fun `wake phrase parsing normalizes and defaults variants`() {
        val parsed = parseWakePhrase("  Hey,  BIDU! ", "hey buddy, Hey Be Do;\n hey buddy ,, ")!!
        assertEquals("hey bidu", parsed.phrase)
        assertEquals(listOf("hey buddy", "hey be do"), parsed.variants)
        assertEquals(listOf("bhai"), parseWakePhrase("Bhai", " ")!!.variants)
        assertNull(parseWakePhrase(" !! ", "bhai"))
    }

    @Test
    fun `display phrase capitalizes each word`() {
        assertEquals("Hey Bidu", displayPhrase("hey bidu"))
        assertEquals("Bhai", displayPhrase("bhai"))
    }

    @Test
    fun `memory keys are trimmed and lowercased`() {
        assertEquals("contact_alias.mummy ji", normalizeMemoryKey("  Contact_Alias.Mummy   Ji "))
    }

    @Test
    fun `voice choices prefer indian voices of the selected engine`() {
        val voices = listOf(
            TtsVoiceInfo("en-us-x-1", "en-US", "engine.a"),
            TtsVoiceInfo("hi-in-x-2", "hi-IN", "engine.a"),
            TtsVoiceInfo("en-in-x-3", "en_IN", "engine.a"),
            TtsVoiceInfo("hi-in-x-4", "hi-IN", "engine.b"),
        )
        assertEquals(listOf("en-in-x-3", "hi-in-x-2"), voiceChoices(voices, "engine.a").map { it.name })
        assertEquals(listOf("hi-in-x-4"), voiceChoices(voices, "engine.b").map { it.name })
        val onlyUs = listOf(TtsVoiceInfo("en-us-x-1", "en-US", "engine.c"))
        assertEquals(onlyUs, voiceChoices(onlyUs, "engine.c"))
    }

    @Test
    fun `download fraction needs a known total`() {
        assertEquals(0.5f, downloadFraction(ModelState.Downloading(50, 100))!!, 0.0001f)
        assertNull(downloadFraction(ModelState.Downloading(50, null)))
        assertNull(downloadFraction(ModelState.Downloading(50, 0)))
        assertEquals(1f, downloadFraction(ModelState.Downloading(150, 100))!!, 0.0001f)
    }
}
