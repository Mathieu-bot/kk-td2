package org.example.model;

public class DishOrder {
  private final int id;
  private final Dish dish;
  private final int quantity;

  public DishOrder(int id, Dish dish, int quantity) {
    this.id = id;
    this.dish = dish;
    this.quantity = quantity;
  }

  public int getId() {
    return id;
  }

  public Dish getDish() {
    return dish;
  }

  public int getQuantity() {
    return quantity;
  }
}
