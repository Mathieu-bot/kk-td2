package org.example;

import org.example.database.DBConnection;
import org.example.model.*;
import org.example.service.DataRetriever;

public class Main {

  public static void main(String[] args) throws Exception {

    DBConnection dbConnection = new DBConnection();
    DataRetriever dataRetriever = new DataRetriever(dbConnection);

    Dish dish = dataRetriever.findDishById(1);
    System.out.println(dish.getDishCost());
    System.out.println(dish.getGrossMargin());

    System.out.println("=== FIND DISH TEST ===");

    try {
      System.out.println("Gross margin: " + dish.getGrossMargin());
    } catch (IllegalStateException e) {
      System.out.println("Expected error: " + e.getMessage());
    }

    System.out.println("\n=== UPDATE PRICE TEST ===");

    dish.setPrice(5000.0);
    Dish updatedDish = dataRetriever.saveDish(dish);

    System.out.println("Updated price: " + updatedDish.getPrice());
    System.out.println("Gross margin after update: " + updatedDish.getGrossMargin());

    System.out.println("\n=== CREATE DISH TEST ===");

    Dish newDish = new Dish(0, "Test Dish", DishTypeEnum.MAIN, 3000.0);

    Ingredient salt = new Ingredient(0, "Salt", 200, CategoryEnum.OTHER);

    Ingredient oil = new Ingredient(0, "Oil", 300, CategoryEnum.OTHER);

    newDish.addIngredient(salt);
    newDish.addIngredient(oil);

    Dish savedDish = dataRetriever.saveDish(newDish);

    System.out.println("New dish ID: " + savedDish.getId());
    System.out.println("Dish cost: " + savedDish.getDishCost());
    System.out.println("Gross margin: " + savedDish.getGrossMargin());

    System.out.println("Dish name: " + dish.getName());
    System.out.println("Dish cost: " + dish.getDishCost());
  }
}
