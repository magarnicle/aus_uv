package com.example.ausuv;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainActivity extends AppCompatActivity {

    private Button refresh;
    private TextView main_text;

    private TextView status;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        main_text = findViewById(R.id.main_text);
        status = findViewById(R.id.status);

        refresh = findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    refresh();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });


        try {
            refresh();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void refresh() throws InterruptedException {

        final double[] longitude = new double[1];
        final double[] latitude = new double[1];
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            longitude[0] = 0.0;
            latitude[0] = 0.0;
        } else {
            Task<Location> location_task = fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            longitude[0] = location.getLongitude();
                            latitude[0] = location.getLatitude();

                        } else {
                            // Default to Canberra
                            longitude[0] = 149.2;
                            latitude[0] = -35.31;
                        }
                        Thread thread = new Thread(new Runnable() {

                            @SuppressLint("MissingPermission")
                            @Override
                            public void run() {
// Logic to handle location object
                                runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        try {
                                            status.setText("Refreshing data...");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                                JSONObject closest_site;
                                if (Math.round(longitude[0]) == 149 && Math.round(latitude[0]) == -35) {
                                    // We're in Canberra - the default - so no need to look it up
                                    try {
                                        closest_site = new JSONObject("{\"Name\": \"Canberra\", \"Latitude\": -35.31, \"Longitude\": 149.2}");
                                    } catch (JSONException e) {
                                        throw new RuntimeException(e);
                                    }
                                } else {
                                    closest_site = closestSite(latitude[0], longitude[0]);
                                    if (closest_site == null) {
                                        try {
                                            closest_site = new JSONObject("{\"Name\": \"Canberra\", \"Latitude\": -35.31, \"Longitude\": 149.2}");
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }
                                JSONObject uv_data = null;
                                try {
                                    uv_data = updateData(closest_site.getDouble("Latitude"), closest_site.getDouble("Longitude"));
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                if (uv_data == null){
                                    runOnUiThread(new Runnable() {

                                        @Override
                                        public void run() {
                                            try {
                                                status.setText("ERROR! Could not refresh UV data." +
                                                        "\nPlease check internet connection.");
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                    return;
                                }
                                final String refresh_time;
                                final String name;
                                final double current_uv;
                                final double max_so_far;
                                JSONArray forecast_values;
                                try {
                                    name = closest_site.getString("Name");
                                    current_uv = uv_data.getDouble("CurrentUVIndex");
                                    max_so_far = uv_data.getDouble("MaximumUVLevel");
                                    forecast_values = uv_data.getJSONArray("GraphData");
                                    refresh_time = uv_data.getString("CurrentDateTime");
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                double max_today = 0.0;
                                String max_occurs_at = "1900-01-01 00:00";
                                for (int i = 0; i < forecast_values.length(); i++) {
                                    JSONObject forecast_value = null;
                                    try {
                                        forecast_value = forecast_values.getJSONObject(i);
                                    } catch (JSONException e) {
                                        continue;
                                    }
                                    double forecast;
                                    String forecast_date;
                                    try {
                                        forecast = forecast_value.getDouble("Forecast");
                                        forecast_date = forecast_value.getString("Date");
                                    } catch (JSONException e) {
                                        continue;
                                    }
                                    if (forecast > max_today){
                                        max_today = forecast;
                                        max_occurs_at = forecast_date;
                                    }
                                }
                                final double forecast_max = max_today;
                                final String forecast_time = max_occurs_at;
                                runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        try {
                                            main_text.setText(
                                                    new StringBuilder()
                                                            .append("Closest Site: ").append(name)
                                                            .append("\nCurrent UV: ").append(String.format("%.2f", current_uv))
                                                            .append("\nMax UV So Far: ").append(String.format("%.2f", max_so_far))
                                                            .append("\nForecast Max UV Today: ").append(String.format("%.2f", forecast_max))
                                                            .append("\nForecast Max Occurs At: ").append(forecast_time.substring(11))
                                                            .append("\nLast Refresh: ").append(refresh_time)
                                                            .toString());
                                            status.setText("");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });




                            }
                        });
                        thread.start();
                    });

        }


    }
    JSONObject updateData(double lat, double lon) {
        try {
            // Get today's date in the format yyyy-mm-dd
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayFormatted = today.format(formatter);

            // API endpoint and parameters
            String endpoint = "https://uvdata.arpansa.gov.au/api/uvlevel";
            String parameters = String.format("longitude=" + lon + "&latitude=" + lat + "&date=%s", todayFormatted);

            // Create URL object
            URL url = new URL(endpoint + "?" + parameters);

            // Create HttpURLConnection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request method
            connection.setRequestMethod("GET");

            // Get response code
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse JSON response

                return new JSONObject(response.toString());
            } else {
                status.setText("Error: HTTP response code " + responseCode);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    JSONObject closestSite(double lat, double lon) {
        // Find the closest UV measurement site for the given lat. and lon.
        try {
            // API endpoint and parameters
            String endpoint = "https://uvdata.arpansa.gov.au/api/closestLocation";
            String parameters = "longitude=" + lon + "&latitude=" + lat;

            // Create URL object
            URL url = new URL(endpoint + "?" + parameters);

            // Create HttpURLConnection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request method
            connection.setRequestMethod("GET");

            // Get response code
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse JSON response

                return new JSONObject(response.toString());
            } else {
                status.setText("Error: HTTP response code " + responseCode);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}