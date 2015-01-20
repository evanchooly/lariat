package com.antwerkz.lariat;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.antwerkz.lariat.model.Item;
import com.antwerkz.lariat.model.Record;
import com.antwerkz.lariat.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import static java.lang.String.format;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.Morphia;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LariatTest {
  public static final String DB_NAME = "lariat_test";

  public static final String ARCH_COLLECTION_NAME = "records_archive";

  private final MongoClient mongoClient;

  private final Morphia morphia = new Morphia();

  private final Datastore datastore;

  private final UserDao userDao;

  private final RecordDao recordDao;

  public LariatTest() throws UnknownHostException {
    mongoClient = new MongoClient();
    datastore = new DatastoreImpl(morphia, mongoClient, DB_NAME);
    final ArchiveInterceptor<Record, ObjectId> recordInterceptor = new ArchiveInterceptor<>(datastore, morphia,
            Record.class);
    final ArchiveInterceptor<User, ObjectId> userInterceptor = new ArchiveInterceptor<>(datastore, morphia,
            User.class);

    morphia.getMapper().addInterceptor(recordInterceptor);
    morphia.getMapper().addInterceptor(userInterceptor);
    
    recordDao = new RecordDao(datastore, recordInterceptor);
    userDao = new UserDao(datastore, userInterceptor);
  }

  @BeforeMethod
  public void setup() {
    datastore.getDB().dropDatabase();
  }

  @Test
  public void archiving() throws Exception {
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
    assertEquals(recordDao.countVersions(record1), 0);
    assertEquals(recordDao.countVersions(record2), 0);

    datastore.save(record1.setContent("Value 1"));
    datastore.save(record2.setContent("Value 1"));
    assertEquals(recordDao.countVersions(record1), 1);
    assertEquals(recordDao.countVersions(record2), 1);

    datastore.save(record1.setContent("Value 2"));
    datastore.save(record2.setContent("Value 2"));
    assertEquals(recordDao.countVersions(record1), 2);
    assertEquals(recordDao.countVersions(record2), 2);

    datastore.save(record1.setContent("Value 3"));
    assertEquals(recordDao.countVersions(record1), 3);
    assertEquals(recordDao.countVersions(record2), 2);

    datastore.save(record1.setContent("Value 4"));
    assertEquals(recordDao.countVersions(record1), 3);
    assertEquals(recordDao.countVersions(record2), 2);

    datastore.save(record1.setContent("Value 5"));
    assertEquals(recordDao.countVersions(record1), 3);
    assertEquals(recordDao.countVersions(record2), 2);
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
    final DBObject archived = get(record).get(0);
    assertEquals(archived.get("content"), "Value 0");
    assertEquals(archived.get(ArchiveInterceptor.ARCHIVE_ID), record.getId());
    assertEquals(archived.get("version"), 1L);
    assertEquals(datastore.find(Record.class).get().getContent(), content);
    
    assertEquals(count(ARCH_COLLECTION_NAME), 1, "Should find 1 archived records");
    
    recordDao.revert(record);
    assertEquals(datastore.find(Record.class).get().getContent(), "Value 0");
    assertEquals(count(ARCH_COLLECTION_NAME), 2, "Should find 2 archived records");
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

    recordDao.revert(record);
  }

  @Test
  public void rollbackToVersion() {
    assertEquals(count("users_archive"), 0, "Should find 0 archived records");
    morphia.map(User.class);
    final User user = new User("Bob Dylan", 60);
    userDao.save(user);
    User history = null;
    for (int i = 1; i < 50; i++) {
      user.setAge(user.getAge() + i);
      userDao.save(user);
      if(user.getVersion() == 20) {
        history = userDao.get(user.getId());
      }
    }

    assertEquals(user.getVersion(), new Long(50));
    assertEquals(userDao.countVersions(user), 49, "Should find 49 archived users");

    final User rolledBack = userDao.revertToVersion(user, 20);
    assertEquals(rolledBack.getVersion(), new Long(user.getVersion() + 1));
    assertEquals(rolledBack.getAge(), history.getAge());
    assertEquals(userDao.countVersions(rolledBack), 50, "Should find 50 archived users");
  }

  private void validate(final Record record, final long count) {
    final long target = Math.min(count, Record.MAX_ARCHIVE_COUNT);
    assertEquals(recordDao.countVersions(record), target);
    if (target > 0) {
      final List<DBObject> dbObjects = get(record);
      evaluate(record, dbObjects.get(0), Math.max(1, count - Record.MAX_ARCHIVE_COUNT + 1));
      evaluate(record, dbObjects.get(dbObjects.size() - 1), count);
    }
  }

  private void evaluate(final Record record, final DBObject item, final long value) {
    assertEquals(item.get("content"), "Value " + value, format("Objects don't match:\n%s\n and \n%s", item, record));
    assertEquals(item.get(ArchiveInterceptor.ARCHIVE_ID), record.getId(),
        format("Objects don't match:\n%s\n and \n%s", item, record));
    assertEquals(item.get("version"), value);
  }

  private List<DBObject> get(final Record record) {
    final DBCursor list = mongoClient.getDB(DB_NAME).getCollection(ARCH_COLLECTION_NAME)
        .find(new BasicDBObject(ArchiveInterceptor.ARCHIVE_ID, record.getId()))
        .sort(new BasicDBObject("version", 1));
    final List<DBObject> results = new ArrayList<>();
    for (final DBObject dbObject : list) {
      results.add(dbObject);
    }
    return results;
  }

  private long count(final String name) {
    return mongoClient.getDB(DB_NAME).getCollection(name).count();
  }
}