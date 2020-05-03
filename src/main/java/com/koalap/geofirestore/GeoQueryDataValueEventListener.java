package com.koalap.geofirestore;

import java.util.List;

public interface GeoQueryDataValueEventListener {
    void onDocumentChange(List<GeoQueryDocumentChange> documentChanges);
}