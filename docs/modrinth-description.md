# Communal Pets

**Share your tamed pets — without asking anyone to install a mod.**

Communal Pets lets several players co-own the same tamed pet on a Fabric server. Besides its
**owner**, a pet can have **caretakers**; together they are its **guardians**. Guardians can command
the pet, switch its behaviour and manage the whole group from in-game menus — and because everything
runs on the **server**, players on a completely vanilla client can see and use all of it.

<!--
  🖼️ IMAGE 1 — 首图（最重要）
  建议做成 5–8 秒动图 GIF：蹲下右键宠物 → 箱子界面弹出 → 点行为按钮 → 界面关闭 + 宠物发光。
  静态图也行，那就截"表1 打开着、宠物在旁边发光"的画面。
  替换下面的 IMAGE_1_URL（用你上传到 Gallery 后拿到的 CDN 链接）。
-->
![Communal Pets — the guardian menu open on a tamed wolf](IMAGE_1_URL)

## Why this exists

In vanilla, a tamed pet belongs to exactly one player. On a server that is often wrong: base pets,
community animals, a shop cat that several people look after. Until now you either shared an account,
passed the pet around with a name tag, or asked everyone to install a client-side pet mod — which
most players simply won't do.

Communal Pets is a **server-side utility**. Install it on the server and it works for everyone who
connects, vanilla clients included. No client mod, no resource pack, no custom textures.

## Features

### Guardians — owner + caretakers

- Caretakers are stored as UUIDs on the pet itself, so they keep working **while the owner is
  offline**, and survive restarts and dimension changes.
- Caretakers can command the pet, sit or stand it, and the pet **defends them and follows them**
  exactly like it does for the owner.

### Four behaviour modes, with an owner-only glow

Right-click a pet to cycle its behaviour. The pet glows in the colour of its new state — and that
glow is **only sent to the guardian group**, so everyone else just sees a normal pet.

| Mode | What it does | Glow |
|---|---|---|
| Follow commander | follows the last person who commanded it | bright green |
| Follow nearest guardian | follows whichever guardian is closest | light blue |
| Wander | roams inside a radius around a centre point (set it with a command) | orange |
| Sit | stays put | red |

### In-game menus that work on vanilla clients

`Sneak + right-click` a tamed pet to open its menu: a real vanilla container, built entirely out of
vanilla items and components. Everything is clickable, everything is server-authoritative, and none
of it requires the client to have this mod.

<!--
  🖼️ IMAGE 2 — 表1（抚养者主界面）
  拍法：蹲下右键，让界面打开着截图。最好让 4 个行为按钮里"当前状态"那个是混凝土、其余是玻璃，
  这样一眼能看出状态指示。
-->
![The guardian menu: behaviour buttons, wander radius, guardians and management](IMAGE_2_URL)

### Applications and invitations

Nobody has to be handed ownership to help out.

- A stranger can **apply** to become a caretaker from the pet's menu; the owner approves or rejects
  it in the request screen.
- The owner can **invite** online players straight from the management screen, or offline players by
  command (resolved through the local name cache, exactly like `/op`).
- Requests arrive as a chat message with clickable **[Accept]** / **[Reject]** buttons, and stay
  pending on the pet until they are answered.

<!--
  🖼️ IMAGE 3 — 表0（非抚养者视角）+ 表0.1（收到邀请）
  两张并排或上下放都行：上面是"宠物信息 + 申请成为照顾者"，下面是"同意 / 拒绝"。
  注意表0 在 op 视角下第三行还会多一个命令方块，普通玩家看不到。
-->
![Applying to become a caretaker, and answering an invitation](IMAGE_3_URL)

### Ownership transfer, with a confirmation screen

Hand the pet over properly: the new owner takes over, the old owner is **demoted to caretaker**
instead of losing access, and nothing happens by accident — transfer asks for confirmation first.

<!--
  🖼️ IMAGE 4 — 表2（抚养者管理，9×6）
  拍法：先加 2–3 个照顾者、再让 1–2 个玩家在线，让左面板（主人+照顾者）和右面板（可邀请的在线玩家）
  都有头颅，中间那列灰色分界线才显得出结构。
