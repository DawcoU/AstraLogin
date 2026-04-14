# 🌌 AstraLogin
**A modern and lightweight login system for Minecraft servers.**

Created and maintained by **DawcoU** 👨‍💻

AstraLogin is a login plugin created for newer versions of Minecraft 1.18-1.21 ⭐ written in Java 17 and intended primarily for RPG and Survival servers.

---

### 🚀 Key Features
* **Security:** Password hashing using the SHA-256 algorithm (no plain text in files).
* **Equipment Protection:** Hiding player inventory when logging in
* **Location Protection:** Teleporting the player to the location set by the admin and teleporting after logging in to the last location or spawn
* **Session:** You return without logging in after leaving for a while
* **Inventory Storage:** Safely hides player inventory and armor until login (Single-file storage). 📦
* **HEX Colors:** Full support for HEX colors in the prefix and all messages. ✨
* **Auto-Updater:** Automatically updates the configuration file when the plugin version changes. 🛠️
* **Update Notifications:** Admin notifications about new versions available on Modrinth.
* **Brute-Force Protection:** Automatically kicks players after exceeding failed login attempts.
* **Blindness Effect:** Keeps unauthorized players in total darkness until they log in. 🌑
* **Session System:** Allows players to rejoin without re-typing their password (IP-based).

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
