package com.antwerkz.lariat;

import com.antwerkz.lariat.model.Record;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.dao.BasicDAO;

public class RecordDao extends BasicDAO<Record, ObjectId> implements ArchivedDao<Record> {
  private ArchiveInterceptor interceptor;

  public RecordDao(final Datastore ds, final ArchiveInterceptor interceptor) {
    super(Record.class, ds);
    this.interceptor = interceptor;
  }

  @Override
  public ArchiveInterceptor getArchiver() {
    return interceptor;
  }
}
