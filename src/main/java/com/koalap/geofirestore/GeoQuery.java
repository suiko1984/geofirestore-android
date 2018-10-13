package com.koalap.geofirestore;
/*
 * Firebase GeoFire Java Library
 *
 * Copyright © 2014 Firebase - All Rights Reserved
 * https://www.firebase.com
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binaryform must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY FIREBASE AS IS AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL FIREBASE BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.koalap.geofirestore.core.GeoHash;
import com.koalap.geofirestore.core.GeoHashQuery;
import com.koalap.geofirestore.util.GeoUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A GeoQuery object can be used for geo queries in a given circle. The GeoQuery class is thread safe.
 */
public class GeoQuery {
    private static final int KILOMETER_TO_METER = 1000;
    private ListenerRegistration removeListerRegistration;

    private static class LocationInfo {
        final GeoLocation location;
        final boolean inGeoQuery;
        final GeoHash geoHash;
        final DocumentSnapshot documentSnapshot;

        public LocationInfo(GeoLocation location, boolean inGeoQuery, DocumentSnapshot documentSnapshot) {
            this.location = location;
            this.inGeoQuery = inGeoQuery;
            this.geoHash = new GeoHash(location);
            this.documentSnapshot = documentSnapshot;
        }
    }

    private final EventListener<QuerySnapshot> queryListener = (queryDocumentSnapshots, e) -> {
        for (DocumentChange dc : queryDocumentSnapshots.getDocumentChanges()) {
            switch (dc.getType()) {
                case ADDED:
                    childAdded(dc.getDocument());
                    break;
                case MODIFIED:
                    childChanged(dc.getDocument());
                    break;
                case REMOVED:
                    childRemoved(dc.getDocument());
                    break;
            }
        }
    };

    private final GeoFire geoFire;
    private final Set<GeoQueryDataEventListener> eventListeners = new HashSet<>();
    private final Map<GeoHashQuery, ListenerRegistration> firebaseQueries = new HashMap<>();
    private final Set<GeoHashQuery> outstandingQueries = new HashSet<>();
    private final Map<String, LocationInfo> locationInfos = new HashMap<>();
    private GeoLocation center;
    private double radius;
    private Set<GeoHashQuery> queries;

    /**
     * Creates a new GeoQuery object centered at the given location and with the given radius.
     * @param geoFire The GeoFire object this GeoQuery uses
     * @param center The center of this query
     * @param radius The radius of the query, in kilometers. The maximum radius that is
     * supported is about 8587km. If a radius bigger than this is passed we'll cap it.
     */
    GeoQuery(GeoFire geoFire, GeoLocation center, double radius) {
        this.geoFire = geoFire;
        this.center = center;
        // convert from kilometers to meters
        this.radius = radius * KILOMETER_TO_METER; // Convert from kilometers to meters.
    }

    private boolean locationIsInQuery(GeoLocation location) {
        return GeoUtils.distance(location, center) <= this.radius;
    }

    private void updateLocationInfo(final DocumentSnapshot documentSnapshot, final GeoLocation location) {
        String key = documentSnapshot.getId();
        LocationInfo oldInfo = this.locationInfos.get(key);
        boolean isNew = oldInfo == null;
        final boolean changedLocation = oldInfo != null && !oldInfo.location.equals(location);
        boolean wasInQuery = oldInfo != null && oldInfo.inGeoQuery;

        boolean isInQuery = this.locationIsInQuery(location);
        if ((isNew || !wasInQuery) && isInQuery) {
            for (final GeoQueryDataEventListener listener: this.eventListeners) {
                this.geoFire.raiseEvent(() -> listener.onDataEntered(documentSnapshot, location));
            }
        } else if (!isNew && isInQuery) {
            for (final GeoQueryDataEventListener listener: this.eventListeners) {
                this.geoFire.raiseEvent(() -> {
                    if (changedLocation) {
                        listener.onDataMoved(documentSnapshot, location);
                    }

                    listener.onDataChanged(documentSnapshot, location);
                });
            }
        } else if (wasInQuery && !isInQuery) {
            for (final GeoQueryDataEventListener listener: this.eventListeners) {
                this.geoFire.raiseEvent(() -> listener.onDataExited(documentSnapshot));
            }
        }
        LocationInfo newInfo = new LocationInfo(location, this.locationIsInQuery(location), documentSnapshot);
        this.locationInfos.put(key, newInfo);
    }

