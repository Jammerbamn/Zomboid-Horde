package com.jammerbam.zomboid.client.audio;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.audio.ZombieSoundPolicy;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.performance.ClientTpsState;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.SoundSetupEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import paulscode.sound.Library;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** TPS-aware sound admission with a non-reserved mob share. */
@SideOnly(Side.CLIENT)
public final class ZombieSoundProtection {
    private static final int ADJUSTMENT_INTERVAL_TICKS = 20;
    private static final Map<SoundManager, AudioState> STATES = new WeakHashMap<>();
    private static Field soundSystemField;
    private static Field soundLibraryField;
    private static Field normalChannelsField;
    private static boolean reflectionFailed;

    @SubscribeEvent
    public void onSoundSetup(SoundSetupEvent event) {
        STATES.clear();
        SoundSystemConfig.setNumberNormalChannels(ModConfig.normalSoundChannels);
        Zomboid.logger.info(
            "Requested {} normal OpenAL sound channels; the device may allocate fewer.",
            ModConfig.normalSoundChannels
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlaySound(PlaySoundEvent event) {
        ISound sound = event.getResultSound();
        if (sound == null) {
            return;
        }

        AudioState state = state(event.getManager());
        removeFinished(event.getManager(), state.allSounds);
        removeFinished(event.getManager(), state.mobSounds);

        boolean mobSound = ZombieSoundPolicy.isMobSound(sound.getCategory());
        if (mobSound && !ZombieSoundPolicy.canAdmit(
            state.mobSounds.size(), currentMobBudget(state)
        )) {
            event.setResultSound(null);
            return;
        }
        if (!ZombieSoundPolicy.bypassesGlobalLimit(sound.getCategory())
            && !ZombieSoundPolicy.canAdmit(
                state.allSounds.size(), state.effectiveChannelBudget
            )) {
            event.setResultSound(null);
            return;
        }

        state.allSounds.add(sound);
        if (mobSound) {
            state.mobSounds.add(sound);
        }
    }

    @SubscribeEvent
    public void onSoundSource(PlaySoundSourceEvent event) {
        SoundSystem soundSystem = getSoundSystem(event.getManager());
        if (soundSystem == null) {
            return;
        }
        updateActualChannelCount(event.getManager(), soundSystem);

        String path = event.getSound().getSoundLocation().getResourcePath();
        if (ZombieSoundPolicy.isZombieSound(path)) {
            // The event fires after newSource and before play. Paulscode processes
            // its command queue in order, so priority is set before channel selection.
            soundSystem.setPriority(event.getUuid(), true);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STATES.isEmpty()) {
            return;
        }
        double ticksPerSecond = ClientTpsState.getTicksPerSecond();
        for (Map.Entry<SoundManager, AudioState> entry : STATES.entrySet()) {
            AudioState state = entry.getValue();
            state.adjustmentTicks++;
            if (state.adjustmentTicks < ADJUSTMENT_INTERVAL_TICKS) {
                continue;
            }
            state.adjustmentTicks = 0;
            removeFinished(entry.getKey(), state.allSounds);
            removeFinished(entry.getKey(), state.mobSounds);

            int previousTier = state.loadTier;
            int previousBudget = state.effectiveChannelBudget;
            if (ModConfig.dynamicSoundChannels) {
                state.loadTier = ZombieSoundPolicy.selectLoadTier(
                    ticksPerSecond, state.loadTier
                );
            } else {
                state.loadTier = 0;
            }
            state.effectiveChannelBudget = ZombieSoundPolicy.effectiveChannelBudget(
                state.normalChannels,
                ModConfig.minimumDynamicSoundChannels,
                state.loadTier
            );
            if (state.loadTier != previousTier
                || state.effectiveChannelBudget != previousBudget) {
                Zomboid.logger.info(
                    "TPS audio budget changed: TPS={}, tier={}, total={}, mob={}, active={}, activeMob={}.",
                    String.format(java.util.Locale.ROOT, "%.2f", ticksPerSecond),
                    state.loadTier,
                    state.effectiveChannelBudget,
                    currentMobBudget(state),
                    state.allSounds.size(),
                    state.mobSounds.size()
                );
            }
        }
    }

    private static int currentMobBudget(AudioState state) {
        return ZombieSoundPolicy.mobSoundBudget(
            state.effectiveChannelBudget,
            ModConfig.mobSoundChannelPercent
        );
    }

    private static AudioState state(SoundManager manager) {
        return STATES.computeIfAbsent(manager, ignored -> new AudioState());
    }

    private static void removeFinished(SoundManager manager, List<ISound> active) {
        Iterator<ISound> iterator = active.iterator();
        while (iterator.hasNext()) {
            if (!manager.isSoundPlaying(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private static void updateActualChannelCount(SoundManager manager,
                                                 SoundSystem soundSystem) {
        AudioState state = state(manager);
        if (state.actualChannelsDetected) {
            return;
        }
        int actual = getActualNormalChannels(soundSystem);
        if (actual <= 0) {
            return;
        }
        state.actualChannelsDetected = true;
        state.normalChannels = actual;
        state.effectiveChannelBudget = ZombieSoundPolicy.effectiveChannelBudget(
            actual,
            ModConfig.minimumDynamicSoundChannels,
            state.loadTier
        );
        Zomboid.logger.info(
            "OpenAL allocated {} normal channels; TPS audio budget starts at {} total and {} mob.",
            actual,
            state.effectiveChannelBudget,
            currentMobBudget(state)
        );
    }

    private static int getActualNormalChannels(SoundSystem soundSystem) {
        try {
            if (soundLibraryField == null) {
                soundLibraryField = SoundSystem.class.getDeclaredField("soundLibrary");
                soundLibraryField.setAccessible(true);
            }
            if (normalChannelsField == null) {
                normalChannelsField = Library.class.getDeclaredField("normalChannels");
                normalChannelsField.setAccessible(true);
            }
            Library library = (Library) soundLibraryField.get(soundSystem);
            if (library == null) {
                return -1;
            }
            Object channels = normalChannelsField.get(library);
            return channels instanceof List ? ((List<?>) channels).size() : -1;
        } catch (NoSuchFieldException | IllegalAccessException | RuntimeException exception) {
            warnReflectionFailure(exception);
            return -1;
        }
    }

    private static SoundSystem getSoundSystem(SoundManager manager) {
        try {
            if (soundSystemField == null) {
                for (Field candidate : SoundManager.class.getDeclaredFields()) {
                    if (SoundSystem.class.isAssignableFrom(candidate.getType())) {
                        candidate.setAccessible(true);
                        soundSystemField = candidate;
                        break;
                    }
                }
            }
            return soundSystemField == null
                ? null
                : (SoundSystem) soundSystemField.get(manager);
        } catch (IllegalAccessException | RuntimeException exception) {
            warnReflectionFailure(exception);
            return null;
        }
    }

    private static void warnReflectionFailure(Exception exception) {
        if (reflectionFailed) {
            return;
        }
        reflectionFailed = true;
        Zomboid.logger.warn(
            "Unable to inspect the client sound engine; using configured channel counts.",
            exception
        );
    }

    private static final class AudioState {
        private final List<ISound> allSounds = new ArrayList<>();
        private final List<ISound> mobSounds = new ArrayList<>();
        private int normalChannels = ModConfig.normalSoundChannels;
        private int effectiveChannelBudget = ModConfig.normalSoundChannels;
        private int loadTier;
        private int adjustmentTicks;
        private boolean actualChannelsDetected;
    }
}
