# Communal Pets

Share your tamed pets with friends: besides **the owner**, a pet can have **caretakers**. All of them
(owner + caretakers = **guardians**) can command the pet, switch its behaviour, and be protected by it.

Everything is **server-side only** — players with a vanilla client can join and use every feature,
including the in-game menus.

[![build](https://github.com/Lunaunde/communal-pets/actions/workflows/build.yml/badge.svg)](https://github.com/Lunaunde/communal-pets/actions/workflows/build.yml)

---

## English

### Requirements

| | |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | required |
| Java | ≥ 25 |
| Side | **server-side only** (installing it on the client is optional) |

### Features

- **Guardians: owner + caretakers.** Caretakers can cycle the pet's behaviour, order it around, and the
  pet will defend them and follow them exactly like it does for the owner.
- **Four behaviour modes**, switched by right-clicking a pet with an empty main hand:

  | mode | colour of the owner-group glow |
  |---|---|
  | follow commander (the last person who commanded it) | bright green |
  | follow nearest guardian | light blue |
  | wander (in a radius around a centre point) | orange |
  | sit | red |

  The glow is **only sent to the owner group** — everyone else sees a normal pet.
- **In-game chest menus.** `Sneak + right-click` a tamed pet to open a vanilla chest GUI built entirely
  out of vanilla items and components. No client mod, no custom textures, no resource pack.
- **Applications & invitations.** Strangers can apply to become a caretaker; the owner approves or
  rejects. The owner can invite online players from the menu, or offline players by command.
- **Ownership transfer** with confirmation screens: the new owner takes over, the old owner is demoted
  to caretaker.
- **Bilingual messages** (`en_us` / `zh_cn`), with a *fallback* so that vanilla clients — which do not
  have this mod's language files — still read real sentences instead of raw translation keys.

### Gestures

| gesture | who | what happens |
|---|---|---|
| right-click (empty main hand) | guardians | cycle the behaviour mode (plain taming / feeding is untouched) |
| sneak + right-click | anyone | open the pet's menu (see below) |

### The menus

| table | shown to | contents |
|---|---|---|
| 0 | non-guardians | pet info, **apply to become a caretaker**, and (op only) a debug entry into table 1 |
| 0.1 | players holding an invitation | **accept / reject** the invitation |
| 1 | guardians | pet info, 4 behaviour buttons, wander radius (name tag + nuggets), guardian heads, and the management paper |
| 2 | owner + op | 9×6 management panel: divider in the middle, left = owner & caretakers, right = online players you can invite |
| 3 | owner + op | confirm "kick from guardians" |
| 3.1 | owner + op | confirm "make this player the new owner" |
| 4 | owner + op | pending applications; left click allows, right click rejects |

Details worth knowing:

- **Table 1** — clicking a behaviour button applies it, shows the state in the action bar and **closes the
  menu** (as designed). The wander radius is adjusted with copper / iron / gold nuggets (±1 / ±4 / ±16)
  and is clamped to 1–99 in the GUI; the command can still go up to 1024.
- **Table 2** — left panel: left click a caretaker's head to kick them (table 3), right click to make
  them the new owner (table 3.1). The owner's own head is display-only. Right panel: click a head to
  invite, click again to withdraw. Both panels page with the name tags in the last row
  (left click = next page, right click = previous page).
- **Table 3 / 3.1** — lime pane confirms, red pane cancels; both return to table 2.
- **Table 4** — the paper in table 1 gets an enchantment glint while there are unhandled applications.

### Permissions

| action | owner | caretaker | op |
|---|---|---|---|
| use tables 0 / 0.1 | ✅ (as anyone else) | – | ✅ |
| open table 1 (behaviour, wander radius) | ✅ | ✅ | ✅ |
| open tables 2 / 3 / 3.1 / 4 (manage people, transfer ownership) | ✅ | ❌ | ✅ |
| accept/reject your own requests | ✅ | ✅ | ✅ |

**Caretakers cannot manage other caretakers.** That is deliberate.

### Requests, invitations and the 15-minute window

- A request/invitation is stored **on the pet**, so it survives restarts and stays visible in the menus
  **forever** until it is answered.
- `/communalpets accept` and `/communalpets reject` are shortcuts for the **most recent** request
  addressed to you, and only work for **15 minutes** after it was created. After that, answer it in the
  menu.
- Notifications are chat messages with clickable `[Accept]` / `[Reject]` buttons.

### Commands

| command | who | description |
|---|---|---|
| `/communalpets accept` \| `reject` | anyone | answer the most recent request addressed to you (15 min window) |
| `/communalpets <pet> caretaker list` | guardian or op | list owner + caretakers |
| `/communalpets <pet> caretaker add <player>` | op | add a caretaker directly |
| `/communalpets <pet> caretaker remove <player>` | op | remove a caretaker |
| `/communalpets <pet> caretaker invite <players>` | owner or op | invite players; **works with offline names** (resolved through the local name cache, same as `/op`) |
| `/communalpets <pet> behavior` | guardian or op | print the current behaviour |
| `/communalpets <pet> behavior set <follow_commander\|follow_nearest\|wander\|sit>` | op | set the behaviour |
| `/communalpets <pet> wander center [set <pos>]` | guardian / op (set) | read or set the wander centre |
| `/communalpets <pet> wander radius [set <value>]` | guardian / op (set) | read or set the wander radius (0 < r ≤ 1024) |

### Configuration

`config/communal-pets.json`:

```json
{
  "fallback_language": "zh_cn"
}
```

`fallback_language` is the language used for clients that **do not have this mod** (or a matching
resource pack): the server reads that file from its own resources and sends the text along with every
component. Clients that do have the mod still use their own language. Set it to `en_us` if your players
are mostly English speakers.

### How it works without a client mod

- **Menus** are vanilla `GENERIC_9x3` / `GENERIC_9x6` containers filled with vanilla item stacks; names,
  lore, counts, glint and player-head skins are plain data components. Clicks are intercepted server-side
  and answered with a full-state resync, so the client's local prediction never moves anything.
- **Glow** uses throwaway scoreboard teams: a fake team is created for the pet and only the owner group is
  told about it, so vanilla clients render the glow for those players only.
- **Text** uses `translatable` components with a fallback string embedded, so vanilla clients can read it.
- The one cosmetic thing that has to come back from the server is the hand swing of the menu gesture
  (one round trip); the menu is opened one tick later so that you still see your own swing.

### Known limitations

- Inviting a player name that is **not in the local name cache** makes the server ask Mojang, which can
  stall the server thread for a moment — exactly like `/op <unknown name>` does. Prefer names that have
  joined the server before.
- If a player installs this mod **on the client too**, they may see an extra hand swing when acting as a
  caretaker (cosmetic only: their client predicts one, the server sends one).
- Behaviour changes made from a menu close the menu (by design).

### Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2 and put
   [Fabric API](https://modrinth.com/mod/fabric-api) in the server's `mods/` folder.
2. Drop `communal-pets-1.0.0.jar` into the server's `mods/` folder.
3. Done — vanilla clients can connect and use everything.

### Build

```bash
./gradlew build          # jar lands in build/libs/
./gradlew runServer      # dev server
```

Requires JDK 25.

### License

CC0-1.0 — do whatever you want with it.

---

## 中文

把你驯服的宠物分享给朋友：除了 **主人**，宠物还可以有 **照顾者**。他们合起来叫 **抚养者**
（主人 + 照顾者），都能指挥宠物、切换行为，也会被宠物保护、被宠物跟随。

全部功能都是 **纯服务端** 的 —— 原版客户端可以直接进服并使用所有功能，包括那些界面。

### 环境要求

| | |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | 必需 |
| Java | ≥ 25 |
| 安装位置 | **只需装在服务端**（装在客户端是可选的） |

### 功能

- **抚养者体系（主人 + 照顾者）**：照顾者也能切行为、发号施令，宠物同样会保护他们、跟随他们。
- **四种行为状态**，空手右键宠物循环切换：

  | 状态 | 发光颜色（只有抚养者看得见） |
  |---|---|
  | 跟随发令人（最后一次指挥它的人） | 亮绿 |
  | 跟随最近的抚养者 | 浅蓝 |
  | 游荡（在中心点周围的半径内） | 橙 |
  | 坐下 | 红 |

- **箱子界面**：`蹲下 + 右键` 已驯服的宠物，打开一个完全用原版物品和组件拼出来的原版箱子 GUI。
  不需要客户端装模组、不需要贴图包。
- **申请与邀请**：陌生人可以申请成为照顾者，由主人批准 / 拒绝；主人可以在界面里邀请在线玩家，
  也可以用指令邀请离线玩家。
- **换主人**：带二次确认；新主人上任，原主人自动降级为照顾者。
- **中英双语**：没装模组的原版客户端也能看到正常文本（服务端把兜底文本随组件一起发过去）。

### 操作手势

| 手势 | 谁 | 效果 |
|---|---|---|
| 空手右键 | 抚养者 | 循环切换行为状态（驯服 / 喂食等原版行为不受影响） |
| 蹲下 + 右键 | 任何人 | 打开该宠物的界面（见下） |

### 七张界面（表）

| 表 | 给谁看 | 内容 |
|---|---|---|
| 0 | 非抚养者 | 宠物信息、**申请成为照顾者**、（仅 op）进入表1 的调试入口 |
| 0.1 | 收到邀请的人 | **同意 / 拒绝** 邀请 |
| 1 | 抚养者 | 宠物信息、4 个行为按钮、游荡半径（命名牌 + 铜/铁/金粒）、抚养者头颅、管理纸张 |
| 2 | 主人 + op | 9×6 管理面板：中间一列分界线，左=主人与照顾者，右=可邀请的在线玩家 |
| 3 | 主人 + op | 确认"踢出抚养者" |
| 3.1 | 主人 + op | 确认"设为新主人" |
| 4 | 主人 + op | 待处理申请，左键允许、右键拒绝 |

几个细节：

- **表1**：点行为按钮会应用状态、在动作栏提示，并**关闭界面**（按设计）。游荡半径用
  铜粒 / 铁粒 / 金粒增减（±1 / ±4 / ±16），界面内夹在 1~99；指令仍可设到 1024。
- **表2**：左面板左键踢人（表3）、右键设为新主人（表3.1），主人自己那颗头颅只读；
  右面板点一下邀请、再点一下撤回。两个面板的最后一行命名牌翻页（左键下一页、右键上一页）。
- **表3 / 3.1**：黄绿玻璃板确定、红色玻璃板取消，都会回到表2。
- **表4**：有未处理申请时，表1 里那张纸会带附魔光泽。

### 权限

| 操作 | 主人 | 照顾者 | op |
|---|---|---|---|
| 使用表0 / 表0.1 | ✅（和其他人一样） | – | ✅ |
| 打开表1（行为、游荡半径） | ✅ | ✅ | ✅ |
| 打开表2 / 3 / 3.1 / 4（管人、换主人） | ✅ | ❌ | ✅ |
| 应答发给自己的申请 / 邀请 | ✅ | ✅ | ✅ |

**照顾者没有管理照顾者的权力**，这是有意为之。

### 申请、邀请与 15 分钟窗口

- 申请 / 邀请保存在**宠物实体**上，重启不丢，在界面里**长期有效**，直到被处理。
- `/communalpets accept`、`/communalpets reject` 是"应答发给你的**最后一条**请求"的快捷方式，
  只在请求产生后的 **15 分钟**内可用；超时就去界面里点。
- 收到请求时会发聊天提示，带可点击的 `[同意]` / `[拒绝]`。

### 指令

| 指令 | 谁能用 | 说明 |
|---|---|---|
| `/communalpets accept` \| `reject` | 任何人 | 应答发给自己的最后一条请求（15 分钟内） |
| `/communalpets <宠物> caretaker list` | 抚养者 / op | 列出主人与照顾者 |
| `/communalpets <宠物> caretaker add <玩家>` | op | 直接加照顾者 |
| `/communalpets <宠物> caretaker remove <玩家>` | op | 移除照顾者 |
| `/communalpets <宠物> caretaker invite <玩家>` | 主人 / op | 邀请，**支持离线玩家名**（走本地名字缓存，和 `/op` 一样） |
| `/communalpets <宠物> behavior` | 抚养者 / op | 查看当前状态 |
| `/communalpets <宠物> behavior set <follow_commander\|follow_nearest\|wander\|sit>` | op | 设置状态 |
| `/communalpets <宠物> wander center [set <坐标>]` | 抚养者 / op | 查看 / 设置游荡中心 |
| `/communalpets <宠物> wander radius [set <值>]` | 抚养者 / op | 查看 / 设置游荡半径（0 < r ≤ 1024） |

### 配置

`config/communal-pets.json`：

```json
{
  "fallback_language": "zh_cn"
}
```

`fallback_language` 是**没装本模组**（也没有对应语言资源包）的客户端看到的语言：服务端从自己资源里
读这个语言文件，把文本随组件一起发出去。装了模组的客户端仍然按自己的语言显示。

### 为什么不需要客户端模组

- **界面**用的是原版 `GENERIC_9x3` / `GENERIC_9x6` 容器，里面塞的是原版物品；名称、Lore、数量、
  附魔光泽、玩家头颅皮肤都是普通的数据组件。点击由服务端接管并回发整屏同步，客户端的本地预测动不了东西。
- **发光**用一次性的计分板队伍实现：只把宠物所在的假队伍发给抚养者，原版客户端就只对他们渲染发光。
- **文本**用带兜底字符串的 `translatable` 组件，原版客户端也能读懂。
- 唯一必须由服务端补的是开界面那一下的**挥手**（晚 1 个 RTT），并且界面会**延迟一 tick** 打开，
  让你自己的视角也能看到挥舞。

### 已知限制

- 用**不在本地名字缓存里**的玩家名邀请，服务端会去问 Mojang，可能卡住服务端线程一小会儿 ——
  和 `/op 不存在的名字` 的行为一样。建议用进过服的名字。
- 如果玩家**在客户端也装了**本模组，他作为照顾者时可能多挥一次手（纯观感：客户端预测一次、
  服务端再发一次）。
- 界面里点行为按钮会关闭界面（按设计）。

### 安装

1. 给 Minecraft 26.2 装好 [Fabric Loader](https://fabricmc.net/use/)，并把
   [Fabric API](https://modrinth.com/mod/fabric-api) 放进服务端的 `mods/`。
2. 把 `communal-pets-1.0.0.jar` 放进服务端的 `mods/`。
3. 完成 —— 原版客户端可以直接连进来使用全部功能。

### 构建

```bash
./gradlew build          # 产物在 build/libs/
./gradlew runServer      # 开发用服务端
```

需要 JDK 25。

### 许可

CC0-1.0，随便用。
