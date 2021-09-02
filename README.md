# Cassandra OPA Authorizer POC

Open Policy Agent (OPA) Authorizer for Apache Cassandra Proof of Concept.

This repository contains POC code to implement a [custom authorizer](https://cassandra.apache.org/doc/latest/cassandra/operating/security.html#authorization) calling out to OPA for authorization decisions.

The proof of concept didn't turn out to my liking, as the Cassandra [authorizer interface](https://github.com/apache/cassandra/blob/12078910549c6c6e9474f8efb3ef274fa7de8209/src/java/org/apache/cassandra/auth/IAuthorizer.java) 
doesn't really allow for the authorizer to make _decisions_. Rather, the authorizer accepts as input the user (including
roles) and the resource being accessed or modified, but not the actual **action** attempted. Instead, the responsibility 
of the authorizer is to return a set of permissions applicable to the user/resource combination. This works well if the 
"authorizer" is something like a database table, listing applicable grants for a user on any given resource. It is 
however not well suited for a model where the authorizer makes the actual authorization _decision_, like how OPA 
normally operates. 

While we could certainly have OPA return a set of permissions (like `ALTER`, `DROP`, etc) based on 
any given user and resource name, this doesn't utilize the power of OPA as a decision engine, but would more resemble an
in-memory database for permissions. Another consequence of this is that decision logging (one of OPA's super powers) 
can't actually log decisions, but would only be able to list requests like "User X attempted something with resource Y".
This can be seen when enabling decision logging in OPA, and inspecting the `input` object:

```json
{
  "resource": {
    "level": "table",
    "name": "data/test_keyspace2/emp",
    "parent": {
      "level": "keyspace",
      "name": "data/test_keyspace2",
      "parent": {
        "level": "root",
        "name": "data",
        "parent": null
      }
    }
  },
  "user": {
    "name": "testing",
    "roles": [
      "roles/testing"
    ]
  }
}
```

Since neither the resource nor the user contains the action performed, we can't make an informed decision on whether to
allow or deny the request.

## Conclusion

While Apache Cassandra in its current version (4.0) provides an extensible authorization mechanism, it is not well 
suited for a decision engine like OPA. Had the action performed been included in the request to the authorizer, OPA (and
other decision engines) would have been an interesting option for externalized authorization.

## Install and run the POC authorizer

1. `./gradlew shadowJar && cp build/libs/cassandra-opa-authorizer-1.0-SNAPSHOT-all.jar "${CASSANDRA_DIR}/lib/"`
2. Edit the `cassandra.yaml` configuration file to include the following values:

```yaml
authenticator: PasswordAuthenticator
authorizer: com.eknert.cassandra.OpaAuthorizer
```
3. Start Cassandra
4. Start OPA with decision logging enabled: `opa run --server --log-format text --set decision_logs.console=true`
5. As a non-superuser, do any action requiring authorization, like creating a keyspace, a table, etc:

```cassandraql
CREATE KEYSPACE test_keyspace
WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};
```
6. Inspect data provided from the authorizer in the OPA decision log.

NOTE that the POC authorizer currently ignores the response from OPA - had we wanted to continue working under this 
model we would have our OPA policy return a set of permissions, which we would have the authorizer read in and convert
into "real" permissions.
