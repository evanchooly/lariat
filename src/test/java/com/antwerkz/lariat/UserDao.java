package com.antwerkz.lariat;

import com.antwerkz.lariat.model.User;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.dao.BasicDAO;

public class UserDao extends BasicDAO<User, ObjectId> implements ArchivedDao<User, ObjectId> {
  private final ArchiveInterceptor interceptor;

  public UserDao(final Datastore ds, final ArchiveInterceptor<User, ObjectId> interceptor) {
    super(User.class, ds);
    this.interceptor = interceptor;
  }

  @Override
  public ArchiveInterceptor<User, ObjectId> getArchiver() {
    return interceptor;
  }
}
