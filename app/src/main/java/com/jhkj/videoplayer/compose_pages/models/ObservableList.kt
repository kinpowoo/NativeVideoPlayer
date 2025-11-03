package com.jhkj.videoplayer.compose_pages.models

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jhkj.videoplayer.compose_pages.widgets.items

// 可观察的列表包装器
class ObservableList<T>(
    private val initialItems: List<T> = emptyList()
) {
    private val _items = mutableStateListOf<T>().apply { addAll(initialItems) }
    val items: List<T> get() = _items

    fun add(item: T) {
        _items.add(item)
    }

    fun addAll(newItems: List<T>) {
        _items.addAll(newItems)
    }

    fun remove(item: T): Boolean {
        return _items.remove(item)
    }

    fun removeAt(index: Int): T {
        return _items.removeAt(index)
    }

    fun update(index: Int, item: T) {
        _items[index] = item
    }

    fun clear() {
        _items.clear()
    }

    val size: Int get() = _items.size
    fun isEmpty(): Boolean = _items.isEmpty()
    operator fun get(index: Int): T = _items[index]
}


@Composable
fun ObservableListExample() {
    val list = remember { ObservableList<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
    }
}
