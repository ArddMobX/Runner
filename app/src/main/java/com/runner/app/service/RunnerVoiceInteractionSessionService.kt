package com.runner.app.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Служба управления сессиями системного ассистента Runner.
 */
class RunnerVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return RunnerVoiceInteractionSession(this)
    }
}
