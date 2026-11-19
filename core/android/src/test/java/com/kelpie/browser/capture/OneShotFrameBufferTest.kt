package com.kelpie.browser.capture

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotFrameBufferTest {
    @Test fun acceptedFrameIsTakenByExactlyOneConsumer() =
        runBlocking {
            val disposed = mutableListOf<Any>()
            val buffer = OneShotFrameBuffer<Any> { disposed += it }
            val item = Any()
            assertTrue(buffer.publish(item))
            assertFalse(buffer.isOpen)
            assertSame(item, buffer.awaitTake())
            buffer.close()
            assertTrue(disposed.isEmpty())
        }

    @Test fun stopDisposesPendingAndLateCallbacks() =
        runBlocking {
            val released = mutableListOf<Any>()
            val buffer = OneShotFrameBuffer<Any> { released += it }
            val first = Any()
            val late = Any()
            assertTrue(buffer.publish(first))
            buffer.close()
            assertFalse(buffer.publish(late))
            assertNull(buffer.awaitTake())
            assertEquals(listOf(first, late), released)
            buffer.close()
            assertEquals(2, released.size)
        }

    @Test fun rejectedCallbackAfterTimeoutIsDisposed() =
        runBlocking {
            val disposed = mutableListOf<Any>()
            val buffer = OneShotFrameBuffer<Any> { disposed += it }
            assertNull(withTimeoutOrNull(5L) { buffer.awaitTake() })
            buffer.close()
            val late = Any()
            assertFalse(buffer.publish(late))
            assertEquals(listOf(late), disposed)
            assertNull(buffer.awaitTake())
        }

    @Test fun cancelledWaitingConsumerCannotOrphanTheProducerFrame() =
        runBlocking {
            val disposed = mutableListOf<Any>()
            val buffer = OneShotFrameBuffer<Any> { disposed += it }
            val consumer = launch(start = CoroutineStart.UNDISPATCHED) { buffer.awaitTake() }
            consumer.cancel()
            val frame = Any()
            assertTrue(buffer.publish(frame))
            consumer.join()
            buffer.close()
            assertEquals(listOf(frame), disposed)
        }

    @Test fun failedQaScriptCannotPublishLateFrame() =
        runBlocking {
            val disposed = mutableListOf<Any>()
            val buffer = OneShotFrameBuffer<Any> { disposed += it }
            buffer.fail()
            assertFalse(buffer.isOpen)
            val late = Any()
            assertFalse(buffer.publish(late))
            assertNull(buffer.awaitTake())
            assertEquals(listOf(late), disposed)
        }

    @Test fun concurrentStopAndCallbackReleaseExactlyOnce() {
        repeat(200) {
            val item = Any()
            var disposals = 0
            val buffer = OneShotFrameBuffer<Any> { synchronized(this) { disposals++ } }
            val go = java.util.concurrent.CountDownLatch(1)
            val first =
                Thread {
                    go.await()
                    buffer.publish(item)
                }
            val second =
                Thread {
                    go.await()
                    buffer.close()
                }
            first.start()
            second.start()
            go.countDown()
            first.join()
            second.join()
            assertEquals(1, disposals)
        }
    }
}
