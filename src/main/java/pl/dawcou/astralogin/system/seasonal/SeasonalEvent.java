package pl.dawcou.astralogin.system.seasonal;

import java.util.List;

// ---------------------------------------------------------------- //
// SeasonalEvent - Contract for all seasonal plugin events
// ---------------------------------------------------------------- //
public interface SeasonalEvent {

    String getName();
    String getId();

    List<String> getSounds();

    String getRandomSound();
}