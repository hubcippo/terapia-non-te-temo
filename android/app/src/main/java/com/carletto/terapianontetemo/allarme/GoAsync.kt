package com.carletto.terapianontetemo.allarme

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Incapsula il pattern comune ai receiver dell'allarme:
 * goAsync() + coroutine su IO + pendingResult.finish() garantito nel finally.
 */
fun BroadcastReceiver.goAsyncIO(blocco: suspend () -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            blocco()
        } finally {
            pendingResult.finish()
        }
    }
}
