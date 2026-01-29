package org.example.model;

import java.time.Instant;
import java.util.List;

public class Table {

  private final int id;
  private final int number;
  private final List<Order> orders;

  public Table(int id, int number, List<Order> orders) {
    this.id = id;
    this.number = number;
    this.orders = orders;
  }

  public int getId() {
    return id;
  }

  public int getNumber() {
    return number;
  }

  public List<Order> getOrders() {
    return orders;
  }

  public boolean isAvailableAt(Instant instant) {
    if (instant == null) {
      throw new IllegalArgumentException("instant must not be null");
    }

    if (orders == null || orders.isEmpty()) {
      return true;
    }

    for (Order order : orders) {
      if (order == null || order.getTableOrder() == null) {
        continue;
      }

      TableOrder tableOrder = order.getTableOrder();
      Instant arrival = tableOrder.getArrivalDatetime();
      Instant departure = tableOrder.getDepartureDatetime();

      if (arrival == null || departure == null) {
        continue;
      }

      // table occupied if [arrival, departure) overlaps the instant
      if (!arrival.isAfter(instant) && departure.isAfter(instant)) {
        return false;
      }
    }

    return true;
  }
}
