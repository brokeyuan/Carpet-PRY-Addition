package me.primaryuan.carpet.handler.redPacket;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    /** 已领取的玩家 */
    public final Set<UUID> claimed = new HashSet<>();
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

    /** 未领取份额的全部物品（过期退回用） */
    public List<ItemStack> unclaimedItems() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = claimed.size(); i < shares.size(); i++) {
            out.addAll(shares.get(i));
        }
        return out;
    }
}
