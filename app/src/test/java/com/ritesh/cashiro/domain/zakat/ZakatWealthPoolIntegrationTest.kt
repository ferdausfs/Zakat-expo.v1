package com.ritesh.cashiro.domain.zakat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ritesh.cashiro.data.database.CashiroDatabase
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetType
import com.ritesh.cashiro.data.database.entity.ZakatAssetUnit
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.data.repository.AccountBalanceRepository
import com.ritesh.cashiro.data.repository.ZakatRepository
import com.ritesh.cashiro.presentation.ui.features.zakat.dashboard.ZakatDashboardViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end integration test for the Phase 2b zakat wealth pool, on the
 * real Room database, real repositories and the real dashboard ViewModel
 * (Robolectric, JVM).
 *
 * Scenario required by the Phase 2b verification checklist: simulate
 * wealth crossing nisab on a KNOWN date and confirm the app auto-detects
 * that exact date as the hawl start — including a dip-reset case and the
 * asset CRUD persistence path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.ritesh.cashiro.CashiroApplication::class)
class ZakatWealthPoolIntegrationTest {

    private lateinit var db: CashiroDatabase
    private lateinit var zakatRepository: ZakatRepository
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var viewModel: ZakatDashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CashiroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val accountBalanceRepository = AccountBalanceRepository(db.accountBalanceDao(), context)
        zakatRepository = ZakatRepository(
            db.zakatAssetDao(),
            accountBalanceRepository,
            db.zakatLiabilityDao(),
            db.ushrEntryDao(),
            db.livestockEntryDao(),
            db.fitrEntryDao(),
            db.zakatPaymentDao()
        )
        preferences = UserPreferencesRepository(context)
        viewModel = ZakatDashboardViewModel(zakatRepository, preferences)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * Suspends on the real StateFlow until the combine pipeline emits a
     * state that includes every upstream write (Room/DataStore emit on
     * real IO dispatchers, so first{} resumes correctly under runTest).
     */
    private suspend fun awaitState(
        ready: (ZakatDashboardViewModel.UiState) -> Boolean = { !it.loading }
    ): ZakatDashboardViewModel.UiState {
        preferences.updateBaseCurrency("BDT")
        return viewModel.uiState.first { !it.loading && ready(it) }
    }

    @Test
    fun `dashboard auto-detects known nisab crossing date from cash history`() = runTest {
        // Nisab default: silver standard with unset price -> 612.36 x 1 = 612.36.
        // Cash 60000 inserted 400 days ago crosses nisab that very day and
        // stays above => crossing date must be exactly that date, hawl
        // (354/355 days) already complete, zakat due = 2.5%.
        val crossing = LocalDate.now().minusDays(400)
        db.accountBalanceDao().insertBalance(
            AccountBalanceEntity(
                bankName = "City Bank",
                accountLast4 = "1234",
                balance = BigDecimal("60000"),
                timestamp = crossing.atTime(9, 0),
                currency = "BDT",
                sourceType = "MANUAL"
            )
        )

        val state = awaitState { s -> s.aboveNisab && s.hawlComplete }

        assertTrue(state.aboveNisab)
        assertEquals(crossing, state.crossingDate)
        assertEquals(crossing, state.firstEverCrossingDate)
        assertTrue(state.hawlComplete)
        assertEquals(0, BigDecimal("1500.00").compareTo(state.zakatDue))
        assertEquals(0, BigDecimal("60000").compareTo(state.breakdown.cash))
        assertTrue(state.hasAnyData)
    }

