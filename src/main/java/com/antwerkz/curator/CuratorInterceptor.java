package com.antwerkz.curator;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.EntityInterceptor;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.Mapper;

import java.util.HashMap;
import java.util.Map;

public class CuratorInterceptor implements EntityInterceptor {
    public static final String ARCHIVE_ID = "_aid";
    public static final String ARCH_NUM = "_archNum";
    private Datastore datastore;
    private Mapper mapper;
    private Map<MappedClass, String> collections = new HashMap<>();

    public CuratorInterceptor(final Datastore datastore, final Mapper mapper) {
        this.datastore = datastore;
        this.mapper = mapper;
        mapper.getMappedClasses().stream()
              .filter(m -> m.getAnnotation(Archived.class) != null)
              .forEach(this::register);
    }

    private void register(final MappedClass mapped) {
        final Archived annotation = mapped.getClazz().getAnnotation(Archived.class);
        if (annotation != null) {
            collections.put(mapped, calculateName(mapped, annotation));
        }
    }

    @Override
    public void preSave(final Object ent, final DBObject dbObj, final Mapper mapper) {
        if (dbObj.get("_id") != null) {
            final DBObject archived = fetchForArchiving(ent, dbObj);
            final DB db = datastore.getDB();
            final DBCollection collection = db.getCollection(getArchiveCollection(ent));
            long num = getNextArchiveNum(collection, archived);
            archived.put(ARCH_NUM, num);
            collection.insert(archived);
        }
    }

    private long getNextArchiveNum(final DBCollection collection, final DBObject archived) {
        final DBObject one = collection.findOne(new BasicDBObject(ARCHIVE_ID, archived.get(ARCHIVE_ID)), new BasicDBObject(ARCH_NUM, 1),
                                                new BasicDBObject(ARCH_NUM, -1));
        System.out.println("one = " + one);
        return one != null ? (Long) one.get(ARCH_NUM) + 1: 0;
    }

    private DBObject fetchForArchiving(final Object ent, final DBObject dbObj) {
        final DB db = datastore.getDB();
        final DBCollection collection = db.getCollection(mapper.getMappedClass(ent).getCollectionName());
        final BasicDBObject archived = new BasicDBObject();
        archived.putAll(collection.findOne(new BasicDBObject("_id", dbObj.get("_id"))));
        archived.put(ARCHIVE_ID, archived.remove("_id"));
        return archived;
    }

    private String getArchiveCollection(final Object ent) {
        String name = collections.get(mapper.getMappedClass(ent));
        if (name == null) {
            register(mapper.getMappedClass(ent));
            name = collections.get(mapper.getMappedClass(ent));
        }
        return name;
    }

    private String calculateName(final MappedClass mapped, final Archived annotation) {
        String name = annotation.value();
        if (name.equals("")) {
            name = mapped.getCollectionName() + "_archive";
        }
        return name;
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
