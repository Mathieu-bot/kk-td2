package org.example.model;

public class DishIngredient {
    private final Dish dish;
    private final Ingredient ingredient;
    private final double quantity;
    private final UnitType unit;

    public DishIngredient(Dish dish, Ingredient ingredient, double quantity, UnitType unit) {
        this.dish = dish;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
    }

    public Dish getDish() {
        return dish;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public double getQuantity() {
        return quantity;
    }

    public UnitType getUnit() {
        return unit;
    }
}
