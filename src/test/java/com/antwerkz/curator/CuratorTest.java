package com.antwerkz.curator;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.antwerkz.curator.model.Record;
import static com.jayway.awaitility.Awaitility.await;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import static java.lang.String.format;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.Morphia;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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

  @BeforeMethod
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
      validate(record, Math.min(i, Record.MAX_ARCHIVE_COUNT));
    }
  }

  @Test
  public void pruning() throws Exception {
    assertEquals(count(ARCH_COLLECTION_NAME), 0, "Should find 0 archived records");
    morphia.map(Record.class);
    final Record record1 = new Record("Record 1", "Value 0");
    final Record record2 = new Record("Record 2", "Value 0");
    datastore.save(record1);
    datastore.save(record2);
    count(record1, 0);
    count(record2, 0);

    datastore.save(record1.setContent("Value 1"));
    datastore.save(record2.setContent("Value 1"));
    count(record1, 1);
    count(record2, 1);

    datastore.save(record1.setContent("Value 2"));
    datastore.save(record2.setContent("Value 2"));
    count(record1, 2);
    count(record2, 2);

    datastore.save(record1.setContent("Value 3"));
    count(record1, 3);
    count(record2, 2);

    datastore.save(record1.setContent("Value 4"));
    count(record1, 3);
    count(record2, 2);

    datastore.save(record1.setContent("Value 5"));
    count(record1, 3);
    count(record2, 2);
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
    DBObject archived = get(record).get(0);
    assertEquals(archived.get("content"), "Value 0");
    assertEquals(archived.get(ArchiveDao.ARCHIVE_ID), record.getId());
    assertEquals(archived.get(ArchiveDao.ARCH_NUM), 0L);
    assertEquals(datastore.find(Record.class).get().getContent(), content);
    curator.rollback(record);
    assertEquals(datastore.find(Record.class).get().getContent(), "Value 0");
    assertEquals(count(ARCH_COLLECTION_NAME), 1, "Should find 1 archived records");
  }

  private void validate(final Record record, final long count) {
    count(record, count);

    long index = 0;
    for (DBObject item : get(record)) {
      assertEquals(item.get("content"), "Value " + index, format("Objects don't match:\n%s\n and \n%s", item, record));
      assertEquals(item.get(ArchiveDao.ARCHIVE_ID), record.getId(),
          format("Objects don't match:\n%s\n and \n%s", item, record));
      assertEquals(item.get(ArchiveDao.ARCH_NUM), index);
      index++;
    }
  }

  private void count(final Record record, final long count) {
    final DBCollection collection = mongoClient.getDB(DB_NAME).getCollection(ARCH_COLLECTION_NAME);
    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(() -> collection.count(new BasicDBObject(ArchiveDao.ARCHIVE_ID, record.getId())) == count);
    assertEquals(collection.count(new BasicDBObject(ArchiveDao.ARCHIVE_ID, record.getId())),
        count, format("Should find %d archived records", count));
  }

  private List<DBObject> get(final Record record) {
    final DBCursor list = mongoClient.getDB(DB_NAME).getCollection(ARCH_COLLECTION_NAME)
        .find(new BasicDBObject(ArchiveDao.ARCHIVE_ID, record.getId()))
        .sort(new BasicDBObject(ArchiveDao.ARCH_NUM, 1));
    final List<DBObject> results = new ArrayList<>();
    for (DBObject dbObject : list) {
      results.add(dbObject);
    }
    return results;
  }

  private long count(final String name) {
    return mongoClient.getDB(DB_NAME).getCollection(name).count();
  }
}