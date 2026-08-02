# Minecraft 1.12.2 Mob Attributes and Equipment

This note records how Forge 14.23.5.2847 and Minecraft 1.12.2 initialize mob
attributes and equipment. It is intended to guide the later design of custom
zombie spawning and horde-definition fields.

The implementation was verified against the MCP `snapshot_20171003` sources
in `forgeSrc-1.12.2-14.23.5.2847-sources.jar`.

## Initialization layers

Mob initialization happens in two distinct phases:

1. Java construction establishes registered attributes and their base values.
2. `onInitialSpawn` adds one-time, spawn-context-dependent state such as
   equipment, enchantments, local-difficulty bonuses, handedness, and zombie
   variants.

Reloading an entity from NBT performs the first phase and then restores saved
state. It does not call `onInitialSpawn`.

### Constructor-time attributes

`EntityLivingBase` calls the overridable `applyEntityAttributes()` method from
its constructor and then sets current health to the resulting maximum health.
The inheritance chain contributes attributes:

- `EntityLivingBase` registers maximum health, knockback resistance, movement
  speed, armor, armor toughness, and swim speed.
- `EntityLiving` supplies follow range through its attribute map.
- `EntityMob` registers attack damage.
- `EntityZombie` sets follow range to `35`, movement speed to approximately
  `0.23`, attack damage to `3`, armor to `2`, and registers the zombie
  reinforcement-chance attribute.

Because `applyEntityAttributes()` is invoked during superclass construction,
a custom override must not depend on subclass fields having been initialized.
Constants and immediately available configuration values are safe.

Changing maximum health after construction does not automatically refill the
mob. Code that changes maximum health during spawn initialization should also
decide whether to call `setHealth(getMaxHealth())`.

## Attribute values and modifiers

Every registered attribute has a base value plus zero or more UUID-identified
modifiers. Minecraft evaluates modifier operations in this order:

1. Operation `0`: add the modifier amount directly to the base.
2. Operation `1`: add `base * amount`.
3. Operation `2`: multiply the accumulated result by `1 + amount`.
4. Clamp the result to the attribute's allowed range.

Calling `setBaseValue` changes only the base. It does not remove spawn,
equipment, potion, or other modifiers.

The standard shared attributes relevant to zombies are:

- `generic.maxHealth`
- `generic.followRange`
- `generic.knockbackResistance`
- `generic.movementSpeed`
- `generic.attackDamage`
- `generic.armor`
- `generic.armorToughness`
- `zombie.spawnReinforcements`

## One-time zombie initialization

Vanilla `EntityZombie.onInitialSpawn` calls the parent implementation and then
performs the following work:

- Applies the common random follow-range modifier and left-handed flag.
- Sets the chance of picking up ground loot from local difficulty.
- Selects shared zombie group data, including the baby-zombie decision.
- Handles chicken-jockey behavior.
- Randomizes the door-breaking task.
- Calls `setEquipmentBasedOnDifficulty`.
- Calls `setEnchantmentBasedOnDifficulty`.
- May equip a Halloween pumpkin with a zero drop chance.
- Applies a small random knockback-resistance bonus.
- May apply a multiplicative follow-range bonus.
- May create a leader zombie with reinforcement and maximum-health bonuses.

Local difficulty is not just the selected difficulty setting. Its calculation
also considers world age, inhabited chunk time, moon phase, and whether the
world is on Hard difficulty.

Natural spawning reuses one `IEntityLivingData` object across members of a
spawn group. The current seeded population manager passes `null` for every
materialized entity, so each zombie creates its group data independently. If
we later want vanilla-style shared variants within a horde, that group state
will need to be generated and persisted at the horde level.

## Difficulty-based equipment

The generic `EntityLiving.setEquipmentBasedOnDifficulty` routine has a
`0.15 * clampedLocalDifficulty` chance to begin generating armor. It selects
an armor material tier and works through the armor slots, with a chance to
stop between pieces. Existing equipment in a slot is retained.

The vanilla zombie override calls the generic armor routine and then has an
additional weapon roll:

- Hard difficulty: `5%`
- Other non-Peaceful difficulties: `1%`
- One-third of successful rolls equip an iron sword.
- The remaining successful rolls equip an iron shovel.

After equipment selection, the held item has a
`0.25 * clampedLocalDifficulty` enchantment chance. Each armor piece has a
`0.5 * clampedLocalDifficulty` enchantment chance.

## Equipment storage and item attributes

`EntityLiving` stores two hand slots and four armor slots. Equipment is
assigned with `setItemStackToSlot` using:

- `MAINHAND`
- `OFFHAND`
- `FEET`
- `LEGS`
- `CHEST`
- `HEAD`

The setter stores the stack. During the living entity's equipment-sync pass,
Minecraft detects a changed stack, sends the equipment packet, fires
`LivingEquipmentChangeEvent`, removes modifiers from the old stack, and
applies modifiers from the new stack.

Consequently, code that equips a mob and immediately reads its effective
attributes before the first living update should not assume the new item's
modifiers have already been incorporated.

Items contribute modifiers only in their applicable slot:

- Swords add attack damage and attack speed in `MAINHAND`.
- Armor adds armor and armor toughness in its matching armor slot.
- Forge allows an item to return a custom equipment slot.

An `ItemStack` can contain an `AttributeModifiers` NBT list. If that list is
present, it replaces the item's normal modifier map for that stack. Entries
may be limited to a named equipment slot. Modifier UUIDs must be non-zero and
must be managed carefully to avoid duplicate-modifier conflicts.