-->
![Managing guardians: owner and caretakers on the left, inviteable players on the right](IMAGE_4_URL)

### Seven menus, one of which is for you

| Menu | Who sees it | What's in it |
|---|---|---|
| 0 | non-guardians | pet info, **apply to become a caretaker** |
| 0.1 | players holding an invitation | **accept** / **reject** |
| 1 | guardians | 4 behaviour buttons, wander radius, guardian list, management paper |
| 2 | owner + op | management panel: kick / promote caretakers, invite players, paging |
| 3 | owner + op | confirm "remove from guardians" |
| 3.1 | owner + op | confirm "make this the new owner" |
| 4 | owner + op | pending applications: allow or reject |

<!--
  🖼️ IMAGE 5 — 表3 / 表3.1（二次确认）
  一张就够：黄绿玻璃板=确定、红色=取消，中间是当事玩家的头颅。
-->
![Confirmation screens for removing a caretaker or transferring ownership](IMAGE_5_URL)

<!--
  🖼️ IMAGE 6 — 表4（申请管理）+ 表1 里那张带附魔光泽的纸张
  拍法：用另一个号先申请，主人这边的纸张就会发光；右键进表4 就能看到申请者头颅。
-->
![Reviewing caretaker applications](IMAGE_6_URL)

### Permissions that make sense

| Action | Owner | Caretaker | Op |
|---|---|---|---|
| Use menus 0 / 0.1 (apply, answer an invitation) | ✅ | ✅ | ✅ |
| Use menu 1 (behaviour, wander radius) | ✅ | ✅ | ✅ |
| Use menus 2 / 3 / 3.1 / 4 (manage people, transfer ownership) | ✅ | ❌ | ✅ |

**Caretakers cannot manage other caretakers.** That is deliberate — it keeps a shared pet from
turning into a tug of war.

### Bilingual, including for vanilla clients

English and 中文 are built in, and the text **follows each player's own language** — including vanilla
clients, which cannot translate anything themselves. Because those clients have no language files for
this mod, every text component carries a **fallback string written in the reader's language**, so a
vanilla client reads real sentences instead of raw translation keys and can switch language in-game.

## Commands

| Command | Who | What |
|---|---|---|
| `/communalpets accept` \| `reject` | anyone | answer the most recent request addressed to you (15-minute window; the menus have no expiry) |
| `/communalpets <pet> caretaker list` | guardian or op | list owner and caretakers |
| `/communalpets <pet> caretaker add <player>` | op | add a caretaker directly |
| `/communalpets <pet> caretaker remove <player>` | op | remove a caretaker |
| `/communalpets <pet> caretaker invite <players>` | owner or op | invite players — **offline names work** |
| `/communalpets <pet> behavior [set <mode>]` | guardian / op | read or set the behaviour |
| `/communalpets <pet> wander center [set <pos>]` | guardian / op | read or set the wander centre |
| `/communalpets <pet> wander radius [set <value>]` | guardian / op | read or set the wander radius (0 < r ≤ 1024) |

## Configuration

`config/communal-pets.json`:

```json
{
  "fallback_language": "zh_cn"
}
```

`fallback_language` is only a last resort (server console, command blocks, and client languages this mod
has no file for). Set it to `en_us` if your players are mostly English speakers.

## Supported

