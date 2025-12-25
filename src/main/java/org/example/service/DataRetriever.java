package org.example.service;

import org.example.model.*;
import org.example.database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataRetriever {

    private final DBConnection dbConnection;

    public DataRetriever(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public Dish findDishById(int id) {
        String sql = """
            SELECT id, name, dish_type
            FROM dish
            WHERE id = ?
            """;
        Connection connection = dbConnection.getDBConnection();

        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Dish dish = new Dish(
                        rs.getInt("id"),
                        rs.getString("name"),
                        DishTypeEnum.valueOf(rs.getString("dish_type"))
                );

                dish.setIngredients(findIngredientsByDishId(dish.getId()));
                return dish;
            }
            throw new RuntimeException("Dish not found (id=" + id + ")");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(connection);
        }
    }

    private List<Ingredient> findIngredientsByDishId(int dishId) {
        List<Ingredient> ingredients = new ArrayList<>();
        String sql = """
            SELECT id, name, price, category
            FROM ingredient
            WHERE id_dish = ?
            """;
        Connection connection = dbConnection.getDBConnection();

        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, dishId);
            return getIngredients(ingredients, ps);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(connection);
        }
    }

    private List<Ingredient> getIngredients(List<Ingredient> ingredients, PreparedStatement ps) throws SQLException {
        ResultSet resultSet = ps.executeQuery();

        if (resultSet.next()) {
            Ingredient ingredient = new Ingredient(
                    resultSet.getInt("id"),
                    resultSet.getString("name"),
                    resultSet.getDouble("price"),
                    CategoryEnum.valueOf(resultSet.getString("category")),
                    null
            );

            ingredients.add(ingredient);
        }
        return ingredients;
    }

}
