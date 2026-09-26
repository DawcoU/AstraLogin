# 🌌 AstraLogin
**A user-friendly, efficient, and reliable login system designed for modern Minecraft servers.**

Created and maintained with 💻 from Poland by DawcoU 🇵🇱

AstraLogin is a modern, high-performance login plugin built specifically for newer Minecraft versions (1.18 - 26.2+).
Developed in Java 17 and leveraging the Paper API for maximum efficiency, it is the perfect choice for Survival, RPG, and SMP servers. 🚀🎮

⭐ AstraLogin supports:
- Paper
- Purpur
- Folia
- Arclight (supported, but not recommended)
- Spigot (supported, but not recommended)

🚰⚠️ Spigot is supported, but it is not the recommended platform.
Some features may behave differently or cause compatibility issues.
Paper or its forks are strongly recommended for the best experience.

---

### 🚀 Key Features

* **Advanced Security:** Password hashing using the **BCrypt** or **Argon2id** algorithm, ensuring no plain text is ever stored and protecting against dictionary attacks. 🛡️🔑🔐💻
* **Suspicious Activity Alerts:** Sends real-time notifications to administrators with permissions when suspicious actions occur, such as a player logging in from an unknown IP address completely different from their saved profile IP. 🚨🌐⚠️🛡️
* **Strict Input Validation:** Prevents players from registering with unauthorized characters, emojis, or special symbols. Includes a configurable regex pattern and pre-built rules to block weak passwords, such as those made entirely of letters or entirely of numbers. 🛑🔤🚫⚠️
* **Detailed Event Logging:** All key server events, including player logins, suspicious access attempts, and administrative actions, are logged to dedicated files in the `logs` folder inside the plugin directory. 📁📝📜🔍
* **Smart Brute-Force Protection:** 🚫🤖🔨
  * **Failed Attempts Limit:** Automatically kicks players after exceeding allowed login attempts. 🥾❌
  * **Margin of Error:** Configure extra "mercy" chances (margin) before a final action is taken. ⏳🛡️
  * **IP Ban Protection:** If the limit is reached, the user's IP is banned to prevent further attacks without bloating your database. 🚫🛡️🌐🔒

* **Two-Factor Authentication (2FA):** Next-level account security! Players can link their accounts to authenticator apps to protect themselves from hackers. 📱🔐📲🛡️
* **Account Management & Statistics:** Comprehensive tracking of player data, including registration/last login dates, 2FA status, UUIDs, and more! 📊🔍📈👤
* **Session System:** Allows players to rejoin without re-typing their password within a configurable time limit (IP-based). 🕒🔄💻
  * **Dedicated 2FA Sessions:** Separately manages 2FA verification, allowing players to authenticate their 2FA code periodically (e.g., once every 2 days) independently from the main login session. 📱🕒🔐⌛

* **In-Game Player Protection:** 🛡️🌍
  * **Equipment Protection:** Complete concealment of player inventory and armor during the login process to prevent exploits and unauthorized spying on other players' gear. 🎒🗡️🦺
  * **Location Protection:** Teleports unauthorized players to a secure location (or spawn) and restores their previous position only after successful login, preventing coordinate leaks and exploits. 📍🌍🗺️✨
  * **Inventory Storage:** Temporarily holds player items during the login process until they are authorized. 📦💾🔒

* **Visual Effects:** 🎨✨🎆
  * **Blindness Effect:** Keeps unauthorized players in total darkness. 🌑🕶️
  * **HEX Colors:** Full support for modern HEX color codes in the prefix and all messages via MiniMessage/Legacy! 🌈🎨🖌️
  * **Interactive Sound Effects:** Plays custom, configurable sound effects during various player actions (e.g., successful login, wrong password entry, invalid password format) to enhance the user experience. 🔊🎶✨

* **Localization & Multilingual Support:** Native built-in language files with full translation support for **English**, **Polish**, **Spanish**, and **German**. 🌐🇵🇱🇬🇧🇪🇸🇩🇪

* **Admin Utilities:** 🛠️⚙️🧰
  * **Auto-Updater:** Configuration files automatically update when you install a newer plugin version. 🔄⚡
  * **Update Notifications:** Notifies administrators when a new version is available, with warnings about pre-release versions, newer versions, and available patches. 🛜🔔⚠️

