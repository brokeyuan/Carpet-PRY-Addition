package me.primaryuan.carpet.handler.playerHead;

/**
 * 通用头颅铁砧换肤的纯状态机：每次改名递增修订号，只有同一修订号的查询结果
 * 才能把会话推进到 READY；更名、超时或失败后迟到结果一律丢弃。
 */
public final class HeadRenameSession {

    public enum State { DEBOUNCING, QUERYING, READY, FAILED }

    private String name;
    private int revision;
    private State state = State.DEBOUNCING;
    private HeadSkin skin;

    /** 记录一次新的输入名称，旧修订立即失效。 */
    void revise(String name) {
        this.name = name;
        this.revision++;
        this.state = State.DEBOUNCING;
        this.skin = null;
    }

    /** 名称就是已有皮肤来源时直接复用快照，不发起查询。 */
    void reuse(HeadSkin skin) {
        this.skin = skin;
        this.state = State.READY;
    }

    void beginQuery() {
        if (state == State.DEBOUNCING) state = State.QUERYING;
    }

    /** @return 结果是否属于当前修订并已被接受 */
    boolean complete(int revision, HeadSkin skin) {
        if (state != State.QUERYING || this.revision != revision) return false;
        if (skin == null || !skin.hasTexture()) return false;
        this.skin = skin;
        this.state = State.READY;
        return true;
    }

    /** @return 这次失败是否属于当前修订（用于只提示一次） */
    boolean fail(int revision) {
        if (state != State.QUERYING && state != State.DEBOUNCING) return false;
        if (this.revision != revision) return false;
        this.state = State.FAILED;
        return true;
    }

    String name() {
        return name;
    }

    int revision() {
        return revision;
    }

    State state() {
        return state;
    }

    HeadSkin skin() {
        return skin;
    }

    boolean ready() {
        return state == State.READY && skin != null;
    }

    /** 未就绪时一律禁止领取，避免拿到没有皮肤的中间结果。 */
    boolean blocksPickup() {
        return !ready();
    }
}
