package pw.binom

import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import kotlin.math.absoluteValue

@Deprecated(message = "Not use it")
class ChannelMap<T> {
    val size: Int = 0

    private class Bucket<T> {

        private class Node<T>(
            val id: ChannelId,
            var value: T
        ) {
            var next: Node<T>? = null
        }

        private var node: Node<T>? = null
        private val lock = SpinLock()

        operator fun get(id: ChannelId): T? {
            forEach { nodeId, value ->
                if (nodeId == id) {
                    return value
                }
            }
            return null
        }

        private inline fun internalForEach(func: (Node<T>) -> Unit) {
            var current: Node<T>? = node
            while (current != null) {
                func(current)
                current = current.next
            }
        }

        inline fun forEach(func: (ChannelId, T) -> Unit) {
            lock.synchronize {
                internalForEach {
                    func(it.id, it.value)
                }
            }
        }

        fun set(id: ChannelId, value: T) {
            lock.synchronize {
                val node = node
                if (node == null) {
                    val e = value
                    this.node = Node(id = id, value = e)
                    return
                }

                internalForEach {
                    if (it.id == id) {
                        it.value = value
                        return
                    }
                }

                val e = value
                val newNode = Node(id = id, value = e)
                newNode.next = node
                this.node = newNode
            }
        }

        fun remove(id: ChannelId) {
            lock.synchronize {
                var current: Node<T>? = node ?: return
                while (current != null) {
                    val n = current.next ?: break
                    if (n.id == id) {
                        current.next = n.next
                        n.next = null
                        break
                    }
                    current = current.next
                }
            }
        }

        fun getOrPut(id: ChannelId, value: () -> T): T {
            lock.synchronize {
                val node = node
                if (node == null) {
                    val e = value()
                    this.node = Node(id = id, value = e)
                    return e
                }

                internalForEach {
                    if (it.id == id) {
                        return it.value
                    }
                }
                val e = value()
                val newNode = Node(id = id, value = e)
                newNode.next = node
                this.node = newNode
                return e
            }
        }

        fun clear() {
            lock.synchronize {
                node = null
            }
        }
    }

    private val buckets = Array<Bucket<T>>(16) { Bucket() }

    private fun getBucket(channelId: ChannelId): Bucket<T> {
        val bucketId = (channelId.raw % buckets.size).absoluteValue
        return buckets[bucketId]
    }

    operator fun get(channelId: ChannelId): T? =
        getBucket(channelId)[channelId]

    operator fun set(channelId: ChannelId, value: T) {

    }

    fun remove(channelId: ChannelId) {
        getBucket(channelId).remove(channelId)
    }

    fun getOrPut(channelId: ChannelId, valueProvider: () -> T): T =
        getBucket(channelId).getOrPut(channelId, valueProvider)

    fun forEachValues(func: (T) -> Unit) {
        buckets.forEach { b ->
            b.forEach { _, value ->
                func(value)
            }
        }
    }

    fun clear() {
        buckets.forEach {
            it.clear()
        }
    }
}
