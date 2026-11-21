package com.restaurantos.modules.realtime.service.impl;

import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.orders.repository.OrderQueryDao;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RealtimeEventPublisherImpl implements RealtimeEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RealtimeEventPublisherImpl.class);
    private final SimpMessagingTemplate messaging;
    private final OrderQueryDao orders;

    public RealtimeEventPublisherImpl(SimpMessagingTemplate messaging, OrderQueryDao orders) {
        this.orders = orders;
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
                try {
                    messaging.convertAndSend("/topic/tenants/" + event.tenantId()
                            + "/branches/" + event.branchId() + "/" + topic, event);
                    if ("orders".equals(topic)) {
                        messaging.convertAndSend("/topic/guest/orders/" + event.resourceId(), event);
                    }
                } catch (RuntimeException ex) {
                    // The database has committed; do not turn a successful mutation into an HTTP error.
                    log.error("Realtime delivery failed for event {} on topic {}", event.eventId(), topic, ex);
                }
            }
        });
    }
}
