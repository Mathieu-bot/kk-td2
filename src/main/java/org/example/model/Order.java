package org.example.model;

import java.time.Instant;
import java.util.List;

public class Order {
  private final int id;
  private final String reference;
  private final Instant creationDateTime;
  private final List<DishOrder> dishOrders;
  private final TableOrder tableOrder;

  public Order(
      int id,
      String reference,
      Instant creationDateTime,
      List<DishOrder> dishOrders,
      TableOrder tableOrder) {
    this.id = id;
    this.reference = reference;
    this.creationDateTime = creationDateTime;
    this.dishOrders = dishOrders;
    this.tableOrder = tableOrder;
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

   public TableOrder getTableOrder() {
     return tableOrder;
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
