package com.restaurantos.modules.realtime.service.impl;

import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RealtimeEventPublisherImpl implements RealtimeEventPublisher {
    private final SimpMessagingTemplate messaging;

    public RealtimeEventPublisherImpl(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publishAfterCommit(RealtimeEvent event) {
        publish(event, "orders");
    }

    @Override
    public void publishServiceRequestAfterCommit(RealtimeEvent event) {
        publish(event, "service-requests");
    }

    private void publish(RealtimeEvent event, String topic) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Realtime events must be registered inside a transaction");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messaging.convertAndSend("/topic/tenants/" + event.tenantId()
                        + "/branches/" + event.branchId() + "/" + topic, event);
            }
        });
    }
}
