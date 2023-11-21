package com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.predictiveback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.essenty.backhandler.BackEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@ExperimentalDecomposeApi
internal class MaterialPredictiveBackAnimatable(
    private val initialEvent: BackEvent,
    private val shape: (progress: Float, edge: BackEvent.SwipeEdge) -> Shape,
) : PredictiveBackAnimatable {

    private val finishProgressAnimatable = Animatable(initialValue = 1F)
    private val finishProgress by derivedStateOf { finishProgressAnimatable.value }
    private val progressAnimatable = Animatable(initialValue = initialEvent.progress)
    private val progress by derivedStateOf { progressAnimatable.value }
    private var edge by mutableStateOf(initialEvent.swipeEdge)
    private var touchY by mutableFloatStateOf(initialEvent.touchY)

    override val exitModifier: Modifier
        get() = Modifier.graphicsLayer { setupExitGraphicLayer() }

    override val enterModifier: Modifier
        get() =
            Modifier.drawWithContent {
                drawContent()
                drawRect(color = Color.Black.copy(alpha = finishProgress * 0.25F))
            }

    private fun GraphicsLayerScope.setupExitGraphicLayer() {
        val pivotFractionX =
            when (edge) {
                BackEvent.SwipeEdge.LEFT -> 1F
                BackEvent.SwipeEdge.RIGHT -> 0F
                BackEvent.SwipeEdge.UNKNOWN -> 0.5F
            }

        transformOrigin = TransformOrigin(pivotFractionX = pivotFractionX, pivotFractionY = 0.5F)

        val scale = 1F - progress / 10F
        scaleX = scale
        scaleY = scale

        val translationXLimit =
            when (edge) {
                BackEvent.SwipeEdge.LEFT -> -8.dp.toPx()
                BackEvent.SwipeEdge.RIGHT -> 8.dp.toPx()
                BackEvent.SwipeEdge.UNKNOWN -> 0F
            }

        translationX = translationXLimit * progress

        val translationYLimit = size.height / 20F - 8.dp.toPx()
        val translationYFactor = ((touchY - initialEvent.touchY) / size.height) * (progress * 3F).coerceAtMost(1f)
        translationY = translationYLimit * translationYFactor

        alpha = finishProgress
        shape = shape(progress, edge)
        clip = true
    }

    override suspend fun animate(event: BackEvent) {
        edge = event.swipeEdge
        touchY = event.touchY
        progressAnimatable.animateTo(event.progress)
    }

    override suspend fun finish() {
        val velocityFactor = progressAnimatable.velocity.coerceAtMost(1F) / 1F
        val progress = progressAnimatable.value
        coroutineScope {
            launch { progressAnimatable.animateTo(progress + (1F - progress) * velocityFactor) }
            launch { finishProgressAnimatable.animateTo(targetValue = 0F, animationSpec = tween()) }
        }
    }
}
