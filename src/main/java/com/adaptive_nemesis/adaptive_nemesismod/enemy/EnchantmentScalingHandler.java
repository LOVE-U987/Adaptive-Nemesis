package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthData;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * 难度影响怪物装备/附魔系统
 *
 * 基于当前难度倍率，提高怪物携带装备和附魔的概率与等级。
 * 难度越高，怪物装备越好、附魔等级越高。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class EnchantmentScalingHandler {

    private static EnchantmentScalingHandler INSTANCE;

    private final Random random = new Random();

    /**
     * 延迟加载的装备数据（避免在无 Minecraft 运行时触发类加载）
     */
    private Item[][] armorByTier;
    private Item[][] weaponsByTier;

    private Item[][] getArmorByTier() {
        if (armorByTier == null) {
            armorByTier = new Item[][] {
                { Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
                { Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
                { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
                { Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS },
                { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS }
            };
        }
        return armorByTier;
    }

    private Item[][] getWeaponsByTier() {
        if (weaponsByTier == null) {
            weaponsByTier = new Item[][] {
                { Items.STONE_SWORD, Items.STONE_AXE, Items.GOLDEN_SWORD, Items.GOLDEN_AXE },
                { Items.IRON_SWORD, Items.IRON_AXE },
                { Items.IRON_SWORD, Items.IRON_AXE },
                { Items.DIAMOND_SWORD, Items.DIAMOND_AXE },
                { Items.NETHERITE_SWORD, Items.NETHERITE_AXE }
            };
        }
        return weaponsByTier;
    }
    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] WEAPON_ENCHANTMENTS = new ResourceKey[] {
        Enchantments.SHARPNESS,
        Enchantments.SMITE,
        Enchantments.BANE_OF_ARTHROPODS,
        Enchantments.FIRE_ASPECT,
        Enchantments.KNOCKBACK,
        Enchantments.LOOTING,
        Enchantments.SWEEPING_EDGE,
        Enchantments.UNBREAKING,
        Enchantments.EFFICIENCY
    };

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] ARMOR_ENCHANTMENTS = new ResourceKey[] {
        Enchantments.PROTECTION,
        Enchantments.FIRE_PROTECTION,
        Enchantments.BLAST_PROTECTION,
        Enchantments.PROJECTILE_PROTECTION,
        Enchantments.THORNS,
        Enchantments.UNBREAKING,
        Enchantments.RESPIRATION,
        Enchantments.AQUA_AFFINITY,
        Enchantments.FEATHER_FALLING,
        Enchantments.DEPTH_STRIDER
    };

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] BOW_ENCHANTMENTS = new ResourceKey[] {
        Enchantments.POWER,
        Enchantments.PUNCH,
        Enchantments.FLAME,
        Enchantments.INFINITY,
        Enchantments.UNBREAKING
    };

    private EnchantmentScalingHandler() {}

    public static synchronized EnchantmentScalingHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EnchantmentScalingHandler();
        }
        return INSTANCE;
    }

    /**
     * 怪物生成事件 - 应用装备/附魔强化
     */
    @SubscribeEvent
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!Config.ENABLE_ENCHANTMENT_SCALING.get()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        if (!(entity instanceof Mob mob)) {
            return;
        }

        if (!(mob instanceof Enemy)) {
            return;
        }

        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 获取当前难度倍率
        double difficultyMultiplier = getDifficultyMultiplier(mob);
        if (difficultyMultiplier <= 1.0) {
            return;
        }

        // 应用装备强化
        applyEquipmentScaling(mob, difficultyMultiplier, serverLevel);
    }

    /**
     * 获取当前难度倍率
     */
    private double getDifficultyMultiplier(Mob mob) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return 1.0;
        }

        // 获取附近玩家的强度
        double range = Config.AREA_SYNC_RANGE.get() * 16;
        AABB searchBox = new AABB(
            mob.getX() - range, mob.getY() - range, mob.getZ() - range,
            mob.getX() + range, mob.getY() + range, mob.getZ() + range
        );

        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(ServerPlayer.class, searchBox);
        if (nearbyPlayers.isEmpty()) {
            return 1.0;
        }

        double totalStrength = 0;
        for (ServerPlayer player : nearbyPlayers) {
            var data = com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator.getInstance()
                .getPlayerStrength(player);
            if (data != null) {
                totalStrength += data.getTotalStrength();
            }
        }
        double avgStrength = totalStrength / nearbyPlayers.size();

        // 基于玩家强度和基础倍率计算难度系数
        double baseMultiplier = 1.0 + (avgStrength * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);

        // 应用世界阶段
        if (Config.ENABLE_WORLD_STAGE.get()) {
            baseMultiplier *= WorldStageManager.getInstance().getWorldStageMultiplier();
        }

        return Math.max(1.0, Math.min(baseMultiplier, 20.0));
    }

    /**
     * 应用装备强化
     */
    private void applyEquipmentScaling(Mob mob, double difficultyMultiplier, ServerLevel serverLevel) {
        float enchantChance = calculateEnchantChance(difficultyMultiplier);
        int enchantLevel = calculateEnchantLevel(difficultyMultiplier);

        DifficultyInstance difficulty = mob.level().getCurrentDifficultyAt(mob.blockPosition());

        // 遍历所有装备槽
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            boolean isArmor = slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
            boolean isHand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
            if (!isArmor && !isHand) {
                continue;
            }

            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) {
                // 有一定概率为怪物生成装备（如果原本没有）
                if (shouldGrantEquipment(difficultyMultiplier, slot, difficulty)) {
                    stack = createEquipmentForSlot(mob, slot, difficultyMultiplier, serverLevel);
                    if (!stack.isEmpty()) {
                        mob.setItemSlot(slot, stack);
                    }
                }
            }

            // 为所有装备（自然生成或新创建）添加附魔
            if (!stack.isEmpty() && random.nextFloat() < enchantChance) {
                applyEnchantments(stack, slot, enchantLevel, serverLevel);
            }
        }
    }

    /**
     * 计算附魔概率 - 纯计算版本，可测试
     *
     * @param difficultyMultiplier 难度倍率
     * @param baseChance 基础概率
     * @param chancePerDifficulty 每单位难度的概率增量
     * @return 最终附魔概率（上限0.95）
     */
    static float calculateEnchantChance(double difficultyMultiplier, float baseChance, float chancePerDifficulty) {
        float additionalChance = (float) ((difficultyMultiplier - 1.0) * chancePerDifficulty);
        return Math.max(0f, Math.min(baseChance + additionalChance, 0.95f));
    }

    /**
     * 计算附魔概率（包装方法 - 从Config读取参数）
     */
    private float calculateEnchantChance(double difficultyMultiplier) {
        return calculateEnchantChance(
            difficultyMultiplier,
            Config.ENCHANTMENT_CHANCE_BASE.get().floatValue(),
            Config.ENCHANTMENT_CHANCE_PER_DIFFICULTY.get().floatValue()
        );
    }

    /**
     * 计算附魔等级 - 纯计算版本，可测试
     *
     * @param difficultyMultiplier 难度倍率
     * @param levelPerDifficulty 每单位难度的等级增量
     * @param maxLevel 最高等级上限
     * @return 最终附魔等级
     */
    static int calculateEnchantLevel(double difficultyMultiplier, double levelPerDifficulty, int maxLevel) {
        int additionalLevel = (int) Math.floor((difficultyMultiplier - 1.0) * levelPerDifficulty);
        return Math.max(1, Math.min(1 + additionalLevel, maxLevel));
    }

    /**
     * 计算附魔等级（包装方法 - 从Config读取参数）
     */
    private int calculateEnchantLevel(double difficultyMultiplier) {
        return calculateEnchantLevel(
            difficultyMultiplier,
            Config.ENCHANTMENT_LEVEL_PER_DIFFICULTY.get(),
            Config.ENCHANTMENT_MAX_LEVEL.get()
        );
    }

    /**
     * 判断是否应该给怪物生成装备 - 纯计算版本，可测试
     *
     * @param difficultyMultiplier 难度倍率
     * @param isMainhand 是否为主手槽
     * @return 生成装备的概率
     */
    static float shouldGrantEquipmentChance(double difficultyMultiplier, boolean isMainhand, float baseChance, float chancePerDifficulty) {
        float additionalChance = (float) ((difficultyMultiplier - 1.0) * chancePerDifficulty);
        float totalChance = baseChance + additionalChance;
        if (isMainhand) {
            totalChance *= 2.0f;
        }
        return Math.min(totalChance, 1.0f);
    }

    /**
     * 判断是否应该给怪物生成装备（从Config读取参数）
     */
    private boolean shouldGrantEquipment(double difficultyMultiplier, EquipmentSlot slot, DifficultyInstance difficulty) {
        float baseChance = Config.EQUIPMENT_BASE_CHANCE.get().floatValue();
        float chancePerDifficulty = Config.EQUIPMENT_CHANCE_PER_DIFFICULTY.get().floatValue();
        float totalChance = shouldGrantEquipmentChance(difficultyMultiplier, slot == EquipmentSlot.MAINHAND, baseChance, chancePerDifficulty);
        return random.nextFloat() < totalChance;
    }

    /**
     * 判断是否应该给怪物生成装备 - 纯计算版本，可测试
     *
     * @param difficultyMultiplier 难度倍率
     * @param isMainhand 是否为主手槽
     * @return 生成装备的概率
     */
    @Deprecated
    static float shouldGrantEquipmentChance(double difficultyMultiplier, boolean isMainhand) {
        return shouldGrantEquipmentChance(difficultyMultiplier, isMainhand, 0.15f, 0.10f);
    }

    /**
     * 根据难度获取装备品质等级
     *
     * @param difficultyMultiplier 难度倍率
     * @return 装备品质等级 (0=皮革, 1=锁链, 2=铁, 3=钻石, 4=下界合金)
     */
    private int getEquipmentTier(double difficultyMultiplier) {
        int baseTier;
        if (difficultyMultiplier >= 6.0) baseTier = 4; // 下界合金
        else if (difficultyMultiplier >= 4.0) baseTier = 3;  // 钻石
        else if (difficultyMultiplier >= 2.5) baseTier = 2;  // 铁
        else if (difficultyMultiplier >= 1.5) baseTier = 1;  // 锁链
        else baseTier = 0; // 皮革

        // 根据配置的概率获得高一档品质的装备
        float upgradeChance = Config.EQUIPMENT_TIER_UPGRADE_CHANCE.get().floatValue();
        if (baseTier < 4 && random.nextFloat() < upgradeChance) {
            baseTier++;
        }

        return baseTier;
    }

    /**
     * 为指定槽位创建装备
     * 根据难度倍率自动选择装备品质
     * 有概率使用其他模组的装备替代原版装备
     *
     * @param mob 目标怪物
     * @param slot 装备槽位
     * @param difficultyMultiplier 难度倍率
     * @param serverLevel 服务端世界
     * @return 创建的装备物品
     */
    private ItemStack createEquipmentForSlot(Mob mob, EquipmentSlot slot, double difficultyMultiplier, ServerLevel serverLevel) {
        int tier = getEquipmentTier(difficultyMultiplier);

        ItemStack vanillaStack = switch (slot) {
            case MAINHAND -> {
                Item[] weapons = getWeaponsByTier()[tier];
                yield new ItemStack(weapons[random.nextInt(weapons.length)]);
            }
            case OFFHAND -> random.nextBoolean() ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY;
            case HEAD -> new ItemStack(getArmorByTier()[tier][0]);
            case CHEST -> new ItemStack(getArmorByTier()[tier][1]);
            case LEGS -> new ItemStack(getArmorByTier()[tier][2]);
            case FEET -> new ItemStack(getArmorByTier()[tier][3]);
            default -> ItemStack.EMPTY;
        };

        // 尝试用其他模组的装备替换原版装备
        if (!vanillaStack.isEmpty()) {
            ItemStack modStack = tryGetModEquipment(serverLevel, slot);
            if (!modStack.isEmpty()) {
                return modStack;
            }
        }

        return vanillaStack;
    }

    /**
     * 尝试从其他模组获取装备
     * 通过扫描物品标签注册表，找到其他模组添加的装备
     *
     * @param level 服务端世界
     * @param slot 装备槽位
     * @return 模组装备，如果没有合适的则返回空
     */
    private ItemStack tryGetModEquipment(ServerLevel level, EquipmentSlot slot) {
        float modChance = Config.EQUIPMENT_MOD_COMPAT_CHANCE.get().floatValue();
        if (random.nextFloat() >= modChance) {
            return ItemStack.EMPTY;
        }

        Registry<Item> itemRegistry = level.registryAccess().registryOrThrow(Registries.ITEM);

        // 根据槽位选择对应的物品标签
        TagKey<Item> tag = switch (slot) {
            case MAINHAND -> null;
            case OFFHAND -> null;
            case HEAD -> ItemTags.HEAD_ARMOR;
            case CHEST -> ItemTags.CHEST_ARMOR;
            case LEGS -> ItemTags.LEG_ARMOR;
            case FEET -> ItemTags.FOOT_ARMOR;
            default -> null;
        };

        List<Item> modItems = new ArrayList<>();

        if (tag != null) {
            // 从标签中收集非原版的模组物品
            for (Item item : itemRegistry) {
                if (item.builtInRegistryHolder().is(tag) && !isVanillaItem(item)) {
                    modItems.add(item);
                }
            }
        } else if (slot == EquipmentSlot.MAINHAND) {
            // 主手：从剑和斧标签中收集模组武器
            for (Item item : itemRegistry) {
                boolean isWeapon = item.builtInRegistryHolder().is(ItemTags.SWORDS)
                    || item.builtInRegistryHolder().is(ItemTags.AXES);
                if (isWeapon && !isVanillaItem(item)) {
                    modItems.add(item);
                }
            }
        } else if (slot == EquipmentSlot.OFFHAND) {
            // 副手：优先找模组盾牌，找不到再找单手武器
            TagKey<Item> shieldTag = TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("shields"));
            for (Item item : itemRegistry) {
                if (item.builtInRegistryHolder().is(shieldTag) && !isVanillaItem(item)) {
                    modItems.add(item);
                }
            }
            // 没有模组盾牌时，退而求其次找单手武器
            if (modItems.isEmpty()) {
                for (Item item : itemRegistry) {
                    if (item.builtInRegistryHolder().is(ItemTags.SWORDS) && !isVanillaItem(item)) {
                        modItems.add(item);
                    }
                }
            }
        }

        if (modItems.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(modItems.get(random.nextInt(modItems.size())));
    }

    /**
     * 判断物品是否为原版物品
     * 通过检查注册命名空间来区分模组物品和原版物品
     *
     * @param item 检查的物品
     * @return 如果是原版物品返回true
     */
    private boolean isVanillaItem(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && id.getNamespace().equals("minecraft");
    }

    /**
     * 应用附魔到装备
     */
    private void applyEnchantments(ItemStack stack, EquipmentSlot slot, int maxLevel, ServerLevel serverLevel) {
        Registry<Enchantment> enchantmentRegistry = serverLevel.registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT);

        ResourceKey<Enchantment>[] possibleEnchantments = getEnchantmentsForSlot(slot);

        // 应用的附魔数量（基于难度）
        int enchantCount = Math.max(1, (int) Math.floor(maxLevel / 2.0));
        if (enchantCount > 3) enchantCount = 3; // 最多3个核心附魔

        for (int i = 0; i < enchantCount; i++) {
            ResourceKey<Enchantment> enchantKey = possibleEnchantments[random.nextInt(possibleEnchantments.length)];
            Holder.Reference<Enchantment> enchantHolder = enchantmentRegistry.getHolder(enchantKey).orElse(null);
            if (enchantHolder == null) continue;

            // 随机附魔等级（1到maxLevel之间）
            int level = random.nextInt(maxLevel) + 1;

            // 检查附魔是否兼容
            try {
                stack.enchant(enchantHolder, level);
            } catch (Exception e) {
                // 附冲突忽略
            }
        }

        // 额外尝试应用其他模组的兼容附魔
        tryApplyModCompatibleEnchantments(stack, enchantmentRegistry, maxLevel);
    }

    /**
     * 尝试应用其他模组添加的兼容附魔
     * 扫描附魔注册表中的所有附魔，找到与物品兼容且不冲突的附魔
     */
    private void tryApplyModCompatibleEnchantments(ItemStack stack, Registry<Enchantment> registry, int maxLevel) {
        // 收集已有附魔用于冲突检查
        Set<Enchantment> existingEnchants = new HashSet<>();
        for (Holder<Enchantment> holder : stack.getEnchantments().keySet()) {
            if (holder.isBound()) {
                existingEnchants.add(holder.value());
            }
        }

        // 找到所有兼容且不冲突的附魔
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment enchant : registry) {
            try {
                if (!enchant.canEnchant(stack) || existingEnchants.contains(enchant)) continue;
                candidates.add(enchant);
            } catch (Exception e) {
                // 跳过有问题的附魔
            }
        }

        if (candidates.isEmpty()) return;

        // 额外1-2个模组附魔
        int extraCount = Math.min(1 + random.nextInt(2), candidates.size());
        for (int i = 0; i < extraCount; i++) {
            int index = random.nextInt(candidates.size());
            Enchantment enchant = candidates.remove(index);
            registry.getResourceKey(enchant).ifPresent(key -> {
                registry.getHolder(key).ifPresent(holder -> {
                    int level = 1 + random.nextInt(Math.max(1, maxLevel / 2));
                    try {
                        stack.enchant(holder, level);
                    } catch (Exception ignored) {}
                });
            });
        }
    }

    /**
     * 获取指定装备槽位对应的附魔列表
     */
    @SuppressWarnings("unchecked")
    private ResourceKey<Enchantment>[] getEnchantmentsForSlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND, OFFHAND -> WEAPON_ENCHANTMENTS;
            case HEAD, CHEST, LEGS, FEET -> ARMOR_ENCHANTMENTS;
            default -> new ResourceKey[0];
        };
    }

    /**
     * 获取怪物类型
     */
    private String getMobType(Mob mob) {
        String name = mob.getType().getDescriptionId().toLowerCase();
        if (name.contains("zombie")) return "zombie";
        if (name.contains("skeleton")) return "skeleton";
        if (name.contains("spider")) return "spider";
        if (name.contains("creeper")) return "creeper";
        return "generic";
    }

    /**
     * 初始化
     */
    public void initialize() {
        AdaptiveNemesisMod.LOGGER.info("📦 怪物装备/附魔强化系统已初始化");
    }
}