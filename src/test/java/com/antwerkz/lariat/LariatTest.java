package com.antwerkz.lariat;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import com.antwerkz.lariat.model.Item;
import com.antwerkz.lariat.model.Record;
import static com.jayway.awaitility.Awaitility.await;
import com.jayway.awaitility.core.ConditionTimeoutException;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import static java.lang.String.format;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.Morphia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LariatTest {
  private static final Logger LOG = LoggerFactory.getLogger(LariatTest.class);

  public static final String DB_NAME = "lariat_test";

  public static final String ARCH_COLLECTION_NAME = "records_archive";

  private MongoClient mongoClient;

  private Morphia morphia = new Morphia();

  private Datastore datastore;

  private ArchiveInterceptor archiveInterceptor;

  public LariatTest() throws UnknownHostException {
    mongoClient = new MongoClient();
    datastore = new DatastoreImpl(morphia, mongoClient, DB_NAME);
    archiveInterceptor = new ArchiveInterceptor(datastore, morphia);
    morphia.getMapper().addInterceptor(archiveInterceptor);
  }

  @BeforeMethod
  public void setup() {
    datastore.getDB().dropDatabase();
  }

  @Test
  public void  archiving() throws Exception {
    assertEquals(count(ARCH_COLLECTION_NAME), 0, "Should find 0 archived records");
    morphia.map(Record.class);
    final Record record = new Record("Record 1", "Value 1");
    datastore.save(record);
    validate(record, 0);
    for (int i = 1; i < 50; i++) {
      datastore.save(record.setContent("Value " + (i + 1)));
      validate(record, i);
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
    assertEquals(archived.get(ArchivedDao.ARCHIVE_ID), record.getId());
    assertEquals(archived.get("version"), 1L);
    assertEquals(datastore.find(Record.class).get().getContent(), content);
    archiveInterceptor.rollback(record);
    assertEquals(datastore.find(Record.class).get().getContent(), "Value 0");
    assertEquals(count(ARCH_COLLECTION_NAME), 0, "Should find 1 archived records");
  }

  @Test
  public void nonArchived() {
    final Item book = new Item("Book", 42);
    morphia.mapPackage(Item.class.getPackage().getName());
    datastore.save(book);
    assertEquals(count("items"), 1, "Should find 1 archived records");
    assertEquals(count("items_archive"), 0, "Should find 0 archived records");
  }

  @Test(expectedExceptions = {NoSuchElementException.class})
  public void noArchivedStateToRollback() {
    assertEquals(count(ARCH_COLLECTION_NAME), 0, "Should find 0 archived records");
    morphia.map(Record.class);
    final Record record = new Record("Record 1", "Value 0");
    datastore.save(record);

    archiveInterceptor.rollback(record);
  }

  private void validate(final Record record, final long count) {
    final long target = Math.min(count, Record.MAX_ARCHIVE_COUNT);
    count(record, target);
    if (target > 0) {
      final List<DBObject> dbObjects = get(record);
      evaluate(record, dbObjects.get(0), Math.max(1, count - Record.MAX_ARCHIVE_COUNT + 1));
      evaluate(record, dbObjects.get(dbObjects.size() - 1), count);
    }
  }

  private void evaluate(final Record record, final DBObject item, final long value) {
    assertEquals(item.get("content"), "Value " + value, format("Objects don't match:\n%s\n and \n%s", item, record));
    assertEquals(item.get(ArchivedDao.ARCHIVE_ID), record.getId(),
        format("Objects don't match:\n%s\n and \n%s", item, record));
    assertEquals(item.get("version"), value);
  }

  private void count(final Record record, final long count) {
    final DBCollection collection = mongoClient.getDB(DB_NAME).getCollection(ARCH_COLLECTION_NAME);
    final long actual = collection.count(new BasicDBObject(ArchivedDao.ARCHIVE_ID, record.getId()));
    assertEquals(actual, count, format("Should find %d archived records but found %d", count, actual));
  }

  private List<DBObject> get(final Record record) {
    final DBCursor list = mongoClient.getDB(DB_NAME).getCollection(ARCH_COLLECTION_NAME)
        .find(new BasicDBObject(ArchivedDao.ARCHIVE_ID, record.getId()))
        .sort(new BasicDBObject("version", 1));
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