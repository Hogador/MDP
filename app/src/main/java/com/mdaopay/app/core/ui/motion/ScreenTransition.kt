package com.mdaopay.app.core.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.unit.IntOffset

val screenFadeIn: EnterTransition = fadeIn(
    animationSpec = tween(MDAODuration.base, easing = MDAOEasing.easeOut)
)

val screenFadeOut: ExitTransition = fadeOut(
    animationSpec = tween(MDAODuration.base, easing = MDAOEasing.easeOut)
)

val tabFadeIn: EnterTransition = fadeIn(
    animationSpec = tween(250, easing = MDAOEasing.easeOut)
)

val tabFadeOut: ExitTransition = fadeOut(
    animationSpec = tween(250, easing = MDAOEasing.easeOut)
)

fun AnimatedContentTransitionScope<*>.slideInFromRight(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut),
        initialOffsetX = { it / 4 }
    ) + fadeIn(animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut))
}

fun AnimatedContentTransitionScope<*>.slideOutToLeft(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut),
        targetOffsetX = { -it / 4 }
    ) + fadeOut(animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut))
}

fun AnimatedContentTransitionScope<*>.slideInFromLeft(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut),
        initialOffsetX = { -it / 4 }
    ) + fadeIn(animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut))
}

fun AnimatedContentTransitionScope<*>.slideOutToRight(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut),
        targetOffsetX = { it / 4 }
    ) + fadeOut(animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut))
}

val bottomSheetEnter: EnterTransition = slideInVertically(
    animationSpec = tween<IntOffset>(MDAODuration.base, easing = MDAOEasing.easeOut),
    initialOffsetY = { it }
) + fadeIn(animationSpec = BottomSheetAnim.enterTransition)

val bottomSheetExit: ExitTransition = slideOutVertically(
    animationSpec = tween<IntOffset>(MDAODuration.base, easing = MDAOEasing.easeOut),
    targetOffsetY = { it }
) + fadeOut(animationSpec = BottomSheetAnim.exitTransition)

val modalEnter: EnterTransition = scaleIn(
    animationSpec = ModalAnim.scaleSpec,
    initialScale = 0.92f
) + fadeIn(animationSpec = ModalAnim.fadeInSpec)

val modalExit: ExitTransition = scaleOut(
    animationSpec = ModalAnim.scaleSpec,
    targetScale = 0.92f
) + fadeOut(animationSpec = ModalAnim.fadeOutSpec)
