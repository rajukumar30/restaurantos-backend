package com.restaurantos.modules.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void healthEndpointReturnsUpStatus() {
        var response = controller.health();

        assertTrue(response.success());
        assertEquals("RestaurantOS API is running", response.message());
        assertEquals("UP", response.data().get("status"));
    }
}
