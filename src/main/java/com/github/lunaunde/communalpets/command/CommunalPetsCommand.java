package com.github.lunaunde.communalpets.command;

import com.github.lunaunde.communalpets.Messages;
import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * /communalpets &lt;宠物&gt; caregiver add|remove &lt;玩家&gt;
 * /communalpets &lt;宠物&gt; caregiver list
 * /communalpets &lt;宠物&gt; behavior
 * /communalpets &lt;宠物&gt; behavior set &lt;follow_commander|follow_nearest|wander|sit&gt;
 * /communalpets &lt;宠物&gt; wander_center
 * /communalpets &lt;宠物&gt; wander_center set &lt;pos&gt;
 * <p>
 * 文本全部走 {@code translatable}，中英文在
 * {@code assets/communal-pets/lang/zh_cn.json} 与 {@code en_us.json} 里。
 * <p>
 * 权限（用 {@code .requires(...)} 写在节点上，所以非 op 看不到那些子命令）：
 * <ul>
 *     <li><b>写操作需要 op</b>（{@code LEVEL_GAMEMASTERS}）：caregiver add / remove、
 *         behavior set、wander_center set；</li>
 *     <li><b>读操作</b>：list / behavior / wander_center —— 主人、已有照护者、管理员都能看
 *         （在 handler 里查，因为 {@code requires} 拿不到已经解析出来的宠物）；</li>
 *     <li>控制台 / 命令方块（没有玩家实体）默认放行。</li>
 * </ul>
 */
