package com.github.lunaunde.communalpets.menu;

import com.github.lunaunde.communalpets.Messages;
import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.github.lunaunde.communalpets.world.entity.animal.PendingRequest;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 纯服务端的"假箱子"界面：服务端把物品塞进一个 {@link SimpleContainer}，客户端看到的还是原版箱子。
 * <p>
 * 关键约束与做法：
 * <ul>
 *     <li><b>客户端会预测搬运</b>：原版客户端收到 {@code GENERIC_9x3/9x6} 后本地建的是普通箱子菜单，
 *         点击时本地会先把物品"拿起来"。所以 {@link #clicked} 拦下所有面板格子的点击、<b>不</b>调用
 *         {@code super}，再 {@link #broadcastFullState()} 把面板真身刷回去；</li>
 *     <li><b>面板格子当按钮</b>：每个格子对应一个 {@link SlotAction}，没有动作的就是纯展示；</li>
 *     <li><b>玩家背包照常</b>：{@link #quickMoveStack} 返回空，Shift 点击不会把东西塞进面板；</li>
 *     <li><b>换行数要重开</b>：3 行和 6 行的菜单实例不能复用（syncId 对应一个尺寸），见
 *         {@link #switchView(PetMenuView)}。</li>
 * </ul>
 * 权限（设计文档）：照顾者<b>没有</b>管理照顾者的权力 —— 表2 / 表3 / 表4 只对主人和 op 开放，
 * 表1（行为 / 游荡半径）对全体抚养者开放。
 */
public class PetOwnerMenu extends ChestMenu {

    /** 表2 左右面板都是 4 列 × 6 行，其中最后一行留给页码 → 每页 20 个头。 */
    private static final int PANEL_COLUMNS = 4;
    private static final int PANEL_PAGE_SIZE = 20;

    /** 表4：27 格减去右下角页码 → 每页 26 条申请。 */
    private static final int APPLICATION_PAGE_SIZE = 26;

    /** 表1 第 3 行：1 个主人头颅 + 最多 7 个照顾者头颅 + 最后 1 格纸张。 */
    private static final int MEMBER_ROW_CARETAKERS = 7;

    /** 离宠物超过 8 格自动关界面（和原版容器的距离手感一致）。 */
    private static final double MAX_DISTANCE_SQR = 64.0;

    private final TamableAnimal pet;
    private final ServerPlayer player;
    private final Container board;
    private final Map<Integer, SlotAction> actions = new HashMap<>();

    private PetMenuView view;
    private int memberPage;
    private int outsiderPage;
    private int applicationPage;
    private @Nullable UUID confirmTarget;

    @FunctionalInterface
    private interface SlotAction {
        /** @param mouseButton 0 = 左键，1 = 右键 */
        void run(int mouseButton);
    }

    PetOwnerMenu(int syncId, Inventory inventory, ServerPlayer player, TamableAnimal pet,
                 PetMenuView view, int memberPage, int outsiderPage, int applicationPage,
                 @Nullable UUID confirmTarget) {
        super(view.rows() == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3,
                syncId, inventory, new SimpleContainer(view.size()), view.rows());
        this.player = player;
        this.pet = pet;
        this.board = this.getContainer();
        this.view = this.sanitize(view);
        this.memberPage = Math.max(0, memberPage);
        this.outsiderPage = Math.max(0, outsiderPage);
        this.applicationPage = Math.max(0, applicationPage);
        this.confirmTarget = confirmTarget;
        this.refresh();
    }

    // ------------------------------------------------------------------
    // 盒子最基础的三件事：能不能用、别让人搬东西、点击怎么解释
    // ------------------------------------------------------------------

    @Override
    public boolean stillValid(Player p) {
        return this.pet.isAlive()
                && this.pet.isTame()
                && p.level() == this.pet.level()
                && p.distanceToSqr(this.pet) <= MAX_DISTANCE_SQR;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        if (slotId >= 0 && slotId < this.board.getContainerSize()) {
            SlotAction action = this.actions.get(slotId);
            if (action != null) {
                action.run(button);
            }
            // 客户端预测出来的"拿起物品"会留在它本地的手上，这里把面板真身刷回去。
            // 动作里可能已经把界面换掉 / 关掉了，所以要确认当前菜单还是自己。
            if (this.player.containerMenu == this) {
                this.broadcastFullState();
            }
            return;
        }
        // 玩家自己的背包格子照常走原版逻辑
        super.clicked(slotId, button, input, clicker);
    }

    // ------------------------------------------------------------------
    // 画界面
    // ------------------------------------------------------------------

    private void refresh() {
        this.actions.clear();
        for (int i = 0; i < this.board.getContainerSize(); i++) {
            this.board.setItem(i, ItemStack.EMPTY);
        }
        switch (this.view) {
            case OUTSIDER -> this.renderOutsider();
            case INVITED -> this.renderInvited();
            case MEMBER -> this.renderMember();
            case MANAGE -> this.renderManage();
            case CONFIRM_KICK, CONFIRM_TRANSFER -> this.renderConfirm();
            case APPLICATIONS -> this.renderApplications();
        }
    }

    private void put(int slot, ItemStack stack, @Nullable SlotAction action) {
        this.board.setItem(slot, stack);
        if (action != null) {
            this.actions.put(slot, action);
        }
    }

    private void renderOutsider() {
        this.put(4, PetMenuIcons.petIcon(this.pet, this.ownerLine()), null);
        this.put(13, PetMenuIcons.applyFeather(), button -> this.applyAsCaretaker());
        if (this.isGameMaster()) {
            // 设计文档：命令方块只对 op 显示，点它进表1（所以 op 蹲下右键先看到的是表0）
            this.put(22, PetMenuIcons.adminBlock(), button -> this.switchView(PetMenuView.MEMBER));
        }
    }

    private void renderInvited() {
        this.put(4, PetMenuIcons.petIcon(this.pet, this.ownerLine()), null);
        this.put(12, PetMenuIcons.acceptPane(), button -> this.answerInvitation(true));
        this.put(14, PetMenuIcons.rejectPane(), button -> this.answerInvitation(false));
        if (this.isGameMaster()) {
            this.put(22, PetMenuIcons.adminBlock(), button -> this.switchView(PetMenuView.MEMBER));
        }
    }

    private void renderMember() {
        this.put(0, PetMenuIcons.petIcon(this.pet, this.ownerLine()), null);

        int state = CommunalPet.getBehaviorState(this.pet);
        this.put(1, this.behaviorButton(state, CommunalPet.BEHAVIOR_FOLLOW_COMMANDER, DyeColor.LIME),
                button -> this.clickBehavior(CommunalPet.BEHAVIOR_FOLLOW_COMMANDER));
        this.put(2, this.behaviorButton(state, CommunalPet.BEHAVIOR_FOLLOW_NEAREST, DyeColor.LIGHT_BLUE),
                button -> this.clickBehavior(CommunalPet.BEHAVIOR_FOLLOW_NEAREST));
        this.put(3, this.behaviorButton(state, CommunalPet.BEHAVIOR_WANDER, DyeColor.ORANGE),
                button -> this.clickBehavior(CommunalPet.BEHAVIOR_WANDER));
        this.put(4, this.behaviorButton(state, CommunalPet.BEHAVIOR_SIT, DyeColor.RED),
                button -> this.clickBehavior(CommunalPet.BEHAVIOR_SIT));

        this.put(9, PetMenuIcons.radiusNameTag(CommunalPet.getWanderRadius(this.pet)), null);
        this.put(10, PetMenuIcons.radiusNugget(Items.COPPER_NUGGET, 1), button -> this.adjustRadius(button, 1));
        this.put(11, PetMenuIcons.radiusNugget(Items.IRON_NUGGET, 4), button -> this.adjustRadius(button, 4));
        this.put(12, PetMenuIcons.radiusNugget(Items.GOLD_NUGGET, 16), button -> this.adjustRadius(button, 16));

        this.put(18, this.ownerHead(), null);
        List<UUID> caretakers = CommunalPet.getCaretakerUUIDs(this.pet);
        for (int i = 0; i < caretakers.size() && i < MEMBER_ROW_CARETAKERS; i++) {
            this.put(19 + i, this.head(caretakers.get(i), "communal-pets.menu.head.caretaker", List.of()), null);
        }

        if (this.canManage()) {
            boolean pending = !CommunalPet.getApplications(this.pet).isEmpty();
            this.put(26, PetMenuIcons.paper(pending), button -> this.switchView(
                    button == 1 ? PetMenuView.APPLICATIONS : PetMenuView.MANAGE));
        }
    }

    private void renderManage() {
        for (int row = 0; row < 6; row++) {
            this.put(row * 9 + 4, PetMenuIcons.divider(), null);
        }

        // 左面板：主人永远在第一格，其余是照顾者
        UUID ownerId = CommunalPet.ownerIdOf(this.pet);
        List<UUID> left = new ArrayList<>();
        if (ownerId != null) {
            left.add(ownerId);
        }
        for (UUID id : CommunalPet.getCaretakerUUIDs(this.pet)) {
            if (!id.equals(ownerId)) {
                left.add(id);
            }
        }
        int leftPages = pageCount(left.size(), PANEL_PAGE_SIZE);
        this.memberPage = Math.min(this.memberPage, leftPages - 1);
        int leftFrom = this.memberPage * PANEL_PAGE_SIZE;
        for (int i = 0; i < PANEL_PAGE_SIZE && leftFrom + i < left.size(); i++) {
            UUID id = left.get(leftFrom + i);
            int slot = (i / PANEL_COLUMNS) * 9 + (i % PANEL_COLUMNS);
            if (id.equals(ownerId)) {
                // 设计文档：主人那一格无 Lore、不可操作
                this.put(slot, this.ownerHead(), null);
            } else {
                this.put(slot, this.head(id, "communal-pets.menu.head.caretaker",
                                List.of(hint("communal-pets.menu.head.caretaker.lore"))),
                        button -> {
                            if (button == 1) {
                                this.askConfirm(PetMenuView.CONFIRM_TRANSFER, id);
                            } else {
                                this.askConfirm(PetMenuView.CONFIRM_KICK, id);
                            }
                        });
            }
        }
        this.put(45, PetMenuIcons.pageNameTag(this.memberPage + 1, leftPages, "communal-pets.menu.page.caretakers"),
                button -> this.turnPage(button, true));

        // 右面板：当前不在抚养者里的在线玩家（设计文档：右侧只列在线玩家，离线走指令邀请）
        List<UUID> right = new ArrayList<>();
        for (ServerPlayer online : this.server().getPlayerList().getPlayers()) {
            if (!left.contains(online.getUUID())) {
                right.add(online.getUUID());
            }
        }
        right.sort(Comparator.comparing(id -> PetMenus.nameOf(this.server(), id).getString()));
        int rightPages = pageCount(right.size(), PANEL_PAGE_SIZE);
        this.outsiderPage = Math.min(this.outsiderPage, rightPages - 1);
        int rightFrom = this.outsiderPage * PANEL_PAGE_SIZE;
        for (int i = 0; i < PANEL_PAGE_SIZE && rightFrom + i < right.size(); i++) {
            UUID id = right.get(rightFrom + i);
            int slot = (i / PANEL_COLUMNS) * 9 + 5 + (i % PANEL_COLUMNS);
            boolean invited = CommunalPet.hasInvitation(this.pet, id);
            List<Component> lore = List.of(invited
                    ? PetMenuIcons.note("communal-pets.menu.head.invited.lore")
                    : hint("communal-pets.menu.head.invite.lore"));
            this.put(slot, this.head(id, "communal-pets.menu.head.invite", lore), button -> this.toggleInvite(id));
        }
        this.put(53, PetMenuIcons.pageNameTag(this.outsiderPage + 1, rightPages, "communal-pets.menu.page.players"),
                button -> this.turnPage(button, false));
    }

    private void renderConfirm() {
        UUID target = this.confirmTarget;
        if (target == null) {
            return;   // 目标丢了就留个空面板，玩家自己按 ESC
        }
        boolean kick = this.view == PetMenuView.CONFIRM_KICK;
        Component name = PetMenus.nameOf(this.server(), target);
        this.put(11, PetMenuIcons.confirmPane(Messages.tr(kick
                        ? "communal-pets.menu.confirm.kick" : "communal-pets.menu.confirm.transfer", name)),
                button -> {
                    if (kick) {
                        this.kick(target);
                    } else {
                        this.transfer(target);
                    }
                });
        this.put(13, this.head(target, null, List.of()), null);
        this.put(15, PetMenuIcons.cancelPane(Messages.tr(kick
                        ? "communal-pets.menu.cancel.kick" : "communal-pets.menu.cancel.transfer", name)),
                button -> this.switchView(PetMenuView.MANAGE));
    }

    private void renderApplications() {
        List<PendingRequest> applications = CommunalPet.getApplications(this.pet);
        int pages = pageCount(applications.size(), APPLICATION_PAGE_SIZE);
        this.applicationPage = Math.min(this.applicationPage, pages - 1);
        int from = this.applicationPage * APPLICATION_PAGE_SIZE;
        for (int i = 0; i < APPLICATION_PAGE_SIZE && from + i < applications.size(); i++) {
            UUID id = applications.get(from + i).player();
            this.put(i, this.head(id, "communal-pets.menu.head.application",
                            List.of(hint("communal-pets.menu.head.application.lore"))),
                    button -> {
                        if (button == 1) {
                            this.rejectApplication(id);
                        } else {
                            this.allowApplication(id);
                        }
                    });
        }
        if (applications.isEmpty()) {
            this.put(13, PetMenuIcons.emptyHint(Messages.tr("communal-pets.menu.applications.empty")), null);
        }
        this.put(26, PetMenuIcons.pageNameTag(this.applicationPage + 1, pages,
                        "communal-pets.menu.page.applications"),
                button -> {
                    this.applicationPage = this.turnedPage(this.applicationPage, button, pages);
                    this.refresh();
                });
    }

    // ------------------------------------------------------------------
    // 动作
    // ------------------------------------------------------------------

    private void clickBehavior(int state) {
        CommunalPet.setBehaviorState(this.pet, state);
        ((CommunalPet) this.pet).communalPets$showBehaviorFeedback(this.player);
        this.player.closeContainer();   // 设计文档：LRO
    }

    private void adjustRadius(int mouseButton, int step) {
        double current = CommunalPet.getWanderRadius(this.pet);
        double next = mouseButton == 1 ? current - step : current + step;
        // 界面里可调范围是 1~99（设计文档 D14）；指令仍然能设到 MAX_WANDER_RADIUS
        CommunalPet.setWanderRadius(this.pet, Math.max(1, Math.min(99, next)));
        this.refresh();
    }

    private void applyAsCaretaker() {
        CommunalPet.addApplication(this.pet, this.player.getUUID());
        this.player.sendSystemMessage(Messages.tr("communal-pets.menu.msg.applied", this.pet.getName()));
        PetMenus.notifyOwner(this.pet, "communal-pets.menu.msg.new_application", this.player.getName());
        this.player.closeContainer();   // LRO
    }

    private void answerInvitation(boolean accept) {
        CommunalPet.removeInvitation(this.pet, this.player.getUUID());
        if (accept) {
            CommunalPet.addCaretaker(this.pet, this.player.getUUID());
            PetMenus.notifyOwner(this.pet, "communal-pets.menu.msg.joined", this.player.getName());
        } else {
            PetMenus.notifyOwner(this.pet, "communal-pets.menu.msg.declined", this.player.getName());
        }
        this.player.closeContainer();   // LRO
    }

    private void toggleInvite(UUID target) {
        if (CommunalPet.hasInvitation(this.pet, target)) {
            // 再点一次 = 撤回邀请（设计文档只写了"点击邀请"，撤回是必要补全）
            CommunalPet.removeInvitation(this.pet, target);
        } else {
            CommunalPet.addInvitation(this.pet, target);
            // 和指令邀请一样：附一行可点击的 [同意] [拒绝]（设计文档 STEP3）
            PetMenus.notifyPending(this.server(), target, "communal-pets.menu.msg.invited", this.pet.getName());
        }
        this.refresh();
    }

    private void askConfirm(PetMenuView confirmView, UUID target) {
        this.confirmTarget = target;
        this.switchView(confirmView);
    }

    private void kick(UUID target) {
        CommunalPet.removeCaretaker(this.pet, target);
        PetMenus.notifyPlayer(this.server(), target, "communal-pets.menu.msg.kicked", this.pet.getName());
        this.switchView(PetMenuView.MANAGE);
    }

    private void transfer(UUID target) {
        CommunalPet.transferOwner(this.pet, target);
        PetMenus.notifyPlayer(this.server(), target, "communal-pets.menu.msg.promoted", this.pet.getName());
        this.switchView(PetMenuView.MANAGE);
    }

    private void allowApplication(UUID target) {
        CommunalPet.removeApplication(this.pet, target);
        CommunalPet.addCaretaker(this.pet, target);
        PetMenus.notifyPlayer(this.server(), target, "communal-pets.menu.msg.accepted", this.pet.getName());
        this.refresh();
    }

    private void rejectApplication(UUID target) {
        CommunalPet.removeApplication(this.pet, target);
        PetMenus.notifyPlayer(this.server(), target, "communal-pets.menu.msg.rejected", this.pet.getName());
        this.refresh();
    }

    /** 页码上界在各自的 render 里夹，这里只需要方向。 */
    private void turnPage(int mouseButton, boolean leftPanel) {
        if (leftPanel) {
            this.memberPage = this.turnedPage(this.memberPage, mouseButton, Integer.MAX_VALUE);
        } else {
            this.outsiderPage = this.turnedPage(this.outsiderPage, mouseButton, Integer.MAX_VALUE);
        }
        this.refresh();
    }

    // ------------------------------------------------------------------
    // 杂项
    // ------------------------------------------------------------------

    private void switchView(PetMenuView next) {
        if (next.rows() == this.view.rows()) {
            this.view = this.sanitize(next);
            this.refresh();
            this.broadcastFullState();
        } else {
            // 行数变了，菜单实例不能复用：换 syncId 重开一个
            PetMenus.open(this.player, this.pet, next,
                    this.memberPage, this.outsiderPage, this.applicationPage, this.confirmTarget);
        }
    }

    /** 打开时和切界面时都过一遍权限，免得界面停在玩家已经没权限的表上。 */
    private PetMenuView sanitize(PetMenuView requested) {
        boolean manageOnly = requested == PetMenuView.MANAGE
                || requested == PetMenuView.CONFIRM_KICK
                || requested == PetMenuView.CONFIRM_TRANSFER
                || requested == PetMenuView.APPLICATIONS;
        if (manageOnly && !this.canManage()) {
            return this.isGuardian() ? PetMenuView.MEMBER : PetMenuView.OUTSIDER;
        }
        if (requested == PetMenuView.INVITED
                && !CommunalPet.hasInvitation(this.pet, this.player.getUUID())) {
            return this.isGuardian() ? PetMenuView.MEMBER : PetMenuView.OUTSIDER;
        }
        return requested;
    }

    private int turnedPage(int current, int mouseButton, int pages) {
        int next = mouseButton == 1 ? current - 1 : current + 1;
        return Math.max(0, Math.min(next, Math.max(0, pages - 1)));
    }

    private static int pageCount(int items, int pageSize) {
        return Math.max(1, (items + pageSize - 1) / pageSize);
    }

    private ItemStack behaviorButton(int currentState, int buttonState, DyeColor color) {
        return PetMenuIcons.behaviorButton(currentState == buttonState, color,
                Messages.tr(CommunalPet.behaviorKeyOf(buttonState)));
    }

    /** 主人头颅；没有主人（理论上不会发生）就给个空提示。 */
    private ItemStack ownerHead() {
        UUID ownerId = CommunalPet.ownerIdOf(this.pet);
        if (ownerId == null) {
            return PetMenuIcons.emptyHint(Messages.tr("communal-pets.menu.head.no_owner"));
        }
        return this.head(ownerId, "communal-pets.menu.head.owner", List.of());
    }

    /**
     * 玩家头颅。名称颜色按角色走，映射放在 {@link PetMenuIcons#headColor}（主人金 / 照顾者蓝 /
     * 邀请候选灰 / 申请黄）。
     *
     * @param nameKey {@code null} 表示只显示玩家名（表3 / 表3.1 中间那颗头颅就是这种，
     *                设计文档里它不该再重复一遍按钮上的话）
     */
    private ItemStack head(UUID id, @Nullable String nameKey, List<Component> lore) {
        Component name = PetMenus.nameOf(this.server(), id);
        if (nameKey != null) {
            name = Messages.tr(nameKey, name);
        }
        return PetMenuIcons.playerHead(id, PetMenus.profileOf(this.server(), id),
                name.copy().withStyle(PetMenuIcons.headColor(nameKey)), lore);
    }

    private Component ownerLine() {
        UUID ownerId = CommunalPet.ownerIdOf(this.pet);
        return PetMenuIcons.ownerLine(ownerId == null ? null : PetMenus.nameOf(this.server(), ownerId));
    }

    /** 说明行走 {@link PetMenuIcons#hint}（深灰），保证整块界面的配色只有一个来源。 */
    private static Component hint(String key, Object... args) {
        return PetMenuIcons.hint(key, args);
    }

    private MinecraftServer server() {
        return this.pet.level().getServer();
    }

    private boolean isGameMaster() {
        return CommunalPet.isGameMaster(this.player);
    }

    private boolean isGuardian() {
        return this.pet.isOwnedBy(this.player);
    }

    private boolean canManage() {
        return CommunalPet.canManage(this.pet, this.player);
    }

    /** 给动作里发消息用的、带可点击应答按钮的提示（设计文档 STEP3）。 */
    static MutableComponent answerButtons() {
        return PetMenus.clickable("communal-pets.menu.msg.accept_button",
                "/communalpets accept", net.minecraft.ChatFormatting.GREEN)
                .append(Component.literal(" "))
                .append(PetMenus.clickable("communal-pets.menu.msg.reject_button",
                        "/communalpets reject", net.minecraft.ChatFormatting.RED));
    }
}
