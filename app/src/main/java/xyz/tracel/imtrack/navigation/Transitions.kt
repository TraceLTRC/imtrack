package xyz.tracel.imtrack.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

private const val DurationMillis = 300

private typealias Enter = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias Exit = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition
private typealias PredictiveEnter = AnimatedContentTransitionScope<NavBackStackEntry>.(swipeEdge: Int) -> EnterTransition
private typealias PredictiveExit = AnimatedContentTransitionScope<NavBackStackEntry>.(swipeEdge: Int) -> ExitTransition

// Full-screen pages (Projects, Settings): the new page slides in over the old one from the right,
// and the old one drifts a quarter-width left behind it. No fades, so nothing dims mid-transition.
// Back, including predictive back, is the same motion reversed.
private val slideSpec = tween<IntOffset>(DurationMillis, easing = FastOutSlowInEasing)
private fun behind(width: Int) = width / 4

internal val pageEnter: Enter = { slideInHorizontally(slideSpec) { width -> width } }
internal val pageExit: Exit = { slideOutHorizontally(slideSpec) { width -> -behind(width) } }
internal val pagePopEnter: Enter = { slideInHorizontally(slideSpec) { width -> -behind(width) } }
internal val pagePopExit: Exit = { slideOutHorizontally(slideSpec) { width -> width } }
internal val pagePredictivePopEnter: PredictiveEnter = { pagePopEnter() }
internal val pagePredictivePopExit: PredictiveExit = { pagePopExit() }

// Sibling tabs: Material "fade through". The old tab fades out quickly, then the new one fades in,
// so the two never show at half opacity together.
private const val FadeOutMillis = 90

internal val tabEnter: Enter = {
    fadeIn(tween(DurationMillis - FadeOutMillis, delayMillis = FadeOutMillis, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(DurationMillis - FadeOutMillis, delayMillis = FadeOutMillis), initialScale = 0.92f)
}
internal val tabExit: Exit = { fadeOut(tween(FadeOutMillis, easing = FastOutLinearInEasing)) }
internal val tabPredictivePopEnter: PredictiveEnter = { tabEnter() }
internal val tabPredictivePopExit: PredictiveExit = { tabExit() }
