package com.businessName.ticketDao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
public class ConnectionObject {

    public static Connection createConnection() {
        try {
            String url = "jdbc:postgresql://mi-postgres-db.cfe68u8wo536.us-east-2.rds.amazonaws.com:5432/tickets_db";
            String user = "postgres";
            String password = "Joseneyra_17";

            Connection dbConnection = DriverManager.getConnection(url, user, password);
            System.out.println("✅ Conectado a PostgreSQL en RDS");
            return dbConnection;

        } catch(SQLException e) {
            System.out.println("❌ Error de conexión");
            e.printStackTrace();
            return null;
        }
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