package xyz.nikitacartes.easyauth.storage.database;

import com.mysql.cj.jdbc.exceptions.CommunicationsException;
import xyz.nikitacartes.easyauth.config.StorageConfigV1;
import xyz.nikitacartes.easyauth.storage.PlayerCacheV0;

import java.sql.*;
import java.util.HashMap;

import static xyz.nikitacartes.easyauth.utils.EasyLogger.*;


public class MySQL implements DbApi {
    private final StorageConfigV1 config;
    private Connection MySQLConnection;

    /**
     * Connects to the MySQL.
     */
    public MySQL(StorageConfigV1 config) {
        this.config = config;
    }

    public void connect() throws DBApiException {
        try {
            LogDebug("You are using MySQL DB");
            Class.forName("com.mysql.cj.jdbc.Driver");
            String uri = "jdbc:mysql://" + config.mysql.mysqlHost + "/" + config.mysql.mysqlDatabase + "?autoReconnect=true";
            LogDebug(String.format("connecting to %s", uri));
            MySQLConnection = DriverManager.getConnection(uri, config.mysql.mysqlUser, config.mysql.mysqlPassword);
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?;");
            preparedStatement.setString(1, config.mysql.mysqlTable);
            if (!preparedStatement.executeQuery().next()) {
                MySQLConnection.createStatement().executeUpdate(
                        String.format("""
                                        CREATE TABLE `%s`.`%s` (
                                            `id` INT NOT NULL AUTO_INCREMENT,
                                            `uuid` VARCHAR(36) NOT NULL,
                                            `data` JSON NOT NULL,
                                            PRIMARY KEY (`id`), UNIQUE (`uuid`)
                                        ) ENGINE = InnoDB;""",
                                config.mysql.mysqlDatabase,
                                config.mysql.mysqlTable
                        )
                );
            }
        } catch (ClassNotFoundException | SQLException e) {
            MySQLConnection = null;
            throw new DBApiException("Failed setting up mysql DB", e);
        }
    }

    private void reConnect() {
        try {
            if (MySQLConnection == null || !MySQLConnection.isValid(5)) {
                LogDebug("Reconnecting to MySQL");
                if (MySQLConnection != null) {
                    MySQLConnection.close();
                }
                connect();
            }
        } catch (DBApiException | SQLException e) {
            LogError("Mysql reconnect failed", e);
        }
    }

    /**
     * Closes database connection.
     */
    public void close() {
        try {
            if (MySQLConnection != null) {
                MySQLConnection.close();
                MySQLConnection = null;
                LogInfo("Database connection closed successfully.");
            }
        } catch (CommunicationsException e) {
            LogError("Can't connect to database while closing", e);
        } catch (SQLException e) {
            LogError("Database connection not closed", e);
        }
    }

    /**
     * Tells whether DbApi connection is closed.
     *
     * @return false if connection is open, otherwise false
     */
    public boolean isClosed() {
        return MySQLConnection == null;
    }


    /**
     * Inserts the data for the player.
     *
     * @param uuid uuid of the player to insert data for
     * @param data data to put inside database
     * @return true if operation was successful, otherwise false
     */
    @Deprecated
    public boolean registerUser(String uuid, String data) {
        try {
            reConnect();
            if (!isUserRegistered(uuid)) {
                PreparedStatement preparedStatement = MySQLConnection.prepareStatement("INSERT INTO " + config.mysql.mysqlTable + " (uuid, data) VALUES (?, ?);");
                preparedStatement.setString(1, uuid);
                preparedStatement.setString(2, data);
                preparedStatement.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            LogError("Register error ", e);
        }
        return false;
    }

    /**
     * Checks if player is registered.
     *
     * @param uuid player's uuid
     * @return true if registered, otherwise false
     */
    public boolean isUserRegistered(String uuid) {
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("SELECT * FROM " + config.mysql.mysqlTable + " WHERE uuid = ?;");
            preparedStatement.setString(1, uuid);
            return preparedStatement.executeQuery().next();
        } catch (SQLException e) {
            LogError("isUserRegistered error", e);
        }
        return false;
    }

    /**
     * Deletes data for the provided uuid.
     *
     * @param uuid uuid of player to delete data for
     */
    public void deleteUserData(String uuid) {
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("DELETE FROM " + config.mysql.mysqlTable + " WHERE uuid = ?;");
            preparedStatement.setString(1, uuid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            LogError("deleteUserData error", e);
        }
    }

    /**
     * Updates player's data.
     *
     * @param uuid uuid of the player to update data for
     * @param data data to put inside database
     */
    @Deprecated
    public void updateUserData(String uuid, String data) {
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("UPDATE " + config.mysql.mysqlTable + " SET data = ? WHERE uuid = ?;");
            preparedStatement.setString(1, data);
            preparedStatement.setString(1, uuid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            LogError("updateUserData error", e);
        }
    }

    /**
     * Gets the hashed password from DbApi.
     *
     * @param uuid uuid of the player to get data for.
     * @return data as string if player has it, otherwise empty string.
     */
    public String getUserData(String uuid) {
        try {
            reConnect();
            if (isUserRegistered(uuid)) {
                PreparedStatement preparedStatement = MySQLConnection.prepareStatement("SELECT data FROM " + config.mysql.mysqlTable + " WHERE uuid = ?;");
                preparedStatement.setString(1, uuid);
                ResultSet query = preparedStatement.executeQuery();
                query.next();
                return query.getString(1);
            }
        } catch (SQLException e) {
            LogError("getUserData error", e);
        }
        return "";
    }

    @Override
    public HashMap<String, String> getAllData() {
        HashMap<String, String> registeredPlayers = new HashMap<>();
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("SELECT * FROM " + config.mysql.mysqlTable + ";");
            ResultSet query = preparedStatement.executeQuery();
            while (query.next()) {
                String uuid = query.getString(2);
                String data = query.getString(3);
                registeredPlayers.put(uuid, data);
            }
        } catch (SQLException e) {
            LogError("getAllData error", e);
        }
        return registeredPlayers;
    }

    public void saveAll(HashMap<String, PlayerCacheV0> playerCacheMap) {
        try {
            reConnect();
            PreparedStatement preparedStatement = MySQLConnection.prepareStatement("INSERT INTO " + config.mysql.mysqlTable + " (uuid, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = ?;");
            // Updating player data.
            playerCacheMap.forEach((uuid, playerCache) -> {
                String data = playerCache.toJson();
                try {
                    preparedStatement.setString(1, uuid);
                    preparedStatement.setString(2, data);
                    preparedStatement.setString(3, data);

                    preparedStatement.addBatch();
                } catch (SQLException e) {
                    LogError(String.format("Error saving player data! %s ", uuid));
                }
            });
            preparedStatement.executeBatch();
        } catch (SQLException | NullPointerException e) {
            LogError("Error saving players data", e);
        }
    }
}
