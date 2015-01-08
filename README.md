# Lariat

A semi-experimental extension to [morphia](https://github.com/mongodb/morphia) that transparently archives
old versions of your entities and allows for rollbacks to previous states.  e.g., the class below will be
archived in to the collection `records_archive` and will keep only the last 3 versions.  More docs and examples are
(probably) on the way but for now see `LariatTest.java`.

```java
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
    }
```