package com.antwerkz.lariat;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.EntityInterceptor;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.annotations.Version;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static java.lang.String.format;

public class ArchiveInterceptor<T, K> implements EntityInterceptor {
    public static final String ARCHIVE_ID = "_aid";

    private final Class<T> clazz;

    private final Datastore datastore;

    private final Morphia morphia;

    private final Mapper mapper;

    private final Map<MappedClass, ArchivedEntity> mappings = new HashMap<>();

    static {
        MappedField.addInterestingAnnotation(Archived.class);
    }

    public ArchiveInterceptor(final Datastore datastore, final Morphia morphia, final Class<T> clazz) {
        this.datastore = datastore;
        this.morphia = morphia;
        this.mapper = morphia.getMapper();
        this.clazz = clazz;

        mapper.getMappedClasses().stream()
                .filter(m -> !m.getFieldsAnnotatedWith(Archived.class).isEmpty())
                .forEach(this::register);
    }

    private ArchivedEntity register(final MappedClass mapped) {
        final ArchivedEntity archivedEntity = new ArchivedEntity(mapped);
        mappings.put(mapped, archivedEntity);

        if (archivedEntity.isArchived()) {
            DBCollection collection = datastore.getDB().getCollection(archivedEntity.getCollection());
            BasicDBObject append = new BasicDBObject(ARCHIVE_ID, 1).append(archivedEntity.getFieldName(), -1);
            collection.createIndex(append,
                    new BasicDBObject("name", "archiveId").append("unique", true));
        }
        return archivedEntity;
    }

    public T revert(final T entity) {
        final long version = getVersion(entity);
        return revertToVersion(entity, version - 1);
    }

    public T revertToVersion(final T entity, final long targetVersion) {
        final T reverted = findArchivedVersion(entity, targetVersion);
        datastore.save(reverted);
        return reverted;
    }

    public T findArchivedVersion(final T entity, final long targetVersion) {
        final K id = (K) mapper.getId(entity);
        final ArchivedEntity archivedEntity = getArchivedEntity((Class<T>) entity.getClass());


        final MappedClass mappedClass = archivedEntity.getMappedClass();
        final DBCollection archCollection = datastore.getDB().getCollection(archivedEntity.getCollection());

        final BasicDBObject previous = (BasicDBObject) archCollection.findOne(
                new BasicDBObject(ARCHIVE_ID, id)
                        .append(archivedEntity.getFieldName(), targetVersion));
        if (previous == null) {
            throw new NoSuchElementException(format("No archived version %d for %s with and ID of %s", targetVersion,
                    mappedClass.getClazz().getName(), id));
        }
        previous.put("_id", previous.remove(ARCHIVE_ID));

        final MappedField mappedField = mappedClass.getFieldsAnnotatedWith(Version.class).get(0);
        final T reverted = morphia.fromDBObject((Class<T>) mappedClass.getClazz(), previous);
        mappedField.setFieldValue(reverted, mappedField.getFieldValue(entity));
        return reverted;
    }

    private long getVersion(final T entity) {
        final MappedClass mc = mapper.getMappedClass(entity);
        return (Long) mc.getFieldsAnnotatedWith(Version.class).get(0).getFieldValue(entity);
    }

    private DBObject fetchForArchiving(final ArchivedEntity archivedEntity, final DBObject dbObj) {
        final String collectionName = archivedEntity.getMappedClass().getCollectionName();
        final DBCollection collection = datastore.getDB().getCollection(collectionName);

        final String fieldName = archivedEntity.getFieldName();
        final BasicDBObject query = new BasicDBObject("_id", dbObj.get("_id"))
                .append(fieldName, ((BasicDBObject) dbObj).getLong(fieldName));

        final DBObject one = collection.findOne(query);
        if (one != null) {
            final BasicDBObject archived = new BasicDBObject(one.toMap());
            archived.put(ARCHIVE_ID, archived.remove("_id"));

            return archived;
        } else {
            return null;
        }
    }

    public ArchivedEntity getArchivedEntity(final Class<? extends T> pClass) {
        final MappedClass mappedClass = mapper.getMappedClass(pClass);

        ArchivedEntity archivedEntity = mappings.get(mappedClass);
        if (archivedEntity == null) {
            archivedEntity = register(mappedClass);
        }
        return archivedEntity;
    }

    public long countVersions(final T entity) {
        return datastore.getDB()
                .getCollection(getArchivedEntity((Class<T>) entity.getClass()).getCollection())
                .count(new BasicDBObject(ARCHIVE_ID, mapper.getId(entity)));
    }

    private void prune(final ArchivedEntity archivedEntity, final Object id, final long version) {
        final DBCollection collection = datastore.getDB().getCollection(archivedEntity.getCollection());
        final BasicDBObject query = new BasicDBObject(ARCHIVE_ID, id)
                .append(archivedEntity.getFieldName(), new BasicDBObject("$lte", version - archivedEntity.getCount()));
        collection.remove(query);
    }

    public String getArchiveCollection(final Class<? extends T> pClass) {
        return getArchivedEntity(pClass).getCollection();
    }

    @Override
    public void preSave(final Object ent, final DBObject dbObj, final Mapper mapper) {
        if (clazz.isAssignableFrom(ent.getClass())) {
            final ArchivedEntity archivedEntity = getArchivedEntity((Class<? extends T>) ent.getClass());
            if (archivedEntity.isArchived() && dbObj.get("_id") != null) {
                final DBObject archived = fetchForArchiving(archivedEntity, dbObj);
                if (archived != null) {
                    datastore.getDB()
                            .getCollection(archivedEntity.getCollection())
                            .insert(archived);
                    prune(archivedEntity, mapper.getId(ent), getVersion((T) ent));
                }
            }
        }
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
