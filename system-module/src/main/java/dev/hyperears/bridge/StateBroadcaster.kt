package dev.hyperears.bridge

import android.content.Context
import android.content.Intent
import dev.hyperears.integration.EarbudState

object StateBroadcaster {
    fun publish(context: Context, state: EarbudState, sessionToken: String) {
        ModuleContract.stateConsumerPackages.forEach { targetPackage ->
            context.sendBroadcast(
                ModuleContract.stateChanged(
                    state = state,
                    sessionToken = sessionToken,
                    targetPackage = targetPackage,
                ).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            )
        }
    }

    fun reply(
        context: Context,
        targetPackage: String,
        state: EarbudState,
        sessionToken: String,
    ) {
        if (targetPackage !in ModuleContract.stateConsumerPackages) return
        context.sendBroadcast(
            ModuleContract.stateChanged(state, sessionToken, targetPackage)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        )
    }
}
