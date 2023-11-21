package com.arkivanov.decompose.extensions.compose.jetpack.stack.animation.predictiveback

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.essenty.backhandler.BackEvent

/**
 * Animates [exitModifier] and [enterModifier] according to incoming [BackEvent] events.
 *
 * [Animatable][androidx.compose.animation.core.Animatable] can be used for animations.
 */
@ExperimentalDecomposeApi
interface PredictiveBackAnimatable {

    /**
     * Returns a [Modifier] for the child being removed (the currently active child).
     * The property must be Compose-observable, e.g. be backed by a Compose state.
     */
    val exitModifier: Modifier

    /**
     * Returns a [Modifier] for the child being shown (the previous child, behind the currently active child).
     * The property must be Compose-observable, e.g. be backed by a Compose state.
     */
    val enterModifier: Modifier

    /**
     * Animates both [exitModifier] and [enterModifier] according to [event].
     * Any previous animation must be cancelled.
     *
     * @see androidx.compose.animation.core.Animatable
     */
    suspend fun animate(event: BackEvent)

    /**
     * Animates both [exitModifier] and [enterModifier] towards the final state.
     * Any previous animation must be cancelled.
     *
     * @see androidx.compose.animation.core.Animatable
     */
    suspend fun finish()
}

/**
 * Creates a default implementation of [PredictiveBackAnimatable] with customisable exit and enter [Modifier]s.
 *
 * @param initialBackEvent an initial [BackEvent] of the predictive back gesture.
 * @param exitModifier a function that returns a [Modifier] for every gesture event, for
 * the child being removed (the currently active child).
 * @param enterModifier a function that returns a [Modifier] for every gesture event, for
 * the previous child (behind the currently active child).
 */
@ExperimentalDecomposeApi
fun predictiveBackAnimatable(
    initialBackEvent: BackEvent,
    exitModifier: (progress: Float, edge: BackEvent.SwipeEdge) -> Modifier,
    enterModifier: (progress: Float, edge: BackEvent.SwipeEdge) -> Modifier,
): PredictiveBackAnimatable =
    DefaultPredictiveBackAnimatable(
        initialBackEvent = initialBackEvent,
        getExitModifier = exitModifier,
        getEnterModifier = enterModifier,
    )

/**
 * Creates an implementation of [PredictiveBackAnimatable] that resembles the
 * [predictive back design for Android](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back).
 *
 * @param initialBackEvent an initial [BackEvent] of the predictive back gesture.
 * @param shape a clipping shape of the child being removed (the currently active child),
 * default is [RoundedCornerShape] that gradually increases following the gesture progress.
 */
@ExperimentalDecomposeApi
fun materialPredictiveBackAnimatable(
    initialBackEvent: BackEvent,
    shape: (progress: Float, edge: BackEvent.SwipeEdge) -> Shape = { progress, _ -> RoundedCornerShape(size = 16.dp * progress) },
): PredictiveBackAnimatable =
    MaterialPredictiveBackAnimatable(
        initialEvent = initialBackEvent,
        shape = shape,
    )
