package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EntityFilterHelper;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthData;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.registries.ForgeRegistries;

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

    /**
     * 模组装备与附魔候选缓存 - 在首次需要时构建，避免每次生成扫描整个注册表
     */
    private Map<EquipmentSlot, List<Item>> modEquipmentCache;
    private List<Item> modMainHandWeaponsCache;
    private List<Item> modShieldsCache;
    private List<Item> modOffHandWeaponsCache;
    private List<Enchantment> modEnchantmentCache;
    private boolean cachesBuilt = false;

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

    /**
     * 构建模组装备与附魔候选缓存
     * 仅在服务端首次需要时执行一次，避免每次实体生成都扫描整个注册表
     *
     * @param level 服务端世界，用于获取注册表访问器
     */
    private synchronized void buildCaches(ServerLevel level) {
        if (cachesBuilt) {
            return;
        }

        modEquipmentCache = new EnumMap<>(EquipmentSlot.class);
        modEquipmentCache.put(EquipmentSlot.HEAD, new ArrayList<>());
        modEquipmentCache.put(EquipmentSlot.CHEST, new ArrayList<>());
        modEquipmentCache.put(EquipmentSlot.LEGS, new ArrayList<>());
        modEquipmentCache.put(EquipmentSlot.FEET, new ArrayList<>());

        modMainHandWeaponsCache = new ArrayList<>();
        modShieldsCache = new ArrayList<>();
        modOffHandWeaponsCache = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS) {
            if (isVanillaItem(item) || !isValidEquipmentItem(item)) {
                continue;
            }

            // 1.20.1: classify via item classes (HEAD_ARMOR tags are 1.21+)
            if (item instanceof ArmorItem armor) {
                switch (armor.getType()) {
                    case HELMET -> modEquipmentCache.get(EquipmentSlot.HEAD).add(item);
                    case CHESTPLATE -> modEquipmentCache.get(EquipmentSlot.CHEST).add(item);
                    case LEGGINGS -> modEquipmentCache.get(EquipmentSlot.LEGS).add(item);
                    case BOOTS -> modEquipmentCache.get(EquipmentSlot.FEET).add(item);
                    default -> {}
                }
            }
            if (item instanceof SwordItem || item instanceof AxeItem) {
                modMainHandWeaponsCache.add(item);
                modOffHandWeaponsCache.add(item);
            }
            if (item instanceof ShieldItem || item == Items.SHIELD) {
                modShieldsCache.add(item);
            }
        }

        modEnchantmentCache = new ArrayList<>();
        for (Enchantment enchant : ForgeRegistries.ENCHANTMENTS) {
            if (enchant == null) continue;
            if (isDangerousEnchantment(enchant)) continue;
            modEnchantmentCache.add(enchant);
        }

        cachesBuilt = true;

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "🗂️ EnchantmentScalingHandler 缓存已构建: 护甲={}, 主手武器={}, 盾牌={}, 副手武器={}, 附魔={}",
                modEquipmentCache.values().stream().mapToInt(List::size).sum(),
                modMainHandWeaponsCache.size(),
                modShieldsCache.size(),
                modOffHandWeaponsCache.size(),
                modEnchantmentCache.size()
            );
        }
    }

    private static Enchantment[] WEAPON_ENCHANTMENTS_CACHE;
    private static Enchantment[] WEAPON_ENCHANTMENTS() {
        if (WEAPON_ENCHANTMENTS_CACHE == null) {
            WEAPON_ENCHANTMENTS_CACHE = new Enchantment[] {
        Enchantments.SHARPNESS,
        Enchantments.SMITE,
        Enchantments.BANE_OF_ARTHROPODS,
        Enchantments.FIRE_ASPECT,
        Enchantments.KNOCKBACK,
        Enchantments.MOB_LOOTING,
        Enchantments.SWEEPING_EDGE,
        Enchantments.UNBREAKING,
        Enchantments.BLOCK_EFFICIENCY
            };
        }
        return WEAPON_ENCHANTMENTS_CACHE;
    }

    private static Enchantment[] ARMOR_ENCHANTMENTS_CACHE;
    private static Enchantment[] ARMOR_ENCHANTMENTS() {
        if (ARMOR_ENCHANTMENTS_CACHE == null) {
            ARMOR_ENCHANTMENTS_CACHE = new Enchantment[] {
        Enchantments.ALL_DAMAGE_PROTECTION,
        Enchantments.FIRE_PROTECTION,
        Enchantments.BLAST_PROTECTION,
        Enchantments.PROJECTILE_PROTECTION,
        Enchantments.THORNS,
        Enchantments.UNBREAKING,
        Enchantments.RESPIRATION,
        Enchantments.AQUA_AFFINITY,
        Enchantments.FALL_PROTECTION,
        Enchantments.DEPTH_STRIDER
            };
        }
        return ARMOR_ENCHANTMENTS_CACHE;
    }

    private static Enchantment[] BOW_ENCHANTMENTS_CACHE;
    private static Enchantment[] BOW_ENCHANTMENTS() {
        if (BOW_ENCHANTMENTS_CACHE == null) {
            BOW_ENCHANTMENTS_CACHE = new Enchantment[] {
        Enchantments.POWER_ARROWS,
        Enchantments.PUNCH_ARROWS,
        Enchantments.FLAMING_ARROWS,
        Enchantments.INFINITY_ARROWS,
        Enchantments.UNBREAKING
            };
        }
        return BOW_ENCHANTMENTS_CACHE;
    }

    /**
     * 生成时无法被攻击的怪物NBT标签黑名单
     */
    private static final String[] INVULNERABLE_TAGS = {
        "Invulnerable",
        "NoAI",
        "PersistenceRequired"
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
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
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

        // 检查黑名单 - 被ban的实体跳过装备/附魔缩放
        if (EntityFilterHelper.getInstance().isBlocked(mob)) {
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
     * ⚠️ 注意：此方法在 MobSpawnEvent.FinalizeSpawn 中调用，那时实体所在区块可能尚未完全生成。
     * 使用 getEntitiesOfClass + AABB 会触发范围内其他区块的加载，与正在进行的世界生成形成死锁。
     * 改用玩家列表迭代 + 距离检查，不会触发新的区块加载。
     */
    private double getDifficultyMultiplier(Mob mob) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return 1.0;
        }

        // 获取附近玩家的强度（安全遍历玩家列表，不触发区块加载）
        double range = Config.AREA_SYNC_RANGE.get() * 16;
        double rangeSq = range * range;
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();

        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() == serverLevel && player.distanceToSqr(mob) <= rangeSq) {
                nearbyPlayers.add(player);
            }
        }

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
     *
     * @param mob 目标怪物
     * @param difficultyMultiplier 难度倍率
     * @param serverLevel 服务端世界
     */
    public void applyEquipmentScaling(Mob mob, double difficultyMultiplier, ServerLevel serverLevel) {
        float enchantChance = calculateEnchantChance(difficultyMultiplier);
        int enchantLevel = calculateEnchantLevel(difficultyMultiplier);

        // 判断该生物是否能使用装备（手持物品+盔甲）
        // 只有僵尸类、骷髅类这类有手的人形敌对生物才配发装备
        // 不然苦力怕、蜘蛛、史莱姆穿盔甲锄头太抽象了 😂
        boolean canEquipGear = isHumanoidMob(mob);

        // 遍历所有装备槽
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            boolean isArmor = slot.getType() == EquipmentSlot.Type.ARMOR;
            boolean isHand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
            if (!isArmor && !isHand) {
                continue;
            }

            // 非人形生物不生成任何装备（武器、盾牌、盔甲全跳过）
            if (!canEquipGear) {
                continue;
            }

            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) {
                // 有一定概率为怪物生成装备（如果原本没有）
                if (shouldGrantEquipment(difficultyMultiplier, slot)) {
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

        // 如果配置了禁用装备掉落，设置所有装备槽位的掉落概率为0
        if (Config.DISABLE_EQUIPMENT_DROP.get()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setDropChance(slot, 0.0F);
            }
        }

        // 装备生成后验证怪物状态
        validateMobState(mob);
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
    private boolean shouldGrantEquipment(double difficultyMultiplier, EquipmentSlot slot) {
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
        double damageCap = getDynamicDamageCap(difficultyMultiplier);

        ItemStack vanillaStack = switch (slot) {
            case MAINHAND -> createSafeWeapon(tier, damageCap);
            case OFFHAND -> random.nextBoolean() ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY;
            case HEAD -> new ItemStack(getArmorByTier()[tier][0]);
            case CHEST -> new ItemStack(getArmorByTier()[tier][1]);
            case LEGS -> new ItemStack(getArmorByTier()[tier][2]);
            case FEET -> new ItemStack(getArmorByTier()[tier][3]);
            default -> ItemStack.EMPTY;
        };

        // 尝试用其他模组的装备替换原版装备
        if (!vanillaStack.isEmpty()) {
            ItemStack modStack = tryGetModEquipment(serverLevel, slot, difficultyMultiplier, damageCap);
            if (!modStack.isEmpty()) {
                return modStack;
            }
        }

        return vanillaStack;
    }

    /**
     * 尝试从其他模组获取装备
     * 通过扫描物品标签注册表，找到其他模组添加的装备
     * 主手武器会检查动态伤害上限，超模武器被过滤掉
     *
     * @param level 服务端世界
     * @param slot 装备槽位
     * @param difficultyMultiplier 当前难度倍率
     * @param damageCap 动态武器伤害上限
     * @return 模组装备，如果没有合适的则返回空
     */
    private ItemStack tryGetModEquipment(ServerLevel level, EquipmentSlot slot, double difficultyMultiplier, double damageCap) {
        float modChance = Config.EQUIPMENT_MOD_COMPAT_CHANCE.get().floatValue();
        if (random.nextFloat() >= modChance) {
            return ItemStack.EMPTY;
        }

        buildCaches(level);

        List<Item> modItems = new ArrayList<>();

        if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
            // 护甲：直接使用预缓存的候选列表
            modItems.addAll(modEquipmentCache.getOrDefault(slot, List.of()));
        } else if (slot == EquipmentSlot.MAINHAND) {
            // 主手：从缓存的模组武器中按动态伤害上限过滤
            for (Item item : modMainHandWeaponsCache) {
                ItemStack testStack = new ItemStack(item);
                double weaponDamage = getWeaponDamage(testStack);
                if (weaponDamage <= damageCap) {
                    modItems.add(item);
                } else if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "⛔ 过滤超模武器: {} (伤害={}, 上限={})",
                        ForgeRegistries.ITEMS.getKey(item),
                        String.format("%.1f", weaponDamage),
                        String.format("%.1f", damageCap)
                    );
                }
            }
        } else if (slot == EquipmentSlot.OFFHAND) {
            // 副手：优先使用缓存的模组盾牌，没有则退而求其次使用单手武器
            modItems.addAll(modShieldsCache);
            if (modItems.isEmpty()) {
                modItems.addAll(modOffHandWeaponsCache);
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
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && id.getNamespace().equals("minecraft");
    }

    /**
     * 应用附魔到装备
     *
     * 先应用原版核心附魔，再尝试应用模组兼容附魔。
     *
     * @param stack 装备物品
     * @param slot 装备槽位
     * @param maxLevel 最高附魔等级
     * @param serverLevel 服务端世界
     */
    private void applyEnchantments(ItemStack stack, EquipmentSlot slot, int maxLevel, ServerLevel serverLevel) {
        applyCoreEnchantments(stack, slot, maxLevel, serverLevel);
        applyModCompatibleEnchantments(stack, serverLevel, maxLevel);
    }

    /**
     * 应用原版核心附魔
     *
     * @param stack 装备物品
     * @param slot 装备槽位
     * @param maxLevel 最高附魔等级
     * @param serverLevel 服务端世界
     */
    private void applyCoreEnchantments(ItemStack stack, EquipmentSlot slot, int maxLevel, ServerLevel serverLevel) {
        Enchantment[] possibleEnchantments = getEnchantmentsForSlot(slot);
        int enchantCount = calculateCoreEnchantmentCount(maxLevel);

        for (int i = 0; i < enchantCount; i++) {
            Enchantment enchant = possibleEnchantments[random.nextInt(possibleEnchantments.length)];
            if (enchant == null) continue;
            if (isDangerousEnchantment(enchant)) continue;

            int level = random.nextInt(maxLevel) + 1;
            try {
                if (enchant.canEnchant(stack)) {
                    stack.enchant(enchant, Math.min(level, enchant.getMaxLevel()));
                }
            } catch (Exception e) {
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "⛔ 核心附魔应用失败: {} (level={}), 原因: {}",
                        ForgeRegistries.ENCHANTMENTS.getKey(enchant), level, e.getMessage()
                    );
                }
            }
        }
    }

    /**
     * 计算核心附魔数量
     *
     * @param maxLevel 最高附魔等级
     * @return 核心附魔数量（1-3）
     */
    private int calculateCoreEnchantmentCount(int maxLevel) {
        int enchantCount = Math.max(1, (int) Math.floor(maxLevel / 2.0));
        return Math.min(enchantCount, 3);
    }

    /**
     * 应用模组兼容附魔
     *
     * 使用预缓存的模组附魔列表，避免每次生成扫描整个注册表。
     *
     * @param stack 装备物品
     * @param serverLevel 服务端世界
     * @param maxLevel 最高附魔等级
     */
    private void applyModCompatibleEnchantments(ItemStack stack, ServerLevel serverLevel, int maxLevel) {
        buildCaches(serverLevel);

        List<Enchantment> candidates = collectCompatibleEnchantments(stack);
        if (candidates.isEmpty()) {
            return;
        }

        // 额外1-2个模组附魔
        int extraCount = Math.min(1 + random.nextInt(2), candidates.size());
        for (int i = 0; i < extraCount; i++) {
            int index = random.nextInt(candidates.size());
            Enchantment holder = candidates.remove(index);
            int level = 1 + random.nextInt(Math.max(1, maxLevel / 2));
            try {
                stack.enchant(holder, Math.min(level, holder.getMaxLevel()));
            } catch (Exception e) {
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "⛔ 模组附魔应用失败: {} (level={}), 原因: {}",
                        ForgeRegistries.ENCHANTMENTS.getKey(holder), level, e.getMessage()
                    );
                }
            }
        }
    }

    /**
     * 收集与当前物品兼容且不冲突的模组附魔候选
     *
     * @param stack 装备物品
     * @return 可用附魔候选列表
     */
    private List<Enchantment> collectCompatibleEnchantments(ItemStack stack) {
        Set<Enchantment> existingEnchants = new HashSet<>(EnchantmentHelper.getEnchantments(stack).keySet());

        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment enchant : modEnchantmentCache) {
            try {
                if (!enchant.canEnchant(stack) || existingEnchants.contains(enchant)) {
                    continue;
                }
                candidates.add(enchant);
            } catch (Exception e) {
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "⛔ 模组附魔候选检查失败: {}, 原因: {}",
                        ForgeRegistries.ENCHANTMENTS.getKey(enchant), e.getMessage()
                    );
                }
            }
        }
        return candidates;
    }

    /**
     * 获取指定装备槽位对应的附魔列表
     */
    private Enchantment[] getEnchantmentsForSlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND, OFFHAND -> WEAPON_ENCHANTMENTS();
            case HEAD, CHEST, LEGS, FEET -> ARMOR_ENCHANTMENTS();
            default -> new Enchantment[0];
        };
    }

    /**
     * 获取怪物类型（用于日志输出）
     */
    private String getMobType(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return id != null ? id.toString() : "unknown";
    }

    /**
     * 判断生物是否为人形生物（有手能拿武器/穿盔甲）
     * 
     * 精准判定规则：
     * - 僵尸系：僵尸、尸壳、溺尸、僵尸村民、僵尸猪灵 ✅
     * - 骷髅系：骷髅、流浪者、凋零骷髅、沼骸 ✅
     * - 灾厄村民（有手的）：卫道士、掠夺者、唤魔者、幻术师 ✅
     * - 猪灵系：猪灵、猪灵蛮兵 ✅
     * - 其他非人形生物（蜘蛛、苦力怕、女巫、末影人、幻翼等）：❌
     *
     * @param mob 目标生物
     * @return 如果为人形生物返回 true
     */
    private boolean isHumanoidMob(Mob mob) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (entityId == null) return false;
        String id = entityId.toString();

        // 僵尸及其变种 - 经典持械单位 💀
        if (id.equals("minecraft:zombie") || id.equals("minecraft:husk") ||
            id.equals("minecraft:drowned") || id.equals("minecraft:zombie_villager") ||
            id.equals("minecraft:zombified_piglin")) {
            return true;
        }

        // 骷髅及其变种 - 弓箭手军团 🏹
        if (id.equals("minecraft:skeleton") || id.equals("minecraft:stray") ||
            id.equals("minecraft:wither_skeleton") || id.equals("minecraft:bogged")) {
            return true;
        }

        // 有手的灾厄村民 - 一条区的武装分子 ⚔️
        if (id.equals("minecraft:vindicator") || id.equals("minecraft:pillager") ||
            id.equals("minecraft:evoker") || id.equals("minecraft:illusioner")) {
            return true;
        }

        // 猪灵系 - 下界武斗派 🔥
        if (id.equals("minecraft:piglin") || id.equals("minecraft:piglin_brute")) {
            return true;
        }

        // 其他统统不发装备，你们不配 🤷
        return false;
    }

    /**
     * 初始化
     */
    public void initialize() {
        AdaptiveNemesisMod.LOGGER.info("📦 怪物装备/附魔强化系统已初始化");
    }

    /**
     * 判断给定文本是否含有危险附魔关键词
     * 静态方法，用于不依赖 Minecraft 运行时的单元测试
     *
     * @param text 待检测的文本（如注册名路径、描述文本）
     * @return 如果包含危险关键词返回true
     */
    static boolean isDangerousEnchantmentKey(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        return lower.contains("immune") || lower.contains("immunity")
            || lower.contains("invulnerable") || lower.contains("invincible")
            || lower.contains("no_damage") || lower.contains("damage_immunity")
            || lower.contains("免伤") || lower.contains("无敌")
            || lower.contains("免疫") || lower.contains("damage_proof")
            || lower.contains("god") || lower.contains("divine_protection");
    }

    /**
     * 判断附魔是否为危险附魔（如伤害免疫、无敌等游戏破坏性效果）
     * 此类附魔如果被怪物装备上，会导致怪物无法被攻击
     *
     * @param holder 附魔持有者引用
     * @return 如果是危险附魔返回true，应跳过
     */
    private boolean isDangerousEnchantment(Enchantment enchant) {
        if (enchant == null) return false;

        ResourceLocation enchantId = ForgeRegistries.ENCHANTMENTS.getKey(enchant);
        if (enchantId == null) return false;
        String path = enchantId.getPath().toLowerCase();

        if (isDangerousEnchantmentKey(path)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "⚠️ 跳过危险附魔: {} (来源模组: {})",
                    enchantId, enchantId.getNamespace()
                );
            }
            return true;
        }

        // Description-key fallback
        try {
            String descKey = enchant.getDescriptionId().toLowerCase();
            if (isDangerousEnchantmentKey(descKey)) {
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * 验证模组装备物品的有效性
     * 防止模组中某些被错误打上装备标签的问题物品导致怪物无法被攻击
     *
     * @param item 待验证的物品
     * @return 如果物品安全有效返回true
     */
    private boolean isValidEquipmentItem(Item item) {
        if (item == null || item == Items.AIR) {
            return false;
        }

        // 跳过已知可能产生副作用的特殊物品
        ItemStack testStack = new ItemStack(item);

        // 跳过没有最大堆叠数的特殊物品（通常是不可常规获得的物品）
        if (testStack.getMaxStackSize() <= 0) {
            return false;
        }

        // 跳过屏障、结构空位等调试物品
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null) {
            String path = id.getPath().toLowerCase();
            if (path.contains("barrier") || path.contains("structure_void")
                || path.contains("debug") || path.contains("command")
                || path.contains("spawn_egg") || path.contains("monster_egg")) {
                return false;
            }
        }

        return true;
    }

    /**
     * 怪物生成后状态验证
     * 检查怪物是否处于可被攻击的正常状态，修复异常状态
     *
     * @param mob 待验证的怪物
     */
    private void validateMobState(Mob mob) {
        // 检查并清除实体真正的无敌标志（isInvulnerable/setInvulnerable 操作 Entity.invulnerable 字段）
        // ⚠️ 注意：getPersistentData().getBoolean("Invulnerable") 是模组自定义数据，和实体无敌无关！
        if (mob.isInvulnerable()) {
            mob.setInvulnerable(false);
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "🔧 修复怪物 {} 的 Invulnerable 标志（由装备生成导致）",
                    mob.getName().getString()
                );
            }
        }

        // 检查血量是否有效
        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double health = healthAttr.getBaseValue();
            if (Double.isNaN(health) || Double.isInfinite(health)) {
                healthAttr.setBaseValue(20.0);
            } else if (health < 1.0) {
                healthAttr.setBaseValue(Math.max(20.0, health));
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.warn(
                        "🔧 修复怪物 {} 的无效 MaxHealth: {}",
                        mob.getName().getString(), health
                    );
                }
            }
        }

        // 确保怪物当前血量有效
        float currentHealth = mob.getHealth();
        if (Double.isNaN(currentHealth) || Double.isInfinite(currentHealth) || currentHealth <= 0) {
            mob.setHealth(mob.getMaxHealth());
        }
    }

    // ==================== 武器动态伤害上限 ====================

    /**
     * 获取武器的攻击伤害值
     * 通过物品的 ATTACK_DAMAGE 属性修饰器计算实际伤害
     *
     * @param stack 武器物品
     * @return 武器总伤害（含基础空手伤害1.0）
     */
    private double getWeaponDamage(ItemStack stack) {
        var modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
        double total = 1.0; // bare-hand base
        if (modifiers != null) {
            for (AttributeModifier mod : modifiers) {
                if (mod.getOperation() == AttributeModifier.Operation.ADDITION) {
                    total += mod.getAmount();
                }
            }
        }
        return total;
    }

    /**
     * 计算动态武器伤害上限
     * 基于当前难度倍率，玩家越强则上限越高
     *
     * @param difficultyMultiplier 当前难度倍率
     * @return 允许的最大武器伤害值
     */
    private double getDynamicDamageCap(double difficultyMultiplier) {
        double baseCap = Config.WEAPON_DAMAGE_BASE_CAP.get();
        double perDifficulty = Config.WEAPON_DAMAGE_CAP_PER_DIFFICULTY.get();
        double maxCap = Config.WEAPON_DAMAGE_MAX_CAP.get();

        // 动态上限 = 基础值 + (倍率-1) * 每倍率增量，不超过绝对上限
        double cap = baseCap + (difficultyMultiplier - 1.0) * perDifficulty;
        return Math.min(cap, maxCap);
    }

    /**
     * 根据动态伤害上限创建安全的武器
     * 从当前品质等级向下尝试，直到找到伤害不超过上限的武器
     * 如果全不行就掏木剑保底 🤷
     *
     * @param tier 期望的品质等级
     * @param damageCap 动态伤害上限
     * @return 符合伤害上限的武器
     */
    private ItemStack createSafeWeapon(int tier, double damageCap) {
        for (int t = tier; t >= 0; t--) {
            Item[] weapons = getWeaponsByTier()[t];
            // 随机打乱武器顺序，避免每次都选同一把
            List<Item> shuffled = new ArrayList<>(java.util.Arrays.asList(weapons));
            java.util.Collections.shuffle(shuffled, random);
            for (Item weapon : shuffled) {
                ItemStack stack = new ItemStack(weapon);
                if (getWeaponDamage(stack) <= damageCap) {
                    return stack;
                }
            }
        }
        // 保底：木剑（1+4=5点伤害，不会超过任何合理上限）
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.warn(
                "⚠️ 所有品质等级的武器均超过伤害上限，使用木剑保底 (cap={})",
                String.format("%.1f", damageCap)
            );
        }
        return new ItemStack(Items.WOODEN_SWORD);
    }
}