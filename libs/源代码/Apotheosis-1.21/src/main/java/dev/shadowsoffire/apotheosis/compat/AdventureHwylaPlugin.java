package dev.shadowsoffire.apotheosis.compat;

import com.google.common.base.Predicates;

import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.util.CommonTooltipUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public class AdventureHwylaPlugin implements IWailaPlugin, IEntityComponentProvider, IServerDataProvider<EntityAccessor> {

    @Override
    public void register(IWailaCommonRegistration reg) {
        reg.registerEntityDataProvider(this, LivingEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration reg) {
        reg.registerEntityComponent(this, Entity.class);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (accessor.getEntity() instanceof LivingEntity living && accessor.getServerData().getBoolean(Invader.BOSS_KEY)) {
            ListTag bossAttribs = accessor.getServerData().getList("apoth.modifiers", Tag.TAG_COMPOUND);
            AttributeMap map = living.getAttributes();
            for (Tag t : bossAttribs) {
                CompoundTag tag = (CompoundTag) t;
                Holder<Attribute> attrib = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.tryParse(tag.getString("id"))).get();
                map.getInstance(attrib).load(tag);
            }
            accessor.getServerData().remove("apoth.modifiers");
            living.getPersistentData().merge(accessor.getServerData());
            CommonTooltipUtil.appendBossData(living.level(), living, tooltip::add);
        }
    }

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor access) {
        if (access.getEntity() instanceof LivingEntity living && living.getPersistentData().getBoolean(Invader.BOSS_KEY)) {
            tag.putBoolean(Invader.BOSS_KEY, true);
            tag.putString(Invader.RARITY_KEY, living.getPersistentData().getString(Invader.RARITY_KEY));
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                AttributeMap map = living.getAttributes();
                ListTag bossAttribs = new ListTag();
                BuiltInRegistries.ATTRIBUTE.holders().map(map::getInstance).filter(Predicates.notNull()).forEach(inst -> {
                    for (AttributeModifier modif : inst.getModifiers()) {
                        if (modif.id().getPath().startsWith(Invader.INVADER_ATTR_PREFIX)) {
                            bossAttribs.add(inst.save());
                            break;
                        }
                    }
                });
                tag.put("apoth.modifiers", bossAttribs);
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return Apotheosis.loc("adventure");
    }

}
