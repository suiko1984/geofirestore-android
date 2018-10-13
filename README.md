# GeoFirestore for Android — Realtime location queries with Firebase

GeoFirestore is an open-source library for Android that allows you to store and query a
set of keys based on their geographic location.

At its heart, GeoFirestore simply stores locations with string keys. Its main
benefit however, is the possibility of querying keys within a given geographic
area - all in realtime.

GeoFirestore uses the [Firebase](https://www.firebase.com/?utm_source=geofire-java) Cloud Firestore for
data storage, allowing query results to be updated in realtime as they change.
GeoFirestore *selectively loads only the data near certain locations, keeping your
applications light and responsive*, even with extremely large datasets.

### Integrating GeoFirestore with your data

GeoFirestore is designed as a lightweight add-on to the Firebase Cloud Firestore Database. However, to keep things
simple, GeoFirestore stores data in its own format and its own location within
your Firestore database. This allows your existing data format and security rules to
remain unchanged and for you to add GeoFirestore as an easy solution for geo queries
without modifying your existing data.

### Example Usage

Assume you are building an app to rate bars and you store all information for a
bar, e.g. name, business hours and price range, at `/bars/<bar-id>`. Later, you
want to add the possibility for users to search for bars in their vicinity. This
is where GeoFirestore comes in. You can store the location for each bar using
GeoFirestore, using the bar IDs as GeoFirestore keys. GeoFirestore then allows you to easily
query which bar IDs (the keys) are nearby. To display any additional information
about the bars, you can load the information for each bar returned by the query
at `/bars/<bar-id>`.

## Including GeoFirestore in your project Android/Java

In order to use GeoFirestore in your project, you need to [add the Firebase Android
SDK](https://firebase.google.com/docs/android/setup). After that you can include GeoFirestore with one of the choices below.

### Gradle

Add a dependency for GeoFirestore to your `gradle.build` file.

For Android applications:

```groovy
dependencies {
    implementation 'com.koalap:geofirestore-android:1.0.1'
}
```

### Maven

GeoFire also works with Maven.

For Android applications:

```xml
<dependency>
  <groupId>com.koalap</groupId>
  <artifactId>geofirestore-android</artifactId>
  <version>1.0.1</version>
</dependency>
```

### GeoFirestore

A `GeoFirestore` object is used to read and write geo location data to your Firebase
Firebase Cloud database and to create queries. To create a new `GeoFirestore` instance you need to attach it to a Firebase Firestore
reference.

```java
CollectionReference ref = FirebaseFirestore.getInstance().collection("collection");
GeoFire geoFire = new GeoFire(ref);
```

#### Setting location data

In GeoFirestore you can set and query locations by string keys. To set a location for
a key simply call the `setLocation` method. The method is passed a key
as a string and the location as a `GeoLocation` object containing the location's latitude and longitude:

```java
geoFire.setLocation("firebase-hq", new GeoLocation(37.7853889, -122.4056973));
```

To check if a write was successfully saved on the server, you can add a
`GeoFire.CompletionListener` to the `setLocation` call:

```java
geoFire.setLocation("firebase-hq", new GeoLocation(37.7853889, -122.4056973),
    new GeoFire.CompletionListener() {
    @Override
    public void onComplete(String key, Exception exception) {
        if (error != null) {
            System.err.println("There was an error saving the location to GeoFire: " + exception.toString());
        } else {
            System.out.println("Location saved on server successfully!");
        }
    }
});
```

To remove a location and delete it from the database simply pass the location's key to `removeLocation`:

```java
geoFire.removeLocation("firebase-hq");
```

#### Retrieving a location

Retrieving a location for a single key in GeoFirestore happens with callbacks:

```java
geoFire.getLocation("firebase-hq", new LocationCallback() {
    @Override
    public void onLocationResult(String key, GeoLocation location) {
        if (location != null) {
            System.out.println(String.format("The location for key %s is [%f,%f]", key, location.latitude, location.longitude));
        } else {
            System.out.println(String.format("There is no location for key %s in GeoFire", key));
        }
    }
                                 
    @Override
    public void onCancelled(Exception exception) {
        System.err.println("There was an error getting the GeoFire location: " + exception.toString());
    }
});
```

### Geo Queries

GeoFirestore allows you to query all keys within a geographic area using `GeoQuery`
objects. As the locations for keys change, the query is updated in realtime and fires events
letting you know if any relevant keys have moved. `GeoQuery` parameters can be updated
later to change the size and center of the queried area.

```java
// creates a new query around [37.7832, -122.4056] with a radius of 0.6 kilometers
GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(37.7832, -122.4056), 0.6);
```

#### Receiving events for geo queries

##### Key Events

There are five kinds of "key" events that can occur with a geo query:

1. **Key Entered**: The location of a key now matches the query criteria.
2. **Key Exited**: The location of a key no longer matches the query criteria.
3. **Key Moved**: The location of a key changed but the location still matches the query criteria.
4. **Query Ready**: All current data has been loaded from the server and all
   initial events have been fired.
5. **Query Error**: There was an error while performing this query, e.g. a
   violation of security rules.

Key entered events will be fired for all keys initially matching the query as well as any time
afterwards that a key enters the query. Key moved and key exited events are guaranteed to be
preceded by a key entered event.

Sometimes you want to know when the data for all the initial keys has been
loaded from the server and the corresponding events for those keys have been
fired. For example, you may want to hide a loading animation after your data has
fully loaded. This is what the "ready" event is used for.

Note that locations might change while initially loading the data and key moved and key
exited events might therefore still occur before the ready event is fired.

When the query criteria is updated, the existing locations are re-queried and the
ready event is fired again once all events for the updated query have been
fired. This includes key exited events for keys that no longer match the query.

To listen for events you must add a `GeoQueryEventListener` to the `GeoQuery`:

```java
geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
    @Override
    public void onKeyEntered(String key, GeoLocation location) {
        System.out.println(String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
    }

    @Override
    public void onKeyExited(String key) {
        System.out.println(String.format("Key %s is no longer in the search area", key));
    }

    @Override
    public void onKeyMoved(String key, GeoLocation location) {
        System.out.println(String.format("Key %s moved within the search area to [%f,%f]", key, location.latitude, location.longitude));
    }

    @Override
    public void onGeoQueryReady() {
        System.out.println("All initial data has been loaded and events have been fired!");
    }

    @Override
    public void onGeoQueryError(Exception exception) {
        System.err.println("There was an error with this query: " + exception.toString());
    }
});
```

You can call either `removeGeoQueryEventListener` to remove a
single event listener or `removeAllListeners` to remove all event listeners
for a `GeoQuery`.

##### Compound query

Since GeoFirestore 1.1.0, you can filter the geo query by adding "whereEqualTo", "whereArrayContains", and/or "limit" criterias.
Note that in order to "queryAtLocation", GeoFirestore natively uses an algorithm that already performs a query using "orderBy("g").startAt(query.getStartValue()).endAt(query.getEndValue())". This is why it is not permitted to add "whereGreater", "whereLess", "orderBy" compound queries (see Query limitations section on this link: https://firebase.google.com/docs/firestore/query-data/queries?authuser=0#query_limitations).
Beware that a StatusException may occur if you have not created an index on Firestore console.

```java
CollectionReference ref = FirebaseFirestore.getInstance().collection("collection");
GeoFire geoFire = new GeoFire(ref, ref.whereEqualTo("availability", true);
```

##### Data Events

If you are storing model data and geo data in the same database location, you may
want access to the `DataSnapshot` as part of geo events. In this case, use a
`GeoQueryDataEventListener` rather than a key listener.

These "data event" listeners have all of the same events as the key listeners with
one additional event type:

  6. **Data Changed**: the underlying `DataSnapshot` has changed. Every "data moved"
     event is followed by a data changed event but you can also get change events without
     a move if the data changed does not affect the location.

Adding a data event listener is similar to adding a key event listener:

```java
geoQuery.addGeoQueryDataEventListener(new GeoQueryDataEventListener() {

  @Override
  public void onDataEntered(DocumentSnapshop documentSnapshot, GeoLocation location) {
    // ...
  }

  @Override
  public void onDataExited(DocumentSnapshop documentSnapshot) {
    // ...
  }

  @Override
  public void onDataMoved(DocumentSnapshop documentSnapshot, GeoLocation location) {
    // ...
  }

  @Override
  public void onDataChanged(DocumentSnapshop documentSnapshot, GeoLocation location) {
    // ...
  }

  @Override
  public void onGeoQueryReady() {
    // ...
  }

  @Override
  public void onGeoQueryError(DatabaseError error) {
    // ...
  }
});

```

#### Updating the query criteria

The `GeoQuery` search area can be changed with `setCenter` and `setRadius`. Key
exited and key entered events will be fired for keys moving in and out of
the old and new search area, respectively. No key moved events will be
fired; however, key moved events might occur independently.

Updating the search area can be helpful in cases such as when you need to update
the query to the new visible map area after a user scrolls.


## Contributing

If you want to contribute to GeoFirestore for Android, clone the repository
and just start making pull requests.

```bash
git clone https://github.com/suiko1984/geofirestore-android.git
```