    private boolean geoHashQueriesContainGeoHash(GeoHash geoHash) {
        if (this.queries == null) {
            return false;
        }
        for (GeoHashQuery query: this.queries) {
            if (query.containsGeoHash(geoHash)) {
                return true;
            }
        }
        return false;
    }

    private void reset() {
        for (Map.Entry<GeoHashQuery, ListenerRegistration> entry: this.firebaseQueries.entrySet()) {
            entry.getValue().remove();
        }
        this.outstandingQueries.clear();
        this.firebaseQueries.clear();
        this.queries = null;
        this.locationInfos.clear();
    }

    private boolean hasListeners() {
        return !this.eventListeners.isEmpty();
    }

    private boolean canFireReady() {
        return this.outstandingQueries.isEmpty();
    }

    private void checkAndFireReady() {
        if (canFireReady()) {
            for (final GeoQueryDataEventListener listener: this.eventListeners) {
                this.geoFire.raiseEvent(listener::onGeoQueryReady);
            }
        }
    }

    private void addValueToReadyListener(final Query firebase, final GeoHashQuery query) {
        firebase.get().addOnCompleteListener(task -> {
            if (task.isCanceled()) {
                synchronized (GeoQuery.this) {
                    for (final GeoQueryDataEventListener listener : GeoQuery.this.eventListeners) {
                        GeoQuery.this.geoFire.raiseEvent(() -> listener.onGeoQueryError(task.getException()));
                    }
                }
            }
            else {
                synchronized (GeoQuery.this) {
                    GeoQuery.this.outstandingQueries.remove(query);
                    GeoQuery.this.checkAndFireReady();
                }
            }
        });
    }

    private void setupQueries() {
        Set<GeoHashQuery> oldQueries = (this.queries == null) ? new HashSet<>() : this.queries;
        Set<GeoHashQuery> newQueries = GeoHashQuery.queriesAtLocation(center, radius);
        this.queries = newQueries;
        for (GeoHashQuery query: oldQueries) {
            if (!newQueries.contains(query)) {
                firebaseQueries.get(query).remove();
                firebaseQueries.remove(query);
                outstandingQueries.remove(query);
            }
        }
        for (final GeoHashQuery query: newQueries) {
            if (!oldQueries.contains(query)) {
                outstandingQueries.add(query);
                CollectionReference collectionReference = this.geoFire.getCollectionReference();
                Query filterQuery = this.geoFire.getQuery();
                Query firebaseQuery = (filterQuery != null ? filterQuery : collectionReference).orderBy("g").startAt(query.getStartValue()).endAt(query.getEndValue());
                final ListenerRegistration registration = firebaseQuery.addSnapshotListener((queryDocumentSnapshots, e) -> {
                    try {
                        if (e != null) {
                            throw e;
                        }
                        for (DocumentChange dc : queryDocumentSnapshots.getDocumentChanges()) {
                            switch (dc.getType()) {
                                case ADDED:
                                    childAdded(dc.getDocument());
                                    break;
                                case MODIFIED:
                                    childChanged(dc.getDocument());
                                    break;
                                case REMOVED:
                                    childRemoved(dc.getDocument());
                                    break;
                            }
                        }
                    }
                    catch (Exception exception) {
                        exception.printStackTrace();
                    }
                });
                addValueToReadyListener(firebaseQuery, query);
                firebaseQueries.put(query, registration);
            }
        }
        for (Map.Entry<String, LocationInfo> info: this.locationInfos.entrySet()) {
            LocationInfo oldLocationInfo = info.getValue();

            if (oldLocationInfo != null) {
                updateLocationInfo(oldLocationInfo.documentSnapshot, oldLocationInfo.location);
            }
        }
        // remove locations that are not part of the geo query anymore
        Iterator<Map.Entry<String, LocationInfo>> it = this.locationInfos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, LocationInfo> entry = it.next();
            if (!this.geoHashQueriesContainGeoHash(entry.getValue().geoHash)) {
                it.remove();
            }
        }

