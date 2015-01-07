package com.antwerkz.lariat;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;

import static com.antwerkz.lariat.ArchivedDao.ARCHIVE_ID;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.EntityInterceptor;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.annotations.Version;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveInterceptor implements EntityInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveInterceptor.class);

  private Datastore datastore;

  private Morphia morphia;

  private Mapper mapper;

  private Map<MappedClass, ArchivedEntity> mappings = new HashMap<>();

  static {
    MappedField.addInterestingAnnotation(Archived.class);
  }

  public ArchiveInterceptor(final Datastore datastore, final Morphia morphia) {
    this.datastore = datastore;
    this.morphia = morphia;
    this.mapper = morphia.getMapper();
    mapper.getMappedClasses().stream()
        .filter(m -> !m.getFieldsAnnotatedWith(Archived.class).isEmpty())
        .forEach(this::register);
  }

  private ArchivedEntity register(final MappedClass mapped) {
    ArchivedEntity archivedEntity = new ArchivedEntity(mapped);
    mappings.put(mapped, archivedEntity);

    if (archivedEntity.isArchived()) {
      datastore.getDB().getCollection(archivedEntity.getCollection())
          .createIndex(new BasicDBObject(ARCHIVE_ID, 1).append(archivedEntity.getFieldName(), -1),
              new BasicDBObject("name", "archiveId").append("unique", true));
    }
    return archivedEntity;
  }

  @SuppressWarnings("unchecked")
  public <T> T rollback(final T entity) {
    final MappedClass mappedClass = mapper.getMappedClass(entity);
    final ArchivedEntity archivedEntity = getArchivedEntity(entity);

    final DBCollection collection = datastore.getDB().getCollection(archivedEntity.getCollection());

    final long version = getVersion(entity);

    final Object id = mapper.getId(entity);

    final BasicDBObject previousQuery = new BasicDBObject(ARCHIVE_ID, id)
        .append(archivedEntity.getFieldName(), version - 1);
    final BasicDBObject previous = (BasicDBObject) collection.findOne(previousQuery);

    previous.put("_id", previous.remove(ARCHIVE_ID));

    final BasicDBObject removeQuery = new BasicDBObject(archivedEntity.getFieldName(),
        new BasicDBObject("$gte", version - 1))
        .append(ARCHIVE_ID, id);
    final WriteResult result = collection.remove(removeQuery);
    if (result.getN() > 1 ) {
      LOG.warn("More than the expected number of versions was removed on rollback.  Possible concurrent updates"
          + "in progress.");
    }
    final T t = (T) morphia.fromDBObject(entity.getClass(), previous);
    final BasicDBObject query = new BasicDBObject("_id", id).append(archivedEntity.getFieldName(), version);
    final WriteResult update = datastore.getDB().getCollection(mappedClass.getCollectionName())
        .update(query, previous);
    if(update.getN() != 1) {
      throw new ConcurrentModificationException("Unable to rollback to the expected version");
    }
    return t;
  }

  private long getVersion(Object entity) {
    final MappedClass mc = mapper.getMappedClass(entity);
    return (Long) mc.getFieldsAnnotatedWith(Version.class).get(0).getFieldValue(entity);
  }

  private DBObject fetchForArchiving(final ArchivedEntity archivedEntity, final DBObject dbObj) {
    final String collectionName = archivedEntity.getMappedClass().getCollectionName();
    final DBCollection collection = datastore.getDB().getCollection(collectionName);

    final String fieldName = archivedEntity.getFieldName();
    final BasicDBObject query = new BasicDBObject("_id", dbObj.get("_id"))
        .append(fieldName, ((BasicDBObject) dbObj).getLong(fieldName));

    final BasicDBObject archived = new BasicDBObject(collection.findOne(query).toMap());
    archived.put(ARCHIVE_ID, archived.remove("_id"));

    return archived;
  }

  private ArchivedEntity getArchivedEntity(final Object ent) {
    MappedClass mappedClass = mapper.getMappedClass(ent);

    ArchivedEntity archivedEntity = mappings.get(mappedClass);
    if (archivedEntity == null) {
      archivedEntity = register(mappedClass);
    }
    return archivedEntity;
  }

  public String getArchiveCollection(final Object ent) {
    ArchivedEntity archivedEntity = getArchivedEntity(ent);
    return archivedEntity.getCollection();
  }

  @Override
  public void preSave(final Object ent, final DBObject dbObj, final Mapper mapper) {
    final ArchivedEntity archivedEntity = getArchivedEntity(ent);
    if (archivedEntity.isArchived() && dbObj.get("_id") != null) {
      final DBObject archived = fetchForArchiving(archivedEntity, dbObj);
      final DB db = datastore.getDB();
      final DBCollection collection = db.getCollection(getArchiveCollection(ent));
      collection.insert(archived);
      prune(archivedEntity, mapper.getId(ent), getVersion(ent));
    }
  }

  private void prune(final ArchivedEntity archivedEntity, final Object id, final long version) {
    final DBCollection collection = datastore.getDB().getCollection(archivedEntity.getCollection());
    final BasicDBObject query = new BasicDBObject(ARCHIVE_ID, id)
        .append(archivedEntity.getFieldName(), new BasicDBObject("$lte", version - archivedEntity.getCount()));
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
