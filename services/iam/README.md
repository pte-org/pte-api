# IAM service notes

## User profile event contract

The UserCreated event includes the safe student display fields used by
Admin's rebuildable roster projection. The event is additive so existing
consumers may continue to deserialize only the fields they need.

IAM currently has no user profile-update endpoint. Any future endpoint that
changes fullName, studentCode, or phone must emit a UserUpdated event and
Admin's StudentRosterConsumer must handle it in the same idempotent
transactional path. Otherwise the roster projection will silently serve stale
profile data.

## User lifecycle

`POST /api/iam/users/{publicId}/reactivate` is the inverse of suspend. It is
tenant-scoped through the authenticated caller and is idempotent when the user
is already `ACTIVE`. A real `SUSPENDED` -> `ACTIVE` transition writes a
`UserReactivated` outbox event; it does not change the password, roles, or
login hash.
