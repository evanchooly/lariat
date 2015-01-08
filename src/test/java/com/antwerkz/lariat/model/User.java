package com.antwerkz.lariat.model;

import com.antwerkz.lariat.Archived;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Version;

@Entity("users")
public class User {
    @Id
    private ObjectId id;
    private String name;
    private int age;

    @Version
    @Archived(count = 100)
    private Long version;

    public User() {
    }

    public User(final String name, final int age) {
        this.name = name;
        this.age = age;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(final ObjectId id) {
        this.id = id;
    }

    public int getAge() {
        return age;
    }

    public void setAge(final int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return String.format("Record{id=%s, age='%s', name='%s'}", id, age, name);
    }
}
