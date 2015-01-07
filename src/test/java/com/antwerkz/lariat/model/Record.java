package com.antwerkz.lariat.model;

import com.antwerkz.lariat.Archived;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Property;
import org.mongodb.morphia.annotations.Version;

@Entity("records")
public class Record {
    public static final int MAX_ARCHIVE_COUNT = 3;

    @Id
    private ObjectId id;
    private String name;
    private String content;
    @Version
    @Archived(count = Record.MAX_ARCHIVE_COUNT)
    private long version;

    public Record() {
    }

    public Record(final String name, final String content) {
        this.name = name;
        this.content = content;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(final ObjectId id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public Record setContent(final String content) {
        this.content = content;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("Record{id=%s, content='%s', name='%s'}", id, content, name);
    }
}
