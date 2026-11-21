package com.f2a.authenticator.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt

/**
 * 计算头部在一次纵向滚动后的新偏移，范围 [-height, 0]。
 * 向上滚（delta < 0）仅在 [canCollapse] 时收起；向下滚总是优先展开。
 */
internal fun nextCollapsingOffset(
    current: Float,
    delta: Float,
    height: Int,
    canCollapse: Boolean
): Float {
    if (height <= 0 || delta == 0f) return current.coerceIn(-height.coerceAtLeast(0).toFloat(), 0f)
    if (delta < 0f && !canCollapse) return current
    return (current + delta).coerceIn(-height.toFloat(), 0f)
}

@Stable
class CollapsingHeaderState {
    var heightPx by mutableIntStateOf(0)
        internal set
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** 返回被头部消费掉的滚动量。 */
    fun scrollBy(delta: Float, canCollapse: Boolean): Float {
        val next = nextCollapsingOffset(offsetPx, delta, heightPx, canCollapse)
        val consumed = next - offsetPx
        offsetPx = next
        return consumed
    }

    fun expand() {
        offsetPx = 0f
    }
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState = remember { CollapsingHeaderState() }

/** 列表上滑时先收起头部，下滑时先展开头部。[canCollapse] 用于避免内容不足一屏时也收起。 */
fun collapsingHeaderConnection(
    state: CollapsingHeaderState,
    canCollapse: () -> Boolean
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y == 0f) return Offset.Zero
        return Offset(0f, state.scrollBy(available.y, canCollapse()))
    }
}

/**
 * 把列表之外区域（限宽后的两侧留白、列表上方的头部）上的纵向拖动转交给 [target]，
 * 否则横屏时只有中间的列表本身能拖动。配合 `Modifier.scrollable(state, Orientation.Vertical)` 使用。
 */
@Composable
fun rememberForwardingScrollState(target: () -> LazyListState?): ScrollableState =
    rememberScrollableState { delta -> target()?.let { -it.dispatchRawDelta(-delta) } ?: 0f }

@Composable
fun CollapsingHeader(
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        state.heightPx = height
        val offset = state.offsetPx.roundToInt().coerceIn(-height, 0)
        layout(width, height + offset) {
            placeables.forEach { it.place(0, offset) }
        }
    }
}
