package com.ritesh.cashiro.presentation.ui.features.zakat.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetType
import com.ritesh.cashiro.data.database.entity.ZakatAssetUnit
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.data.repository.ZakatRepository
import com.ritesh.cashiro.domain.zakat.WealthPoolCalculator
import com.ritesh.cashiro.domain.zakat.ZakatCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ritesh.cashiro.data.model.Currency

/**
 * ViewModel for the zakat asset ledger (Phase 2b).
 *
 * Lists every tracked asset with its live-computed value (metals priced
 * from the user-maintained per-gram rate, non-metals at the entered
 * value) and supports add / edit / delete.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ZakatAssetsViewModel @Inject constructor(
    private val zakatRepository: ZakatRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    data class AssetRow(
        val asset: ZakatAssetEntity,
        val value: BigDecimal
    )

    data class UiState(
        val loading: Boolean = true,
        val currencyCode: String = com.ritesh.cashiro.data.model.Currency.DEFAULT_CURRENCY_CODE,
        val rows: List<AssetRow> = emptyList(),
        val totalValue: BigDecimal = BigDecimal.ZERO,
        val goldPricePerGram: String = "",
        val silverPricePerGram: String = ""
    )

    private val prices = combine(
        userPreferencesRepository.zakatGoldPricePerGram,
        userPreferencesRepository.zakatSilverPricePerGram
    ) { gold, silver -> gold to silver }

    private val rowsFlow = prices.flatMapLatest { (goldPrice, silverPrice) ->
        zakatRepository.observeAssets().combine(
            userPreferencesRepository.baseCurrency
        ) { assets, baseCurrency ->
            val gold = parseAmount(goldPrice)
            val silver = parseAmount(silverPrice)
            val computed = assets.map { asset ->
                AssetRow(
                    asset = asset,
                    value = WealthPoolCalculator.assetValue(asset, gold, silver)
                )
            }
            UiState(
                loading = false,
                currencyCode = baseCurrency,
                rows = computed,
                totalValue = computed.fold(BigDecimal.ZERO) { acc, row ->
                    acc.add(row.value)
                }.setScale(2, java.math.RoundingMode.HALF_UP),
                goldPricePerGram = goldPrice,
                silverPricePerGram = silverPrice
            )
        }
    }

    val uiState: StateFlow<UiState> = rowsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    fun saveAsset(asset: ZakatAssetEntity) {
        viewModelScope.launch { zakatRepository.upsertAsset(asset) }
    }

    fun deleteAsset(id: Long) {
        viewModelScope.launch { zakatRepository.deleteAsset(id) }
    }

    private fun parseAmount(raw: String): BigDecimal {
        if (raw.isBlank()) return BigDecimal.ZERO
        return try {
            BigDecimal(raw.trim()).max(BigDecimal.ZERO)
        } catch (e: NumberFormatException) {
            BigDecimal.ZERO
        }
    }
}
