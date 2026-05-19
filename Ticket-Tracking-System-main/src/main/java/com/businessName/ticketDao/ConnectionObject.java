package com.businessName.ticketDao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectionObject {

    public static Connection createConnection() {
        try {
            String host = getEnvOrDefault("DB_HOST", "localhost");
            String port = getEnvOrDefault("DB_PORT", "5432");
            String database = getEnvOrDefault("DB_NAME", "tickets_db");
            String url = getEnvOrDefault("DB_URL", "jdbc:postgresql://" + host + ":" + port + "/" + database);
            String user = getEnvOrDefault("DB_USER", "postgres");
            String password = getEnvOrDefault("DB_PASSWORD", "");

            Connection dbConnection = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to PostgreSQL");
            return dbConnection;

        } catch (SQLException e) {
            System.out.println("PostgreSQL connection error");
            e.printStackTrace();
            return null;
        }
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    public static void main(String[] args) {
        try {
            Connection conn = createConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM p2_sandbox.employee_type");
            while (rs.next()) {
                System.out.println(rs.getInt("type_id") + " - " + rs.getString("description"));
            }

            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
