package com.jhkj.videoplayer.compose_pages.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State

// 泛型可观察 Stack
class ObservableStack<T> {
    private val _items = mutableStateListOf<T>()
    val items: List<T> get() = _items

    fun push(item: T) = _items.add(item)

    fun pop(): T? = if (_items.isNotEmpty()) _items.removeAt(_items.size - 1) else null

    fun peek(): T? = _items.lastOrNull()

    val size: Int get() = _items.size
    val isEmpty: Boolean get() = _items.isEmpty()
    fun clear() = _items.clear()
}

@Composable
fun <T> rememberObservableStack(initialItems: List<T> = emptyList()): ObservableStack<T> {
    return remember {
        ObservableStack<T>().apply {
            initialItems.forEach { push(it) }
        }
    }
}