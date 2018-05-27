package com.koalap.geofirestore;

/**
 * Classes implementing this interface can be used to receive the locations stored in GeoFire.
 */
public interface LocationCallback {

    /**
     * This method is called with the current location of the key. location will be null if there is no location
     * stored in GeoFire for the key.
     * @param key The key whose location we are getting
     * @param location The location of the key
     */
    public void onLocationResult(String key, GeoLocation location);

    /**
     * Called if the callback could not be added due to failure on the server or security rules.
     * @param exception The exception that occurred
     */
    public void onCancelled(Exception exception);

}