| | |
|---|---|
| Minecraft | **26.2** |
| Loader | Fabric Loader ≥ 0.19.3 + Fabric API |
| Java | ≥ 25 |
| Installed on | **server only** — clients may install it, but never need to |
| Pets | **wolves, cats, parrots** |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) and put
   [Fabric API](https://modrinth.com/mod/fabric-api) in the server's `mods/` folder.
2. Drop `communal-pets-<version>.jar` into the server's `mods/` folder.
3. Done — vanilla clients can connect and use everything.

## How it works without a client mod

For the curious, and for anyone worried about compatibility:

- The menus are vanilla `GENERIC_9x3` / `GENERIC_9x6` containers filled with vanilla item stacks;
  names, lore, counts, glint and player-head skins are plain data components. Clicks are handled on
  the server and answered with a full-state resync, so the client's local prediction can never move
  an item.
- The glow uses throwaway scoreboard teams: a fake team is created for the pet, and only the guardian
  group is told about it — vanilla clients then render the glow for those players only.
- All text is a `translatable` component whose fallback string is written in the reader's own language,
  so vanilla clients read it — and an open menu is redrawn as soon as the player changes language.

## Known limitations

- Inviting a name that is **not in the local name cache** makes the server ask Mojang, which can stall
  the server thread for a moment — the same thing `/op <unknown name>` does. Prefer names that have
  joined the server before.
- If a player installs this mod client-side **as well**, they may see an extra hand swing while acting
  as a caretaker. Cosmetic only.
- Changing the behaviour from the menu closes the menu (by design).

## License

GPL-3.0-or-later — free software: use it, change it, ship it; but a modified
version you distribute has to stay free software, source included. Source is on
[GitHub](https://github.com/Lunaunde/communal-pets).

<!--
  ============================================================================
  ⬇️⬇️⬇️  以  下  是  给  你  自  己  看  的  备  注  ，粘贴到 Modrinth 前请整段删掉  ⬇️⬇️⬇️
  ============================================================================

  一、Modrinth 短摘要（Summary 那个单行框，不是这个长描述框）：
      Share your tamed wolves, cats and parrots with friends: extra caretakers, four behaviour
      modes, in-game menus and ownership transfer — pure server-side, so vanilla clients can
      connect and use every menu.

  二、图片怎么落地：
      1. 先把截图/动图传到 Modrinth 的 Gallery（或任意图床）；
      2. 拿到 CDN 链接后替换正文里的 IMAGE_1_URL … IMAGE_6_URL；
      3. 如果懒得配 URL，就把 `![...](IMAGE_n_URL)` 那几行整行删掉 —— 但正文里配图的转化率
         比只放 Gallery 高很多，建议至少保留 IMAGE 1 和 IMAGE 2。
      4. Modrinth 的 Markdown 支持 `![alt](url)`；描述编辑器里也有插图按钮。

  三、拍摄清单（按重要性排序）：
      [1] 首图动图（5–8 秒）：蹲下右键 → 界面弹出 → 点一个行为按钮 → 界面关闭 → 宠物发光。
          这是唯一能让路人 3 秒内看懂的素材，值得花时间。
      [2] 表1 静态图：让"当前状态"那颗是混凝土、其它是玻璃，状态指示一眼可见。
      [3] 表0 + 表0.1：用另一个非抚养者账号截"申请"，再用被邀请账号截"同意/拒绝"。
          注意被邀请者必须在线才会收到聊天提示。
      [4] 表2：先加 2–3 个照顾者、并让 1–2 个玩家在线，左右面板都有头颅才好看。
      [5] 表3 或 表3.1：一张确认页即可。
      [6] 表4：另一个账号先申请，主人这边表1 的纸张会带附魔光泽，右键进表4 截图。
      [7] 可选：四种发光颜色的对比（夜里/暗处更容易看清）。

      拍摄小技巧：
      - 截图统一 16:9、1920×1080、PNG；F1 隐藏 HUD 更干净（但会连动作栏提示一起隐藏，
        想展示"已切换状态"的提示就别按 F1）。
      - 先把宠物用命名牌起个名字，"[主人] / [照顾者]" 那行文字才有辨识度。
      - 分页命名牌要 >20 个头才会出现，这个不用强求。
      - 发光颜色在暗环境或夜晚最明显；白天建议换个深色背景（比如石砖/深板岩墙）。
      - 聊天里的 [同意] / [拒绝] 记得把鼠标悬停在上面再截，能看出是可点击的。

  四、发布前自查（对应 Modrinth 内容规则 2.1，描述要"清楚、诚实"）：
      - 已写清：这是什么、解决什么问题、装哪一侧、支持哪些版本/生物、有什么限制 ✔
      - 没有夸大：不写 "the best" / "唯一" / "支持所有生物" ✔
      - 没有贬低其它 mod（那个竞品也存在，别指名对比）✔
      - 版本号在 Versions 标签页维护，正文里只写 "<version>" 占位，避免每次更新都要改正文 ✔
-->
