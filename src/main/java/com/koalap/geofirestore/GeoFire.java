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

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.koalap.geofirestore.core.GeoHash;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A GeoFire instance is used to store geo location data in Firebase.
 */
public class GeoFire {
    public static Logger LOGGER = Logger.getLogger("GeoFire");

    /**
     * A listener that can be used to be notified about a successful write or an error on writing.
     */
    public interface CompletionListener {
        /**
         * Called once a location was successfully saved on the server or an error occurred. On success, the parameter
         * error will be null; in case of an error, the error will be passed to this method.
         *
         * @param key   The key whose location was saved
         * @param exception The exception or null if no exception occurred
         */
        void onComplete(String key, Exception exception);
    }

    /**
     * A small wrapper class to forward any events to the LocationEventListener.
     */
    private static class LocationValueEventListener implements OnCompleteListener<DocumentSnapshot> {

        private final LocationCallback callback;
        private final String key;

        LocationValueEventListener(String key, LocationCallback callback) {
            this.key = key;
            this.callback = callback;
        }

        @Override
        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
            if (task.isSuccessful()) {
                if (task.getResult() == null) {
                    this.callback.onLocationResult(key, null);
                } else {
                    GeoLocation location = GeoFire.getLocationValue(task.getResult());
                    if (location != null) {
                        this.callback.onLocationResult(key, location);
                    } else {
                        String message = "GeoFire data has invalid format: " + task.getResult().toString();
                        this.callback.onCancelled(new Exception(message));
                    }
                }
            }
            else {
                this.callback.onCancelled(task.getException());
            }
        }
    }

    static GeoLocation getLocationValue(DocumentSnapshot documentSnapshot) {
        try {
            Map<String, Object> data = documentSnapshot.getData();
            List<?> location = (List<?>) data.get("l");
            Number latitudeObj = (Number) location.get(0);
            Number longitudeObj = (Number) location.get(1);
            double latitude = latitudeObj.doubleValue();
            double longitude = longitudeObj.doubleValue();
            if (location.size() == 2 && GeoLocation.coordinatesValid(latitude, longitude)) {
                return new GeoLocation(latitude, longitude);
            } else {
                return null;
            }
        } catch (NullPointerException e) {
            return null;
        } catch (ClassCastException e) {
            return null;
        }
    }

    private final CollectionReference collectionReference;
    private final EventRaiser eventRaiser;

    /**
     * Creates a new GeoFire instance at the given Firebase reference.
     *
     * @param documentReference The Firebase reference this GeoFire instance uses
     */
    public GeoFire(CollectionReference documentReference) {
        this.collectionReference = documentReference;
        EventRaiser eventRaiser;
        try {
            eventRaiser = new AndroidEventRaiser();
        } catch (Throwable e) {
            // We're not on Android, use the ThreadEventRaiser
            eventRaiser = new ThreadEventRaiser();
        }
        this.eventRaiser = eventRaiser;
    }

    /**
     * @return The Firebase reference this GeoFire instance uses
     */
    public CollectionReference getCollectionReference() {
        return this.collectionReference;
    }

    /**
     * Sets the location for a given key.
     *
     * @param key      The key to save the location for
     * @param location The location of this document
     */
    public void setLocation(final String key, GeoLocation location) {
        this.setLocation(key, location, null);
    }

    /**
     * Sets the location for a given key.
     *
     * @param key                The key to save the location for
     * @param location           The location of this key
     * @param completionListener A listener that is called once the location was successfully saved on the server or an
     *                           error occurred
     */
    public void setLocation(final String key, final GeoLocation location, final CompletionListener completionListener) {
        DocumentReference docRef = this.collectionReference.document(key);
        GeoHash geoHash = new GeoHash(location);
        Map<String, Object> updates = new HashMap<>();
        updates.put("g", geoHash.getGeoHashString());
        updates.put("l", Arrays.asList(location.latitude, location.longitude));
        if (completionListener != null) {
            docRef.set(updates, SetOptions.merge()).addOnCompleteListener(task -> completionListener.onComplete(key, task.getException()));
        } else {
            docRef.update(updates);
        }
    }

    /**
     * Removes the location for a key from this GeoFire.
     *
     * @param key The key to remove from this GeoFire
     */
    public void removeLocation(final String key) {
        this.removeLocation(key, null);
    }

    /**
     * Removes the location for a key from this GeoFire.
     *
     * @param key                The key to remove from this GeoFire
     * @param completionListener A completion listener that is called once the location is successfully removed
     *                           from the server or an error occurred
     */
    public void removeLocation(final String key, final CompletionListener completionListener) {
        DocumentReference docRef = this.collectionReference.document(key);
        Map<String, Object> updates = new HashMap<>();
        updates.put("g", FieldValue.delete());
        updates.put("l", FieldValue.delete());
        if (completionListener != null) {
            docRef.update(updates).addOnCompleteListener(task -> completionListener.onComplete(key, task.getException()));
        } else {
            docRef.update(updates);
        }
    }

    /**
     * Gets the current location for a key and calls the callback with the current value.
     *
     * @param key      The key whose location to get
     * @param callback The callback that is called once the location is retrieved
     */
    public void getLocation(String key, LocationCallback callback) {
        DocumentReference keyRef = collectionReference.document(key);
        LocationValueEventListener valueListener = new LocationValueEventListener(key, callback);
        keyRef.get().addOnCompleteListener(valueListener);
    }

    /**
     * Returns a new Query object centered at the given location and with the given radius.
     *
     * @param center The center of the query
     * @param radius The radius of the query, in kilometers
     * @return The new GeoQuery object
     */
    public GeoQuery queryAtLocation(GeoLocation center, double radius) {
        return new GeoQuery(this, center, radius);
    }

    void raiseEvent(Runnable r) {
        this.eventRaiser.raiseEvent(r);
    }
}
