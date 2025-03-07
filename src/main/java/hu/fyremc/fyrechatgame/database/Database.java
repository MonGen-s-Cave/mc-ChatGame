package hu.fyremc.fyrechatgame.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.Getter;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

@Getter
public final class Database {
    private final MongoClient client;
    private final MongoDatabase database;

    public Database(@NotNull DatabaseConfig config) {
        String connectionString;

        if (config.username().isEmpty() || config.password().isEmpty()) {
            connectionString = String.format("mongodb://%s:%d/%s", config.host(), config.port(), config.database());
        } else {
            connectionString = String.format("mongodb://%s:%s@%s:%d/%s",
                    config.username(),
                    config.password(),
                    config.host(),
                    config.port(),
                    config.database()
            );
        }

        this.client = MongoClients.create(connectionString);
        this.database = client.getDatabase(config.database());
        ensureCollections();
    }

    public void close() {
        if (client != null) client.close();
    }

    public void insertDocument(@NotNull String collectionName, @NotNull Document document) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        if (collection.countDocuments(new Document("name", document.getString("name"))) == 0) {
            collection.insertOne(document);
        }
    }

    public boolean documentExists(@NotNull String collectionName, @NotNull String fieldName, @NotNull String value) {
        return database.getCollection(collectionName)
                .countDocuments(new Document(fieldName, value)) > 0;
    }

    private void ensureCollections() {
        if (!collectionExists()) {
            database.createCollection("players");
        }
    }

    private boolean collectionExists() {
        for (String name : database.listCollectionNames()) {
            if (name.equalsIgnoreCase("players")) {
                return true;
            }
        }
        return false;
    }
}
