package org.example.model;

import java.time.Instant;
import java.util.*;

public class Order {
  private final int id;
  private final String reference;
  private double amountWithoutVAT;
  private double amountWithVAT;
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

  public Double getTotalAmountWithoutVAT() {
    return dishOrders.stream()
        .mapToDouble(
            dishOrder -> {
              Dish dish = dishOrder.getDish();
              Double price = dish.getPrice();
              if (price == null) {
                throw new IllegalStateException("Price not set for dish " + dish.getName());
              }
              return price * dishOrder.getQuantity();
            })
        .sum();
  }

  public Double getTotalAmountWithVAT() {
    return getTotalAmountWithoutVAT() * 1.2;
  }
}