        checkAndFireReady();
    }

    private void childAdded(DocumentSnapshot documentSnapshot) {
        GeoLocation location = GeoFire.getLocationValue(documentSnapshot);
        if (location != null) {
            this.updateLocationInfo(documentSnapshot, location);
        } else {
            // throw an error in future?
        }
    }

    private void childChanged(DocumentSnapshot documentSnapshot) {
        GeoLocation location = GeoFire.getLocationValue(documentSnapshot);
        if (location != null) {
            this.updateLocationInfo(documentSnapshot, location);
        } else {
            // throw an error in future?
        }
    }

    private void childRemoved(DocumentSnapshot documentSnapshot) {
        final String key = documentSnapshot.getId();
        final LocationInfo info = this.locationInfos.get(key);
        if (info != null) {
            removeListerRegistration = this.geoFire.getCollectionReference().document(key).addSnapshotListener((documentSnapshot1, e) -> {
                removeListerRegistration.remove();
                synchronized(GeoQuery.this) {
                    final LocationInfo info1 = GeoQuery.this.locationInfos.get(key);
                    GeoQuery.this.locationInfos.remove(key);
                    for (final GeoQueryDataEventListener listener: GeoQuery.this.eventListeners) {
                        GeoQuery.this.geoFire.raiseEvent(() -> listener.onDataExited(info1.documentSnapshot));
                    }
                }
            });
        }
    }

    /**
     * Adds a new GeoQueryEventListener to this GeoQuery.
     *
     * @throws IllegalArgumentException If this listener was already added
     *
     * @param listener The listener to add
     */
    public synchronized void addGeoQueryEventListener(final GeoQueryEventListener listener) {
        addGeoQueryDataEventListener(new EventListenerBridge(listener));
    }

    /**
     * Adds a new GeoQueryEventListener to this GeoQuery.
     *
     * @throws IllegalArgumentException If this listener was already added
     *
     * @param listener The listener to add
     */
    public synchronized void addGeoQueryDataEventListener(final GeoQueryDataEventListener listener) {
        if (eventListeners.contains(listener)) {
            throw new IllegalArgumentException("Added the same listener twice to a GeoQuery!");
        }
        eventListeners.add(listener);
        if (this.queries == null) {
            this.setupQueries();
        } else {
            for (final Map.Entry<String, LocationInfo> entry: this.locationInfos.entrySet()) {
                final LocationInfo info = entry.getValue();

                if (info.inGeoQuery) {
                    this.geoFire.raiseEvent(() -> listener.onDataEntered(info.documentSnapshot, info.location));
                }
            }
            if (this.canFireReady()) {
                this.geoFire.raiseEvent(listener::onGeoQueryReady);
            }
        }
    }

    /**
     * Removes an event listener.
     *
     * @throws IllegalArgumentException If the listener was removed already or never added
     *
     * @param listener The listener to remove
     */
    public synchronized void removeGeoQueryEventListener(GeoQueryEventListener listener) {
        removeGeoQueryEventListener(new EventListenerBridge(listener));
    }

    /**
     * Removes an event listener.
     *
     * @throws IllegalArgumentException If the listener was removed already or never added
     *
     * @param listener The listener to remove
     */
    public synchronized void removeGeoQueryEventListener(final GeoQueryDataEventListener listener) {
        if (!eventListeners.contains(listener)) {
            throw new IllegalArgumentException("Trying to remove listener that was removed or not added!");
        }
        eventListeners.remove(listener);
        if (!this.hasListeners()) {
            reset();
        }
    }

    /**
     * Removes all event listeners from this GeoQuery.
     */
    public synchronized void removeAllListeners() {
        eventListeners.clear();
        reset();
    }

    /**
     * Returns the current center of this query.
     * @return The current center
     */
    public synchronized GeoLocation getCenter() {
        return center;
    }

    /**
     * Sets the new center of this query and triggers new events if necessary.
     * @param center The new center
     */
    public synchronized void setCenter(GeoLocation center) {
        this.center = center;
        if (this.hasListeners()) {
            this.setupQueries();
        }
    }

    /**
     * Returns the radius of the query, in kilometers.
     * @return The radius of this query, in kilometers
     */
    public synchronized double getRadius() {
        // convert from meters
        return radius / 1000;
    }

    /**
     * Sets the radius of this query, in kilometers, and triggers new events if necessary.
     * @param radius The new radius value of this query in kilometers
     */
    public synchronized void setRadius(double radius) {
        // convert to meters
        this.radius = radius * 1000;
        if (this.hasListeners()) {
            this.setupQueries();
        }
    }

    /**
     * Sets the center and radius (in kilometers) of this query, and triggers new events if necessary.
     * @param center The new center
     * @param radius The new radius value of this query in kilometers
     */
    public synchronized void setLocation(GeoLocation center, double radius) {
        this.center = center;
        // convert radius to meters
        this.radius = radius * 1000;
        if (this.hasListeners()) {
            this.setupQueries();
        }
    }
}