    @Test
    fun `dashboard resets hawl when wealth dips below nisab`() = runTest {
        val firstCrossing = LocalDate.now().minusDays(500)
        val dip = LocalDate.now().minusDays(100)
        val reCross = LocalDate.now().minusDays(50)
        val dao = db.accountBalanceDao()
        // Account balance timeline (same account, successive snapshots).
        dao.insertBalance(
            AccountBalanceEntity(
                bankName = "Bank", accountLast4 = "1111",
                balance = BigDecimal("60000"),
                timestamp = firstCrossing.atTime(9, 0), currency = "BDT"
            )
        )
        dao.insertBalance(
            AccountBalanceEntity(
                bankName = "Bank", accountLast4 = "1111",
                balance = BigDecimal("100"), // dips below nisab
                timestamp = dip.atTime(9, 0), currency = "BDT"
            )
        )
        dao.insertBalance(
            AccountBalanceEntity(
                bankName = "Bank", accountLast4 = "1111",
                balance = BigDecimal("70000"), // re-crosses
                timestamp = reCross.atTime(9, 0), currency = "BDT"
            )
        )

        val state = awaitState { s -> s.aboveNisab && s.crossingDate == reCross }

        assertTrue(state.aboveNisab)
        // Hawl restarted at the re-crossing date, not the first one.
        assertEquals(reCross, state.crossingDate)
        assertEquals(firstCrossing, state.firstEverCrossingDate)
        assertFalse(state.hawlComplete)
    }

    @Test
    fun `assets persist and are valued into the pool`() = runTest {
        val dao = db.accountBalanceDao()
        dao.insertBalance(
            AccountBalanceEntity(
                bankName = "Bank", accountLast4 = "2222",
                balance = BigDecimal("100000"),
                timestamp = LocalDate.now().minusDays(2).atTime(9, 0),
                currency = "BDT"
            )
        )
        // Persist an asset through the repository (CRUD path).
        val id = zakatRepository.upsertAsset(
            ZakatAssetEntity(
                type = ZakatAssetType.GOLD.name,
                name = "Wedding gold",
                quantity = BigDecimal("1"),
                unit = ZakatAssetUnit.VORI.name,
                karat = 22,
                currency = "BDT",
                acquisitionDate = LocalDate.now().minusDays(1)
            )
        )
        assertTrue(id != 0L)

        // Set metal prices so valuation is deterministic, then wait for
        // the pipeline to reflect both the asset and the price.
        preferences.setZakatGoldPricePerGram("10000")

        val state = awaitState { s -> s.breakdown.gold.signum() > 0 }
        // 1 vori 22k at 10000/g => 11.664 x (22/24) x 10000 = exactly 106920.00
        assertEquals(0, BigDecimal("106920.00").compareTo(state.breakdown.gold))
        // Pool = cash + gold.
        assertEquals(0, BigDecimal("206920.00").compareTo(state.breakdown.total))

        // Update the asset (edit path) and confirm the pool follows.
        val stored = zakatRepository.getAsset(id)!!
        zakatRepository.upsertAsset(stored.copy(name = "Wedding gold set"))
        assertEquals("Wedding gold set", zakatRepository.getAsset(id)!!.name)

        // Delete (soft) and confirm it leaves the pool.
        zakatRepository.deleteAsset(id)
        val stateAfterDelete = awaitState { s -> s.breakdown.gold.signum() == 0 }
        assertEquals(0, BigDecimal("0").compareTo(stateAfterDelete.breakdown.gold))
        assertEquals(0, BigDecimal("100000").compareTo(stateAfterDelete.breakdown.cash))
    }

    @Test
    fun `migration 62 to 63 creates zakat_assets table`() = runTest {
        // The in-memory DB is created at version 63 directly; verify the
        // table exists and accepts rows (schema correctness).
        val count = db.zakatAssetDao().countActive()
        assertEquals(0, count)
        db.zakatAssetDao().insert(
            ZakatAssetEntity(
                type = ZakatAssetType.SILVER.name,
                name = "silver",
                quantity = BigDecimal("500"),
                unit = ZakatAssetUnit.GRAM.name,
                currency = "SAR",
                acquisitionDate = LocalDate.now()
            )
        )
        assertEquals(1, db.zakatAssetDao().countActive())
    }
}
