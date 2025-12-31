package org.example.service;

import org.example.database.DBConnection;
import org.example.model.Dish;
import org.example.model.DishTypeEnum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void findIngredients() {
    }

    @Test
    void createIngredients() {
    }

    @Test
    void saveDish() {
    }

    @Test
    void findDishByIngredientName() {
    }

    @Test
    void findIngredientsByCriteria() {
    }
}