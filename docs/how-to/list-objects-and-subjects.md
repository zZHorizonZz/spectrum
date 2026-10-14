# List objects and subjects

Use a list when one check per item is too slow. For example, show a user the documents they can view, or show the
owner of a document who can view it. A list gives the same answers as a check of each item.

## Before you begin

- Open the relationships with a model. For more information, refer to [Write a model](write-a-model.md).

## List the objects of a subject

Call `listObjects` with the subject, the relation and the type of the objects:

```java
relations.listObjects("user:ana", "viewer", "doc")
    .onSuccess(System.out::println);
```

The result lists the objects in order:

```txt output
[doc:memo, doc:plan]
```

To list the objects that every user can view, pass `user:*` as the subject.

## List the subjects of an object

1. Call `listSubjects` with the object, the relation and the type of the subjects:

   ```java
   relations.listSubjects("doc:plan", "viewer", "user")
       .onSuccess(subjects -> System.out.println(TextFormat.printer().printToString(subjects)));
   ```

2. Read the result. A `SubjectSet` holds some subjects, or every subject but some:

   ```txt output
   subjects: "user:ana"
   subjects: "user:bob"
   ```

   If `all` is `true`, every subject of the type has the relation, except the subjects in `except`:

   ```txt output
   all: true
   except: "user:bob"
   ```

3. Handle both forms in your code:

   ```java
   boolean canView(SubjectSet subjects, String user) {
     return subjects.getAll()
         ? !subjects.getExceptList().contains(user)
         : subjects.getSubjectsList().contains(user);
   }
   ```

## Verify

Compare a list with a check of one item. Both give the same answer:

```java
relations.check("user:ana", "viewer", "doc:plan")
    .onSuccess(System.out::println);
```

```txt output
true
```

## Next steps

- [Check evaluation](../concepts/check-evaluation.md)
- [Limits](../reference/limits.md)
