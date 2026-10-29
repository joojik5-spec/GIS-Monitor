package tk.glucodata.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal fun dashboardBlockForKey(key: Any): DashboardBlock? =
    if (key.toString().startsWith("stats:")) DashboardBlock.Statistics
    else DashboardBlock.entries.firstOrNull { it.name == key }

internal fun <T> moveDashboardItem(order: List<T>, from: Int, to: Int): List<T> =
    order.toMutableList().apply { add(to, removeAt(from)) }

internal class DashboardDragState(private val list: LazyListState) {
    var block by mutableStateOf<DashboardBlock?>(null)
        private set
    var key by mutableStateOf<Any?>(null)
        private set
    var pointerY by mutableFloatStateOf(0f)
        private set
    private var grabOffset = 0f
    private var pendingLayoutOffset: Int? = null
    private var direction = 0f
    val translation: Float get() {
        val held = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return pointerY - grabOffset - held.offset
    }
    fun start(value: DashboardBlock, itemKey: Any, localY: Float) {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey } ?: return
        block = value; key = itemKey; grabOffset = localY; pointerY = item.offset + localY
        pendingLayoutOffset = null; direction = 0f
    }
    fun stop() { block = null; key = null; pendingLayoutOffset = null; direction = 0f }
    fun drag(delta: Float) {
        if (kotlin.math.abs(delta) > 2f) direction = kotlin.math.sign(delta)
        pointerY += delta; reorderIfCrossed()
    }
    fun reorderIfCrossed() {
        val heldBlock = block ?: return
        val held = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        // Multiple touch samples can precede the next list layout. Wait for the moved
        // slot before accepting another crossing, so a quick finger cannot swap back.
        if (pendingLayoutOffset == held.offset) return
        pendingLayoutOffset = null
        val middle = pointerY - grabOffset + held.size / 2f
        val order = DashboardLayout.order
        val from = order.indexOf(heldBlock)
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val candidate = dashboardBlockForKey(item.key)
            val targetIndex = order.indexOf(candidate)
            val center = item.offset + item.size / 2f
            candidate != null && candidate != heldBlock &&
                middle >= item.offset && middle <= item.offset + item.size &&
                ((direction > 0f && targetIndex > from && middle >= center) ||
                 (direction < 0f && targetIndex < from && middle <= center))
        } ?: return
        val targetBlock = dashboardBlockForKey(target.key) ?: return
        val to = order.indexOf(targetBlock)
        if (from >= 0 && to >= 0 && from != to) {
            pendingLayoutOffset = held.offset
            DashboardLayout.reorder(moveDashboardItem(order, from, to))
        }
    }
}

@Composable
internal fun rememberDashboardDragState(list: LazyListState): DashboardDragState {
    val state = remember(list) { DashboardDragState(list) }
    LaunchedEffect(state.key) {
        while (state.key != null) {
            val info = list.layoutInfo
            val direction = when {
                state.pointerY < info.viewportStartOffset + 100 -> -18f
                state.pointerY > info.viewportEndOffset - 100 -> 18f
                else -> 0f
            }
            if (direction != 0f) { list.scrollBy(direction); state.reorderIfCrossed() }
            delay(16)
        }
    }
    return state
}

@Composable
internal fun dashboardBlockDrag(block: DashboardBlock, itemKey: Any, state: DashboardDragState): Modifier {
    val haptic = LocalHapticFeedback.current
    val title = stringResource(block.titleRes)
    return Modifier.zIndex(if (state.block == block) 2f else 0f)
        .graphicsLayer { translationY = if (state.block == block) state.translation else 0f }
        .semantics { contentDescription = title }
        .pointerInput(block, itemKey, state) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(block, itemKey, it.y); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onDragEnd = state::stop, onDragCancel = state::stop
            ) { change, amount -> change.consume(); state.drag(amount.y) }
        }
}

@Composable
internal fun navigationButtonDrag(route: String): Modifier {
    var offset by remember(route) { mutableFloatStateOf(0f) }
    var dragging by remember(route) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    return Modifier.zIndex(if (dragging) 2f else 0f)
        .graphicsLayer { translationX = offset }
        .pointerInput(route) {
            detectDragGesturesAfterLongPress(
                onDragStart = { dragging = true; offset = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onDragCancel = { dragging = false; offset = 0f },
                onDragEnd = {
                    val order = DashboardLayout.navigation
                    val from = order.indexOf(route)
                    val to = (from + (offset / size.width.coerceAtLeast(1)).roundToInt()).coerceIn(0, order.lastIndex)
                    if (from >= 0 && from != to) DashboardLayout.reorderNavigation(moveDashboardItem(order, from, to))
                    dragging = false; offset = 0f
                }
            ) { change, amount -> change.consume(); offset += amount.x }
        }
}
