package org.example.service;

import org.example.database.DBConnection;
import org.example.model.CategoryEnum;
import org.example.model.Dish;
import org.example.model.DishTypeEnum;
import org.example.model.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataRetrieverTest {
    DataRetriever dataRetriever = new DataRetriever(new DBConnection());

    @Test
    void testFindDishById_existing() {
        Dish dish = dataRetriever.findDishById(1);
        assertEquals("Salade fraiche", dish.getName());
        assertEquals(DishTypeEnum.START, dish.getDishType());
        assertEquals(2, dish.getIngredients().size());
    }

    @Test
    void testFindDishById_notExisting() {
        assertThrows(RuntimeException.class, () -> dataRetriever.findDishById(999));
    }

    @Test
    void testFindIngredients_page2_size2() {
        List<Ingredient> ingredients = dataRetriever.findIngredients(2, 2);
        assertEquals(2, ingredients.size());
        assertEquals("Poulet", ingredients.get(0).getName());
        assertEquals("Chocolat", ingredients.get(1).getName());
    }

    @Test
    void testFindIngredients_emptyPage() {
        List<Ingredient> ingredients = dataRetriever.findIngredients(3, 5);
        assertTrue(ingredients.isEmpty());
    }


    @Test
    void createIngredients() {
    }

    @Test
    void saveDish() {
    }

    @Test
    void testFindDishByIngredientName() {
        List<Dish> dishes = dataRetriever.findDishByIngredientName("eur");
        assertEquals(1, dishes.size());
        assertEquals("Gateau au chocolat", dishes.getFirst().getName());
    }

    @Test
    void testFindIngredientsByCriteria_categoryOnly() {
        List<Ingredient> ingredients = dataRetriever.findIngredientsByCriteria(
                null, CategoryEnum.VEGETABLE, null, 1, 10
        );
        assertEquals(2, ingredients.size());
    }

    @Test
    void testFindIngredientsByCriteria_noResult() {
        List<Ingredient> ingredients = dataRetriever.findIngredientsByCriteria(
                "cho", null, "Sal", 1, 10
        );
        assertTrue(ingredients.isEmpty());
    }

    @Test
    void testFindIngredientsByCriteria_specific() {
        List<Ingredient> ingredients = dataRetriever.findIngredientsByCriteria(
                "cho", null, "gâteau", 1, 10
        );
        assertEquals(1, ingredients.size());
        assertEquals("Chocolat", ingredients.getFirst().getName());
    }

}