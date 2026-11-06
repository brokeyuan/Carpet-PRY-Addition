package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerPanicGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomLookAroundGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import me.primaryuan.carpet.brain.goal.PlayerTemptGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/**
 * 猪模式脑（装配器）：完全中立，被打就跑，闲时散步，见食物靠近。
 *
 * <p>装配按 26.3/1.21.11 字节码（{@code Pig.registerGoals}）：恐慌（pri 1，
 * {@code PanicGoal(1.25)} 默认 PANIC_CAUSES——含玩家/生物攻击与投射物）→
 * 引诱×2（pri 4，{@code TemptGoal(1.2, #pig_food, canScare=false)} 与
 * {@code TemptGoal(1.2, carrot_on_a_stick, canScare=false)} 同位双 Goal，
 * 猪可被胡萝卜钓竿引诱；canScare=false 不受玩家动作吓跑）→ 漫游（pri 6，1.0）
 * → 看人（pri 7，6 格）/ 张望（pri 8）。无攻击目标（targetSelector 空置，
 * 永不主动索敌）。不做：繁殖/跟随父母（Breed/FollowParent，食物与繁殖系统）。</p>
 */
public class PigBrain extends PlayerBrainController {

    public PigBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "pig";
    }

    @Override
    protected void assemble() {
        // 被打/着火 → 恐慌狂奔（原版 PanicGoal(1.25) pri 1，默认 PANIC_CAUSES）
        this.goalSelector.addGoal(1, new PlayerPanicGoal(this.prowler, 1.25));
        // 见手持 #pig_food（胡萝卜/土豆/甜菜根等）的玩家 → 引诱跟随（原版 pri 4）
        this.goalSelector.addGoal(4, new PlayerTemptGoal(this.prowler, ItemTags.PIG_FOOD, 1.2));
        // 见手持胡萝卜钓竿的玩家 → 同位第二条引诱（原版 pri 4 同位双 Goal）
        this.goalSelector.addGoal(4, new PlayerTemptGoal(this.prowler, Items.CARROT_ON_A_STICK, 1.2));
        // 无事 → 随机散步（原版 WaterAvoidingRandomStrollGoal pri 6，1.0）
        this.goalSelector.addGoal(6, new PlayerRandomStrollGoal(this.prowler, 1.0));
        // 空闲看人/随机张望（原版 pri 7/8，看人 6 格）
        this.goalSelector.addGoal(7, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new PlayerRandomLookAroundGoal(this.prowler));
    }
}
