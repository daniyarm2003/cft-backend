package com.cft.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.core.io.ClassPathResource;

import java.io.*;

public class GoogleServices {

    @FunctionalInterface
    private interface GoogleServiceBuilderSupplier<T> {
        T get(HttpTransport transport, JsonFactory factory, HttpRequestInitializer initializer);
    }

    private static final String CREDENTIALS_FILE = "credentials.json";

    private static final HttpTransport HTTP_TRANSPORT = getHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final GoogleCredentials CREDENTIALS = getCredentials();

    public static final Sheets SHEETS_SERVICE = getSheetsService();
    public static final Drive DRIVE_SERVICE = getDriveService();

    private static HttpTransport getHttpTransport() {
        try {
            return GoogleNetHttpTransport.newTrustedTransport();
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static GoogleCredentials getCredentials() {
        ClassPathResource resource = new ClassPathResource(CREDENTIALS_FILE);
        InputStream inStream;

        try {
            inStream = resource.getInputStream();

            return GoogleCredentials.fromStream(inStream)
                    .createScoped(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE);
        }
        catch(IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static <T> T getGoogleServiceBuilder(GoogleServiceBuilderSupplier<T> supplier) {
        return HTTP_TRANSPORT == null || CREDENTIALS == null ?
                null : supplier.get(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(CREDENTIALS));
    }

    private static Sheets getSheetsService() {
        Sheets.Builder builder = getGoogleServiceBuilder(Sheets.Builder::new);
        return builder != null ? builder.setApplicationName("CFT Sheets").build() : null;
    }

    private static Drive getDriveService() {
        Drive.Builder builder = getGoogleServiceBuilder(Drive.Builder::new);
        return builder != null ? builder.setApplicationName("CFT Drive").build() : null;
    }
}
