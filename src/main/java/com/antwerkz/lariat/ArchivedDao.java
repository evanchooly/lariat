package com.antwerkz.lariat;

public interface ArchivedDao<T> {
    String ARCHIVE_ID = "_aid";

    default T rollback(T entity)  {
        return getArchiver().rollback(entity);
    }

    ArchiveInterceptor getArchiver();
}
