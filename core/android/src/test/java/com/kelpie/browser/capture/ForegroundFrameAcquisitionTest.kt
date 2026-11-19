package com.kelpie.browser.capture

import com.kelpie.browser.handlers.ScreenshotRefusal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Virtual time uses the project's aligned kotlinx-coroutines-test 1.8.0, not wall-clock sleeps. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundFrameAcquisitionTest {
    private class Candidate(
        val rejection: ForegroundFrameRejection? = null,
    ) {
        var disposals = 0

        fun dispose() {
            disposals += 1
        }
    }

    @Test
    fun staleAndBlankCandidatesAreDisposedBeforeOneVerifiedFrameTransfers() =
        runTest {
            val stale = Candidate(ForegroundFrameRejection.DOCUMENT_PIXEL_PROOF)
            val blank = Candidate(ForegroundFrameRejection.BLANK_CONTENT)
            val verified = Candidate()
            val candidates = listOf(stale, blank, verified)
            var acquisitions = 0
            val result =
                acquireVerifiedForegroundFrame(
                    next = { candidates[acquisitions++] },
                    rejection = { it.rejection },
                    dispose = Candidate::dispose,
                )
            assertSame(verified, result)
            assertEquals(3, acquisitions)
            assertEquals(listOf(1, 1, 0), candidates.map { it.disposals })
            // Ownership has transferred to the consumer, which releases the selected frame.
            result.dispose()
            assertEquals(listOf(1, 1, 1), candidates.map { it.disposals })
        }

    @Test
    fun aConstantStaleProducerExhaustsTheCapWithoutReturningPixels() = runTest { requireCapRefusal(ForegroundFrameRejection.DOCUMENT_PIXEL_PROOF) }

    @Test
    fun aConstantGrayProducerExhaustsTheCapWithoutReturningPixels() = runTest { requireCapRefusal(ForegroundFrameRejection.BLANK_CONTENT) }

    @Test
    fun deadlineCoversTheWholeAcquisitionInsteadOfRestartingForEveryCandidate() =
        runTest {
            val candidates = mutableListOf<Candidate>()
            var acquisitions = 0
            val failure =
                refuse {
                    acquireVerifiedForegroundFrame(
                        next = {
                            acquisitions += 1
                            delay(900)
                            Candidate(ForegroundFrameRejection.DOCUMENT_PIXEL_PROOF).also(candidates::add)
                        },
                        rejection = { it.rejection },
                        dispose = Candidate::dispose,
                    )
                }
            assertEquals("NO_FRESH_FRAME", failure.code)
            assertEquals(2500L, testScheduler.currentTime)
            assertEquals(3, acquisitions)
            assertEquals(2, candidates.size)
            assertTrue(candidates.all { it.disposals == 1 })
        }

    @Test
    fun cancellationWhileInspectingAnOwnedCandidateReleasesItExactlyOnce() =
        runTest {
            val candidate = Candidate()
            val inspecting = CompletableDeferred<Unit>()
            val request =
                launch {
                    acquireVerifiedForegroundFrame(
                        next = { candidate },
                        rejection = {
                            inspecting.complete(Unit)
                            awaitCancellation()
                        },
                        dispose = Candidate::dispose,
                    )
                }
            inspecting.await()
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertEquals(1, candidate.disposals)
        }

    @Test
    fun timeoutDiscardingAnAcceptedResultStillReleasesItsCandidate() =
        runTest {
            val candidate = Candidate()
            var accepted = false
            val failure =
                refuse {
                    acquireVerifiedForegroundFrame(
                        next = {
                            // Suspend the timeout scope so completion must cross its result handoff.
                            delay(1)
                            candidate
                        },
                        rejection = {
                            testScheduler.advanceTimeBy(2500)
                            testScheduler.runCurrent()
                            // Model non-suspending verification completing as timeout wins delivery.
                            accepted = true
                            null
                        },
                        dispose = Candidate::dispose,
                    )
                }
            assertTrue(accepted)
            assertEquals("NO_FRESH_FRAME", failure.code)
            assertEquals(1, candidate.disposals)
        }

    @Test
    fun callerCancellationAsVerificationAcceptsCannotTransferOrOrphanItsFrame() =
        runTest {
            val candidate = Candidate()
            var accepted = false
            var delivered = false
            val request =
                launch {
                    val caller = currentCoroutineContext()[Job]!!
                    acquireVerifiedForegroundFrame(
                        next = {
                            delay(1)
                            candidate
                        },
                        rejection = {
                            caller.cancel()
                            accepted = true
                            null
                        },
                        dispose = Candidate::dispose,
                    )
                    delivered = true
                }
            request.join()
            assertTrue(accepted)
            assertTrue(request.isCancelled)
            assertFalse(delivered)
            assertEquals(1, candidate.disposals)
        }

    @Test
    fun changedOwnerOrPrivacyIsTerminalAndNeverRenewsTheRequest() =
        runTest {
            for (code in listOf("NO_FRESH_FRAME", "OBSERVATION_RACE", "FRAME_PRIVACY_UNVERIFIED")) {
                val candidate = Candidate()
                var acquisitions = 0
                val revoked = ScreenshotRefusal(code, "Synthetic request fence changed")
                val failure =
                    refuse {
                        acquireVerifiedForegroundFrame(
                            next = {
                                acquisitions += 1
                                candidate
                            },
                            rejection = { throw revoked },
                            dispose = Candidate::dispose,
                        )
                    }
                assertSame(revoked, failure)
                assertEquals(1, acquisitions)
                assertEquals(1, candidate.disposals)
            }
        }

    @Test
    fun failedProducerCallbackEndsAcquisitionWithoutRetryingAnAction() =
        runTest {
            var acquisitions = 0
            val failure =
                refuse {
                    acquireVerifiedForegroundFrame<Candidate>(
                        next = {
                            acquisitions += 1
                            null
                        },
                        rejection = { throw AssertionError("No frame can be inspected") },
                        dispose = { throw AssertionError("No frame was allocated") },
                    )
                }
            assertEquals("NO_FRESH_FRAME", failure.code)
            assertEquals(1, acquisitions)
        }

    @Test
    fun cancelledAcquisitionCannotRetainALatePixelCopyCallback() =
        runTest {
            val candidate = Candidate()
            val waiting = CompletableDeferred<Unit>()
            val callback = OneShotFrameBuffer<Candidate>(Candidate::dispose)
            val request =
                launch {
                    acquireVerifiedForegroundFrame(
                        next = {
                            try {
                                waiting.complete(Unit)
                                callback.awaitTake()
                            } finally {
                                callback.close()
                            }
                        },
                        rejection = { it.rejection },
                        dispose = Candidate::dispose,
                    )
                }
            waiting.await()
            request.cancelAndJoin()
            assertFalse(callback.publish(candidate))
            callback.close()
            assertEquals(1, candidate.disposals)
        }

    private suspend fun requireCapRefusal(reason: ForegroundFrameRejection) {
        val candidates = mutableListOf<Candidate>()
        val failure =
            refuse {
                acquireVerifiedForegroundFrame(
                    next = { Candidate(reason).also(candidates::add) },
                    rejection = { it.rejection },
                    dispose = Candidate::dispose,
                )
            }
        assertEquals("NO_FRESH_FRAME", failure.code)
        assertTrue(failure.message.orEmpty().contains(reason.message))
        assertEquals(8, candidates.size)
        assertTrue(candidates.all { it.disposals == 1 })
    }

    private suspend fun refuse(block: suspend () -> Unit): ScreenshotRefusal {
        try {
            block()
        } catch (error: ScreenshotRefusal) {
            return error
        }
        throw AssertionError("Unverified acquisition returned pixels")
    }
}
