package pl.dawcou.astralogin.system.seasonal;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.AstraLogin;
import pl.dawcou.astralogin.system.seasonal.events.HalloweenEvent;
import pl.dawcou.astralogin.system.utils.TimeUtils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// ---------------------------------------------------------------- //
// SeasonalEventManager - Handles date checking and event collisions
// ---------------------------------------------------------------- //
public class SeasonalEventManager {

    private final AstraLogin plugin;
    private SeasonalEvent activeEvent;

    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

    public SeasonalEventManager(AstraLogin plugin) {
        this.plugin = plugin;
        loadEvents();
    }

    public void loadEvents() {
        this.activeEvent = null;
        FileConfiguration config = plugin.getConfig();

        if (!config.getBoolean("seasonal-events.enabled", true)) {
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("seasonal-events.events");
        if (section == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        List<SeasonalEvent> matchedEvents = new ArrayList<>();

        for (String eventKey : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(eventKey);
            if (eventSection == null || !eventSection.getBoolean("enabled", true)) {
                continue;
            }

            String dateRange = eventSection.getString("date-range", "");
            if (isDateInRange(today, dateRange)) {
                SeasonalEvent event = createEventInstance(eventKey, eventSection);
                if (event != null) {
                    matchedEvents.add(event);
                }
            }
        }

        // Safety check: Prevent multiple active events simultaneously
        if (matchedEvents.size() > 1) {
            plugin.getLogger().warning("[SeasonalEvents] Conflict detected! Multiple seasonal events match current server date:");
            for (SeasonalEvent event : matchedEvents) {
                plugin.getLogger().warning(" - " + event.getName());
            }
            plugin.getLogger().warning("[SeasonalEvents] Event loading aborted to prevent collisions! No seasonal event will be active.");
            return;
        }

        if (matchedEvents.size() == 1) {
            this.activeEvent = matchedEvents.get(0);
            plugin.getNoticeManager().sendSeasonalEventActivated(Objects.requireNonNull(activeEvent).getName());
        }
    }

    private SeasonalEvent createEventInstance(String eventKey, ConfigurationSection section) {
        List<String> sounds = section.getStringList("sounds");
        if (eventKey.equalsIgnoreCase("halloween")) {
            return new HalloweenEvent(sounds);
        }
        return null;
    }

    private boolean isDateInRange(LocalDate today, String range) {
        if (range == null || !range.contains("-")) {
            return false;
        }

        try {
            String[] parts = range.split("-");
            if (parts.length != 2) {
                return false;
            }

            String[] startParts = parts[0].trim().split("\\.");
            String[] endParts = parts[1].trim().split("\\.");

            if (startParts.length != 2 || endParts.length != 2) {
                return false;
            }

            int startDay = Integer.parseInt(startParts[0]);
            int startMonth = Integer.parseInt(startParts[1]);
            int endDay = Integer.parseInt(endParts[0]);
            int endMonth = Integer.parseInt(endParts[1]);

            // Validate real calendar date bounds (throws DateTimeException on invalid month/day)
            MonthDay.of(startMonth, startDay);
            MonthDay.of(endMonth, endDay);

            int currentVal = today.getMonthValue() * 100 + today.getDayOfMonth();
            int startVal = startMonth * 100 + startDay;
            int endVal = endMonth * 100 + endDay;

            if (startVal <= endVal) {
                return currentVal >= startVal && currentVal <= endVal;
            } else {
                // Support range spanning across new year (e.g. 20.12-05.01)
                return currentVal >= startVal || currentVal <= endVal;
            }
        } catch (DateTimeException | NumberFormatException e) {
            plugin.getLogger().warning("[SeasonalEvents] Invalid date or format: '" + range + "'. Expected format: DD.MM-DD.MM with valid calendar dates!");
            return false;
        }
    }

    // ---------------------------------------------------------------- //
    // Send seasonal message to player if event is active and cooldown allows it
    // ---------------------------------------------------------------- //
    public void sendEventNotice(Player player) {
        if (activeEvent == null) {
            return;
        }

        String path = "seasonal-events." + activeEvent.getId().toLowerCase();
        List<String> messages = plugin.getLanguageManager().getMessageList(path);

        if (messages == null || messages.isEmpty()) {
            return;
        }

        String rawTime = plugin.getConfig().getString(
                "seasonal-events.message-cooldown",
                "10 minutes"
        );

        long cooldownMillis = Math.max(
                0L,
                TimeUtils.parseTime(rawTime, 600_000L)
        );

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastSent = lastMessageTime.get(playerId);

        if (lastSent != null && now - lastSent < cooldownMillis) {
            return;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(messages.size());
        String randomMessage = messages.get(randomIndex);

        player.sendMessage(randomMessage);
        lastMessageTime.put(playerId, now);
    }

    public SeasonalEvent getActiveEvent() {
        return activeEvent;
    }

    public boolean hasActiveEvent() {
        return activeEvent != null;
    }
}