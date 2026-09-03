package com.ritesh.cashiro.presentation.ui.features.zakat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.domain.zakat.ZakatCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Zakat calculator screen.
 *
 * Holds the user-entered wealth, metal prices and hawl start date, and
 * re-evaluates [ZakatCalculator] on every change. All amounts are entered
 * and displayed in the user's base currency unit (from preferences), so
 * the whole feature works identically for SAR, BDT or any other currency.
 */
@HiltViewModel
class ZakatViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    data class UiState(
        val currencyCode: String = "INR",
        val cash: String = "",
        val goldGrams: String = "",
        val silverGrams: String = "",
        val investments: String = "",
        val debtsOwed: String = "",
        val goldPricePerGram: String = "",
        val silverPricePerGram: String = "",
        val nisabMethod: ZakatCalculator.NisabMethod = ZakatCalculator.NisabMethod.SILVER,
        val hawlStartDate: LocalDate = LocalDate.now(),
        val assessment: ZakatCalculator.Assessment? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    val baseCurrency: StateFlow<String> = userPreferencesRepository.baseCurrency
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "INR"
        )

    init {
        reevaluate()
        viewModelScope.launch {
            baseCurrency.collect { code ->
                _uiState.value = _uiState.value.copy(currencyCode = code)
                reevaluate()
            }
        }
    }

    fun onCashChange(value: String) = updateAndEvaluate { it.copy(cash = value) }

    fun onGoldGramsChange(value: String) = updateAndEvaluate { it.copy(goldGrams = value) }

    fun onSilverGramsChange(value: String) = updateAndEvaluate { it.copy(silverGrams = value) }

    fun onInvestmentsChange(value: String) = updateAndEvaluate { it.copy(investments = value) }

    fun onDebtsOwedChange(value: String) = updateAndEvaluate { it.copy(debtsOwed = value) }

    fun onGoldPriceChange(value: String) = updateAndEvaluate { it.copy(goldPricePerGram = value) }

    fun onSilverPriceChange(value: String) = updateAndEvaluate { it.copy(silverPricePerGram = value) }

    fun onNisabMethodChange(method: ZakatCalculator.NisabMethod) =
        updateAndEvaluate { it.copy(nisabMethod = method) }

    fun onHawlStartDateChange(date: LocalDate) =
        updateAndEvaluate { it.copy(hawlStartDate = date) }

    private fun updateAndEvaluate(reducer: (UiState) -> UiState) {
        _uiState.value = reducer(_uiState.value)
        reevaluate()
    }

    private fun reevaluate() {
        val state = _uiState.value
        val prices = ZakatCalculator.MetalPrices(
            goldPerGram = parseAmount(state.goldPricePerGram),
            silverPerGram = parseAmount(state.silverPricePerGram)
        )
        val wealth = ZakatCalculator.Wealth(
            cash = parseAmount(state.cash),
            goldGrams = parseAmount(state.goldGrams),
            silverGrams = parseAmount(state.silverGrams),
            investments = parseAmount(state.investments),
            debtsOwed = parseAmount(state.debtsOwed)
        )
        val assessment = ZakatCalculator.calculate(
            wealth = wealth,
            prices = prices,
            method = state.nisabMethod,
            hawl = ZakatCalculator.Hawl(
                startDate = state.hawlStartDate,
                today = LocalDate.now()
            )
        )
        _uiState.value = state.copy(assessment = assessment)
    }

    /** Parses user input into a non-negative amount; blank/invalid input is zero. */
    private fun parseAmount(raw: String): BigDecimal {
        if (raw.isBlank()) return BigDecimal.ZERO
        return try {
            BigDecimal(raw.trim()).max(BigDecimal.ZERO)
        } catch (e: NumberFormatException) {
            BigDecimal.ZERO
        }
    }
}
