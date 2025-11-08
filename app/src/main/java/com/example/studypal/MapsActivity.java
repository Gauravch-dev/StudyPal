package com.example.studypal;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import com.example.studypal.Place;
import com.example.studypal.PlacesAdapter;
import com.example.studypal.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private final int LOCATION_REQUEST_CODE = 100;

    private RecyclerView recyclerView;
    private PlacesAdapter adapter;
    private ArrayList<Place> placeList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps); // Use updated XML

        recyclerView = findViewById(R.id.recyclerViewPlaces);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        getUserLocation();
    }

    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST_CODE);

            return;
        }

        fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null
        ).addOnSuccessListener(location -> {

            if (location != null) {
                LatLng userLoc = new LatLng(location.getLatitude(), location.getLongitude());

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLoc, 15));
                mMap.addMarker(new MarkerOptions().position(userLoc).title("You are here"));

                fetchNearbyStudyPlaces(location.getLatitude(), location.getLongitude());
            } else {
                Toast.makeText(this, "Couldn't get location.", Toast.LENGTH_SHORT).show();
            }

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchNearbyStudyPlaces(double lat, double lng) {

        String apiKey = "AIzaSyDw2PASM67HTfZ8qT5zmcYhRiWmrY3OY4c";

        int radius = 2000;
        String keyword = "library|book store|cafe";

        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=" + lat + "," + lng +
                "&radius=" + radius +
                "&keyword=" + keyword +
                "&key=" + apiKey;

        new Thread(() -> {

            try {
                URL requestUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) requestUrl.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(conn.getInputStream()));

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());

                if (!json.getString("status").equals("OK")) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Places API: " + json.optString("status"),
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                JSONArray results = json.getJSONArray("results");

                runOnUiThread(() -> {
                    placeList.clear();

                    for (int i = 0; i < results.length(); i++) {
                        try {
                            JSONObject place = results.getJSONObject(i);

                            String name = place.getString("name");
                            String address = place.optString("vicinity", "No address");

                            double lat1 = place.getJSONObject("geometry")
                                    .getJSONObject("location").getDouble("lat");

                            double lng1 = place.getJSONObject("geometry")
                                    .getJSONObject("location").getDouble("lng");

                            mMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(lat1, lng1))
                                    .title(name)
                                    .icon(BitmapDescriptorFactory
                                            .defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                            placeList.add(new Place(name, address));

                        } catch (Exception ignored) {}
                    }

                    adapter = new PlacesAdapter(placeList);
                    recyclerView.setAdapter(adapter);

                    Toast.makeText(this,
                            "Found " + placeList.size() + " study places",
                            Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            getUserLocation();
        }
    }
}
