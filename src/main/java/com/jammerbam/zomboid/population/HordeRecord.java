package com.jammerbam.zomboid.population;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HordeRecord {
    private final int planningRegionX;
    private final int planningRegionZ;
    private final int anchorChunkX;
    private final int anchorChunkZ;
    private final String groupId;
    private final String definitionId;
    private final int centerX;
    private final int centerZ;
    private final int plannedSize;
    private final int spreadRadius;
    private final List<HordeMember> members;

    public HordeRecord(int planningRegionX, int planningRegionZ, int anchorChunkX,
                       int anchorChunkZ, String groupId, String definitionId, int centerX,
                       int centerZ, int plannedSize, int spreadRadius, List<HordeMember> members) {
        this.planningRegionX = planningRegionX;
        this.planningRegionZ = planningRegionZ;
        this.anchorChunkX = anchorChunkX;
        this.anchorChunkZ = anchorChunkZ;
        this.groupId = groupId;
        this.definitionId = definitionId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.plannedSize = plannedSize;
        this.spreadRadius = spreadRadius;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
    }

    public int getPlanningRegionX() {
        return planningRegionX;
    }

    public int getPlanningRegionZ() {
        return planningRegionZ;
    }

    public int getAnchorChunkX() {
        return anchorChunkX;
    }

    public int getAnchorChunkZ() {
        return anchorChunkZ;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getPlannedSize() {
        return plannedSize;
    }

    public int getSpreadRadius() {
        return spreadRadius;
    }

    public List<HordeMember> getMembers() {
        return members;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("PlanningRegionX", planningRegionX);
        tag.setInteger("PlanningRegionZ", planningRegionZ);
        tag.setInteger("AnchorChunkX", anchorChunkX);
        tag.setInteger("AnchorChunkZ", anchorChunkZ);
        tag.setString("GroupId", groupId);
        tag.setString("DefinitionId", definitionId);
        tag.setInteger("CenterX", centerX);
        tag.setInteger("CenterZ", centerZ);
        tag.setInteger("PlannedSize", plannedSize);
        tag.setInteger("SpreadRadius", spreadRadius);
        NBTTagList memberList = new NBTTagList();
        for (HordeMember member : members) {
            memberList.appendTag(member.writeToNBT());
        }
        tag.setTag("Members", memberList);
        return tag;
    }

    public static HordeRecord readFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("PlanningRegionX")) {
            return readLegacy(tag);
        }

        List<HordeMember> members = new ArrayList<>();
        NBTTagList memberList = tag.getTagList("Members", 10);
        for (int i = 0; i < memberList.tagCount(); i++) {
            members.add(HordeMember.readFromNBT(memberList.getCompoundTagAt(i)));
        }
        return new HordeRecord(
            tag.getInteger("PlanningRegionX"),
            tag.getInteger("PlanningRegionZ"),
            tag.getInteger("AnchorChunkX"),
            tag.getInteger("AnchorChunkZ"),
            tag.getString("GroupId"),
            tag.getString("DefinitionId"),
            tag.getInteger("CenterX"),
            tag.getInteger("CenterZ"),
            tag.getInteger("PlannedSize"),
            tag.getInteger("SpreadRadius"),
            members
        );
    }

    private static HordeRecord readLegacy(NBTTagCompound tag) {
        int centerX = tag.getInteger("CenterX");
        int centerZ = tag.getInteger("CenterZ");
        List<HordeMember> members = new ArrayList<>();
        int normalWeight = tag.getInteger("NormalWeight");
        int huskWeight = tag.getInteger("HuskWeight");
        int villagerWeight = tag.getInteger("VillagerWeight");
        if (normalWeight > 0) {
            members.add(new HordeMember("minecraft:zombie", normalWeight));
        }
        if (huskWeight > 0) {
            members.add(new HordeMember("minecraft:husk", huskWeight));
        }
        if (villagerWeight > 0) {
            members.add(new HordeMember("minecraft:zombie_villager", villagerWeight));
        }
        if (members.isEmpty()) {
            members.add(new HordeMember("minecraft:zombie", 1));
        }
        return new HordeRecord(
            Math.floorDiv(Math.floorDiv(centerX, 16), HordeCatalog.PLANNING_REGION_SIZE_CHUNKS),
            Math.floorDiv(Math.floorDiv(centerZ, 16), HordeCatalog.PLANNING_REGION_SIZE_CHUNKS),
            Math.floorDiv(centerX, 16),
            Math.floorDiv(centerZ, 16),
            tag.getString("GroupId"),
            "zomboid:legacy",
            centerX,
            centerZ,
            tag.getInteger("PlannedSize"),
            tag.getInteger("SpreadRadius"),
            members
        );
    }
}
