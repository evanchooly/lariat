package com.antwerkz.lariat;

import com.antwerkz.lariat.model.Record;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.dao.BasicDAO;

public class RecordDao extends BasicDAO<Record, ObjectId> implements ArchivedDao<Record, ObjectId> {
  private final ArchiveInterceptor interceptor;

  public RecordDao(final Datastore ds, final ArchiveInterceptor<Record, ObjectId> interceptor) {
    super(Record.class, ds);
    this.interceptor = interceptor;
  }

  @Override
  public ArchiveInterceptor<Record, ObjectId> getArchiver() {
    return interceptor;
  }
}
