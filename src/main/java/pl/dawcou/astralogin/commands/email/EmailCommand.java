package pl.dawcou.astralogin.commands.email;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorManager;
import pl.dawcou.astralogin.auth.security.twofactor.TwoFactorMethod;
import pl.dawcou.astralogin.system.SoundManager;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EmailCommand implements CommandExecutor, TabCompleter {

    private final AstraLogin plugin;
    private final TwoFactorManager twoFactorManager;

    public EmailCommand(AstraLogin plugin, TwoFactorManager twoFactorManager) {
        this.plugin = plugin;
        this.twoFactorManager = twoFactorManager;
    }

    // --------------------------------------------------
    // Command routing
    // --------------------------------------------------

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.usage"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        String action = args[0];
        UUID uuid = player.getUniqueId();

        if (action.equalsIgnoreCase("set")) {
            if (args.length != 2) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.usage"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }
            if (twoFactorManager.getEmailManager().hasEmailAddress(uuid)) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.already-set"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }

            return setEmail(player, uuid, args[1]);
        }

        else if (action.equalsIgnoreCase("del")) {
            boolean confirmed = args.length == 2 && args[1].equalsIgnoreCase("confirm");
            if (args.length != 1 && !confirmed) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.usage"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }
            return deleteEmail(player, uuid, confirmed);
        }

        player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.usage"));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
        return true;
    }

    // --------------------------------------------------
    // Email set and delete
    // --------------------------------------------------

    private boolean setEmail(Player player, UUID uuid, String email) {
        // Never allow changing the destination while MAIL 2FA protects the account.
        if (twoFactorManager.has2FA(uuid) && twoFactorManager.getMethod(uuid) == TwoFactorMethod.MAIL) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.mail-2fa-active"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        if (isVerificationPending(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.setup-active"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        if (!twoFactorManager.getEmailManager().setEmailAddress(uuid, email)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.invalid-address"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.set-success").replace("%email%", email.trim()));
        plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
        plugin.getLogManager().log("Player " + player.getName() + " updated their email address");
        return true;
    }

    private boolean deleteEmail(Player player, UUID uuid, boolean confirmed) {
        String currentEmail = twoFactorManager.getEmailManager().getEmailAddress(uuid);
        if (currentEmail == null || currentEmail.isBlank()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.not-set"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        if (isVerificationPending(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.setup-active"));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        if (!confirmed) {
            String baseMsgStr = plugin.getLanguageManager().getWithPrefix("mail.delete.confirm")
                    .replace("%email%", currentEmail);
            String btnTextStr = plugin.getLanguageManager().getMessage("mail.delete.button");
            String hoverTextStr = plugin.getLanguageManager().getMessage("mail.delete.hover")
                    .replace("%email%", currentEmail);

            Component baseMsg = LegacyComponentSerializer.legacySection()
                    .deserialize(baseMsgStr + " ");

            Component confirmBtn = LegacyComponentSerializer.legacySection()
                    .deserialize(btnTextStr)
                    .clickEvent(ClickEvent.runCommand("/email del confirm"))
                    .hoverEvent(LegacyComponentSerializer.legacySection().deserialize(hoverTextStr));

            plugin.getAdventure().player(player).sendMessage(baseMsg.append(confirmBtn));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
            return true;
        }

        boolean mailTwoFactorActive = twoFactorManager.has2FA(uuid)
                && twoFactorManager.getMethod(uuid) == TwoFactorMethod.MAIL;

        // TwoFactorManager automatically disables MAIL 2FA when deleting the email address while TOTP stays enabled.
        twoFactorManager.getEmailManager().deleteEmailAddress(uuid);

        if (mailTwoFactorActive) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.deleted-mail-disabled"));
        } else {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("mail.deleted"));
        }

        plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);
        plugin.getLogManager().log("Player " + player.getName() + " deleted their email address");
        return true;
    }

    // --------------------------------------------------
    // Setup state check
    // --------------------------------------------------

    private boolean isVerificationPending(UUID uuid) {
        return twoFactorManager.getTotpManager().isSetupActive(uuid)
                || twoFactorManager.getEmailManager().hasPendingCode(uuid);
    }

    // --------------------------------------------------
    // Tab completion
    // --------------------------------------------------

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, Command command, @NonNull String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("email") || !command.getName().equalsIgnoreCase("mail")) {
            return null;
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Stream.of("set", "del")
                    .filter(value -> value.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}