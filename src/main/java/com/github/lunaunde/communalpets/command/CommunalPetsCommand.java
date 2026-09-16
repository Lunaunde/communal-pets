package com.github.lunaunde.communalpets.command;

import com.github.lunaunde.communalpets.Messages;
import com.github.lunaunde.communalpets.menu.PetMenus;
import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.github.lunaunde.communalpets.world.entity.animal.PendingRequest;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * /communalpets accept | reject
 * /communalpets &lt;宠物&gt; caretaker add|remove &lt;玩家&gt;
 * /communalpets &lt;宠物&gt; caretaker list
 * /communalpets &lt;宠物&gt; caretaker invite &lt;玩家&gt;
 * /communalpets &lt;宠物&gt; behavior
 * /communalpets &lt;宠物&gt; behavior set &lt;follow_commander|follow_nearest|wander|sit&gt;
 * /communalpets &lt;宠物&gt; wander center
 * /communalpets &lt;宠物&gt; wander center set &lt;pos&gt;
 * /communalpets &lt;宠物&gt; wander radius
 * /communalpets &lt;宠物&gt; wander radius set &lt;值&gt;
 * <p>
 * 文本全部走 {@code translatable}，中英文在
 * {@code assets/communal-pets/lang/zh_cn.json} 与 {@code en_us.json} 里。
 * <p>
 * 权限（用 {@code .requires(...)} 写在节点上，所以非 op 看不到那些子命令）：
 * <ul>
 *     <li><b>写操作需要 op</b>（{@code LEVEL_GAMEMASTERS}）：caretaker add / remove、
 *         behavior set、wander center set、wander radius set；</li>
 *     <li><b>读操作</b>：list / behavior / wander center / wander radius —— 主人、已有照顾者、管理员都能看
 *         （在 handler 里查，因为 {@code requires} 拿不到已经解析出来的宠物）；</li>
 *     <li>控制台 / 命令方块（没有玩家实体）默认放行。</li>
 * </ul>
 */
public final class CommunalPetsCommand {