* **AutoLogin Premium:** Automatic login system for premium accounts: 🌟👤💻
  * **FULL Mode:** Secure, complete Mojang authentication handshake. 🔒✅
  * **MINI Mode:** Prototype mode that checks nickname existence in Mojang database without full authentication, allowing cracked logins with that nickname without a password. 🧪🔓

* **Password Recovery:** Using the PIN as a separate source of authentication confirmation, you can reset your password yourself. 🔑🔄📌🛡️
* **Security Reminders:** Periodically reminds players to boost their account security by enabling 2FA or setting up a recovery PIN. 🔔🛡️⏰📢

---

### 🛠️ Commands & Permissions

| Command                                                      | Description                                                                     | Permission                                                                                                                |
|:-------------------------------------------------------------|:--------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------|
| `/register <pass> <repeat>`                                  | Register a new account 🔐                                                       | *None*                                                                                                                    |
| `/login <pass>`                                              | Log into the server 🔑                                                          | *None*                                                                                                                    |
| `/logout`                                                    | Log out of the server ➡️                                                        | *None*                                                                                                                    |
| `/pin <set> <PIN/Automatically>`                             | Generates a random PIN or requires manual entry (Depending on configuration) ➡️ | *None*                                                                                                                    |
| `/changepassword <old> <new> <repeat>`                       | Change your current password 🔄                                                 | *None*                                                                                                                    |
| `/2fa <setup/unsetup/code>`                                  | Manage and verify your 2FA security 📱                                          | *None*                                                                                                                    |
| `/forgotpass <PIN>`                                          | Resets password using pin 🔑🔄                                                  | *None*                                                                                                                    |
| `/account <player>`                                          | View advanced account stats (UUID, IP, 2FA, dates) 📊                           | `astralogin.account`                                                                                                      |
| `/accountslist`                                              | View all AstraLogin accounts 👥                                                 | `astralogin.accountslist`                                                                                                 |
| `/resetpassword <player>`                                    | Deletes a player's password ❌                                                   | `astralogin.resetpassword`                                                                                                |
| `/resetip <player>`                                          | Resets the player's IP address 🌐                                               | `astralogin.resetip`                                                                                                      |
| `/resetpin <player>`                                         | Resets the player's PIN 🔑❌                                                     | `astralogin.resetpin`                                                                                                     |
| `/resetaccount <player>`                                     | **Wipes all player data** (password, IP, location, 2FA) ⚠️                      | `astralogin.resetaccount`                                                                                                 |
| `/moveaccount <old player> <new player>`                     | Transfers all AstraLogin player data to another account ▶️                      | `astralogin.moveaccount`                                                                                                  |
| `/loginspawn <setspawn/delspawn> <before_login/after_login>` | Sets and removes the selected login spawn 🗺️                                   | `astralogin.setspawn`, `astralogin.delspawn`                                                                              |
| `/astralogin reload`                                         | Reloads the plugin configuration ⚙️                                             | `astralogin.reload`                                                                                                       |
| `/iptrust <info/set/reset> <IP> <score>`                     | Shows IP reputation, resets, and sets 📈📉                                      | `astralogin.iptrust.info`, `astralogin.iptrust.set`, `astralogin.iptrust.reset`                                           |
| `/ipmanager <info/unban/bypass/unbypass> <player>`           | Manage IP bans and bypass status for players 🌐🛡️                              | `astralogin.ipmanager.info`, `astralogin.ipmanager.unban`, `astralogin.ipmanager.bypass`, `astralogin.ipmanager.unbypass` |

**Other Permissions:**
* `astralogin.update` – Receive a notification when a new version is available. 📡🔔
* `astralogin.alerts` – Sends alerts about suspicious player activity 🔔👁️

---

### 📥 Installation

1. Download the `.jar` file from [Modrinth](https://modrinth.com/plugin/astralogin). 📥
2. Drop it into your `plugins` folder. 📂
3. Restart your server. 🔄
4. Customize your messages in the languages folder and settings in `config.yml`. 📝⚙️

---

### 🛠️ Other Projects
🛡️ **[AstraRedstoneSystems](https://modrinth.com/plugin/astraredstonesystems)** - Check out my other plugin! It's an advanced redstone mechanics system with number gates and more!

---

# Links 💾

**GitHub AstraLogin:** [GitHub](https://github.com/DawcoU/AstraLogin) 🖥️

---

### 🌐 Support & Community
If you need help, want to report a bug, or follow the development by **DawcoU**, join our official Discord community:  
🔗 **[Join our Discord Server](https://discord.gg/XcmcPMJZMT)** 💬👥