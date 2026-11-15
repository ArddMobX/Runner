package com.runner.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.runner.app.tools.TermuxBridge

/**
 * Принимает результат RUN_COMMAND от Termux.
 *
 * Termux отправляет его через PendingIntent, который создаёт TermuxBridge.
 * Здесь только распаковываем bundle и будим ожидающую корутину.
 */
class TermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val executionId = intent.getIntExtra(TermuxBridge.EXTRA_EXECUTION_ID, -1)
        if (executionId < 0) return

        val bundle = intent.getBundleExtra(TermuxBridge.EXTRA_RESULT_BUNDLE)
        if (bundle == null) {
            TermuxBridge.onResult(
                executionId,
                TermuxBridge.TermuxResult(
                    stdout = "",
                    stderr = "Termux не вернул result bundle",
                    exitCode = -1,
                    err = -1,
                    errMsg = ""
                )
            )
            return
        }

        TermuxBridge.onResult(
            executionId,
            TermuxBridge.TermuxResult(
                stdout = bundle.getString(TermuxBridge.RESULT_STDOUT).orEmpty(),
                stderr = bundle.getString(TermuxBridge.RESULT_STDERR).orEmpty(),
                exitCode = bundle.getInt(TermuxBridge.RESULT_EXIT_CODE, -1),
                err = bundle.getInt(TermuxBridge.RESULT_ERR, -1),
                errMsg = bundle.getString(TermuxBridge.RESULT_ERRMSG).orEmpty()
            )
        )
    }
}
