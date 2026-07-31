package dev.hyperears.hook

import android.os.Looper
import android.view.View
import dev.hyperears.integration.EarbudAdapterRegistry
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.MiLinkCardPresentationId
import dev.hyperears.integration.NoiseMode
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.Locale

/**
 * Routes a native MiLink card to the presentation adapter selected by its concrete device adapter.
 *
 * This coordinator contains no model-specific view logic and performs no layout polling.
 */
internal class MiLinkHeadsetDetailExtension(
    hostClassLoader: ClassLoader,
    stateProvider: (String) -> EarbudState,
    controlSender: (String, NoiseMode) -> Unit,
) {
    private class Target(
        root: View,
        var address: String,
        var publishedPresentationId: MiLinkCardPresentationId?,
    ) {
        val root = WeakReference(root)
        var boundPresentationId: MiLinkCardPresentationId? = null
        var binding: MiLinkCardBinding? = null
    }

    private val environment = MiLinkCardEnvironment(
        hostClassLoader = hostClassLoader,
        stateProvider = stateProvider,
        controlSender = controlSender,
    )
    private val targetLock = Any()
    private val targets = WeakHashMap<View, Target>()

    fun bind(
        root: View,
        address: String,
        publishedPresentationId: MiLinkCardPresentationId?,
    ) {
        val normalizedAddress = address.uppercase(Locale.ROOT)
        val target = synchronized(targetLock) {
            targets[root]?.also { existing ->
                if (existing.address != normalizedAddress) {
                    release(existing)
                    existing.address = normalizedAddress
                }
                if (existing.publishedPresentationId != publishedPresentationId) {
                    release(existing)
                    existing.publishedPresentationId = publishedPresentationId
                }
            } ?: Target(
                root = root,
                address = normalizedAddress,
                publishedPresentationId = publishedPresentationId,
            ).also { created ->
                targets[root] = created
                installAttachLifecycle(root, created)
            }
        }
        dispatchRender(target)
        root.post {
            if (target.binding == null && root.isAttachedToWindow) render(target)
        }
    }

    private fun installAttachLifecycle(root: View, target: Target) {
        root.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    dispatchRender(target)
                }

                override fun onViewDetachedFromWindow(view: View) {
                    release(target)
                }
            },
        )
    }

    fun onStateChanged(state: EarbudState) {
        val address = state.address?.uppercase(Locale.ROOT) ?: return
        synchronized(targetLock) {
            targets.values.filter { it.address == address }
        }.forEach(::dispatchRender)
    }

    private fun dispatchRender(target: Target) {
        val root = target.root.get() ?: return
        if (!root.isAttachedToWindow) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            render(target)
        } else {
            root.post { render(target) }
        }
    }

    private fun render(target: Target) {
        val root = target.root.get() ?: return
        if (!root.isAttachedToWindow) return
        val retainedState = environment.stateProvider(target.address)
        val localState = retainedState.takeIf(EarbudState::sessionActive)
        val localPresentationId = EarbudAdapterRegistry
            .integratedById(localState?.modelId)
            ?.miLinkCardPresentationId
        val presentationId = localPresentationId ?: target.publishedPresentationId
        if (target.boundPresentationId != presentationId) {
            release(target)
        }
        val cardAdapter = presentationId
            ?.let(MiLinkCardAdapterRegistry::resolve)
            ?: return
        if (target.binding == null) {
            target.binding = cardAdapter.bind(root, target.address, environment)
            target.boundPresentationId = presentationId.takeIf { target.binding != null }
        }
        target.binding?.render(localState ?: EarbudState())
    }

    private fun release(target: Target) {
        target.binding?.unbind()
        target.binding = null
        target.boundPresentationId = null
    }
}
