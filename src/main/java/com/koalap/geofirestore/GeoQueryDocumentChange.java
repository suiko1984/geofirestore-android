package com.koalap.geofirestore;

import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class GeoQueryDocumentChange {
    public GeoQueryDocumentChange(final QueryDocumentSnapshot documentSnapshot,
                                  final GeoPoint geoPoint) {
        this.documentSnapshot = documentSnapshot;
        this.geoPoint = geoPoint;
    }

    public QueryDocumentSnapshot getDocumentSnapshot() {
        return documentSnapshot;
    }

    public void setDocumentSnapshot(final QueryDocumentSnapshot documentSnapshot) {
        this.documentSnapshot = documentSnapshot;
    }

    public GeoPoint getGeoPoint() {
        return geoPoint;
    }

    public void setGeoPoint(final GeoPoint geoPoint) {
        this.geoPoint = geoPoint;
    }

    private QueryDocumentSnapshot documentSnapshot;
    private GeoPoint geoPoint;
}
