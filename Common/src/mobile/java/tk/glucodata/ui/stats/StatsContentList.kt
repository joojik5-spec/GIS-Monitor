package tk.glucodata.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tk.glucodata.ui.components.TabScreenDefaults

internal class StatsContentScope {
    val sections = mutableListOf<Pair<Any?, @Composable () -> Unit>>()
    fun item(key: Any? = null, content: @Composable () -> Unit) {
        sections.add(key to content)
    }
    fun <T> itemsIndexed(items: List<T>, key: (Int, T) -> Any, content: @Composable (Int, T) -> Unit) {
        items.forEachIndexed { index, value -> item(key(index, value)) { content(index, value) } }
    }
}

internal class StatsSectionEntry(val id: Any, render: @Composable () -> Unit) {
    val content = mutableStateOf(render)
}

/** Only keys change the parent list. Reading updates recompose the visible card alone. */
internal class StatsSectionBridge {
    val entries = mutableStateOf<List<StatsSectionEntry>>(emptyList())
    fun update(sections: List<Pair<Any?, @Composable () -> Unit>>) {
        val old = entries.value.associateBy { it.id }
        val next = sections.mapIndexed { index, (id, render) ->
            val key = id ?: "section-$index"
            (old[key] ?: StatsSectionEntry(key, render)).also { it.content.value = render }
        }
        if (entries.value.map { it.id } != next.map { it.id }) entries.value = next
    }
}

/** Embedded statistics share the dashboard scroll instead of trapping it in a fixed viewport. */
@Composable
internal fun StatsContentList(
    embedded: Boolean,
    listState: LazyListState,
    bridge: StatsSectionBridge? = null,
    content: StatsContentScope.() -> Unit,
) {
    val sections = StatsContentScope().apply(content).sections
    if (bridge != null) {
        SideEffect { bridge.update(sections) }
    } else if (embedded) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            sections.forEachIndexed { index, (id, render) -> key(id ?: index) { render() } }
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = TabScreenDefaults.contentPadding()) {
            itemsIndexed(sections, key = { index, entry -> entry.first ?: "section-$index" }) { _, entry ->
                entry.second()
            }
        }
    }
}
