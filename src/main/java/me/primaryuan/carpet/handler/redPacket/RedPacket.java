package me.primaryuan.carpet.handler.redPacket;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一个红包实例：份额在发出时按类型一次性切好（{@link RedPacketSplitter}），
 * 领取即取走一整份；过期把未领取份额原样退回发送者。
 */
public final class RedPacket {

    public enum Type {
        /** 拼手气：所有物品随机分配，总量守恒 */
        LUCKY,
        /** 普通：种类与数量平分，余数随机落份 */
        NORMAL,
        /** 专属：只有目标玩家能领，物品整体给出，恒 1 份 */
        TARGETED,
        /** 口令：聊天框打出正确口令领取，分配同拼手气 */
        PASSWORD
    }

    public final int id;
    public final UUID senderId;
    public final String senderName;
    public final Type type;
    public final String message;
    /** 每份物品（份额在发出时切好） */
    public final List<List<ItemStack>> shares;
    /** 专属红包目标；其余类型为 null */
    public final UUID target;
    public final String targetName;
    /** 口令；发出时为 null（铁砧确认后才写入并广播） */
    public String password;
    /** 过期时刻（服务器 tick） */
    public final long expireTick;
    /**
     * 已领取的玩家（插入序 = 领取顺序，与份额下标一一对应）——
     * LinkedHashSet 保证手气王"并列取先领取"与领取明细的顺序稳定
     */
    public final Set<UUID> claimed = new LinkedHashSet<>();
    /** 已领取玩家名（uuid → 名，领取时记录，结算明细用——领取者离线后名字仍可查） */
    public final Map<UUID, String> claimerNames = new LinkedHashMap<>();
    /** 过期标记：仅保留用于"已过期"提示，不再可领 */
    public boolean expired;
    /** 领完标记：保留一段时间供"已被领完"提示，清理同过期 */
    public boolean done;

    public RedPacket(int id, UUID senderId, String senderName, Type type, String message,
                     List<List<ItemStack>> shares, UUID target, String targetName, long expireTick) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.type = type;
        this.message = message;
        this.shares = shares;
        this.target = target;
        this.targetName = targetName;
        this.expireTick = expireTick;
    }

    /** 剩余可领份数 */
    public int sharesLeft() {
        return shares.size() - claimed.size();
    }

    /** 领取一份（调用方已通过全部校验），返回该份物品 */
    public List<ItemStack> takeShare(UUID player) {
        // 已领份数即前 sharesLeft 份的下标游标：按剩余份额顺序取，避免遍历匹配
        int index = claimed.size();
        claimed.add(player);
        List<ItemStack> share = shares.get(index);
        List<ItemStack> copy = new ArrayList<>(share.size());
        for (ItemStack stack : share) {
            copy.add(stack.copyWithCount(stack.getCount()));
        }
        return copy;
    }

    /** 过期前提醒标记（30 秒一次，只发一条） */
    public boolean warned;

    /**
     * 手气王份额下标：按价值函数加总最大者（并列取先领取），无人领取或全员零价值返回 -1。
     * 仅随机切分类型（拼手气/口令）有"手气"语义；普通为平均分配、专属只有一人。
     * 价值函数由调用方注入（管理器传价值表，单测传 lambda）。
     */
    public int luckKingIndex(java.util.function.ToLongFunction<ItemStack> valueFn) {
        int best = -1;
        long bestSum = 0;
        int index = 0;
        for (UUID ignored : claimed) {
            long sum = 0;
            for (ItemStack stack : shares.get(index)) {
                sum += valueFn.applyAsLong(stack);
            }
            if (sum > bestSum) {
                bestSum = sum;
                best = index;
            }
            index++;
        }
        return best;
    }

    /** 按领取顺序的第 index 个领取者（takeShare 的游标语义：插入序 = 份额下标） */
    public UUID claimerAt(int index) {
        int i = 0;
        for (UUID uuid : claimed) {
            if (i++ == index) {
                return uuid;
            }
        }
        return null;
    }

    /**
     * 红包是否只含单种物品——手气王结算的前提：
     * 混合物品（钻石+泥土混发）的份额组合没有公平的"手气"可比性，不评王。
     */
    public boolean hasSingleItemType() {
        String only = null;
        for (List<ItemStack> share : shares) {
            for (ItemStack stack : share) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString();
                if (only == null) {
                    only = id;
                } else if (!only.equals(id)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 未领取份额的全部物品（过期退回用） */
    public List<ItemStack> unclaimedItems() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = claimed.size(); i < shares.size(); i++) {
            out.addAll(shares.get(i));
        }
        return out;
    }
}
