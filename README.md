# 🌌 AstraLogin
**A modern and lightweight login system for Minecraft servers.**

Created and maintained by **DawcoU** 👨‍💻

AstraLogin is a modern, high-performance login plugin built specifically for newer Minecraft versions (1.18 - 1.20+).
Developed in Java 17 and leveraging the Paper API for maximum efficiency, it is the perfect choice for Survival, RPG, and SMP servers.

---
### 🚀 Key Features

* **Advanced Security:** Password hashing using the **BCrypt** algorithm, ensuring no plain text is ever stored and protecting against dictionary attacks. 🛡️
* **Smart Brute-Force Protection:**
* Automatically kicks players after exceeding failed login attempts.
* **Margin of Error:** Configure extra "mercy" chances (margin) before a final action is taken.
* **Temporary IP Ban:** If the limit is reached, the user's IP is temporarily banned in RAM to prevent further attacks without bloating your database.

* **Session System:** Allows players to rejoin without re-typing their password within a configurable time limit (IP-based). 🕒
* **Equipment Protection:** Complete concealment of player inventory and armor during the login process to prevent exploits.
* **Location Protection:** Teleports unauthorized players to a secure location (or spawn) and restores their previous position only after successful login.
* **Inventory Storage:** Safely caches player items in a single-file system until they are authorized. 📦
* **Visual Effects:**
* **Blindness Effect:** Keeps unauthorized players in total darkness. 🌑
* **HEX Colors:** Full support for modern HEX color codes in the prefix and all messages. ✨

* **Admin Utilities:**
* **Auto-Updater:** Configuration files automatically update when you install a newer plugin version. 🛠️
* **Update Notifications:** Notifies admins when a new version is available on Modrinth.

---

### 🛠️ Commands & Permissions
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/register <pass> <repeat>` | Register a new account | *None* |
| `/login <pass>` | Log into the server | *None* |
| `/changepassword <old> <new>` | Change your current password | *None* |
| `/resetpassword <player>` | Deletes a player's account | `astralogin.resetpassword` |
| `/resetip <player>` | Resets the IP lock for a player | `astralogin.resetip` |
| `/astralogin reload` | Reloads the plugin config | `astralogin.reload` |

**Other Permissions:**
* `astralogin.updates` – Receive a notification when a new version is available. 📡

### 📥 Installation
1. Download the `.jar` file from [Modrinth](https://modrinth.com/plugin/astralogin).
2. Drop it into your `plugins` folder.
3. Restart your server.
4. Customize your messages in `config.yml`.

---

### 🌐 Support & Community
If you need help, want to report a bug, or follow the development by **DawcoU**, join our Discord: [https://discord.gg/XcmcPMJZMT]
