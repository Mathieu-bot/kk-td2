package org.example.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Dish {
    private final int id;
    private final String name;
    private final DishTypeEnum dishType;
    private Double price;
    private final List<DishIngredient> dishIngredients;

    public Dish(int id, String name, DishTypeEnum dishType, Double price) {
        this.id = id;
        this.name = name;
        this.dishType = dishType;
        this.price = price;
        this.dishIngredients = new ArrayList<>();
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


    public List<Ingredient> getIngredients() {
        List<Ingredient> result = new ArrayList<>();
        for (DishIngredient di : dishIngredients) {
            Ingredient ingredient = di.getIngredient();
            ingredient.setQuantity(di.getQuantity());
            result.add(ingredient);
        }
        return result;
    }

    public void setIngredients(List<Ingredient> ingredients) {
        this.dishIngredients.clear();
        if (ingredients == null) {
            return;
        }
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null) {
                continue;
            }
            double quantity = ingredient.getQuantity() == null ? 1.0 : ingredient.getQuantity();
            this.dishIngredients.add(new DishIngredient(this, ingredient, quantity, Unit.KG));
        }
    }

    public List<DishIngredient> getDishIngredients() {
        return dishIngredients;
    }

    public void setDishIngredients(List<DishIngredient> dishIngredients) {
        this.dishIngredients.clear();
        if (dishIngredients == null) {
            return;
        }
        for (DishIngredient di : dishIngredients) {
            if (di == null) {
                continue;
            }
            this.dishIngredients.add(new DishIngredient(this, di.getIngredient(), di.getQuantity(), di.getUnit()));
        }
    }

    public void addIngredient(Ingredient ingredient) {
        if (ingredient != null) {
            double quantity = ingredient.getQuantity() == null ? 1.0 : ingredient.getQuantity();
            this.dishIngredients.add(new DishIngredient(this, ingredient, quantity, Unit.KG));
        }
    }

    public Double getDishCost() {
        double totalPrice = 0;
        for (DishIngredient di : dishIngredients) {
            totalPrice += di.getIngredient().getPrice() * di.getQuantity();
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
                ", dishIngredients=" + dishIngredients +
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
