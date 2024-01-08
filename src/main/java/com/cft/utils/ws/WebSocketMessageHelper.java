package com.cft.utils.ws;

import com.cft.config.WebSocketConfig;
import com.cft.entities.*;
import com.cft.entities.ws.SimpleWSUpdate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

public class WebSocketMessageHelper {
    public static <T> void sendUpdate(SimpMessagingTemplate wsTemplate, String endpoint, SimpleWSUpdate.UpdateOrigin origin,
                                   SimpleWSUpdate.UpdateType updateType, T data) {

        wsTemplate.convertAndSend(endpoint, new SimpleWSUpdate<>(origin, updateType, data));
    }

    public static void sendFighterUpdate(SimpMessagingTemplate wsTemplate, SimpleWSUpdate.UpdateOrigin origin,
                                         SimpleWSUpdate.UpdateType updateType, Fighter fighter) {

        sendUpdate(wsTemplate, WebSocketConfig.FIGHTER_ENDPOINT, origin, updateType, fighter);
    }

    public static void sendFightUpdate(SimpMessagingTemplate wsTemplate, SimpleWSUpdate.UpdateOrigin origin,
                                         SimpleWSUpdate.UpdateType updateType, Fight fight) {

        sendUpdate(wsTemplate, WebSocketConfig.FIGHT_ENDPOINT, origin, updateType, fight);
    }

    public static void sendEventUpdate(SimpMessagingTemplate wsTemplate, SimpleWSUpdate.UpdateOrigin origin,
                                       SimpleWSUpdate.UpdateType updateType, CFTEvent event) {

        sendUpdate(wsTemplate, WebSocketConfig.EVENT_ENDPOINT, origin, updateType, event);
    }
}
