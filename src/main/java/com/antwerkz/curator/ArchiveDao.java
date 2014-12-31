package com.antwerkz.curator;

public interface ArchiveDao<T> {
    String ARCHIVE_ID = "_aid";
    String ARCH_NUM = "_archNum";

    default T rollback(T entity)  {
        return getCurator().rollback(entity);
    }

    Curator getCurator();
}
