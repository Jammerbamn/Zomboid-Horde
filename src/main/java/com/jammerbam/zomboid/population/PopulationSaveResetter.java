package com.jammerbam.zomboid.population;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Resets population ledgers belonging to dimensions that are not currently loaded. */
public final class PopulationSaveResetter {
    private static final String CURRENT_FILE = ZombiePopulationData.DATA_NAME + ".dat";
    private static final String LEGACY_FILE = ZombiePopulationData.LEGACY_DATA_NAME + ".dat";

    private PopulationSaveResetter() {
    }

    public static ResetPlan prepare(Path worldRoot, Set<Path> loadedDataDirectories)
        throws IOException {
        Path root = worldRoot.toAbsolutePath().normalize();
        Set<Path> loaded = new HashSet<>();
        for (Path directory : loadedDataDirectories) {
            Path normalized = directory.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) {
                throw new IOException("Loaded dimension data directory is outside the world save: "
                    + normalized);
            }
            loaded.add(normalized);
        }

        Map<Path, LedgerFiles> ledgers = new HashMap<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    String name = path.getFileName().toString();
                    if (!CURRENT_FILE.equals(name) && !LEGACY_FILE.equals(name)) {
                        return;
                    }
                    Path normalized = path.toAbsolutePath().normalize();
                    if (!normalized.startsWith(root)
                        || normalized.getParent() == null
                        || !"data".equalsIgnoreCase(normalized.getParent().getFileName().toString())) {
                        return;
                    }
                    LedgerFiles files = ledgers.computeIfAbsent(
                        normalized.getParent(), ignored -> new LedgerFiles()
                    );
                    if (CURRENT_FILE.equals(name)) {
                        files.current = normalized;
                    } else {
                        files.legacy = normalized;
                    }
                });
            }
        }

        List<PendingWrite> writes = new ArrayList<>();
        List<Path> legacyFiles = new ArrayList<>();
        int clearedRegions = 0;
        int clearedHordes = 0;
        for (Map.Entry<Path, LedgerFiles> entry : ledgers.entrySet()) {
            LedgerFiles files = entry.getValue();
            if (files.legacy != null) {
                legacyFiles.add(files.legacy);
            }
            if (loaded.contains(entry.getKey())) {
                continue;
            }

            Path source = files.current != null ? files.current : files.legacy;
            if (source == null) {
                continue;
            }
            ZombiePopulationData data = read(source);
            clearedRegions += data.getInitializedRegionCount();
            clearedHordes += data.getHordeCount();
            data.resetForRegeneration();
            writes.add(new PendingWrite(
                entry.getKey().resolve(CURRENT_FILE), serialize(data)
            ));
        }
        return new ResetPlan(writes, legacyFiles, clearedRegions, clearedHordes);
    }

    private static ZombiePopulationData read(Path path) throws IOException {
        NBTTagCompound wrapper;
        try (InputStream input = Files.newInputStream(path)) {
            wrapper = CompressedStreamTools.readCompressed(input);
        }
        ZombiePopulationData data = new ZombiePopulationData();
        data.readFromNBT(wrapper.getCompoundTag("data"));
        return data;
    }

    private static byte[] serialize(ZombiePopulationData data) throws IOException {
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("data", data.writeToNBT(new NBTTagCompound()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(wrapper, output);
        return output.toByteArray();
    }

    public static final class ResetPlan {
        private final List<PendingWrite> writes;
        private final List<Path> legacyFiles;
        private final int clearedRegions;
        private final int clearedHordes;

        private ResetPlan(List<PendingWrite> writes, List<Path> legacyFiles,
                          int clearedRegions, int clearedHordes) {
            this.writes = Collections.unmodifiableList(new ArrayList<>(writes));
            this.legacyFiles = Collections.unmodifiableList(new ArrayList<>(legacyFiles));
            this.clearedRegions = clearedRegions;
            this.clearedHordes = clearedHordes;
        }

        public int getOfflineLedgerCount() {
            return writes.size();
        }

        public int getClearedRegionCount() {
            return clearedRegions;
        }

        public int getClearedHordeCount() {
            return clearedHordes;
        }

        public void apply() throws IOException {
            for (PendingWrite write : writes) {
                writeAtomically(write.path, write.contents);
            }
            for (Path legacy : legacyFiles) {
                Files.deleteIfExists(legacy);
            }
        }

        private static void writeAtomically(Path destination, byte[] contents)
            throws IOException {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(
                destination.getParent(), ".zomboid-population-", ".tmp"
            );
            try {
                Files.write(temporary, contents);
                try {
                    Files.move(
                        temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(
                        temporary, destination, StandardCopyOption.REPLACE_EXISTING
                    );
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static final class PendingWrite {
        private final Path path;
        private final byte[] contents;

        private PendingWrite(Path path, byte[] contents) {
            this.path = path;
            this.contents = contents;
        }
    }

    private static final class LedgerFiles {
        private Path current;
        private Path legacy;
    }
}
