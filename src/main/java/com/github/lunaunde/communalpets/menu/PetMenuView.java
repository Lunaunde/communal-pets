package com.github.lunaunde.communalpets.menu;

/**
 * 七个假箱子界面（设计文档里的表0 ~ 表4）。
 * <p>
 * 行数是构造 {@link net.minecraft.world.inventory.ChestMenu} 时定死的，所以 3 行和 6 行之间切换
 * 必须换一个菜单实例（换 syncId 重开）——{@link #rows()} 就是给那个判断用的。
 */
public enum PetMenuView {

    /** 表0：非抚养者。 */
    OUTSIDER(3),
    /** 表0.1：收到邀请的非抚养者。 */
    INVITED(3),
    /** 表1：抚养者（行为 / 游荡半径 / 抚养者一览）。 */
    MEMBER(3),
    /** 表2：抚养者管理（左：已在抚养者中；右：可邀请的在线玩家）。 */
    MANAGE(6),
    /** 表3：确认踢出抚养者。 */
    CONFIRM_KICK(3),
    /** 表3.1：确认换主人。 */
    CONFIRM_TRANSFER(3),
    /** 表4：申请管理（主人审批）。 */
    APPLICATIONS(3);

    private final int rows;

    PetMenuView(int rows) {
        this.rows = rows;
    }

    /** 箱子行数（每行 9 格）。 */
    public int rows() {
        return rows;
    }

    /** 面板格子总数。 */
    public int size() {
        return rows * 9;
    }
}
