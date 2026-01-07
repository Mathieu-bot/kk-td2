package org.example;

import org.example.database.DBConnection;
import org.example.service.DataRetriever;

public class Main {
    public static void main(String[] args) {

        DataRetriever retriever = new DataRetriever(new DBConnection());
        System.out.println(retriever.findDishById(1));

        System.out.println(retriever.findIngredients(1, 3));
    }
}