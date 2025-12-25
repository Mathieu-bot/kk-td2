package org.example.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private final String JDBC_URL = System.getenv("JDBC_URL");
    private final String USERNAME = System.getenv("USERNAME");
    private final String PASSWORD = System.getenv("PASSWORD");

    public Connection getDBConnection()  {
        try {
            return DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to connect to database", e);
        }
    }

    public void close(Connection connection) {
        if (connection != null) {
            try {
            connection.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