public final class CommunalPetsCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_PET =
            new SimpleCommandExceptionType(Messages.tr("communal-pets.command.error.not_pet"));

    private static final SimpleCommandExceptionType ERROR_NOT_ALLOWED =
            new SimpleCommandExceptionType(Messages.tr("communal-pets.command.error.not_allowed"));

    private static final SimpleCommandExceptionType ERROR_IS_OWNER =
            new SimpleCommandExceptionType(Messages.tr("communal-pets.command.error.is_owner"));

    private static final SimpleCommandExceptionType ERROR_ALREADY_CAREGIVER =
            new SimpleCommandExceptionType(Messages.tr("communal-pets.command.error.already_caregiver"));

    private static final SimpleCommandExceptionType ERROR_NOT_CAREGIVER =
            new SimpleCommandExceptionType(Messages.tr("communal-pets.command.error.not_caregiver"));

    private static final SimpleCommandExceptionType ERROR_UNKNOWN_BEHAVIOR =
            new SimpleCommandExceptionType(Messages.tr("communal-pets.command.error.unknown_behavior"));

    /**
     * 所有"写"操作的门槛：op（{@link Commands#LEVEL_GAMEMASTERS}，和原版 /gamemode、/summon 同级）。
     * 写在节点上而不是 handler 里，所以非 op 的客户端连这些子命令的补全都收不到。
     */
    private static final Predicate<CommandSourceStack> OPERATOR =
            Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private CommunalPetsCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                dispatcher.register(Commands.literal("communalpets")
                        // EntityArgument.entity() == new EntityArgument(true, false)：
                        // 选择器匹配到多个实体会走原版报错（argument.entity.toomany），
                        // 所以这里天然满足"最多一个实体"。
                        .then(Commands.argument("pet", EntityArgument.entity())
                                .then(Commands.literal("caregiver")
                                        .then(Commands.literal("add")
                                                .requires(OPERATOR)
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> addCaregiver(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntity(ctx, "pet"),
                                                                EntityArgument.getPlayer(ctx, "player")))))
                                        .then(Commands.literal("remove")
                                                .requires(OPERATOR)
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> removeCaregiver(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntity(ctx, "pet"),
                                                                EntityArgument.getPlayer(ctx, "player")))))
                                        .then(Commands.literal("list")
                                                .executes(ctx -> listCaregivers(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "pet")))))
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
                                .then(Commands.literal("wander_center")
                                        .executes(ctx -> getWanderCenter(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "pet")))
                                        .then(Commands.literal("set")
                                                .requires(OPERATOR)
                                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                                        .executes(ctx -> setWanderCenter(
                                                                ctx.getSource(),
                                                                EntityArgument.getEntity(ctx, "pet"),
                                                                Vec3Argument.getVec3(ctx, "pos")))))))));
    }

    // ------------------------------------------------------------------
    // caregiver 子命令
    // ------------------------------------------------------------------

    private static int addCaregiver(CommandSourceStack source, Entity target, ServerPlayer player)
            throws CommandSyntaxException {
        TamableAnimal pet = petOf(target);

        UUID ownerId = ownerIdOf(pet);
        if (ownerId != null && ownerId.equals(player.getUUID())) {
            throw ERROR_IS_OWNER.create();
        }
        if (CommunalPet.getCaregiverUUIDs(pet).contains(player.getUUID())) {
            throw ERROR_ALREADY_CAREGIVER.create();
        }

        CommunalPet.addCaregiver(pet, player);
        source.sendSuccess(() -> Messages.tr("communal-pets.command.caregiver.added",
                player.getName(), pet.getName()), true);
        return 1;
    }

    private static int removeCaregiver(CommandSourceStack source, Entity target, ServerPlayer player)
            throws CommandSyntaxException {
        TamableAnimal pet = petOf(target);

        UUID ownerId = ownerIdOf(pet);
        if (ownerId != null && ownerId.equals(player.getUUID())) {
            throw ERROR_IS_OWNER.create();
        }
        if (!CommunalPet.getCaregiverUUIDs(pet).contains(player.getUUID())) {
            throw ERROR_NOT_CAREGIVER.create();
        }

        CommunalPet.removeCaregiver(pet, player.getUUID());
        source.sendSuccess(() -> Messages.tr("communal-pets.command.caregiver.removed",
                player.getName(), pet.getName()), true);
        return 1;
    }

    private static int listCaregivers(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        TamableAnimal pet = petOf(target);
        checkCanView(source, pet);

        UUID ownerId = ownerIdOf(pet);
        // 直接用 UUID（存储本体）：离线照护者也能列出来，而且不用经过实体视图（那里离线会解析成 null）
        List<UUID> caregiverIds = CommunalPet.getCaregiverUUIDs(pet);

        // 主人放第一行并标 [owner]，其余的标 [caregiver]
        MutableComponent message =
                Messages.tr("communal-pets.command.caregiver.list.header", pet.getName());
        int listed = 0;
        if (ownerId != null) {
            message.append(entryLine(source, ownerId,
                    "communal-pets.command.caregiver.tag.owner", ChatFormatting.GOLD));
            listed++;
        }
        for (UUID id : caregiverIds) {
            if (id.equals(ownerId)) {
                continue;   // 万一存档里混进了主人的 UUID，不重复显示
            }
            message.append(entryLine(source, id,
                    "communal-pets.command.caregiver.tag.caregiver", ChatFormatting.GRAY));
            listed++;
        }
        if (listed == 0) {
            message.append(Messages.tr("communal-pets.command.caregiver.list.empty"));
        }

        source.sendSuccess(() -> message, false);
        return listed;
    }

    private static Component entryLine(CommandSourceStack source, UUID id, String tagKey, ChatFormatting tagColor) {
        return Component.literal("\n - ")
                .append(nameOf(source, id))
                .append(Component.literal(" "))
                .append(Messages.tr(tagKey).withStyle(tagColor));
    }

    // ------------------------------------------------------------------
    // wander_center 子命令
    // ------------------------------------------------------------------

    private static int getWanderCenter(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        TamableAnimal pet = petOf(target);
        checkCanView(source, pet);

        Vec3 center = CommunalPet.getWanderCenter(pet);
        source.sendSuccess(() -> Messages.tr("communal-pets.command.wander_center.get",
                pet.getName(), coordinates(center)), false);
        return 1;
    }

    private static int setWanderCenter(CommandSourceStack source, Entity target, Vec3 pos) throws CommandSyntaxException {
        TamableAnimal pet = petOf(target);

        CommunalPet.setWanderCenter(pet, pos);
        source.sendSuccess(() -> Messages.tr("communal-pets.command.wander_center.set",
                pet.getName(), coordinates(pos)), true);
        return 1;
    }

    /** 坐标三元组，%s 由 lang 里的键决定怎么排版（不依赖原版的 chat.coordinates）。 */
    private static Component coordinates(Vec3 pos) {
        return Messages.tr("communal-pets.command.coordinates",
                format(pos.x), format(pos.y), format(pos.z));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ------------------------------------------------------------------
    // behavior 子命令
    // ------------------------------------------------------------------

    private static int getBehavior(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        TamableAnimal pet = petOf(target);
        checkCanView(source, pet);

        int state = CommunalPet.getBehaviorState(pet);
        source.sendSuccess(() -> Messages.tr("communal-pets.command.behavior.get",
                pet.getName(), Messages.tr(CommunalPet.behaviorKeyOf(state))), false);
        return state;   // 返回值 = 状态序号，方便 /execute store result
    }

    private static int setBehavior(CommandSourceStack source, Entity target, String name) throws CommandSyntaxException {
        TamableAnimal pet = petOf(target);

        // 别名只是为了顺手（followcommander / follownearest / commander / nearest 都能敲）
        int state = switch (name) {
            case "follow_commander", "followcommander", "commander" -> CommunalPet.BEHAVIOR_FOLLOW_COMMANDER;
            case "follow_nearest", "follownearest", "nearest" -> CommunalPet.BEHAVIOR_FOLLOW_NEAREST;
            case "wander" -> CommunalPet.BEHAVIOR_WANDER;
            case "sit" -> CommunalPet.BEHAVIOR_SIT;
            default -> throw ERROR_UNKNOWN_BEHAVIOR.create();
        };

        CommunalPet.setBehaviorState(pet, state);
        source.sendSuccess(() -> Messages.tr("communal-pets.command.behavior.set",
                pet.getName(), Messages.tr(CommunalPet.behaviorKeyOf(state))), true);
        return 1;
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 目标必须是已驯服的 {@link TamableAnimal}（本模组在它身上混入了 {@link CommunalPet}）。 */
    private static TamableAnimal petOf(Entity target) throws CommandSyntaxException {
        if (!(target instanceof TamableAnimal pet) || !pet.isTame()) {
            throw ERROR_NOT_PET.create();
        }
        return pet;
    }

    /** 主人来自原版的 Owner 字段（不在 caregivers 列表里）。 */
    private static @Nullable UUID ownerIdOf(TamableAnimal pet) {
        EntityReference<LivingEntity> owner = pet.getOwnerReference();
        return owner == null ? null : owner.getUUID();
    }

    private static boolean isGameMaster(CommandSourceStack source) {
        return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
    }

    /** 主人、已有的照护者、管理员都能查看。 */
    private static void checkCanView(CommandSourceStack source, TamableAnimal pet) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return;
        }
        if (pet.isOwnedBy(player) || isGameMaster(source)) {
            return;
        }
        throw ERROR_NOT_ALLOWED.create();
    }

    /**
     * UUID -&gt; 显示名：先看在线玩家，再查 usercache（{@code nameToIdCache} 是本地文件缓存，不联网），
     * 最后退化成 UUID 字符串。
     * <p>
     * 别用 {@code services().profileResolver().fetchById(uuid)}：那个在缓存未命中时会向 Mojang
     * 发网络请求（{@code sessionService.fetchProfile(id, true)}），在服务端线程上会把整个服务器卡住。
     */
    private static Component nameOf(CommandSourceStack source, UUID id) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        return source.getServer().services().nameToIdCache().get(id)
                .<Component>map(entry -> Component.literal(entry.name()))
                .orElseGet(() -> Component.literal(id.toString()));
    }
}
