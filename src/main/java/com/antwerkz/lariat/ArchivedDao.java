package com.antwerkz.lariat;

public interface ArchivedDao<T, K> {
  default long countVersions(final T entity) {
    return getArchiver().countVersions(entity);
  }

  default T findArchivedVersion(final T entity, final long targetVersion) {
    return getArchiver().findArchivedVersion(entity, targetVersion);
  }

  default T revert(final T entity) {
    return getArchiver().revert(entity);
  }

  default T revertToVersion(final T entity, final long targetVersion) {
    return getArchiver().revertToVersion(entity, targetVersion);
  }

  ArchiveInterceptor<T, K> getArchiver();
}
