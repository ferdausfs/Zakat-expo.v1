package com.ritesh.cashiro.ui.navigation

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import com.ritesh.cashiro.CashiroApplication
import com.ritesh.cashiro.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Navigation stability regression harness.
 *
 * Reproduces the reported failure mode: the app becoming unresponsive /
 * crashing after a handful of taps of normal use. Drives the REAL
 * [MainActivity] through the real bottom-nav destinations repeatedly
 * (tab rotations, rapid re-taps on the new Zakat tab, open/close cycles)
 * and fails with the caught exception/timeout if the app ever crashes,
 * finishes itself, or stops responding (JUnit timeout => ANR-class bug).
 *
 * Runs on the JVM via Robolectric, so it is part of the normal unit-test
 * suite and gates CI (`Tests` workflow) on every push.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = CashiroApplication::class,
    qualifiers = "w411dp-h891dp-420dpi"
)
class NavigationStressTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // Bottom-nav destinations (contentDescription of each tab icon = its title)
    private val tabs = listOf("Home", "Analytics", "Transactions", "Zakat")

    // Onboarding primary buttons (first-launch flow), in any order
    private val onboardingLabels = listOf(
        "Get Started", "Continue", "Next", "Skip for now", "Finish Setup",
        "Enable Tracking", "Stay Informed", "Allow"
    )

    // Text markers of onboarding forms that must be filled before Continue works
    private val formMarkers = listOf("Bank Name", "Balance", "What should we call you")

    private fun heapSnapshot(): String {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val totalMb = rt.totalMemory() / (1024 * 1024)
        val rssMb = try {
            java.io.File("/proc/self/status").readLines()
                .firstOrNull { it.startsWith("VmRSS") }?.split(Regex("\\s+"))?.getOrNull(1)
                ?.let { (it.toLong() / 1024).toString() } ?: "?"
        } catch (_: Exception) { "?" }
        return "heap ${usedMb}/${totalMb} MB, hostRSS ${rssMb} MB"
    }

    private fun tabMatcher(label: String) = hasContentDescription(label) and hasClickAction()

    private fun onboardingMatcher(label: String) =
        (hasContentDescription(label) or hasText(label)) and hasClickAction()

    /** Wait (bounded) until any of the labels is present & clickable. */
    private fun awaitAnyLabel(labels: List<String>, timeoutMs: Long = 8_000): Boolean {
        val matcher = labels.map { onboardingMatcher(it) }.reduce { acc, m -> acc or m }
        return try {
            composeRule.waitUntil(timeoutMillis = timeoutMs) {
                composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
            }
            true
        } catch (t: Throwable) {
            if (t !is java.util.concurrent.TimeoutException) {
                println("[NavStress] awaitAnyLabel got: $t")
            }
            println("[NavStress] screen tree (waiting for $labels):\n" +
                composeRule.onRoot(useUnmergedTree = true).printToString().take(1800))
            false
        }
    }

    /** Type a profile name if an empty editable field exists (enables onboarding Continue). */
    private fun tryTypeName(): Boolean {
        val edits = composeRule.onAllNodes(hasSetTextAction())
        if (edits.fetchSemanticsNodes().isEmpty()) return false
        return try {
            edits[0].performTextInput("Ferdaus")
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun onboardingFormPresent(): Boolean = formMarkers.any { marker ->
        composeRule.onAllNodes(hasText(marker, substring = true))
            .fetchSemanticsNodes().isNotEmpty()
    }

    /** Fill every visible onboarding form field (bank name, balance, last4, ...). */
    private fun fillOnboardingForm(): Boolean {
        val edits = composeRule.onAllNodes(hasSetTextAction())
        val count = edits.fetchSemanticsNodes().size
        if (count == 0) return false
        val values = listOf("Zakat Bank", "1000", "1234", "extra")
        var filled = 0
        for (i in 0 until count) {
            try {
                edits[i].performTextClearance()
                edits[i].performTextInput(values.getOrElse(i) { "x" })
                filled++
            } catch (_: Exception) {
                // field not directly editable — skip
            }
        }
        println("[NavStress] filled $filled onboarding form field(s)")
        return filled > 0
    }

    private fun firstMatch(labels: List<String>, onboarding: Boolean): SemanticsNodeInteraction? {
        for (label in labels) {
            val nodes = composeRule.onAllNodes(
                if (onboarding) onboardingMatcher(label) else tabMatcher(label)
            )
            if (nodes.fetchSemanticsNodes().isNotEmpty()) return nodes[0]
        }
        return null
    }

    private fun assertAppAlive(where: String) {
        composeRule.activityRule.scenario.onActivity { activity: MainActivity ->
            assertTrue(
                "$where: MainActivity finished/destroyed (crash or crash-screen)",
                !activity.isFinishing && !activity.isDestroyed
            )
        }
    }

    /** Returns the label of the first clickable match, for logging. */
    private fun matchedLabel(labels: List<String>, onboarding: Boolean): String? {
        for (label in labels) {
            val nodes = composeRule.onAllNodes(
                if (onboarding) onboardingMatcher(label) else tabMatcher(label)
            )
            if (nodes.fetchSemanticsNodes().isNotEmpty()) return label
        }
        return null
    }

    private fun dumpTree(): String =
        try {
            composeRule.onRoot(useUnmergedTree = true).printToString().take(6000)
        } catch (_: Exception) {
            "<semantics tree unavailable>"
        }

    @Test(timeout = 420_000)
    fun repeatedNavigationThroughMainFlows_doesNotCrashOrHang() {
        // ---------- Phase A: first-launch onboarding (fresh install flow) ----------
        // Step 1 asks for a profile name; a later step requires filling the
        // "Add Your Main Account" form before its Continue becomes effective.
        var onboardingTaps = 0
        var accountFills = 0
        while (onboardingTaps < 24) {
            // The account form must be filled before its Continue advances;
            // so check for it before trying any button.
            val accountForm = composeRule.onAllNodes(hasText("Bank Name", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
            if (accountForm && accountFills < 2 && fillOnboardingForm()) {
                accountFills++
                continue
            }
            val appeared = awaitAnyLabel(onboardingLabels)
            val label = if (appeared) matchedLabel(onboardingLabels, onboarding = true) else null
            val node = if (appeared) firstMatch(onboardingLabels, onboarding = true) else null
            if (node != null && label != null) {
                println("[NavStress] tapping onboarding '$label' (#${onboardingTaps + 1})")
                node.performClick()
                onboardingTaps++
                continue
            }
            break
        }
        println("[NavStress] onboarding taps: $onboardingTaps; ${heapSnapshot()}")
        if (onboardingTaps == 0) {
            throw AssertionError("Onboarding not driven; screen:\n" + dumpTree())
        }

        // ---------- Phase B: 10 full rotations over all 4 bottom tabs ----------
        repeat(10) { rotation ->
            for (tab in tabs) {
                try {
                    composeRule.waitUntil(timeoutMillis = 15_000) {
                        composeRule.onAllNodes(tabMatcher(tab)).fetchSemanticsNodes().isNotEmpty()
                    }
                } catch (t: Throwable) {
                    throw AssertionError(
                        "[NavStress] rotation $rotation: tab '$tab' not reachable; tree:\n${dumpTree()}", t
                    )
                }
                firstMatch(listOf(tab), onboarding = false)!!.performClick()
                composeRule.waitForIdle()
                assertAppAlive("rotation $rotation after tapping '$tab'")
            }
            println("[NavStress] rotation ${rotation + 1}/10 done; ${heapSnapshot()}")
        }

        // ---------- Phase C: rapid re-tap hammering (reported repro pattern) ----------
        repeat(6) { round ->
            for (tab in tabs) {
                firstMatch(listOf(tab), onboarding = false)!!.performClick()
                // deliberately no waitForIdle between clicks: simulates fast tapping
            }
            composeRule.waitForIdle()
            assertAppAlive("rapid round $round")
            println("[NavStress] rapid round ${round + 1}/6 done; ${heapSnapshot()}")
        }

        // ---------- Phase D: settle & final verification ----------
        composeRule.waitForIdle()
        assertAppAlive("final")
        println("[NavStress] PASS: 40 rotation taps + 24 rapid taps completed; ${heapSnapshot()}")
    }
}
