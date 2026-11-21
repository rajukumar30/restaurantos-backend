package com.restaurantos.modules.realtime.service;

import com.restaurantos.modules.realtime.dto.RealtimeEvent;

public interface RealtimeEventPublisher {
    void publishAfterCommit(RealtimeEvent event);
    void publishServiceRequestAfterCommit(RealtimeEvent event);
}
