package com.antwerkz.curator;

class ArchivedEntity {
  private String collection;
  private long count;

  public ArchivedEntity(final String collection, final long count) {
    this.collection = collection;
    this.count = count;
  }

  public String getCollection() {
    return collection;
  }

  public long getCount() {
    return count;
  }
}
