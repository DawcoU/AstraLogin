package pl.dawcou.astralogin.system;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import pl.dawcou.astralogin.auth.AstraLogin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SchedulerManager {

    private final AstraLogin plugin;
    private final boolean hasAsyncScheduler;
    private final boolean isFolia;

    public SchedulerManager(AstraLogin plugin) {
        this.plugin = plugin;
        this.hasAsyncScheduler = checkAsyncScheduler();
        this.isFolia = checkFolia();
    }

    @FunctionalInterface
    public interface Task {
        void cancel();
    }

    private boolean checkAsyncScheduler() {
        try {
            // Sprawdzamy czy metoda getAsyncScheduler istnieje w runtime serwera
            Bukkit.getServer().getClass().getMethod("getAsyncScheduler");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Wykonuje zadanie asynchronicznie natychmiast (działa na Paper/Folia oraz Spigot/Arclight)
     */
    public void runAsync(Runnable runnable) {
        if (hasAsyncScheduler) {
            Bukkit.getServer().getAsyncScheduler().runNow(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    /**
     * Wykonuje zadanie asynchronicznie cyklicznie.
     */
    public Task runAsyncRepeating(Consumer<Task> taskConsumer, long initialDelay, long period, TimeUnit unit) {
        if (hasAsyncScheduler) {
            // Wynik runAtFixedRate przekazujemy od razu do lambdy
            var paperTask = Bukkit.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin,
                    st -> {
                        Task taskHandle = st::cancel;
                        taskConsumer.accept(taskHandle);
                    },
                    initialDelay,
                    period,
                    unit
            );
            return paperTask::cancel;
        } else {
            long initialDelayTicks = unit.toSeconds(initialDelay) * 20;
            long periodTicks = unit.toSeconds(period) * 20;

            java.util.concurrent.atomic.AtomicReference<BukkitTask> bukkitTaskRef = new java.util.concurrent.atomic.AtomicReference<>();

            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                Task taskHandle = () -> {
                    if (bukkitTaskRef.get() != null) {
                        bukkitTaskRef.get().cancel();
                    }
                };
                taskConsumer.accept(taskHandle);
            }, initialDelayTicks, periodTicks);

            bukkitTaskRef.set(task);
            return task::cancel;
        }
    }

    /**
     * Uruchamia powtarzalne zadanie asynchroniczne (bez potrzeby anulowania wewnątrz).
     */
    public Task runAsyncRepeating(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        return runAsyncRepeating(task -> runnable.run(), initialDelay, period, unit);
    }

    /**
     * Wykonuje zadanie asynchronicznie z opóźnieniem w tickach (1 tick = 50ms)
     */
    public void runAsyncLater(Runnable runnable, long delayTicks) {
        if (hasAsyncScheduler) {
            Bukkit.getServer().getAsyncScheduler().runDelayed(
                    plugin,
                    task -> runnable.run(),
                    delayTicks * 50,
                    TimeUnit.MILLISECONDS
            );
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks);
        }
    }

    /**
     * Wykonuje zadanie synchronicznie na głównym wątku
     */
    public void runSync(Runnable runnable) {
        if (hasAsyncScheduler && isFolia) {
            Bukkit.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }
}