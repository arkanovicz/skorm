# kddl format

redundancy around the database name

# ksql format

- Allow an entity context:

```
Book {
  attr fooBar...
}
```

- Review syntax, so that Json.Object becomes a default response atom

Problem. If we make the type after the colon optional, how do we specify multiplicity? On which goes the '*' ?

Or maybe it's not a problem.

Alternative: get rid of 'attr'

=>

 - fun or mut or mutation for mutations
 - val or scalar for scalars
 - row for rows
 - rowset for rowsets

scalar
row
rowset
mutation

- does not need database name (redundant, and not used)
