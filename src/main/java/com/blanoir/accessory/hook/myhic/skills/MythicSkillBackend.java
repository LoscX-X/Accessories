package com.blanoir.accessory.hook.myhic.skills;

import com.blanoir.accessory.module.skill.*;
import com.blanoir.accessory.module.skill.trigger.SkillTrigger;
import io.lumine.mythic.bukkit.*;
import org.bukkit.entity.*;

/** The only skill executor that links MythicMobs classes. */
public final class MythicSkillBackend implements SkillBackend {
    public static final String ACCESSORY_CAST_META = "blacc:accessory-cast";
    @Override public boolean cast(Player owner, SkillDefinition skill, Entity target, Entity triggerEntity) {
        return MythicBukkit.inst().getAPIHelper().castSkill(owner, skill.skill(), metadata -> {
            metadata.setMetadata(ACCESSORY_CAST_META, true);
            if (target != null) metadata.setEntityTarget(BukkitAdapter.adapt(target));
            Entity trigger = triggerEntity == null ? target : triggerEntity;
            if (trigger != null) metadata.setTrigger(BukkitAdapter.adapt(trigger));
            if (skill.trigger() == SkillTrigger.ON_DEATH) {
                metadata.setExecuteAfterDeath(true);
                if (skill.forceSync()) metadata.setIsAsync(false);
            }
        });
    }
}
