package com.runner.app

import android.app.Application
import com.runner.app.util.CrashReportManager

/**
 * Ставит обработчик падений до создания Activity и ViewModel.
 * Без этого краш в MainViewModel до onCreate оставался без отчёта.
 */
class RunnerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReportManager.init(this)
    }
}
