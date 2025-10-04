package ru.data.anonymization.tool.methods.controllers;

import lombok.Data;

import java.io.Serializable;
import java.sql.*;

@Data
public class ControllerDB implements Serializable {

    private String host;
    private String port;
    private String database;
    private String username;
    private String password;
    private Connection connection;
    private Statement statement;

    public void connect() {
        try {
            connection = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + database,username,password);
            statement = connection.createStatement(
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_UPDATABLE
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public void disconnect(){
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
