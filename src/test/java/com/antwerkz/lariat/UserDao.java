package com.antwerkz.lariat;

import com.antwerkz.lariat.model.User;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.dao.BasicDAO;

public class UserDao extends BasicDAO<User, ObjectId> implements ArchivedDao<User> {
  private ArchiveInterceptor interceptor;

  public UserDao(final Datastore ds, final ArchiveInterceptor interceptor) {
    super(User.class, ds);
    this.interceptor = interceptor;
  }

  @Override
  public ArchiveInterceptor getArchiver() {
    return interceptor;
  }
}
