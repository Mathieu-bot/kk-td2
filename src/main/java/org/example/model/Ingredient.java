package org.example.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Ingredient {
    private final int id;
    private final String name;
    private final double price;
    private final CategoryEnum category;
    private Double quantity;
    private List<StockMovement> stockMovementList = new ArrayList<StockMovement>();

    public Ingredient(int id, String name, double price, CategoryEnum category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public CategoryEnum getCategory() { return category; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return "Ingredient{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", category=" + category +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ingredient that)) return false;
        return id == that.id &&
                Double.compare(that.price, price) == 0 &&
                Objects.equals(name, that.name) &&
                category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, price, category);
    }
}
