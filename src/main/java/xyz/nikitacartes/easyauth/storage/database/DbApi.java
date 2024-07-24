package xyz.nikitacartes.easyauth.storage.database;

import xyz.nikitacartes.easyauth.storage.PlayerCacheV0;

import java.util.HashMap;

public interface DbApi {
    /**
     * Opens database connection.
     */
    void connect() throws DBApiException;

    /**
     * Closes database connection.
     */
    void close();

    /**
     * Tells whether DbApi connection is closed.
     *
     * @return false if connection is open, otherwise false
     */
    boolean isClosed();

    /**
     * Inserts the data for the player.
     *
     * @param uuid uuid of the player to insert data for
     * @param data data to put inside database
     * @return true if operation was successful, otherwise false
     */
    boolean registerUser(String uuid, String data);

    /**
     * Checks if player is registered.
     *
     * @param uuid player's uuid
     * @return true if registered, otherwise false
     */
    boolean isUserRegistered(String uuid);

    /**
     * Deletes data for the provided uuid.
     *
     * @param uuid uuid of player to delete data for
     */
    void deleteUserData(String uuid);

    /**
     * Updates player's data.
     *
     * @param uuid uuid of the player to update data for
     * @param data data to put inside database
     */
    void updateUserData(String uuid, String data);

    /**
     * Gets the hashed password from DbApi.
     *
     * @param uuid uuid of the player to get data for.
     * @return data as string if player has it, otherwise empty string.
     */
    String getUserData(String uuid);

    /**
     * Get all data from DbApi.
     * @return HashMap with all data.
     */
    HashMap<String, String> getAllData();

    void saveAll(HashMap<String, PlayerCacheV0> playerCacheMap);
}
