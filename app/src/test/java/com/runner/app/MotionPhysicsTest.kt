package com.runner.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import com.runner.app.ui.theme.MotionSettings
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.resolveEffectiveMotionEnabled
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhysicsTest {

    @After
    fun restoreMotionSetting() {
        MotionSettings.enabled = true
    }

    @Test
    fun resolveEffectiveMotionEnabled_respectsUserToggleAndSystemScale() {
        assertTrue(resolveEffectiveMotionEnabled(userMotionEnabled = true, systemAnimatorScale = 1f))
        assertTrue(resolveEffectiveMotionEnabled(userMotionEnabled = true, systemAnimatorScale = 0.5f))
        assertTrue(resolveEffectiveMotionEnabled(userMotionEnabled = true, systemAnimatorScale = 2f))

        assertFalse(resolveEffectiveMotionEnabled(userMotionEnabled = false, systemAnimatorScale = 1f))
        assertFalse(resolveEffectiveMotionEnabled(userMotionEnabled = true, systemAnimatorScale = 0f))
        assertFalse(resolveEffectiveMotionEnabled(userMotionEnabled = true, systemAnimatorScale = -1f))
        assertFalse(resolveEffectiveMotionEnabled(userMotionEnabled = false, systemAnimatorScale = 0f))
    }

    @Test
    fun silkyMaterialPhysics_maintainsSpatialAndEffectsInvariants() {
        // Spatial stiffness tiers: fast > default > slow > 0
        assertTrue(MotionTokens.SpatialFastStiffness > MotionTokens.SpatialDefaultStiffness)
        assertTrue(MotionTokens.SpatialDefaultStiffness > MotionTokens.SpatialSlowStiffness)
        assertTrue(MotionTokens.SpatialSlowStiffness > 0f)

        // Spatial damping stays in silky non-cartoon range (gentle settle, no jelly bounce)
        assertTrue(MotionTokens.SpatialFastDamping in 0.72f..0.90f)
        assertTrue(MotionTokens.SpatialDefaultDamping in 0.78f..0.90f)
        assertTrue(MotionTokens.SpatialSlowDamping in 0.80f..0.92f)

        // Effects springs MUST be critically damped (1.0f) so alpha/color never overshoot
        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.EffectsFastDamping, 0.0001f)
        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.EffectsDefaultDamping, 0.0001f)
        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.EffectsSlowDamping, 0.0001f)
        assertTrue(MotionTokens.EffectsFastStiffness > MotionTokens.EffectsDefaultStiffness)
        assertTrue(MotionTokens.EffectsDefaultStiffness > MotionTokens.EffectsSlowStiffness)
    }

    @Test
    fun motionTokens_whenEnabled_returnsExpectedPhysicsSpecsAndTransitions() {
        MotionSettings.enabled = true

        val fast = MotionTokens.spatialFast<Float>() as SpringSpec<Float>
        assertEquals(MotionTokens.SpatialFastDamping, fast.dampingRatio, 0.0001f)
        assertEquals(MotionTokens.SpatialFastStiffness, fast.stiffness, 0.0001f)

        val defaultSpatial = MotionTokens.spatialDefault<Float>() as SpringSpec<Float>
        assertEquals(MotionTokens.SpatialDefaultDamping, defaultSpatial.dampingRatio, 0.0001f)
        assertEquals(MotionTokens.SpatialDefaultStiffness, defaultSpatial.stiffness, 0.0001f)

        val slow = MotionTokens.spatialSlow<Float>() as SpringSpec<Float>
        assertEquals(MotionTokens.SpatialSlowDamping, slow.dampingRatio, 0.0001f)
        assertEquals(MotionTokens.SpatialSlowStiffness, slow.stiffness, 0.0001f)

        val effectsFast = MotionTokens.effectsFast<Float>() as SpringSpec<Float>
        assertEquals(Spring.DampingRatioNoBouncy, effectsFast.dampingRatio, 0.0001f)

        val effectsDefault = MotionTokens.effectsDefault<Float>() as SpringSpec<Float>
        assertEquals(Spring.DampingRatioNoBouncy, effectsDefault.dampingRatio, 0.0001f)

        val effectsSlow = MotionTokens.effectsSlow<Float>() as SpringSpec<Float>
        assertEquals(Spring.DampingRatioNoBouncy, effectsSlow.dampingRatio, 0.0001f)

        assertTrue(MotionTokens.fluidSpring<Float>() is SpringSpec<Float>)
        assertTrue(MotionTokens.fluidTween<Float>() is TweenSpec<Float>)

        val forward = MotionTokens.sharedAxisXTransition(forward = true)
        val backward = MotionTokens.sharedAxisXTransition(forward = false)
        assertNotEquals(EnterTransition.None, forward.targetContentEnter)
        assertNotEquals(ExitTransition.None, forward.initialContentExit)
        assertEquals(1f, forward.targetContentZIndex, 0.0001f)
        assertEquals(0f, backward.targetContentZIndex, 0.0001f)

        assertNotEquals(EnterTransition.None, MotionTokens.expandSectionEnter())
        assertNotEquals(ExitTransition.None, MotionTokens.expandSectionExit())
        assertNotEquals(EnterTransition.None, MotionTokens.floatingRevealEnter())
        assertNotEquals(ExitTransition.None, MotionTokens.floatingRevealExit())
    }

    @Test
    fun motionTokens_whenDisabled_forcesFullZeroAnimationSnapAndNone() {
        MotionSettings.enabled = false

        assertTrue(MotionTokens.spatialFast<Float>() is SnapSpec<Float>)
        assertTrue(MotionTokens.spatialDefault<Float>() is SnapSpec<Float>)
        assertTrue(MotionTokens.spatialSlow<Float>() is SnapSpec<Float>)
        assertTrue(MotionTokens.effectsFast<Float>() is SnapSpec<Float>)
        assertTrue(MotionTokens.effectsDefault<Float>() is SnapSpec<Float>)
        assertTrue(MotionTokens.effectsSlow<Float>() is SnapSpec<Float>)
        assertTrue(MotionTokens.fluidSpring<Float>() is SnapSpec<Float>)
        assertTrue(MotionTokens.fluidTween<Float>() is SnapSpec<Float>)

        val forward = MotionTokens.sharedAxisXTransition(forward = true)
        assertEquals(EnterTransition.None, forward.targetContentEnter)
        assertEquals(ExitTransition.None, forward.initialContentExit)

        val title = MotionTokens.sharedAxisTitleTransition(forward = false)
        assertEquals(EnterTransition.None, title.targetContentEnter)
        assertEquals(ExitTransition.None, title.initialContentExit)

        val icon = MotionTokens.iconSwapTransition()
        assertEquals(EnterTransition.None, icon.targetContentEnter)
        assertEquals(ExitTransition.None, icon.initialContentExit)

        val swap = MotionTokens.contentSwapTransition()
        assertEquals(EnterTransition.None, swap.targetContentEnter)
        assertEquals(ExitTransition.None, swap.initialContentExit)

        assertEquals(EnterTransition.None, MotionTokens.expandSectionEnter())
        assertEquals(ExitTransition.None, MotionTokens.expandSectionExit())
        assertEquals(EnterTransition.None, MotionTokens.floatingRevealEnter())
        assertEquals(ExitTransition.None, MotionTokens.floatingRevealExit())
    }
}
