package org.example.model;

import java.util.*;
import java.time.Instant;

public class Order {
    private final int id;
    private final String reference;
    private final Instant creationDateTime;
    private final java.util.List<DishOrder> dishOrders;

    public Order(int id, String reference, Instant creationDateTime, List<DishOrder> dishOrders) {
        this.id = id;
        this.reference = reference;
        this.creationDateTime = creationDateTime;
        this.dishOrders = dishOrders;
    }

    public int getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCreationDateTime() {
        return creationDateTime;
    }

    public List<DishOrder> getDishOrders() {
        return dishOrders;
    }
}
