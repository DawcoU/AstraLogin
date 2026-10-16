package pl.dawcou.astralogin.system.seasonal.events;

import pl.dawcou.astralogin.system.seasonal.SeasonalEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// ---------------------------------------------------------------- //
// HalloweenEvent - Handles Halloween event sound pool and state
// ---------------------------------------------------------------- //
public class HalloweenEvent implements SeasonalEvent {

    private final List<String> sounds;

    public HalloweenEvent(List<String> sounds) {
        this.sounds = sounds;
    }

    @Override
    public String getName() {
        return "Halloween";
    }

    @Override
    public String getId() {
        return "halloween";
    }

    @Override
    public List<String> getSounds() {
        return sounds;
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