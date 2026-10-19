package com.runner.app.service

import android.service.voice.VoiceInteractionService

/**
 * Системная служба голосового взаимодействия Android (VoiceInteractionService).
 * Позволяет назначить Runner «Цифровым помощником по умолчанию» в настройках Android
 * (Settings -> Default apps -> Digital Assistant).
 */
class RunnerVoiceInteractionService : VoiceInteractionService()
