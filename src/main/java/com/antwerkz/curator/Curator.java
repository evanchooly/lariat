package com.antwerkz.curator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.antwerkz.curator.ArchiveDao.ARCHIVE_ID;
import static com.antwerkz.curator.ArchiveDao.ARCH_NUM;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.EntityInterceptor;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.Mapper;

public class Curator implements EntityInterceptor {
  private Datastore datastore;

  private Morphia morphia;

  private Mapper mapper;

  private Map<MappedClass, ArchivedEntity> collections = new HashMap<>();

  private ThreadPoolExecutor executor;

  public Curator(final Datastore datastore, final Morphia morphia) {
    this.datastore = datastore;
    this.morphia = morphia;
    this.mapper = morphia.getMapper();
    mapper.getMappedClasses().stream()
        .filter(m -> m.getAnnotation(Archived.class) != null)
        .forEach(this::register);
    executor = new ThreadPoolExecutor(2, 10, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(100));
  }

  private ArchivedEntity register(final MappedClass mapped) {
    final Archived annotation = mapped.getClazz().getAnnotation(Archived.class);
    ArchivedEntity archivedEntity = null;
    if (annotation != null) {
      final String name = calculateName(mapped, annotation);
      final DBCollection collection = datastore.getDB().getCollection(name);
      collection
          .createIndex(new BasicDBObject(ARCHIVE_ID, 1).append(ARCH_NUM, -1), new BasicDBObject("name", "archiveId"));
      collection.createIndex(new BasicDBObject(ARCH_NUM, -1), new BasicDBObject("name", "archiveNumber"));

      archivedEntity = new ArchivedEntity(name, annotation.count());
      collections.put(mapped, archivedEntity);
    }
    return archivedEntity;
  }

  @SuppressWarnings("unchecked")
  public <T> T rollback(final T entity) {
    final String name = getArchiveCollection(entity);
    final DB db = datastore.getDB();
    final DBCollection collection = db.getCollection(name);
    final BasicDBObject latest = (BasicDBObject) collection
        .findOne(new BasicDBObject(ARCHIVE_ID, mapper.getId(entity)),
            null, new BasicDBObject(ARCH_NUM, -1));
    latest.put("_id", latest.remove(ARCHIVE_ID));
    collection.remove(new BasicDBObject(ARCH_NUM, new BasicDBObject("$gte", latest.getLong(ARCH_NUM) - 1)));
    final T t = (T) morphia.fromDBObject(entity.getClass(), latest);
    datastore.save(t);
    return t;
  }

  private long getNextArchiveNum(final DBCollection collection, final Object archivedId) {
    return getLatestArchiveNum(collection, archivedId) + 1;
  }

  private long getLatestArchiveNum(final DBCollection collection, final Object archivedId) {
    final DBObject latest = collection.findOne(
        new BasicDBObject(ARCHIVE_ID, archivedId),
        new BasicDBObject(ARCH_NUM, 1),
        new BasicDBObject(ARCH_NUM, -1));
    return latest != null ? (Long) latest.get(ARCH_NUM) : -1;
  }

  private DBObject fetchForArchiving(final MappedClass mappedClass, final DBObject dbObj) {
    final DB db = datastore.getDB();
    final DBCollection collection = db.getCollection(mappedClass.getCollectionName());
    final BasicDBObject archived = new BasicDBObject();
    archived.putAll(collection.findOne(new BasicDBObject("_id", dbObj.get("_id"))));
    archived.put(ARCHIVE_ID, archived.remove("_id"));
    return archived;
  }

  public String getArchiveCollection(final Object ent) {
    ArchivedEntity archivedEntity = collections.get(mapper.getMappedClass(ent));
    if (archivedEntity == null) {
      archivedEntity = register(mapper.getMappedClass(ent));
    }
    return archivedEntity.getCollection();
  }

  private String calculateName(final MappedClass mapped, final Archived annotation) {
    String name = annotation.value();
    if (name.equals("")) {
      name = mapped.getCollectionName() + "_archive";
    }
    return name;
  }

  @Override
  public void preSave(final Object ent, final DBObject dbObj, final Mapper mapper) {
    if (dbObj.get("_id") != null) {
      final MappedClass mappedClass = mapper.getMappedClass(ent);
      final DBObject archived = fetchForArchiving(mappedClass, dbObj);
      final DB db = datastore.getDB();
      final DBCollection collection = db.getCollection(getArchiveCollection(ent));
      long num = getNextArchiveNum(collection, archived.get(ARCHIVE_ID));
      archived.put(ARCH_NUM, num);
      collection.insert(archived);
      executor.submit(() -> prune(collections.get(mappedClass), mapper.getId(ent)));
    }
  }

  private void prune(final ArchivedEntity archivedEntity, final Object id) {
    final DBCollection collection = datastore.getDB().getCollection(archivedEntity.getCollection());
    final BasicDBObject query = new BasicDBObject(ARCHIVE_ID, id)
        .append(ARCH_NUM, new BasicDBObject("$lte", getLatestArchiveNum(collection, id) - archivedEntity.getCount()));
    collection.remove(query);
  }

  @Override
  public void prePersist(final Object ent, final DBObject dbObj, final Mapper mapper) {
  }

  @Override
  public void postPersist(final Object ent, final DBObject dbObj, final Mapper mapper) {
  }

  @Override
  public void preLoad(final Object ent, final DBObject dbObj, final Mapper mapper) {
  }

  @Override
  public void postLoad(final Object ent, final DBObject dbObj, final Mapper mapper) {
  }
}
