package com.antwerkz.lariat;

public interface ArchivedDao<T> {
  default long countVersions(T entity) {
    return getArchiver().countVersions(entity);
  }

  default T rollback(T entity) {
    return getArchiver().rollback(entity);
  }

  default T rollbackToVersion(final T entity, final long targetVersion) {
    return getArchiver().rollbackToVersion(entity, targetVersion);
  }

  ArchiveInterceptor getArchiver();
}
