package ovh.verbois.retrohub.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** La synchronisation de fond attend le réseau et ne fait rien sans compte relié. */
@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test
    fun repeatedRequestsShareOnePendingSync() {
        runBlocking { repeat(30) { SyncWorker.enqueue(context).join() } }
        val pending = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncWorker.NAME).get().filter { !it.state.isFinished }
        assertEquals(1, pending.size)
    }

    @Test
    fun syncWaitsForTheNetwork() {
        runBlocking { SyncWorker.enqueue(context).join() }
        val info = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncWorker.NAME).get().single()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        // Le réseau arrive : la tâche part.
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(info.id)
        val after = WorkManager.getInstance(context).getWorkInfoById(info.id).get()!!
        assertTrue(after.state.isFinished || after.state == WorkInfo.State.RUNNING)
    }

    @Test
    fun maintenanceDelayReplacesThePendingSync() {
        runBlocking { SyncWorker.enqueue(context).join() }
        runBlocking { SyncWorker.enqueue(context, delaySeconds = 120).join() }
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncWorker.NAME).get().filter { !it.state.isFinished }
        assertEquals(1, infos.size)
        assertTrue(infos.single().initialDelayMillis >= 120_000)
    }

    @Test
    fun withoutAnAccountTheWorkerSucceedsQuietly() {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context.applicationContext).build()
        val result = runBlocking { worker.doWork() }
        assertTrue(result is ListenableWorker.Result.Success)
    }
}
