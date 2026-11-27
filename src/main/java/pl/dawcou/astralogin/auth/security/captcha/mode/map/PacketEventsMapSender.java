
package pl.dawcou.astralogin.auth.security.captcha.mode.map;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import org.bukkit.entity.Player;
import pl.dawcou.astralogin.auth.security.captcha.generator.MapCaptchaGenerator;

import java.util.Collections;

public class PacketEventsMapSender {

    public void sendMap(
            Player player,
            int mapId,
            MapCaptchaGenerator.ChallengeResult challenge
    ) {
        WrapperPlayServerMapData packet = new WrapperPlayServerMapData(
                mapId,
                (byte) 0,
                false,
                true,
                Collections.emptyList(),
                128,
                128,
                0,
                0,
                challenge.mapData()
        );

        PacketEvents.getAPI()
                .getPlayerManager()
                .sendPacket(player, packet);
    }
}