Enchantments are stored on the item but generally affect combat through
`EnchantmentHelper`, rather than becoming ordinary attribute modifiers.

## Ground-item pickup and drops

Mobs only scan for ground equipment when `CanPickUpLoot` is enabled and the
Forge mob-griefing check allows it. Vanilla compares candidate equipment with
the current stack:

- Swords are primarily compared by attack damage.
- Armor is primarily compared by armor value.
- NBT/enchantment presence helps break equal-value ties.
- Binding Curse can prevent armor replacement.

When a mob picks up an item, the replaced stack may drop, the new item's drop
chance becomes `2.0`, and the mob is marked persistent.

Default hand and armor drop chances are `0.085`. Values above `1.0` represent
guaranteed-equipment behavior. Vanishing Curse prevents the item from
dropping. Non-guaranteed damageable equipment receives randomized durability
when dropped.

Profile-defined equipment therefore needs an explicit drop-chance policy; an
equipped item and its drop behavior are separate decisions.

## Persistence

Living mob NBT stores:

- Base attributes and saved attribute modifiers
- Hand and armor `ItemStack` lists
- Hand and armor drop-chance lists
- `CanPickUpLoot`
- `PersistenceRequired`
- Active potion effects and other living state

Before writing the attribute list, Minecraft temporarily removes equipment
modifiers and reapplies them afterward. This prevents item-derived modifiers
from being saved as permanent entity modifiers and then applied twice after
loading.

On reload, equipment and attributes are restored from NBT and
`onInitialSpawn` is deliberately skipped. Any profile initialization must
therefore happen only for a genuinely new entity, not every time it joins the
world.

## Spawn-path differences

### Natural spawning

The normal order is:

1. Construct the entity.
2. Set position and rotation.
3. Fire Forge's spawn checks.
4. Test `getCanSpawnHere()` and collision.
5. Fire `LivingSpawnEvent.SpecialSpawn`.
6. Call `onInitialSpawn` unless special-spawn handling replaces it.
7. Perform a final collision check.
8. Add the entity to the world.

### Spawn eggs

Spawn eggs construct and position the entity, run the special-spawn hook,
call `onInitialSpawn`, add it to the world, and then apply the egg's custom
entity NBT.

### Mob spawners

A mob spawner calls `onInitialSpawn` only when its spawn data contains nothing
but the entity ID. Custom spawn NBT bypasses normal one-time initialization,
because the NBT is expected to describe the desired state.

### `/summon`

`/summon` without custom NBT calls `onInitialSpawn`. Supplying custom NBT
causes NBT state to be loaded and skips `onInitialSpawn`.

### Chunk reload

Chunk loading constructs the entity and restores NBT. It never calls
`onInitialSpawn`.

### Current seeded population manager

The current manager:

1. Creates the configured registry entity.
2. Finds and validates its position.
3. Applies population tags.
4. Enables persistence.
5. Calls `onInitialSpawn` with local difficulty and `null` group data.
6. Adds the entity to the world.

This is close to vanilla initialization and means JSON-selected zombies still
receive vanilla armor, weapons, enchantments, baby/leader rolls, and spawn
modifiers. It intentionally performs its own daylight-aware spawn validation
instead of using the complete natural-spawn event path.

Those vanilla equipment and trait rolls consume the entity's runtime random
stream, not the seeded population generator's deterministic random stream.
They persist once the entity is saved, but they are not suitable for content
that must be reproducible directly from the world seed. Deterministic custom
equipment should be rolled into the saved spawn plan before materialization.

`ZombieBehaviorEvents` then handles `EntityJoinWorldEvent` and replaces the
movement-speed and follow-range base values from the Forge configuration.
Random modifiers created by `onInitialSpawn` remain active because changing a
base value does not remove modifiers.

## Design implications for custom horde entities

Before implementing profile-defined custom zombies, we need to choose between
two initialization policies:

### Vanilla-derived initialization

Call the entity's `onInitialSpawn` and then apply the horde profile.

Advantages:

- Retains compatibility with ordinary vanilla/Forge initialization.
- Retains subclass-specific setup.
- Works well for third-party entities whose initialization is unknown.

Costs:

- Vanilla zombie initialization can create babies, jockeys, equipment,
  enchantments, leader bonuses, and random modifiers before the profile is
  applied.
- Some side effects, especially creating a chicken jockey, are not cleanly
  undone by replacing attributes or equipment afterward.

### Profile-controlled initialization

For our own entity classes, override `onInitialSpawn` without invoking the
full `EntityZombie` implementation, then explicitly initialize only the
features the profile permits.

Advantages:

- Deterministic makeup and equipment.
- No unwanted vanilla variants or side effects.
- Clear ownership of health, equipment, drop chances, and special traits.

Costs:

- We must deliberately reproduce any common behavior we want, such as
  handedness or local-difficulty scaling.
- This policy cannot safely be imposed on arbitrary third-party entity
  classes.

A practical hybrid is likely:

- Our custom zombie classes use profile-controlled initialization.
- Vanilla and third-party entities retain their normal `onInitialSpawn`.
- After initialization, a horde profile may explicitly override registered
  base attributes, equipment, drop chances, loot pickup, and current health.
- Generated values are saved in the existing persistent horde/entity record so
  rematerialization never rerolls them.

Additional future definition fields may include:

- Explicit saved modifiers with stable UUIDs and operations
- Equipment entries by slot, item registry ID, metadata, NBT, weight, and
  drop chance
- Enchantment rules
- Loot-pickup permission
- Initialization mode for entities owned by this mod
- Health refill policy after maximum-health changes
