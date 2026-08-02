package com.jammerbam.zomboid.audio;

import net.minecraft.util.SoundCategory;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZombieSoundPolicyTest {
    @Test
    public void recognizesAllZombieFamilyEvents() {
        assertTrue(ZombieSoundPolicy.isZombieSound("entity.zombie.ambient"));
        assertTrue(ZombieSoundPolicy.isZombieSound("entity.zombie_villager.hurt"));
        assertTrue(ZombieSoundPolicy.isZombieSound("entity.husk.step"));
        assertFalse(ZombieSoundPolicy.isZombieSound("entity.skeleton.ambient"));
    }

    @Test
    public void protectionHonorsItsConcurrentLimit() {
        assertTrue(ZombieSoundPolicy.canAdmit(11, 12));
        assertFalse(ZombieSoundPolicy.canAdmit(12, 12));
        assertFalse(ZombieSoundPolicy.canAdmit(0, 0));
    }

    @Test
    public void recognizesMobAndCriticalSoundCategories() {
        assertTrue(ZombieSoundPolicy.isMobSound(SoundCategory.HOSTILE));
        assertTrue(ZombieSoundPolicy.isMobSound(SoundCategory.NEUTRAL));
        assertFalse(ZombieSoundPolicy.isMobSound(SoundCategory.PLAYERS));
        assertTrue(ZombieSoundPolicy.bypassesGlobalLimit(SoundCategory.PLAYERS));
        assertTrue(ZombieSoundPolicy.bypassesGlobalLimit(SoundCategory.MUSIC));
        assertFalse(ZombieSoundPolicy.bypassesGlobalLimit(SoundCategory.AMBIENT));
    }

    @Test
    public void tpsTierDegradesImmediatelyAndRecoversWithHysteresis() {
        assertEquals(0, ZombieSoundPolicy.selectLoadTier(20.0D, 0));
        assertEquals(1, ZombieSoundPolicy.selectLoadTier(19.4D, 0));
        assertEquals(3, ZombieSoundPolicy.selectLoadTier(14.0D, 0));
        assertEquals(3, ZombieSoundPolicy.selectLoadTier(15.2D, 3));
        assertEquals(2, ZombieSoundPolicy.selectLoadTier(15.5D, 3));
        assertEquals(1, ZombieSoundPolicy.selectLoadTier(20.0D, 2));
        assertEquals(0, ZombieSoundPolicy.selectLoadTier(19.8D, 1));
    }

    @Test
    public void budgetsScaleWithTpsAndMobShare() {
        assertEquals(128, ZombieSoundPolicy.effectiveChannelBudget(128, 28, 0));
        assertEquals(96, ZombieSoundPolicy.effectiveChannelBudget(128, 28, 1));
        assertEquals(64, ZombieSoundPolicy.effectiveChannelBudget(128, 28, 2));
        assertEquals(32, ZombieSoundPolicy.effectiveChannelBudget(128, 28, 3));
        assertEquals(28, ZombieSoundPolicy.effectiveChannelBudget(64, 28, 3));
        assertEquals(38, ZombieSoundPolicy.mobSoundBudget(128, 30.0D));
        assertEquals(19, ZombieSoundPolicy.mobSoundBudget(64, 30.0D));
    }
}
