package xyz.nikitacartes.easyauth.storage.database;


import com.mongodb.MongoClientException;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.ReplaceOneModel;
import org.bson.Document;
import xyz.nikitacartes.easyauth.config.StorageConfigV1;
import xyz.nikitacartes.easyauth.storage.PlayerCacheV0;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.*;

public class MongoDB implements DbApi {
    private final StorageConfigV1 config;
    private MongoCollection<Document> collection;
    private MongoClient mongoClient;

    public MongoDB(StorageConfigV1 config) {
        this.config = config;
    }

    public void connect() throws DBApiException {
        LogDebug("You are using Mongo DB");
        try {
            mongoClient = MongoClients.create(config.mongodb.mongodbConnectionString);
            MongoDatabase database = mongoClient.getDatabase(config.mongodb.mongodbDatabase);
            collection = database.getCollection("players");
        } catch (MongoClientException | MongoCommandException e) {
            throw new DBApiException("Failed connecting to MongoDB", e);
        }
    }

    public void close() {
        mongoClient.close();
        LogInfo("Database connection closed successfully.");
        mongoClient = null;
        collection = null;
    }

    public boolean isClosed() { return mongoClient == null; }

    @Override
    public boolean registerUser(String uuid, String data) {
        LogError("RegisterUser isn't implemented in MongoDB");
        return false;
    }

    public boolean isUserRegistered(String uuid) {
        return collection.find(eq("UUID", uuid)).iterator().hasNext();
    }

    public void deleteUserData(String uuid) {
        collection.deleteOne(eq("UUID", uuid));
    }

    @Override
    public void updateUserData(String uuid, String data) {
        LogError("updateUserData isn't implemented in MongoDB");
    }

    public String getUserData(String uuid) {
        if (isUserRegistered(uuid)) {
            Document data = collection.find(eq("UUID", uuid)).iterator().next();
            return data.toJson();
        }
        return "";
    }

    @Override
    public HashMap<String, String> getAllData() {
        HashMap<String, String> registeredPlayers = new HashMap<>();
        collection.find().forEach(document -> {
            String uuid = document.getString("UUID");
            String data = document.toJson();
            registeredPlayers.put(uuid, data);
        });
        return registeredPlayers;
    }

    public void saveAll(HashMap<String, PlayerCacheV0> playerCacheMap) {
        List<InsertOneModel<Document>> writeList = new ArrayList<>();
        List<ReplaceOneModel<Document>> updateList = new ArrayList<>();
        playerCacheMap.forEach((uuid, playerCache) -> {
            // Save as BSON not JSON stringified
            if (!isUserRegistered(uuid)) {
                writeList.add(new InsertOneModel<>(new Document("UUID", uuid).append("password", playerCache.password)));
            } else {
                updateList.add(new ReplaceOneModel<>(eq("UUID", uuid), new Document("UUID", uuid).append("password", playerCache.password).append("is_authenticated", playerCache.isAuthenticated).append("last_ip", playerCache.lastIp).append("valid_until", playerCache.validUntil).append("last_kicked", playerCache.lastKicked)));
            }
        });
        if (!writeList.isEmpty()) collection.bulkWrite(writeList);
        if (!updateList.isEmpty()) collection.bulkWrite(updateList);
    }
}
