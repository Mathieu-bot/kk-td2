package org.example.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Dish {
    private final int id;
    private final String name;
    private final DishTypeEnum dishType;
    private Double price;
    private final List<Ingredient> ingredients;

    public Dish(int id, String name, DishTypeEnum dishType, Double price) {
        this.id = id;
        this.name = name;
        this.dishType = dishType;
        this.price = price;
        this.ingredients = new ArrayList<>();
    }

    public Dish(int id, String name, DishTypeEnum dishType) {
        this(id, name, dishType, null);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public DishTypeEnum getDishType() { return dishType; }
    public Double getPrice() { return price; }

    public void setPrice(Double price) {
        this.price = price;
    }

    public List<Ingredient> getIngredients() { return ingredients; }

    public void setIngredients(List<Ingredient> ingredients) {
        this.ingredients.clear();
        this.ingredients.addAll(ingredients);
    }

    public void addIngredient(Ingredient ingredient) {
        if (ingredient != null) {
            this.ingredients.add(ingredient);
        }
    }

    public Double getDishCost() {
        double totalPrice = 0;
        for (Ingredient ingredient : ingredients) {
            Double quantity = ingredient.getQuantity();
            if (quantity == null) {
                throw new IllegalStateException(
                        "Quantity not found, not possible to calculate dish cost."
                );
            }
            totalPrice += ingredient.getPrice() * quantity;
        }
        return totalPrice;
    }

    public Double getGrossMargin() {
        if (price == null) {
            throw new IllegalStateException(
                    "Price not found, not possible to calculate margin."
            );
        }
        return price - getDishCost();
    }

    @Override
    public String toString() {
        return "Dish{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", dishType=" + dishType +
                ", price=" + price +
                ", ingredients=" + ingredients +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dish dish)) return false;
        return id == dish.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
