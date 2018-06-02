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

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * GeoQuery notifies listeners with this interface about documentSnapshots that entered, exited, or moved within the query.
 */
public interface GeoQueryDataEventListener {

    /**
     * Called if a documentSnapshot entered the search area of the GeoQuery. This method is called for every documentSnapshot currently in the
     * search area at the time of adding the listener.
     *
     * This method is once per documentSnapshot, and is only called again if onDataExited was called in the meantime.
     *
     * @param documentSnapshot The associated documentSnapshot that entered the search area
     * @param location The location for this documentSnapshot as a GeoLocation object
     */
    void onDataEntered(DocumentSnapshot documentSnapshot, GeoLocation location);

    /**
     * Called if a documentSnapshot exited the search area of the GeoQuery. This is method is only called if onDataEntered was called
     * for the documentSnapshot.
     *
     * @param documentSnapshot The associated documentSnapshot that exited the search area
     */
    void onDataExited(DocumentSnapshot documentSnapshot);

    /**
     * Called if a documentSnapshot moved within the search area.
     *
     * This method can be called multiple times.
     *
     * @param documentSnapshot The associated documentSnapshot that moved within the search area
     * @param location The location for this documentSnapshot as a GeoLocation object
     */
    void onDataMoved(DocumentSnapshot documentSnapshot, GeoLocation location);

    /**
     * Called if a documentSnapshot changed within the search area.
     *
     * An onDataMoved() is always followed by onDataChanged() but it is be possible to see
     * onDataChanged() without an preceding onDataMoved().
     *
     * This method can be called multiple times for a single location change, due to the way
     * the Firestore Database handles floating point numbers.
     *
     * Note: this method is not related to ValueEventListener#onDataChange(DocumentSnapshot).
     *
     * @param documentSnapshot The associated documentSnapshot that moved within the search area
     * @param location The location for this documentSnapshot as a GeoLocation object
     */
    void onDataChanged(DocumentSnapshot documentSnapshot, GeoLocation location);

    /**
     * Called once all initial GeoFire data has been loaded and the relevant events have been fired for this query.
     * Every time the query criteria is updated, this observer will be called after the updated query has fired the
     * appropriate documentSnapshot entered or documentSnapshot exited events.
     */
    void onGeoQueryReady();

    /**
     * Called in case an error occurred while retrieving locations for a query, e.g. violating security rules.
     * @param error The error that occurred while retrieving the query
     */
    void onGeoQueryError(Exception error);

}