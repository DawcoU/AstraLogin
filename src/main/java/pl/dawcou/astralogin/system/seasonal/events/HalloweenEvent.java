package pl.dawcou.astralogin.system.seasonal.events;

import pl.dawcou.astralogin.system.seasonal.SeasonalEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// ---------------------------------------------------------------- //
// HalloweenEvent - Handles Halloween event sound pool and state
// ---------------------------------------------------------------- //
public record HalloweenEvent(List<String> sounds) implements SeasonalEvent {

    @Override
    public String getName() {
        return "Halloween";
    }

    @Override
    public String getId() {
        return "halloween";
    }

    @Override
    public String getRandomSound() {
        if (sounds == null || sounds.isEmpty()) {
            return null;
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(sounds.size());
        return sounds.get(randomIndex);
    }
}