    /**
     * 所有"写"操作的门槛：op（{@link Commands#LEVEL_GAMEMASTERS}，和原版 /gamemode、/summon 同级）。
     * 写在节点上而不是 handler 里，所以非 op 的客户端连这些子命令的补全都收不到。
     */
    /**
     * 指令报错文本：按"谁在执行"选语言 —— 玩家用他客户端的语言，控制台 / 命令方块用服务端默认语言。
     * 所以异常类型必须每次现造（以前是在类初始化时就建好的，那样只有一种语言、切语言也不会变）。
     */
    private static SimpleCommandExceptionType error(CommandSourceStack source, String key, Object... args) {
        return new SimpleCommandExceptionType(Messages.tr(source, key, args));
    }
    private static final Predicate<CommandSourceStack> OPERATOR =
            Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private CommunalPetsCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                dispatcher.register(Commands.literal("communalpets")
                        // ⚠ 顺序有意义：Brigadier 在多个子节点都能解析同一段输入时，把它收集进
                        // potentials 后做一次**稳定**排序（完全消费输入、没有异常者优先），再取
                        // potentials.get(0)。所以 accept / reject 必须注册在 <宠物> 实体参数之前，
                        // 否则 /communalpets accept 会被当成"一个叫 accept 的玩家"。
                        .then(Commands.literal("accept")
                                .executes(ctx -> answerRequest(ctx.getSource(), true)))
                        .then(Commands.literal("reject")
                                .executes(ctx -> answerRequest(ctx.getSource(), false)))
                        // EntityArgument.entity() == new EntityArgument(true, false)：
                        // 选择器匹配到多个实体会走原版报错（argument.entity.toomany），
                        // 所以这里天然满足"最多一个实体"。
                        .then(Commands.argument("pet", EntityArgument.entity())
                                .then(Commands.literal("caretaker")
                                        .then(Commands.literal("add")
                                                .requires(OPERATOR)
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> addCaretaker(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntity(ctx, "pet"),
                                                                EntityArgument.getPlayer(ctx, "player")))))
                                        .then(Commands.literal("remove")
                                                .requires(OPERATOR)
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> removeCaretaker(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntity(ctx, "pet"),
                                                                EntityArgument.getPlayer(ctx, "player")))))
                                        .then(Commands.literal("list")
                                                .executes(ctx -> listCaretakers(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "pet"))))
                                        // 离线邀请：GameProfileArgument 和原版 /op 同源，能按名字从
                                        // usercache 解析**离线**玩家（本地文件，不联网）；
                                        // EntityArgument.player() 只能解析在线玩家，做不到设计文档要的离线邀请。
                                        .then(Commands.literal("invite")
                                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                                        .executes(ctx -> inviteCaretakers(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntity(ctx, "pet"),
                                                                GameProfileArgument.getGameProfiles(ctx, "players"))))))
                                .then(Commands.literal("behavior")
                                        .executes(ctx -> getBehavior(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "pet")))
                                        .then(Commands.literal("set")
                                                .requires(OPERATOR)
                                                .then(Commands.argument("state", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                                List.of("follow_commander", "follow_nearest", "wander", "sit"), builder))
                                                        .executes(ctx -> setBehavior(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntity(ctx, "pet"),
                                                                StringArgumentType.getString(ctx, "state"))))))
                                .then(Commands.literal("wander")
                                        .then(Commands.literal("center")
                                                .executes(ctx -> getWanderCenter(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "pet")))
                                                .then(Commands.literal("set")
                                                        .requires(OPERATOR)
                                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                                .executes(ctx -> setWanderCenter(
                                                                        ctx.getSource(),
                                                                        EntityArgument.getEntity(ctx, "pet"),
                                                                        Vec3Argument.getVec3(ctx, "pos"))))))
                                        .then(Commands.literal("radius")
                                                .executes(ctx -> getWanderRadius(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "pet")))
                                                .then(Commands.literal("set")
                                                        .requires(OPERATOR)
                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> setWanderRadius(
                                                                        ctx.getSource(),
                                                                        EntityArgument.getEntity(ctx, "pet"),
                                                                        DoubleArgumentType.getDouble(ctx, "value"))))))))));
    }

    // ------------------------------------------------------------------
    // caretaker 子命令
    // ------------------------------------------------------------------

    private static int addCaretaker(CommandSourceStack source, Entity target, ServerPlayer player)
            throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);

        UUID ownerId = ownerIdOf(pet);
        if (ownerId != null && ownerId.equals(player.getUUID())) {
            throw error(source, "communal-pets.command.error.is_owner").create();
        }
        if (CommunalPet.getCaretakerUUIDs(pet).contains(player.getUUID())) {
            throw error(source, "communal-pets.command.error.already_caretaker").create();
        }

        CommunalPet.addCaretaker(pet, player);
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.caretaker.added",
                player.getName(), pet.getName()), true);
        return 1;
    }

    private static int removeCaretaker(CommandSourceStack source, Entity target, ServerPlayer player)
            throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);

        UUID ownerId = ownerIdOf(pet);
        if (ownerId != null && ownerId.equals(player.getUUID())) {
            throw error(source, "communal-pets.command.error.is_owner").create();
        }
        if (!CommunalPet.getCaretakerUUIDs(pet).contains(player.getUUID())) {
            throw error(source, "communal-pets.command.error.not_caretaker").create();
        }

        CommunalPet.removeCaretaker(pet, player.getUUID());
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.caretaker.removed",
                player.getName(), pet.getName()), true);
        return 1;
    }

    private static int listCaretakers(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);
        checkCanView(source, pet);

        UUID ownerId = ownerIdOf(pet);
        // 直接用 UUID（存储本体）：离线照顾者也能列出来，而且不用经过实体视图（那里离线会解析成 null）
        List<UUID> caretakerIds = CommunalPet.getCaretakerUUIDs(pet);

        // 主人放第一行并标 [owner]，其余的标 [caretaker]
        MutableComponent message =
                Messages.tr(source, "communal-pets.command.caretaker.list.header", pet.getName());
        int listed = 0;
        if (ownerId != null) {
            message.append(entryLine(source, ownerId,
                    "communal-pets.command.caretaker.tag.owner", ChatFormatting.GOLD));
            listed++;
        }
        for (UUID id : caretakerIds) {
            if (id.equals(ownerId)) {
                continue;   // 万一存档里混进了主人的 UUID，不重复显示
            }
            message.append(entryLine(source, id,
                    "communal-pets.command.caretaker.tag.caretaker", ChatFormatting.GRAY));
            listed++;
        }
        if (listed == 0) {
            message.append(Messages.tr(source, "communal-pets.command.caretaker.list.empty"));
        }

        source.sendSuccess(() -> message, false);
        return listed;
    }

    // ------------------------------------------------------------------
    // caretaker invite（支持离线玩家）
    // ------------------------------------------------------------------

    /**
     * 邀请玩家成为照顾者。
     * <p>
     * 权限写在 handler 里而不是 {@code .requires(OPERATOR)}：主人（非 op）也要能邀请离线玩家，
     * 但照顾者没有管理照顾者的权力（{@link CommunalPet#canManage}）。
     */
    private static int inviteCaretakers(CommandSourceStack source, Entity target, Collection<NameAndId> players)
            throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);
        checkCanManage(source, pet);

        UUID ownerId = ownerIdOf(pet);
        List<UUID> caretakers = CommunalPet.getCaretakerUUIDs(pet);
        int invited = 0;
        for (NameAndId profile : players) {
            UUID id = profile.id();
            if (id.equals(ownerId) || caretakers.contains(id)) {
                continue;   // 主人不用被邀请，已经是照顾者的也不用
            }
            CommunalPet.addInvitation(pet, id);
            // 在线就立刻提示（附可点击的同意 / 拒绝）；离线则邀请挂在实体上，他下次开界面就能看到
            PetMenus.notifyPending(source.getServer(), id,
                    "communal-pets.command.caretaker.invited", pet.getName());
            invited++;
        }
        if (invited == 0) {
            throw error(source, "communal-pets.command.error.nothing_to_invite").create();
        }
        int sent = invited;
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.caretaker.invite.sent",
                sent, pet.getName()), true);
        return invited;
    }

    // ------------------------------------------------------------------
    // accept / reject（对"最后一条发给自己的请求"应答）
    // ------------------------------------------------------------------

    /**
     * {@code /communalpets accept|reject}：只认"发给自己的最后一条"待处理请求，而且它必须发生在
     * {@link PendingRequest#COMMAND_WINDOW_MILLIS} 之内（设计文档 C9）。
     * <p>
     * 请求存在<b>宠物实体</b>上，所以这里得在所有<b>已加载</b>的区块里找一遍；宠物所在区块没加载时
     * 会明确报"没有待处理请求"，而不是假装成功 —— 那种情况走到宠物旁边用界面应答即可
     * （界面里的请求长期有效）。
     */
    private static int answerRequest(CommandSourceStack source, boolean accept) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw error(source, "communal-pets.command.error.player_only").create();   // 控制台没有"发给自己的请求"
        }

        MinecraftServer server = source.getServer();
        TamableAnimal foundPet = null;
        PendingRequest found = null;
        UUID foundTarget = null;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof TamableAnimal pet) || !pet.isTame()) {
                    continue;
                }
                // 两个方向：别人申请我这只宠物（我是主人 / op），或者我被这只宠物邀请
                PendingRequest mine = null;
                UUID mineTarget = null;
                if (CommunalPet.isOwner(pet, player) || isGameMaster(source)) {
                    PendingRequest application = CommunalPet.latestApplication(pet);
                    if (application != null) {
                        mine = application;
                        mineTarget = application.player();
                    }
                }
                PendingRequest invitation = CommunalPet.latestInvitation(pet);
                if (invitation != null && invitation.player().equals(player.getUUID())
                        && (mine == null || invitation.createdAtMillis() > mine.createdAtMillis())) {
                    mine = invitation;
                    mineTarget = player.getUUID();
                }
                if (mine != null && (found == null || mine.createdAtMillis() > found.createdAtMillis())) {
                    foundPet = pet;
                    found = mine;
                    foundTarget = mineTarget;
                }
            }
        }

        if (found == null || foundTarget == null) {
            throw error(source, "communal-pets.command.error.no_request").create();
        }
        if (!found.withinCommandWindow()) {
            // 时间窗之外的请求只能去界面里点（界面里的请求长期有效）
            throw error(source, "communal-pets.command.error.request_expired").create();
        }

        UUID ownerId = ownerIdOf(foundPet);
        CommunalPet.removeApplication(foundPet, foundTarget);
        CommunalPet.removeInvitation(foundPet, foundTarget);
        if (accept) {
            CommunalPet.addCaretaker(foundPet, foundTarget);
        }

        UUID target = foundTarget;
        TamableAnimal pet = foundPet;
        Component targetName = PetMenus.nameOf(server, target);
        source.sendSuccess(() -> Messages.tr(source, accept
                        ? "communal-pets.command.answer.accepted"
                        : "communal-pets.command.answer.rejected",
                targetName, pet.getName()), true);

        // 通知另一头：批的是别人的申请 → 通知申请人；答的是自己的邀请 → 通知主人
        UUID other = target.equals(player.getUUID()) ? ownerId : target;
        if (other != null && !other.equals(player.getUUID())) {
            PetMenus.notifyPlayer(server, other, accept
                            ? "communal-pets.command.answer.notify.accepted"
                            : "communal-pets.command.answer.notify.rejected",
                    pet.getName());
        }
        return 1;
    }

    /** 只有主人和 op 能管理照顾者（设计文档：照顾者没有管理照顾者的权力）。 */
    private static void checkCanManage(CommandSourceStack source, TamableAnimal pet) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null || CommunalPet.canManage(pet, player)) {
            return;   // 控制台 / 命令方块放行，和现有读写权限策略保持一致
        }
        throw error(source, "communal-pets.command.error.not_allowed").create();
    }

    private static Component entryLine(CommandSourceStack source, UUID id, String tagKey, ChatFormatting tagColor) {
        return Component.literal("\n - ")
                .append(nameOf(source, id))
                .append(Component.literal(" "))
                .append(Messages.tr(source, tagKey).withStyle(tagColor));
    }

    // ------------------------------------------------------------------
    // wander 子命令（center / radius）
    // ------------------------------------------------------------------

    private static int getWanderCenter(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);
        checkCanView(source, pet);

        Vec3 center = CommunalPet.getWanderCenter(pet);
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.wander_center.get",
                pet.getName(), coordinates(source, center)), false);
        return 1;
    }

    private static int setWanderCenter(CommandSourceStack source, Entity target, Vec3 pos) throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);

        CommunalPet.setWanderCenter(pet, pos);
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.wander_center.set",
                pet.getName(), coordinates(source, pos)), true);
        return 1;
    }

    private static int getWanderRadius(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);
        checkCanView(source, pet);

        double radius = CommunalPet.getWanderRadius(pet);
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.wander_radius.get",
                pet.getName(), format(radius)), false);
        return 1;
    }

    private static int setWanderRadius(CommandSourceStack source, Entity target, double radius)
            throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);

        // 上界由 MAX_WANDER_RADIUS 决定，指令层先报错（实体上的 setter 也会夹，但那只是兜底）
        if (!(radius > 0) || radius > CommunalPet.MAX_WANDER_RADIUS) {
            throw error(source, "communal-pets.command.error.invalid_radius", format(CommunalPet.MAX_WANDER_RADIUS)).create();
        }

        CommunalPet.setWanderRadius(pet, radius);
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.wander_radius.set",
                pet.getName(), format(radius)), true);
        return 1;
    }

    /** 坐标三元组，%s 由 lang 里的键决定怎么排版（不依赖原版的 chat.coordinates）。 */
    private static Component coordinates(CommandSourceStack source, Vec3 pos) {
        return Messages.tr(source, "communal-pets.command.coordinates",
                format(pos.x), format(pos.y), format(pos.z));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ------------------------------------------------------------------
    // behavior 子命令
    // ------------------------------------------------------------------

    private static int getBehavior(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);
        checkCanView(source, pet);

        int state = CommunalPet.getBehaviorState(pet);
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.behavior.get",
                pet.getName(), Messages.tr(source, CommunalPet.behaviorKeyOf(state))), false);
        return state;   // 返回值 = 状态序号，方便 /execute store result
    }

    private static int setBehavior(CommandSourceStack source, Entity target, String name) throws CommandSyntaxException {
        TamableAnimal pet = petOf(source, target);

        // 别名只是为了顺手（followcommander / follownearest / commander / nearest 都能敲）
        int state = switch (name) {
            case "follow_commander", "followcommander", "commander" -> CommunalPet.BEHAVIOR_FOLLOW_COMMANDER;
            case "follow_nearest", "follownearest", "nearest" -> CommunalPet.BEHAVIOR_FOLLOW_NEAREST;
            case "wander" -> CommunalPet.BEHAVIOR_WANDER;
            case "sit" -> CommunalPet.BEHAVIOR_SIT;
            default -> throw error(source, "communal-pets.command.error.unknown_behavior").create();
        };

        CommunalPet.setBehaviorState(pet, state);
        source.sendSuccess(() -> Messages.tr(source, "communal-pets.command.behavior.set",
                pet.getName(), Messages.tr(source, CommunalPet.behaviorKeyOf(state))), true);
        return 1;
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 目标必须是已驯服的 {@link TamableAnimal}（本模组在它身上混入了 {@link CommunalPet}）。 */
    private static TamableAnimal petOf(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        if (!(target instanceof TamableAnimal pet) || !pet.isTame()) {
            throw error(source, "communal-pets.command.error.not_pet").create();
        }
        return pet;
    }

    /** 主人来自原版的 Owner 字段（不在 caretakers 列表里）。 */
    private static @Nullable UUID ownerIdOf(TamableAnimal pet) {
        EntityReference<LivingEntity> owner = pet.getOwnerReference();
        return owner == null ? null : owner.getUUID();
    }

    private static boolean isGameMaster(CommandSourceStack source) {
        return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
    }

    /** 主人、已有的照顾者、管理员都能查看。 */
    private static void checkCanView(CommandSourceStack source, TamableAnimal pet) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return;
        }
        if (pet.isOwnedBy(player) || isGameMaster(source)) {
            return;
        }
        throw error(source, "communal-pets.command.error.not_allowed").create();
    }

    /**
     * UUID → 显示名：实现集中在 {@link PetMenus#nameOf}，界面和指令共用同一份
     * （在线玩家 → usercache → UUID 字符串，全程不联网）。
     */
    private static Component nameOf(CommandSourceStack source, UUID id) {
        return PetMenus.nameOf(source.getServer(), id);
    }
}
