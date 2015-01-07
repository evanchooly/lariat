package com.antwerkz.lariat;

import java.util.List;

import static java.lang.String.format;
import org.mongodb.morphia.annotations.Version;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.MappingException;

class ArchivedEntity {
  private String collection;

  private long count;

  private String fieldName;

  private final boolean archived;

  MappedClass mappedClass;

  public ArchivedEntity(final MappedClass mappedClass) {
    this.mappedClass = mappedClass;
    final List<MappedField> list = mappedClass.getFieldsAnnotatedWith(Archived.class);
    archived = !list.isEmpty();
    if (archived) {
      final MappedField archiveField = list.get(0);
      final MappedField versionField = mappedClass.getFieldsAnnotatedWith(Version.class).get(0);
      if(!archiveField.equals(versionField)) {
        throw new MappingException(format("@Archived must be on the same field as @Version.  "
                + "@Archived is on %s instead of %s", archiveField.getFullName(), versionField.getFullName()));
      }
      final Archived annotation = archiveField.getAnnotation(Archived.class);

      this.collection = getCollection(annotation);
      this.count = annotation.count();
      this.fieldName = versionField.getNameToStore();
    }
  }

  private String getCollection(final Archived annotation) {
    String name = annotation.collection();
    if (name.equals("")) {
      name = mappedClass.getCollectionName() + "_archive";
    }
    return name;
  }

  public boolean isArchived() {
    return archived;
  }

  public String getCollection() {
    return collection;
  }

  public long getCount() {
    return count;
  }

  public String getFieldName() {
    return fieldName;
  }

  public MappedClass getMappedClass() {
    return mappedClass;
  }

  public void setMappedClass(final MappedClass mappedClass) {
    this.mappedClass = mappedClass;
  }

  @Override
  public String toString() {
    return "ArchivedEntity{" +
        "archived=" + archived +
        ", collection='" + collection + '\'' +
        ", count=" + count +
        ", fieldName='" + fieldName + '\'' +
        '}';
  }
}
