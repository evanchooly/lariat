package com.antwerkz.curator;

import com.antwerkz.curator.model.Record;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.Morphia;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

public class CuratorTest {

    public static final String DB_NAME = "curator_test";
    public static final String ARCH_COLLECTION_NAME = "records_archive";
    private MongoClient mongoClient;
    private Morphia morphia = new Morphia();
    private Datastore datastore;
    private Curator curator;

    public CuratorTest() throws UnknownHostException {
        mongoClient = new MongoClient();
        datastore = new DatastoreImpl(morphia, mongoClient, DB_NAME);
        curator = new Curator(datastore, morphia);
        morphia.getMapper().addInterceptor(curator);
    }

    @BeforeTest
    public void setup() {
        datastore.getDB().dropDatabase();
    }

    @Test
    public void archiving() throws Exception {
        assertEquals(count(ARCH_COLLECTION_NAME), 0, "Should find 0 archived records");
        morphia.map(Record.class);

        final Record record = new Record("Record 1", "Value 0");
        datastore.save(record);
        validate(record, 0);

        for (int i = 1; i < 50; i++) {
            datastore.save(record.setContent("Value " + i));
            validate(record, i);
        }
    }

    @Test
    public void rollbacks() {
        assertEquals(count(ARCH_COLLECTION_NAME), 0, "Should find 0 archived records");
        morphia.map(Record.class);

        final Record record = new Record("Record 1", "Value 0");
        datastore.save(record);

        validate(record, 0);

        final String content = "I should get rolledback";
        datastore.save(record.setContent(content));
        DBObject archived = get(ARCH_COLLECTION_NAME).get(0);
        assertEquals(archived.get("content"), "Value 0");
        assertEquals(archived.get(ArchiveDao.ARCHIVE_ID), record.getId());
        assertEquals(archived.get(ArchiveDao.ARCH_NUM), 0L);

        assertEquals(datastore.find(Record.class).get().getContent(), content);

        curator.rollback(record);

        assertEquals(datastore.find(Record.class).get().getContent(), "Value 0");
    }

    private void validate(final Record record, final long count) {
        assertEquals(count(ARCH_COLLECTION_NAME), count, format("Should find %d archived records", count));
        List<DBObject> list = get(ARCH_COLLECTION_NAME);
        long index = 0;
        for (DBObject item : list) {
            assertEquals(item.get("content"), "Value " + index);
            assertEquals(item.get(ArchiveDao.ARCHIVE_ID), record.getId());
            assertEquals(item.get(ArchiveDao.ARCH_NUM), index);
            index++;
        }
    }

    private List<DBObject> get(final String collectionName) {
        final DBCollection collection = mongoClient.getDB(DB_NAME).getCollection(collectionName);
        final DBCursor limit = collection.find().sort(new BasicDBObject(ArchiveDao.ARCH_NUM, 1));
        final List<DBObject> list = new ArrayList<>();
        for (DBObject dbObject : limit) {
            list.add(dbObject);
        }
        return list;
    }

    private long count(final String name) {
        final DBCollection collection = mongoClient.getDB(DB_NAME).getCollection(name);
        return collection.count();
    }
}