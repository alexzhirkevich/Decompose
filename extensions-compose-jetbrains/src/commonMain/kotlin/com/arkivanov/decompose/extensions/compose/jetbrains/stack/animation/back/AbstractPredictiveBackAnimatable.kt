package com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.back

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arkivanov.essenty.backhandler.BackEvent

internal abstract class AbstractPredictiveBackAnimatable(
    initialBackEvent: BackEvent,
) : PredictiveBackAnimatable {

    private var event: BackEvent by mutableStateOf(initialBackEvent)
    private val progressAnimatable = Animatable(initialValue = initialBackEvent.progress)

    protected val progress: Float get() = progressAnimatable.value
    protected val swipeEdge: BackEvent.SwipeEdge by derivedStateOf { event.swipeEdge }

    override suspend fun animate(event: BackEvent) {
        this.event = event
        progressAnimatable.snapTo(targetValue = event.progress)
    }

    override suspend fun finish() {
        progressAnimatable.animateTo(targetValue = 1F)
    }
}
