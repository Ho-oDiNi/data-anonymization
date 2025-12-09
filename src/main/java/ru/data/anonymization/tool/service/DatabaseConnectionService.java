package ru.data.anonymization.tool.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;

@Service
public class DatabaseConnectionService {
    private final String defaultHost;
    private final String defaultPort;
    private final String defaultDatabase;
    private final String defaultUsername;
    private final String defaultPassword;

    private String host;
    private String port;
    @Getter
    private String database;
    @Getter
    private String username;
    private String password;

    private String jdbcUrl;
    private Connection connection;
    private Statement statement;

    public DatabaseConnectionService(
            @Value("${app.database.host:localhost}") String defaultHost,
            @Value("${app.database.port:5432}") String defaultPort,
            @Value("${app.database.name:postgres}") String defaultDatabase,
            @Value("${app.database.username:postgres}") String defaultUsername,
            @Value("${app.database.password:postgres}") String defaultPassword
    ) {
        this.defaultHost = defaultHost;
        this.defaultPort = defaultPort;
        this.defaultDatabase = defaultDatabase;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    public boolean isConnected() {
        try {
            return connection != null
                    && statement != null
                    && !connection.isClosed()
                    && !statement.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void setNameDB(String newNameDB) {
        database = newNameDB;
        jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    public void setConnection(String host, String port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    public boolean connectWithDefaultSettings() {
        if (isConnected()) {
            return true;
        }
        setConnection(defaultHost, defaultPort, defaultDatabase, defaultUsername, defaultPassword);
        return connect();
    }

    public boolean connect() {
        boolean isConnect;
        try {
            connection = DriverManager.getConnection(jdbcUrl, username, password);
            statement = connection.createStatement(
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_UPDATABLE
            );
            isConnect = true;
        } catch (SQLException e) {
            isConnect = false;
        }
        return isConnect;
    }

    public boolean disconnect() {
        boolean isConnect;
        try {
            if (statement != null) {
                statement.close();
            }
            if (connection != null) {
                connection.close();
            }
            statement = null;
            connection = null;
            isConnect = true;
        } catch (SQLException e) {
            isConnect = false;
        }
        return isConnect;
    }

    public void execute(String sql) throws SQLException {
        statement.execute(sql);
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        try {
            return statement.executeQuery(sql);
        } catch (SQLException e) {
            System.out.println(e.getLocalizedMessage());
            throw e;
        }
    }

    public PreparedStatement getPrepareStatement(String sql) throws SQLException {
        try {
            return connection.prepareStatement(sql);
        } catch (SQLException e) {
            System.out.println(e.getLocalizedMessage());
            throw e;
        }
    }
}
