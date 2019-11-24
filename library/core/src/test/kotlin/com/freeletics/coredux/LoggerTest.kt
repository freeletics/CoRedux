package com.freeletics.coredux

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@ObsoleteCoroutinesApi
@UseExperimental(ExperimentalCoroutinesApi::class)
class LoggerTest : Spek({
    describe("A Logger") {
        val storeName = "some-store"
        val testScope by memoized { TestCoroutineScope() }
        val loggerDispatcher by memoized { TestCoroutineDispatcher() }
        val testLoggers by memoized { listOf(
            TestLogger(testScope),
            TestLogger(testScope),
            TestLogger(testScope)
        ) }

        afterEachTest {
            testScope.cleanupTestCoroutines()
        }

        context("when log sinks are available") {
            val logger by memoized {
                Logger(
                    storeName,
                    testScope,
                    testLoggers.map { it.sink },
                    loggerDispatcher
                )
            }

            context("and to log sinks dispatcher is busy") {
                beforeEachTest {
                    loggerDispatcher.pauseDispatcher()
                }

                context("on receiving first events") {
                    beforeEachTest {
                        logger.logEvent { LogEvent.StoreCreated }
                        logger.logEvent { LogEvent.ReducerEvent.Start }
                        loggerDispatcher.resumeDispatcher()
                    }

                    it("should deliver first events on dispatcher availability") {
                        testLoggers[testLoggers.lastIndex].assertLogEvents(
                            LogEvent.StoreCreated,
                            LogEvent.ReducerEvent.Start
                        )
                    }
                }
            }
        }
    }
})
