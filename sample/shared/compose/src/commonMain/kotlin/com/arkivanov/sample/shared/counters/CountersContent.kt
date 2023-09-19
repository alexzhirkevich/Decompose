package com.arkivanov.sample.shared.counters

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.StackAnimator
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.back.PredictiveBackAnimatable
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.back.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.isFront
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimator
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.backhandler.BackEvent
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.sample.shared.counters.counter.CounterComponent
import com.arkivanov.sample.shared.counters.counter.CounterContent
import com.arkivanov.sample.shared.counters.counter.PreviewCounterComponent

private class FooPredictiveBackAnimatable(
    event: BackEvent,
) : PredictiveBackAnimatable {

    private val animatable = Animatable(initialValue = getProgress(event.progress))
    override val enterModifier: Modifier get() = Modifier.slideEnterModifier(progress = animatable.value)
    override val exitModifier: Modifier get() = Modifier.slideExitModifier(progress = animatable.value)

    override suspend fun animate(event: BackEvent) {
        animatable.animateTo(targetValue = getProgress(event.progress), animationSpec = animationSpec)
    }

    override suspend fun finish() {
        animatable.animateTo(targetValue = 1F, animationSpec = animationSpec)
    }

    private fun getProgress(progress: Float): Float =
        if (progress < PROGRESS_THRESHOLD) {
            progress
        } else {
            val factor = (progress - PROGRESS_THRESHOLD) / (1F - PROGRESS_THRESHOLD)
            PROGRESS_START + (1F - PROGRESS_START) * factor
        }

    private companion object {
        private const val PROGRESS_THRESHOLD = 0.05F
        private const val PROGRESS_START = 0.8F
        private val animationSpec = spring<Float>(stiffness = Spring.StiffnessHigh)
    }
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
internal fun CountersContent(component: CountersComponent, modifier: Modifier = Modifier) {
    Children(
        stack = component.childStack,
        modifier = modifier,
        animation = predictiveBackAnimation(
            backHandler = component.backHandler,
            animation = stackAnimation(iosLikeSlide()),
            onGestureStarted = ::FooPredictiveBackAnimatable,
            onBack = component::onBackClicked,
        ),
    ) {
        CounterContent(
            component = it.instance,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        )
    }
}

private fun iosLikeSlide(animationSpec: FiniteAnimationSpec<Float> = tween()): StackAnimator =
    stackAnimator(animationSpec = animationSpec) { factor, direction, content ->
        content(
            Modifier
                .then(if (direction.isFront) Modifier else Modifier.fade(factor + 1F))
                .offsetXFactor(factor = if (direction.isFront) factor else factor * 0.5F)
        )
    }

private fun Modifier.slideExitModifier(progress: Float): Modifier =
    offsetXFactor(progress)

private fun Modifier.slideEnterModifier(progress: Float): Modifier =
    fade(progress).offsetXFactor((progress - 1f) * 0.5f)

private fun Modifier.fade(factor: Float) =
    drawWithContent {
        drawContent()
        drawRect(color = Color(red = 0F, green = 0F, blue = 0F, alpha = (1F - factor.coerceIn(0F, 1F)) / 4F))
    }

private fun Modifier.offsetXFactor(factor: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)

        layout(placeable.width, placeable.height) {
            placeable.placeRelative(x = (placeable.width.toFloat() * factor).toInt(), y = 0)
        }
    }

@Preview
@Composable
internal fun CountersPreview() {
    CountersContent(component = PreviewCountersComponent())
}

internal class PreviewCountersComponent : CountersComponent {
    override val backHandler: BackHandler = BackDispatcher()

    override val childStack: Value<ChildStack<*, CounterComponent>> =
        MutableValue(
            ChildStack(
                configuration = Unit,
                instance = PreviewCounterComponent(),
            )
        )

    override fun onBackClicked() {}
    override fun onBackClicked(toIndex: Int) {}
}
