package com.antwerkz.lariat.model;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Entity("items")
public class Item {
  @Id
  private ObjectId id;
  private String name;
  private int value;

  public Item() {
  }

  public Item(final String name, final int value) {
    this.name = name;
    this.value = value;
  }
}
