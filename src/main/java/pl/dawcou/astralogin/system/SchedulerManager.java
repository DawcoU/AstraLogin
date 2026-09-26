package pl.dawcou.astralogin.system;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import pl.dawcou.astralogin.AstraLogin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SchedulerManager {

    private final AstraLogin plugin;

    private final boolean hasPaperAsyncApi;
    private final boolean isFolia;
    private final boolean hasTeleportAsync;

    public SchedulerManager(AstraLogin plugin) {
        this.plugin = plugin;
        this.hasPaperAsyncApi = checkAsyncScheduler();
        this.isFolia = checkFolia();
        this.hasTeleportAsync = checkTeleportAsync();
    }

    // Metoda sprawdzająca, czy serwer pozwala na mechanizmy Folii
    public boolean isFolia() {
        return isFolia;
    }

    @FunctionalInterface
    public interface Task {
        void cancel();
    }

    private boolean checkAsyncScheduler() {
        try {
            Server.class.getMethod("getAsyncScheduler");
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

    private boolean checkTeleportAsync() {
        try {
            Entity.class.getMethod("teleportAsync", Location.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Executes a task for a specific entity on its region thread (Folia compatible).
     */
    public void runForEntity(Entity entity, Runnable runnable) {
        if (isFolia) {
            entity.getScheduler().run(plugin, task -> runnable.run(), null);
        } else {
            runSync(runnable);
        }
    }

    /**
     * Executes a delayed task for a specific entity on its region thread (Folia compatible).
     */
    public Task runForEntityLater(Entity entity, Runnable runnable, long delayTicks) {
        if (isFolia) {
            var foliaTask = entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, delayTicks);
            return foliaTask != null ? foliaTask::cancel : () -> {};
        } else {
            return runSyncLater(runnable, delayTicks);
        }
    }

    /**
     * Safe teleportation logic for Spigot, Paper, and Folia.
     */
    public void teleport(Entity entity, Location location) {
        if (isFolia || hasTeleportAsync) {
            entity.teleportAsync(location);
        } else {
            entity.teleport(location);
        }
    }

    /**
     * Executes a task asynchronously immediately.
     */
    public void runAsync(Runnable runnable) {
        if (hasPaperAsyncApi) {
            Bukkit.getServer().getAsyncScheduler().runNow(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    /**
     * Executes a task asynchronously with repeating period.
     */
    public Task runAsyncRepeating(Consumer<Task> taskConsumer, long initialDelay, long period, TimeUnit unit) {
        if (hasPaperAsyncApi) {
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

    public Task runAsyncRepeating(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        return runAsyncRepeating(task -> runnable.run(), initialDelay, period, unit);
    }

    /**
     * Executes a task asynchronously with delay in ticks.
     */
    public void runAsyncLater(Runnable runnable, long delayTicks) {
        if (hasPaperAsyncApi) {
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
     * Executes a repeating task synchronously (GlobalRegionScheduler on Folia).
     */
    public Task runSyncRepeating(Consumer<Task> taskConsumer, long initialDelayTicks, long periodTicks) {
        if (hasPaperAsyncApi && isFolia) {
            var foliaTask = Bukkit.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    st -> {
                        Task taskHandle = st::cancel;
                        taskConsumer.accept(taskHandle);
                    },
                    initialDelayTicks,
                    periodTicks
            );
            return foliaTask::cancel;
        } else {
            java.util.concurrent.atomic.AtomicReference<BukkitTask> bukkitTaskRef = new java.util.concurrent.atomic.AtomicReference<>();

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
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

    public Task runSyncRepeating(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return runSyncRepeating(task -> runnable.run(), initialDelayTicks, periodTicks);
    }

    /**
     * Executes a delayed synchronous task (GlobalRegionScheduler on Folia).
     */
    public Task runSyncLater(Runnable runnable, long delayTicks) {
        if (hasPaperAsyncApi && isFolia) {
            var foliaTask = Bukkit.getServer().getGlobalRegionScheduler().runDelayed(
                    plugin,
                    task -> runnable.run(),
                    delayTicks
            );
            return foliaTask::cancel;
        } else {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
            return task::cancel;
        }
    }

    /**
     * Executes a synchronous task on the global main thread / global region.
     */
    public void runSync(Runnable runnable) {
        if (hasPaperAsyncApi && isFolia) {
            Bukkit.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }
}