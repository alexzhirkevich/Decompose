package com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.back

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.emptyStackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.utils.BackGestureHandler
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.essenty.backhandler.BackEvent
import com.arkivanov.essenty.backhandler.BackHandler
import kotlinx.coroutines.launch

/**
 * Wraps the provided [animation], handles the predictive back gesture and animates
 * the transition from the current [Child] to the previous one.
 * Calls [onBack] when the animation is finished.
 *
 * @param backHandler a source of the predictive back gesture events, see [BackHandler].
 * @param animation a [StackAnimation] for regular transitions.
 * @param onGestureStarted a function that is called when the predictive back gesture begins,
 * returns [PredictiveBackAnimatable] responsible for animations.
 * @param onBack a callback that is called when the gesture is finished.
 */
@ExperimentalDecomposeApi
fun <C : Any, T : Any> predictiveBackAnimation(
    backHandler: BackHandler,
    animation: StackAnimation<C, T>? = null,
    onGestureStarted: (BackEvent) -> PredictiveBackAnimatable = {
        PredictiveBackAnimatable(
            initialBackEvent = it,
            exitModifier = { progress, edge -> Modifier.exitModifier(progress = progress, edge = edge) },
            enterModifier = { progress, _ -> Modifier.enterModifier(progress = progress) },
        )
    },
    onBack: () -> Unit,
): StackAnimation<C, T> =
    PredictiveBackAnimation(
        backHandler = backHandler,
        animation = animation ?: emptyStackAnimation(),
        onGestureStarted = onGestureStarted,
        onBack = onBack,
    )

private fun Modifier.exitModifier(progress: Float, edge: BackEvent.SwipeEdge): Modifier =
    scale(1F - progress * 0.25F)
        .absoluteOffset(
            x = when (edge) {
                BackEvent.SwipeEdge.LEFT -> 32.dp * progress
                BackEvent.SwipeEdge.RIGHT -> (-32).dp * progress
                BackEvent.SwipeEdge.UNKNOWN -> 0.dp
            },
        )
        .alpha(((1F - progress) * 2F).coerceAtMost(1F))
        .clip(RoundedCornerShape(size = 64.dp * progress))

private fun Modifier.enterModifier(progress: Float): Modifier =
    drawWithContent {
        drawContent()
        drawRect(color = Color(red = 0F, green = 0F, blue = 0F, alpha = (1F - progress) / 4F))
    }

private class PredictiveBackAnimation<C : Any, T : Any>(
    private val backHandler: BackHandler,
    private val animation: StackAnimation<C, T>,
    private val onGestureStarted: (BackEvent) -> PredictiveBackAnimatable,
    private val onBack: () -> Unit,
) : StackAnimation<C, T> {

    @Composable
    override fun invoke(stack: ChildStack<C, T>, modifier: Modifier, content: @Composable (child: Child.Created<C, T>) -> Unit) {
        var activeConfigurations: Set<C> by remember { mutableStateOf(emptySet()) }

        val childContent =
            remember(content) {
                movableContentOf<Child.Created<C, T>> { child ->
                    key(child.configuration) {
                        content(child)

                        DisposableEffect(Unit) {
                            activeConfigurations += child.configuration
                            onDispose { activeConfigurations -= child.configuration }
                        }
                    }
                }
            }

        var data: Data<C, T> by rememberMutableStateWithLatest(key = stack) { latestData ->
            Data(stack = stack, key = latestData?.nextKey ?: 0)
        }

        val (dataStack, dataKey, dataAnimatable) = data

        val items =
            if (dataAnimatable == null) {
                listOf(Item(stack = dataStack, key = dataKey, modifier = Modifier))
            } else {
                listOf(
                    Item(stack = dataStack.dropLast(), key = dataKey + 1, modifier = dataAnimatable.enterModifier),
                    Item(stack = dataStack, key = dataKey, modifier = dataAnimatable.exitModifier),
                )
            }

        Box(modifier = modifier) {
            items.forEach { item ->
                key(item.key) {
                    animation(
                        stack = item.stack,
                        modifier = Modifier.fillMaxSize().then(item.modifier),
                        content = childContent,
                    )
                }
            }
        }

        val isBackEnabled = stack.backStack.isNotEmpty()
        val isBackGestureEnabled = isBackEnabled && ((dataAnimatable != null) || (activeConfigurations.size == 1))

        if (isBackEnabled) {
            if (isBackGestureEnabled) {
                val scope = rememberCoroutineScope()

                BackGestureHandler(
                    backHandler = backHandler,
                    onBackStarted = {
                        data = data.copy(animatable = onGestureStarted(it))
                    },
                    onBackProgressed = {
                        scope.launch { data.animatable?.animate(it) }
                    },
                    onBackCancelled = {
                        data = data.copy(animatable = null)
                    },
                    onBack = {
                        if (data.animatable == null) {
                            onBack()
                        } else {
                            scope.launch {
                                data.animatable?.finish()
                                onBack()
                            }
                        }
                    }
                )
            } else {
                BackGestureHandler(backHandler = backHandler, onBack = onBack)
            }
        }
    }

    @Composable
    private fun <T : Any> rememberMutableStateWithLatest(
        key: Any,
        getValue: (latestValue: T?) -> T,
    ): MutableState<T> {
        val latestValue: Holder<T?> = remember { Holder(value = null) }
        val state = remember(key) { mutableStateOf(getValue(latestValue.value)) }
        latestValue.value = state.value

        return state
    }

    private fun <C : Any, T : Any> ChildStack<C, T>.dropLast(): ChildStack<C, T> =
        ChildStack(active = backStack.last(), backStack = backStack.dropLast(1))

    private data class Data<out C : Any, out T : Any>(
        val stack: ChildStack<C, T>,
        val key: Int,
        val animatable: PredictiveBackAnimatable? = null,
    ) {
        val nextKey: Int get() = if (animatable == null) key else key + 1
    }

    private data class Item<out C : Any, out T : Any>(
        val stack: ChildStack<C, T>,
        val key: Int,
        val modifier: Modifier,
    )

    private class Holder<T>(var value: T)
}
