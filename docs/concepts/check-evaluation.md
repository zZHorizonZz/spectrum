# Check evaluation

Spectrum answers three questions from the relationships and the model:

| Method         | Question                                                   | Direction            |
| -------------- | ---------------------------------------------------------- | -------------------- |
| `check`        | Does this subject have this relation to this object?       | Down from the object |
| `listObjects`  | Which objects of a type does this subject have it to?      | Up from the subject  |
| `listSubjects` | Which subjects of a type have this relation to the object? | Down from the object |

The three methods always agree. If `check` returns `true`, the object is in `listObjects` and the subject is in
`listSubjects`.

## Check

A check starts at the object and follows the rewrite of the relation, as Zanzibar does. For each relation of an
object that it reaches, the check reads the relationships of that relation once. It stops at the first subject that
answers the question.

For example, to check `user:ana viewer doc:plan`, the check reads `doc:plan#viewer`. If `doc:plan` has a `parent`,
the check reads `doc:plan#parent` and then the viewers of each parent folder. If a relationship names
`group:eng#member`, the check reads the members of `group:eng`.

A check of `user:*` asks whether every user has the relation. It returns `true` only when no user is left out.

## Cycles

Relationships can form a cycle. For example, `group:a` has the members of `group:b`, and `group:b` has the members
of `group:a`. A check that reaches a relation again on its own path counts that relation as `false` there. The check
then stops instead of looping. A subject that has the relation has it by a path without the cycle, so the answer is
still correct.

A cycle through a `difference` can leave a relation undecided. For example, readers are viewers who are not blocked,
and blocked users are readers. Spectrum uses three values, as in Kleene logic: true, false and undecided. An
undecided relation never lets a subject in. The check returns `true` only for a relation that is true.

## Lists of objects

A list of objects starts at the subject and goes up. Each level is one read of the relationships whose subject is
one of the subjects that the level before reached. For example, the list reads the groups that `user:ana` is a
member of, then the folders that those groups can view, and so on.

Going up cannot decide an `intersection` or a `difference`. With a model that has one, the list checks each object
that it finds before it returns the object.

## Lists of subjects

A list of subjects returns a `SubjectSet`: some subjects, or every subject but some. For example, if every user can
view a document but `user:bob` is blocked, the set is every user except `user:bob`:

```txt
all: true
except: "user:bob"
```

OpenFGA returns `user:*` in this case and leaves out the exception. A `SubjectSet` names it, so the list gives
exactly the subjects that a check lets in.

## Contextual relationships

A request can bring relationships that hold only for it, such as the groups of a user from a token.
`relations.with(contextual)` gives relationships in which a check and a list count those relationships as if the
table had them. OpenFGA calls them contextual tuples. Nothing writes them, and a read gives only the relationships
of the table. The model must allow each of them, as it must allow a relationship that you write.

## Depth

A check and a list follow the model at most 25 levels deep. Each step to another relation is one level: a
`computed` rewrite, a `from` rewrite, or a userset such as `group:eng#member`. Beyond 25 levels, the method fails
with an `IllegalStateException`. A chain that long is usually a mistake in the model or in the relationships.

## Reads

Each call reads each relation of an object at most once. A list of objects reads up to 500 subjects in one
statement, so a large set of subjects takes several statements. Spectrum does not cache reads between calls.

## Related resources

- [Models](models.md)
- [List objects and subjects](../how-to/list-objects-and-subjects.md)
- [Limits](../reference/limits.md)
