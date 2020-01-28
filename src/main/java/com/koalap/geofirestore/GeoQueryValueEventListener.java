package com.koalap.geofirestore;

import com.google.firebase.firestore.DocumentChange;

import java.util.List;


public interface GeoQueryValueEventListener {
    void onDocumentChange(List<DocumentChange> documentChanges);
}