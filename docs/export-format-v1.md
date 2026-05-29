# Smart Random Reminder Export v1

`Smart Random Reminder Export v1` is the stable backup format. Future app
changes must keep importing this format so older text exports continue to work.

## Header

Current exports start with:

```text
Smart Random Reminder Export v1
```

Imports must also continue accepting the legacy header:

```text
Phone Reminder Export v1
```

## Required Order

Current v1 exports write global settings first:

```text
Default start hour: <0-22>
Default end hour: <1-23>
Reminder days: <DAY,DAY>
```

Each reminder block is then written as:

```text
---
Reminder:
<one or more reminder text lines>
End reminder
Notifications per week: <number>
Notifications per day: <number>
```

## Compatibility Rules

- Do not rename or remove the existing v1 labels.
- Do not reorder required v1 lines in newly exported files unless a new export
  version is introduced.
- New fields must be optional for import and should be appended after the
  required reminder schedule lines.
- The importer must ignore unknown optional reminder metadata.
- Keep the golden export test updated only when intentionally introducing a new
  versioned format.
